"""Durable bounded telemetry slice. Native decoder is a mandatory process boundary."""
from __future__ import annotations
import hashlib,re,subprocess
from pathlib import Path
import psycopg

class IngestError(ValueError):pass
class Conflict(IngestError):pass
class Gap(IngestError):pass

def ident(value):
 if not isinstance(value,str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}',value):raise IngestError('Invalid tenant/device identifier')
 return value

class TelemetryStore:
 def __init__(self,dsn,decoder,before_commit=None):self.dsn=dsn;self.decoder=str(Path(decoder).resolve());self.before_commit=before_commit or (lambda:None)
 def migrate(self):
  with psycopg.connect(self.dsn) as db:
   db.execute('SELECT pg_advisory_xact_lock(174151027)')
   db.execute('CREATE SCHEMA IF NOT EXISTS forgepulse')
   db.execute('CREATE TABLE IF NOT EXISTS forgepulse.schema_version(version integer PRIMARY KEY CHECK(version=1))')
   db.execute('INSERT INTO forgepulse.schema_version VALUES(1) ON CONFLICT DO NOTHING')
   versions=db.execute('SELECT version FROM forgepulse.schema_version').fetchall()
   if versions!=[(1,)]:raise IngestError('Unsupported schema version')
   db.execute('''CREATE TABLE IF NOT EXISTS forgepulse.devices(
    tenant text NOT NULL,device text NOT NULL,last_sequence bigint NOT NULL DEFAULT 0 CHECK(last_sequence>=0),
    last_event_ms bigint NOT NULL DEFAULT -1,PRIMARY KEY(tenant,device))''')
   db.execute('''CREATE TABLE IF NOT EXISTS forgepulse.samples(
    tenant text NOT NULL,device text NOT NULL,sequence bigint NOT NULL CHECK(sequence>0),
    event_ms bigint NOT NULL CHECK(event_ms>=0),temperature_milli integer NOT NULL,
    frame_hash text NOT NULL CHECK(length(frame_hash)=64),frame bytea NOT NULL CHECK(octet_length(frame)=28),
    PRIMARY KEY(tenant,device,sequence),
    FOREIGN KEY(tenant,device) REFERENCES forgepulse.devices(tenant,device))''')
 def register(self,tenant,device):
  ident(tenant);ident(device)
  with psycopg.connect(self.dsn) as db:db.execute('INSERT INTO forgepulse.devices(tenant,device) VALUES(%s,%s) ON CONFLICT DO NOTHING',(tenant,device))
 def decode(self,frame):
  if not isinstance(frame,bytes) or len(frame)!=28:raise IngestError('Exactly one bounded wire frame required')
  try:r=subprocess.run([self.decoder],input=frame,capture_output=True,timeout=3,check=False)
  except (OSError,subprocess.TimeoutExpired):raise IngestError('Native decoder unavailable; no data committed') from None
  if r.returncode!=0 or len(r.stdout)>160:raise IngestError('Native decoder rejected wire frame')
  try:sequence,event_ms,temperature=(int(x) for x in r.stdout.decode('ascii').split())
  except (ValueError,UnicodeError):raise IngestError('Native decoder response invalid') from None
  # PostgreSQL signed bigint is an explicit narrower service boundary than the uint64 wire.
  if not 1<=sequence<=2**63-1 or not 0<=event_ms<=2**63-1:raise IngestError('Wire sequence/time exceeds durable service contract')
  return sequence,event_ms,temperature
 def ingest(self,tenant,device,frame):
  ident(tenant);ident(device);sequence,event_ms,temperature=self.decode(frame);fingerprint=hashlib.sha256(frame).hexdigest()
  with psycopg.connect(self.dsn) as db:
   row=db.execute('SELECT last_sequence,last_event_ms FROM forgepulse.devices WHERE tenant=%s AND device=%s FOR UPDATE',(tenant,device)).fetchone()
   if row is None:raise IngestError('Unknown tenant/device; register before ingestion')
   last,last_time=row
   if sequence<=last:
    prior=db.execute('SELECT frame,frame_hash FROM forgepulse.samples WHERE tenant=%s AND device=%s AND sequence=%s',(tenant,device,sequence)).fetchone()
    if prior is None or bytes(prior[0])!=frame or prior[1]!=fingerprint:raise Conflict('Conflicting or corrupt replay')
    return {'status':'replay','sequence':sequence,'frame_hash':fingerprint}
   if sequence!=last+1:raise Gap('Sequence gap; recover missing frames before advancing')
   if event_ms<last_time:raise IngestError('Event time regressed; correction workflow not yet implemented')
   db.execute('INSERT INTO forgepulse.samples VALUES(%s,%s,%s,%s,%s,%s,%s)',(tenant,device,sequence,event_ms,temperature,fingerprint,frame))
   db.execute('UPDATE forgepulse.devices SET last_sequence=%s,last_event_ms=%s WHERE tenant=%s AND device=%s',(sequence,event_ms,tenant,device))
   self.before_commit()
   return {'status':'accepted','sequence':sequence,'frame_hash':fingerprint}
 def snapshot(self,tenant,device):
  ident(tenant);ident(device)
  with psycopg.connect(self.dsn) as db:
   db.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
   head=db.execute('SELECT last_sequence,last_event_ms FROM forgepulse.devices WHERE tenant=%s AND device=%s',(tenant,device)).fetchone()
   if head is None:raise IngestError('Unknown tenant/device')
   # Bounded response: the last 256 values. Full replay rows remain durable.
   rows=db.execute('SELECT sequence,event_ms,temperature_milli,frame_hash,frame FROM forgepulse.samples WHERE tenant=%s AND device=%s ORDER BY sequence DESC LIMIT 256',(tenant,device)).fetchall();rows.reverse()
   for row in rows:
    if hashlib.sha256(bytes(row[4])).hexdigest()!=row[3] or self.decode(bytes(row[4]))!=tuple(row[:3]):raise Conflict('Stored wire data failed native/hash validation')
   if rows:
    if rows[0][0]!=max(1,head[0]-255) or rows[-1][0]!=head[0] or rows[-1][1]!=head[1]:raise Conflict('Device head does not match durable history')
    for previous,current in zip(rows,rows[1:]):
     if current[0]!=previous[0]+1 or current[1]<previous[1]:raise Conflict('Stored sequence/time continuity failed')
   elif head[0]!=0:raise Conflict('Device head does not match durable history')
   return {'tenant':tenant,'device':device,'last_sequence':head[0],'last_event_ms':head[1],'samples':[{'sequence':r[0],'event_ms':r[1],'temperature_milli':r[2],'frame_hash':r[3]} for r in rows],'limits':'Last 256 samples shown; local adapter, no authenticated HTTP or hosted service claim.'}
