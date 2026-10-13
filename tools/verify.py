"""Build and exercise both initial kernels with no network services or dependencies."""
import json, pathlib, shutil, struct, subprocess, tempfile, zlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
def run(args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)
with tempfile.TemporaryDirectory(prefix="atlasforge-verify-") as directory:
    out = pathlib.Path(directory)
    compiler = [shutil.which("javac")] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    run(compiler + ["-d", out] + sorted((ROOT/"asterledger/core").rglob("*.java")))
    run(["java", "-cp", out, "io.atlasforge.ledger.LedgerEngineTest"])
    native = ROOT/"forgepulse/native"
    run(["gcc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2", "-c", native/"crc32.c", "-o", out/"crc32.o"])
    for source, target in [("tests.cpp", "tests"),("main.cpp", "forgepulse-replay")]:
        run(["g++", "-std=c++20", "-Wall", "-Wextra", "-Werror", "-O2", native/"telemetry.cpp", native/source, out/"crc32.o", "-o", out/target])
    run([out/"tests"])
    frames=[]
    for sequence in range(1,101):
        body=struct.pack("<4sQQi",b"FGP1",sequence,sequence*1000,20000+sequence*100)
        frames.append(body+struct.pack("<I",zlib.crc32(body)))
    fixture=out/"sample.bin";fixture.write_bytes(b"".join(frames))
    result=subprocess.run([str(out/"forgepulse-replay"),str(fixture)],capture_output=True,text=True,check=True)
    report=json.loads(result.stdout);assert report["last_sequence"]==100 and report["window_count"]==61
    assert abs(report["mean_celsius"]-27)<1e-9
    for bit in range(28*8):
        broken=bytearray(frames[0]);broken[bit//8]^=1<<(bit%8);fixture.write_bytes(broken)
        failure=subprocess.run([str(out/"forgepulse-replay"),str(fixture)],capture_output=True,text=True)
        assert failure.returncode==2 and not failure.stdout, (bit,failure)
    fixture.write_bytes(b"".join(frames)[:-1])
    failure=subprocess.run([str(out/"forgepulse-replay"),str(fixture)],capture_output=True,text=True)
    assert failure.returncode==2 and not failure.stdout
    print("Independent Python binary fixture passed: 100 frames; all 224 one-bit corruptions rejected; truncated input rejected.")
