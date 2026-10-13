# Milestones and release criteria

Target finish: November 30, 2026, America/Chicago. Dates are planning targets; completion requires evidence. No fabricated completion percentages.

| Gate | Target | AsterLedger | ForgePulse |
| --- | --- | --- | --- |
| M0 | October 13 | Reference ledger, monetary invariants, concurrent replay and fault-before-commit checks | Bounded C/C++ decoding, replay fencing, window oracle and independent Python wire fixtures |
| M1 | October 20 | PostgreSQL schema/migrations, durable posting API, tenant authorization, restart recovery | Durable telemetry ingestion API, tenant/device identity, storage, restart/replay integration |
| M2 | October 27 | Real Kafka outbox/consumer pipeline, retries, quarantine and settlement lifecycle | Real Kafka delivery, feature lineage, late-data/correction workflow and incident lifecycle |
| M3 | November 3 | Angular operator workflows, statements, approvals and evidence exports | React operator workflows, maintenance scheduling, ownership and audit exports |
| M4 | November 10 | Temporal reporting, fee/limit scenarios, query optimization and concurrent load benchmarks | Time-based ML baselines, backtesting, drift/calibration, optimized time-series queries |
| M5 | November 17 | Failure injection, replay/backfill, security boundaries and full recovery drills | OpenAI incident assistance with citations, offline fallback, evaluation and human-approved plans |
| M6 | November 24 | Containers, Kubernetes, Terraform/AWS adapters, Jenkins/CI and observability verified | Containers, Kubernetes, model/service monitoring, automated release and recovery verified |
| M7 | November 30 | Tagged release, complete core product flows, measured limits, demo/source/evidence and portfolio entry | Tagged release, complete core product flows, evaluated models, demo/source/evidence and portfolio entry |

## Daily execution

Complete a meaningful implementation or repair slice in **both** projects, verified and committed. Prefer one connected backlog item per project; research-only days must explain the blocker and do not count as implementation. Work does not count toward the daily three-project collection. Resume unfinished slices before creating substitutes. Maintain one dated run/lease per repository to prevent overlapping execution.

Each daily report states: code changed, acceptance items completed, tests and observed failures, commit links, deployed versus local state, blockers, next slice and milestone risk. A progress percentage is allowed only if its fixed denominator and evidence are explicit; default to completed acceptance items out of total.

## Release gate for each project

- All twelve architecture capabilities have implemented paths and documented limits.
- End-to-end ingest → validated persistent state → operation/review → export works after reload and process restart.
- Tenancy and RBAC tests cover unauthorized access and service boundaries.
- Real database/broker/container integrations have automated checks; mocks do not satisfy these gates.
- Race/idempotency/retry/recovery and schema-evolution failures preserve invariants.
- SQL optimization includes a stable workload, EXPLAIN ANALYZE, before/after plans and correctness comparison.
- Benchmarks report environment, seed, size, warmup, repetitions, latency distribution and resource cost; no copied scale targets.
- ForgePulse models are evaluated on temporal/device holdouts against a naive baseline; failure cases and uncertainty are shown.
- AI features have grounded evidence, invalid-output handling, timeout/cost controls and evaluation; a chat box alone is insufficient.
- Responsive and keyboard-accessible UI works on desktop/mobile with loading, empty and error states.
- CI, reproducible container build, rollout/rollback, observability and restore drills actually run.
- Essential services are deployed if access and existing/free resources support them. Any unavailable hosting dependency is a release blocker, not a fake success.
- Versioned documentation, demo data, runbook, skill evidence, source links and portfolio integration are verified.

By November 10, assess any dependency risk that threatens release; document a concrete mitigation without silently dropping the core scope. Re-scope only with a visible explanation and preserve correctness. November 30 is a target, not an automatic completion flag.
