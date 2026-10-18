# M1 durable-storage slice

This is local verified storage integration, not a complete M1 service or a deployed flagship. The PostgreSQL schemas are separate; no real funds or machine data are used. Authentication/RBAC, Spring Boot/FastAPI routes, retention, pooling, Kafka and later milestones remain pending.

AsterLedger uses JDBC 42.7.8 and the existing Java reference kernel inside a PostgreSQL transaction. A tenant row lock serializes that tenant's writes; every transaction restores and validates its immutable posting history, applies the kernel operation, and commits the new journal receipt atomically. Account and posting keys are tenant-composite primary keys. Duplicate identical posts return the original receipt; changed payloads conflict. Reversals remain kernel-checked. Reads use repeatable-read snapshots. History replay is intentionally linear, so no production throughput/query optimization claim is made. Per-tenant chains differ from a single cross-tenant global journal.

ForgePulse passes every incoming 28-byte frame through the existing C/C++ decoder process before SQL. PostgreSQL locks the tenant/device head, rejects gaps, regressing event times and changed replays, and atomically commits the original frame/hash plus decoded fields and sequence head. Replay equality includes original bytes. A snapshot rereads and validates the last 256 stored frames with the actual native decoder. Full raw history persists; retention and authenticated ingestion remain later gates. Wire uint64 sequence values above signed bigint max are explicitly rejected at the storage boundary. Native process-per-frame overhead is not a production ingestion design or measured throughput claim.

Both adapters accept an already authorized tenant from their caller. They do not authenticate it. Database-owner tampering is not prevented by hashes or application-scoped SQL. Do not expose these adapters directly to untrusted users before the M1 transport/auth slice. Only isolated synthetic test databases are authorized for these commands.

Reproduce with Java 17+, GCC/G++ C++20, Python 3.10+, Docker and network access to the pinned JDBC artifact. The runner verifies the JDBC SHA256 before use. PostgreSQL 17 image digest verified in the initial run: `sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24`.

```sh
docker run -d --name atlasforge-test-pg -p 127.0.0.1:55432:5432 \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=atlasforge_test \
  postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24
python3 -m venv .build/venv
.build/venv/bin/pip install -r tools/storage-requirements.txt
ATLASFORGE_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/atlasforge_test \
ATLASFORGE_PG_DSN='host=127.0.0.1 port=55432 dbname=atlasforge_test user=postgres' \
.build/venv/bin/python tools/verify_storage.py
```

The loopback-only, trust-authenticated disposable container above is exclusively a local test fixture. It is not a production authentication configuration. Existing environments must preserve their injected Docker/network/CA settings. Containers and dependency artifacts are not committed. Stop/remove only the test container you created when finished.

Verification covers real SQL, duplicate and conflicting replay, tenant isolation, monetary/native-decoder invariants, injected failures before commit, independent balances/frames and separate-process recovery. Future additions must retain the original M0 tests. No Docker/Kubernetes/cloud skill is inferred solely from configuration.
