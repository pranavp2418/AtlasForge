"""Real PostgreSQL checks. Requires an isolated synthetic test database, no cloud keys."""
import hashlib,os,pathlib,shutil,subprocess,sys,tempfile,urllib.request,uuid
ROOT=pathlib.Path(__file__).resolve().parents[1]
for key in ('ATLASFORGE_JDBC_URL','ATLASFORGE_PG_DSN'):
 if not os.environ.get(key):raise SystemExit(key+' must select an isolated test database')
def run(args,env=None):subprocess.run([str(x) for x in args],cwd=ROOT,check=True,env=env)
with tempfile.TemporaryDirectory(prefix='atlasforge-storage-') as directory:
 out=pathlib.Path(directory);jar=out/'postgresql-42.7.8.jar'
 with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar',timeout=30) as response:jar.write_bytes(response.read())
 if hashlib.sha256(jar.read_bytes()).hexdigest()!='2a32a9dcbc42d67a50ad3a0de5efd102c8d2be46720045f2cbd6689f160ab7c7':raise SystemExit('JDBC artifact checksum mismatch')
 sources=[]
 for folder in ('asterledger/core/src','asterledger/storage/src','asterledger/storage/tests'):sources+=sorted((ROOT/folder).rglob('*.java'))
 compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 run(compiler+['-d',out]+sources);cp=os.pathsep.join([str(out),str(jar)])
 run(['java','-cp',cp,'io.atlasforge.ledger.PostgresLedgerTest'])
 tenant='restart-'+uuid.uuid4().hex
 for mode in ('write','read'):run(['java','-cp',cp,'io.atlasforge.ledger.PostgresRestartProbe',mode,tenant])
 native=ROOT/'forgepulse/native'
 run(['gcc','-std=c11','-Wall','-Wextra','-Werror','-O2','-c',native/'crc32.c','-o',out/'crc32.o'])
 decoder=out/'forgepulse-decode'
 run(['g++','-std=c++20','-Wall','-Wextra','-Werror','-O2',native/'telemetry.cpp',native/'decode_cli.cpp',out/'crc32.o','-o',decoder])
 env=dict(os.environ,FORGEPULSE_DECODER=str(decoder))
 run([sys.executable,'-m','unittest','discover','-s','forgepulse/tests','-v'],env)
 for mode in ('write','read'):run([sys.executable,'forgepulse/tests/restart_probe.py',mode,tenant],env)
 print('Both real PostgreSQL slices and separate-process restart probes passed.')
