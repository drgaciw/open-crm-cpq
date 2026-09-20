# ADR gap implementation

Implementation branches share baseline `6c50370` (source baseline `50077a4`). The original workspace and its unrelated UAT changes are preserved. Integration is in `codex/adr-gap-integration`.

## Scope and ownership

| Lane | Implementation | Verification |
|---|---|---|
| ADR-008 | Configuration, approval, document, contract schemas in shared `opencrm`; legacy overrides; no migration checksum edits | Disposable PostgreSQL 16: four schemas, outbox isolation, history/checksums, legacy public schemas, refusal to baseline unmanaged data |
| ADR-013 | Canonical quote-scoped history includes completed decisions; BFF uses history endpoint | Routing and quote tests; legacy flag, fallback and deduplication remain |
| Runtime | AI moves to 8090, gateway default/dev routes, launcher and opt-in Compose profile | Actual gateway YAML binding, launcher syntax; live startup evidence recorded separately |
| ADR-014 | Catalog, decimal cost, Jev evaluation, deterministic policy ranking, constrained execution behind a disabled flag | Offline fixtures and mock HTTP; live canary remains a rollout gate |
| ADR-005 | Read-only readiness inventory | Default remains true; no environment cutover |

## Approvals readiness

Run `psql -X -v ON_ERROR_STOP=1 -f backend/scripts/approval-readiness.sql` with the target connection supplied through PostgreSQL environment variables or a service file. The query runs in a read-only transaction, emits an aggregate evidence record, and never flips configuration or includes customer payloads. A database error means evidence is unavailable, not that the count is zero.

Preserve its output with the environment, operator and change-ticket identity. A zero count satisfies only J5-A. Follow the [authoritative J5 checklist](forge/impl/j5-dual-read-readiness.md) for the separately authorized environment soak and rollback drill; the checked-in `${APPROVALS_LEGACY_LOCAL_READ:true}` remains unchanged.

## Database deployment

See [database-schema-alignment.md](database-schema-alignment.md). New defaults do not migrate existing standalone databases. Pin legacy database/schema overrides until the reviewed backup, migration rehearsal, validation and rollback procedure is complete.

## AI replay and rollout

`com.opencrm.ai.selection.SelectionReplayCli` accepts profile, catalog and evidence JSON files, an output report path, an explicit evaluation timestamp, a policy version, and `complete` or `partial`. It performs no network calls and never activates a policy. Partial evaluation cannot publish a winner. Evidence must match the exact profile and catalog versions.

Ranking rejects missing prices, restricted providers, failed capability probes, stale evidence, insufficient successful samples, low quality/confidence, excessive latency and excessive cost. Remaining candidates are ranked by total measured cost per success (including failures and evaluation overhead), quality, latency and stable model/provider key. Policy expiry cannot exceed catalog or benchmark freshness.

Live rollout requires workload-owner quality/latency/confidence/sample thresholds, permitted evaluator/execution providers, a spending cap, cost reconciliation, shadow/canary evidence and a rollback drill. No live result is implied by passing offline tests.
