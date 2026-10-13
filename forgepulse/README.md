# ForgePulse

Predictive industrial operations — **in progress**.

Current executable slice: native C/C++ telemetry validation and rolling-window feature analysis. The CLI streams fixed-size frames, validates framing/checksums before use, fences duplicate/conflicting sequences, requires gap recovery and rejects late events pending a future correction workflow. Rolling windows have time and count bounds; replay history is explicitly capped at 256 samples. Means/min/max and stable linear trend are real calculations, not a trained ML model.

## Wire contract

A frame is exactly 28 bytes, little endian: four-byte `FGP1` magic, uint64 sequence, uint64 nonnegative event milliseconds, int32 temperature in millidegrees Celsius, then IEEE CRC32 over the first 24 bytes. Sequence starts at one; timestamps are nondecreasing; temperature lies between -273150 and 200000. CRC detects accidental corruption and provides no cryptographic authenticity. Tenant/device authorization belongs in the planned service boundary.

`python3 tools/verify.py` builds the replay CLI in a temporary directory and exercises its input-to-JSON workflow with an independent Python encoder/CRC. All 224 one-bit corruptions and truncated input must fail without producing a success report. The C/C++ suite separately checks sequence rules and rolling features against a direct reference.

**Limits:** sample data are synthetic; no machine is controlled. Transport authentication, persistent checkpoints, APIs, learned prediction, UI, Kafka and deployment remain planned. The kernel does not claim recovery from allocation failure or security against authenticated adversaries. See the architecture and roadmap for the full product scope.
