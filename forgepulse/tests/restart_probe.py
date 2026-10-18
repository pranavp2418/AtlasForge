import os,pathlib,struct,sys,zlib
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'service'))
from storage import TelemetryStore
store=TelemetryStore(os.environ['ATLASFORGE_PG_DSN'],os.environ['FORGEPULSE_DECODER'])
mode,tenant=sys.argv[1:]
if mode=='write':
 store.migrate();store.register(tenant,'pump');body=struct.pack('<4sQQi',b'FGP1',1,1000,42420);store.ingest(tenant,'pump',body+struct.pack('<I',zlib.crc32(body)))
else:
 snapshot=store.snapshot(tenant,'pump');assert snapshot['last_sequence']==1 and snapshot['samples'][0]['temperature_milli']==42420
print('Telemetry process',mode,'verified')
