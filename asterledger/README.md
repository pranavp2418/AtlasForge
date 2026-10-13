# AsterLedger

Financial event, ledger and settlement infrastructure — **in progress**.

Current executable slice: `core/src/io/atlasforge/ledger/LedgerEngine.java`.

The reference kernel uses exact minor-unit arithmetic, tenant-scoped account identities, immutable receipts, a hash-linked journal, order-independent idempotency fingerprints, rejection before mutation, full reversals and synchronized in-process concurrency. Negative balances require explicit account configuration. Reversal cannot be repeated with a new key; retries return the original receipt.

Tests cover invalid balance/currency/tenant inputs, conflicting retries, state preservation under injected failure, eighty concurrent duplicate requests and one thousand independently accumulated balance checks. Run `python3 tools/verify.py` from the repository root.

**Limits:** reference state is in memory; no restart durability, external authentication, process-distributed consistency, real-money operation or cloud deployment is claimed. PostgreSQL/Spring Boot adapters and operational workflows are next. Hash chaining is an integrity aid, not an authenticated tamper-proof audit service. Planned capabilities and release gates are in `docs/`.
