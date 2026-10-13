# AtlasForge

Two independent flagship engineering projects by Pranav Patel. **Work in progress.** Target release: **November 30, 2026**. These builds are additional to the three daily portfolio projects.

| Project | Product and engineering scope | Current verified work |
| --- | --- | --- |
| [AsterLedger](asterledger/) | Multi-tenant financial event ingestion, exact double-entry accounting, settlement, exception review, temporal reporting and operational recovery | Java reference ledger: atomic posting, idempotency, reversals, tenant/currency boundaries, balance invariants, audit chain and concurrent replay tests |
| [ForgePulse](forgepulse/) | Industrial telemetry ingestion, time-series features, predictive maintenance, evidence-grounded incident analysis, scheduling and model evaluation | C/C++ bounded frame decoder, sequence/replay fencing, rolling features and streaming replay CLI, independently checked by Python |

## Verify the first milestone

Requires Java 17+, GCC, G++ with C++20 support, and Python 3.10+. No cloud services or API keys are required for the current kernels.

```sh
python3 tools/verify.py
```

Build outputs and binary fixtures are created in a temporary directory. This is a runnable core milestone, **not a completed cloud platform**. The Java reference engine is in memory; production database persistence, service transport, authentication, web interfaces, Kafka, deployment and AI integrations remain planned. No real money or physical machinery is controlled.

## Review the work

- [Architecture and service boundaries](docs/ARCHITECTURE.md)
- [Acceptance criteria and November milestones](docs/ROADMAP.md)
- [Skill-to-feature evidence](SKILL_EVIDENCE_MATRIX.json)
- [Current progress](PROGRESS.json)
- [Daily work records](runs/)
- [Current hiring evidence informing the stack](docs/DEMAND_EVIDENCE.md)

Progress advances only when the corresponding code and checks exist. Planned infrastructure does not establish a verified skill. Synthetic workloads and model evaluations will document their seeds, workloads and limits. We will measure throughput, latency, query plans, memory and prediction quality before making performance claims.
