# Architecture decisions

## AsterLedger: financial event and accounting platform

AsterLedger operates on synthetic financial activity. It is not a payment processor or trading service. The independent kernel is an executable specification for the later PostgreSQL-backed service; it does not currently provide cross-process transactions or durable storage.

Planned connected capabilities:

1. Tenant-scoped identity, RBAC and service authorization with auditable admin operations.
2. Schema-versioned JSON/CSV ingestion, validation, rejection quarantine and resumable bulk imports.
3. Exact integer-minor-unit multi-leg journal transactions, reservations, reversals and account statements.
4. Transactional outbox to Kafka, consumer idempotency, ordering boundaries and replay tooling.
5. Settlement state machine with approval gates, exception ownership and retryable downstream handoffs.
6. Versioned fee rules, historical/as-of reporting and correction-aware backfills.
7. Account/currency reconciliation, close reports and traceable evidence exports.
8. Liquidity/limit monitoring and explicit scenario analysis using synthetic data.
9. Database indexes, query plans, partitioning where justified, and concurrent workload benchmarks.
10. Angular operator interface with responsive layouts, keyboard controls, clear pending/error states and live updates.
11. Docker packaging, Kubernetes deployment, automated CI/CD, rollout/rollback and disaster-recovery drills.
12. OpenTelemetry traces and metrics, CloudWatch integration, S3 audit exports and Lambda import jobs.

**Stack plan:** Java/Spring Boot, Angular/TypeScript, PostgreSQL, Kafka, Docker/Kubernetes, Terraform, Jenkins and GitHub Actions, AWS adapters. Start with a modular service and separate the ingestion worker when its retry lifecycle justifies a boundary. Every extracted service requires a documented responsibility and contract. Kafka and PostgreSQL must be exercised as real services before being marked verified.

**Core invariants:** balanced entries per currency; immutable committed postings; all tenant references scoped; identical idempotent requests return the same receipt; changed payloads conflict; replay never duplicates a posting; rejected work changes neither balances nor journal. Production adapters must enforce these under concurrent transactions and restarts.

## ForgePulse: predictive industrial operations

ForgePulse uses generated machine telemetry and fault scenarios. It is an offline operational decision-support platform, not a safety controller. Its C/C++ kernel owns bounded wire decoding and stream validation. Python owns durable orchestration, data transformations, models and APIs. React owns the operator experience.

Planned connected capabilities:

1. Tenant/site/device registration, RBAC and authenticated ingestion contracts.
2. Bounded C/C++ edge ingestion, checksummed frames, sequence fencing and offline replay.
3. Kafka telemetry transport, event-time windows, late-event quarantine and reviewed corrections.
4. PostgreSQL time-series storage, partition retention and query-plan optimization.
5. Feature lineage and training datasets split by device and time to prevent leakage.
6. Forecasting/anomaly baselines, model-version registry, calibration and drift monitoring.
7. Incident lifecycle: evidence capture, severity rationale, ownership, review and escalation.
8. Maintenance scheduling constrained by resources, machine downtime and deadlines.
9. Citation-grounded incident explanation via OpenAI APIs with explicit fallback, latency/cost limits and prompt-injection evaluations. Explanations cannot issue machine commands or bypass approval.
10. Human-approved remediation plans and reproducible simulation of downstream effects.
11. React responsive operations UI with timeline, device drill-downs, queue and model evaluation views.
12. Docker/Kubernetes packaging, Terraform/AWS adapters, CI/CD, OpenTelemetry/CloudWatch and restore/replay drills.

**Stack plan:** C/C++, Python/FastAPI, React/TypeScript, PostgreSQL, Kafka, OpenAI APIs, model evaluation tooling, Docker/Kubernetes and Terraform. Add a model-serving component only after evaluation proves it improves a baseline. Frameworks are selected from their current official documentation when implemented; dependency versions are locked.

**Core invariants:** corrupt data never enters feature state; replays are harmless within the explicitly bounded history; conflicting duplicates are rejected; gaps are recovered before advancing the sequence; correction workflows cannot silently rewrite history; every forecast links to the exact feature/model version; human approval fences operational actions.

## Shared operating model

Each project has its own service/schema/runtime boundary and acceptance backlog. Root tooling runs both suites without coupling domain state. GitHub Actions runs secret-free checks on trusted source; Jenkins integration will reproduce the same commands in a verified local pipeline. Configuration files alone do not count as working CI/CD.

Infrastructure validation starts with reproducible local service integration. No paid resources, plan upgrades or unapproved permission expansion. Cloud deployment is gated on available account access, runtime support and cost. A hosted UI alone does not count as a deployed distributed backend. If a host cannot run the essential services, report that early and retain the real runnable stack.

ADRs must explain consistency, failure semantics, schema evolution, tenancy, performance budgets, observability, recovery and the cost of any new service. No production-scale, HA, security-certification or company-affiliation claims without evidence.
