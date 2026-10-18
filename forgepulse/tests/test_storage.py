import concurrent.futures,os,pathlib,random,struct,sys,unittest,uuid,zlib
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'service'))
from storage import TelemetryStore,IngestError,Conflict,Gap
import psycopg

def frame(seq,value=25000,time=None):
 body=struct.pack('<4sQQi',b'FGP1',seq,seq*1000 if time is None else time,value);return body+struct.pack('<I',zlib.crc32(body))
class StorageTest(unittest.TestCase):
 def setUp(self):
  self.dsn=os.environ['ATLASFORGE_PG_DSN'];self.decoder=os.environ['FORGEPULSE_DECODER'];self.store=TelemetryStore(self.dsn,self.decoder);self.store.migrate();self.store.migrate();self.tenant='t-'+uuid.uuid4().hex;self.store.register(self.tenant,'pump');self.store.register(self.tenant+'-other','pump')
 def test_native_validation_atomic_corruption_and_gap(self):
  before=self.store.snapshot(self.tenant,'pump')
  for bit in range(224):
   broken=bytearray(frame(1));broken[bit//8]^=1<<(bit%8)
   with self.assertRaises(IngestError):self.store.ingest(self.tenant,'pump',bytes(broken))
  for data in (b'',frame(1)[:-1],frame(1)+b'x'):
   with self.assertRaises(IngestError):self.store.ingest(self.tenant,'pump',data)
  with self.assertRaises(Gap):self.store.ingest(self.tenant,'pump',frame(2))
  self.assertEqual(before,self.store.snapshot(self.tenant,'pump'))
 def test_replay_conflicts_tenants_and_restart(self):
  r=self.store.ingest(self.tenant,'pump',frame(1));self.assertEqual(r['status'],'accepted')
  fresh=TelemetryStore(self.dsn,self.decoder);self.assertEqual(fresh.ingest(self.tenant,'pump',frame(1))['status'],'replay')
  with self.assertRaises(Conflict):fresh.ingest(self.tenant,'pump',frame(1,26000))
  self.assertEqual(fresh.snapshot(self.tenant+'-other','pump')['samples'],[])
  with self.assertRaises(IngestError):fresh.ingest('unknown','pump',frame(1))
  with self.assertRaises(IngestError):fresh.ingest(self.tenant,'pump',frame(2,time=999))
  self.assertEqual(fresh.snapshot(self.tenant,'pump')['last_sequence'],1)
 def test_concurrent_replay_has_one_acceptance(self):
  def post(_):return TelemetryStore(self.dsn,self.decoder).ingest(self.tenant,'pump',frame(1))['status']
  with concurrent.futures.ThreadPoolExecutor(8) as pool:r=list(pool.map(post,range(32)))
  self.assertEqual(r.count('accepted'),1);self.assertEqual(r.count('replay'),31);self.assertEqual(len(self.store.snapshot(self.tenant,'pump')['samples']),1)
 def test_precommit_failure_and_retry(self):
  def fail():raise RuntimeError('Injected precommit failure')
  failing=TelemetryStore(self.dsn,self.decoder,fail)
  with self.assertRaises(RuntimeError):failing.ingest(self.tenant,'pump',frame(1))
  self.assertEqual(self.store.snapshot(self.tenant,'pump')['last_sequence'],0)
  self.assertEqual(self.store.ingest(self.tenant,'pump',frame(1))['status'],'accepted')
 def test_independent_100_frame_roundtrip(self):
  rng=random.Random(151026);expected=[]
  for seq in range(1,101):
   temperature=rng.randrange(-20000,80000);expected.append((seq,seq*1000,temperature));self.store.ingest(self.tenant,'pump',frame(seq,temperature))
  rows=TelemetryStore(self.dsn,self.decoder).snapshot(self.tenant,'pump')['samples'];self.assertEqual([(r['sequence'],r['event_ms'],r['temperature_milli']) for r in rows],expected)
 def test_stored_corruption_and_signed_bigint_boundary(self):
  with self.assertRaises(IngestError):self.store.ingest(self.tenant,'pump',frame(2**63,time=1000))
  self.store.ingest(self.tenant,'pump',frame(1))
  with psycopg.connect(self.dsn) as db:db.execute("UPDATE forgepulse.samples SET frame_hash=%s WHERE tenant=%s AND device='pump'",('0'*64,self.tenant))
  with self.assertRaises(Conflict):self.store.snapshot(self.tenant,'pump')
if __name__=='__main__':unittest.main()
