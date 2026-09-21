# OMS recovery pilot

All HTTP paths are under `/api/v1`. Assignment requires `oms:assign`; evidence adjudication requires both `oms:recover` and `oms:evidence`; other commands require `oms:recover`; reads require `oms:read` and an authorized scope/account. Exception lists and exhausted-outbox lists filter scope/account before count and pagination, returning the common ApiResponse array plus pagination envelope.

| Route | Body / behavior |
| --- | --- |
| GET `/order-exceptions` | page, size (1..100), status, category, owner |
| POST `/order-exceptions/{id}/assign` | expectedVersion, operationKey, reason, owner |
| POST `/order-exceptions/{id}/resolve` | expectedVersion, operationKey, reason, action: RECONCILE / RETRY / WAIVE |
| GET `/order-exceptions/{id}/operations/{key}` | durable command receipt, authorized through its order and bound to this exception |
| POST `/orders/{id}/evidence/{evidenceId}/adjudications` | expectedVersion (order), operationKey, reason, authorityReference, occurredAt |
| GET `/orders/{id}/evidence-adjudications` | immutable adjudication records for this authorized order |
| GET `/order-outbox/exhausted` | page, size; no payload/error exposure |
| POST `/orders/{id}/outbox/{eventId}/recover` | expectedVersion, operationKey, reason |
| POST `/orders/{id}/tasks/{taskId}/no-effect` | expectedVersion, operationKey, reason, evidenceReference, occurredAt |
| POST `/orders/{id}/operations/{key}/abandon` | expectedVersion, separate recovery operationKey, reason |

Assignment/resolution expectedVersion means the **exception** version; all other recovery commands use the **order** version. Every mutation locks the order before the exception or delivery row. Nested execution commands use a deterministic child operation key and REQUIRED transactions. Same-body replay returns the current order version; a changed body or stale version is 409. Mutation responses are 202 with durable Operation receipts; SUCCEEDED means the transaction committed, not that an external effect completed.

WAIVE only accepts informational REVIEW_NOTE and records RESOLVED plus immutable WAIVE receipt. It never changes task/evidence/quantity/hold/finance state. Failure categories cannot be waived. RECONCILE invokes the executor and closes only categories whose persisted evidence/state proves resolution. Conflicting fulfillment observations may be disavowed only by an explicit append-only adjudication with authority reference/time/reason, both recovery and evidence permissions, and the current order version. Only unapplied CONFLICT evidence qualifies; applied evidence cannot be retracted. The original ledger and its CONFLICT state remain intact; adjudication never changes quantities or completes work. Correct operational evidence is recorded separately with a new external reference. The contradiction queue closes only when every conflicting observation is adjudicated; source/terminal corrections and finance contradictions remain in their dedicated workflows. RETRY delegates all eligibility checks to the executor, including unknown-outcome and held-order fences. No-effect reconciliation requires an operator's authoritative downstream result reference and timestamp after the current dispatch generation; it does not dispatch. Subsequent retry preserves the original external operation key.

Outbox recovery conditionally updates the same unpublished exhausted event, retaining its UUID and payload. Live leases cannot be reset. Update, receipt, order audit and new notification outbox insert commit atomically; the common REQUIRES_NEW recovery helper is deliberately not used.

Abandonment only reserves an absent client command identity. It cannot cancel or prove absence of external task operations. The original `(scope_id,operation_key)` is inserted into order_idempotency with a non-command tombstone hash under the order lock, along with immutable recovery_command_tombstones. If the original command already committed, abandonment fails. If abandonment wins, late delivery of the original command receives 409. Tombstone lookup must take precedence over ordinary SUCCEEDED lookup (shared OrderOperationController integration owned by coordinator). Abandoning an old key does not authorize replay of unknown external effects.

## Daily reconciliation

Scheduling is opt-in: `oms.recovery.enabled=true`, explicit comma-separated `oms.recovery.scopes`, and optional `oms.recovery.scan-delay-ms` (default 5000), and `oms.recovery.batch-size` (default 100, validated 1..100). Empty scopes scan nothing. Every scheduler tick scans at most its configured batch; each UTC day has a persisted snapshot upper time and `(created_at,id)` cursor per scope, including terminal orders. After a day's current window completes, newly arrived orders extend its upper bound and resume beyond the existing cursor on the next tick. Each completed window has an immutable recovery_daily_checkpoints record, so catch-up does not overwrite prior completion evidence. A fresh day starts a full scan; an incomplete prior day resumes first.

The defaults cover 100,000 orders in 1,000 batches: 5,000 seconds of scheduler delay (about 1.4 hours), plus actual SQL time. Even allowing 60 seconds of SQL per batch gives a theoretical daily capacity of 132,900 orders. This arithmetic is a configuration capacity check, not a 100k-row performance benchmark. Internal Micrometer gauges `oms.reconciliation.incomplete.runs`, `oms.reconciliation.oldest.run.age.seconds`, and `oms.reconciliation.since.completion.seconds` expose cached backlog and completion freshness without customer identifiers. Scrapes perform no database I/O; `oms.reconciliation.sample.age.seconds` starts at positive infinity and grows if refresh fails, while `oms.reconciliation.refresh.failures` increments and the last good sample remains available. No completed scan is represented by positive infinity, never a fresh zero. Sampling uses `oms.recovery.metrics-poll-ms` (default 30000) and per-query `oms.recovery.metrics-query-timeout-seconds` (default 5, allowed 1..30); operators must alert on approaching 24 hours and tune against measured database time.

A database row lock with SKIP LOCKED fences concurrent scanners. The batch transaction commits cursor, immutable findings, queue exceptions, audit and outbox together; rollback/crash leaves the prior cursor intact. There is no synthetic JWT or user impersonation: configured service scope authorizes this internal finding-only job, audited as oms-reconciler. It does not retry, release stock, accept financial proof, or complete tasks. Findings cover unknown outcomes, deadlines, exhausted attempts/events, expired allocations, pending/conflicting evidence, cumulative finance quantity and monetary mismatches (partial acknowledgments sum exactly; earlier unknown/rejected facts remain immutable), late contradictory finance outcomes, terminal and activation-dependency inconsistencies.

## Verification

From `backend` (Docker required; no skip-on-missing-Docker):

```powershell
./mvnw.cmd -pl services/order-management -am test '-Dtest=RecoveryIntegrationTest,RecoveryHttpIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dgroups=integration' '-DexcludedGroups='
./mvnw.cmd -pl services/order-management -am test '-Dtest=RecoveryIntegrationTest,ExecutionIntegrationTest,FulfillmentIntegrationTest,FinanceIntegrationTest,OrderPostgresIntegrationTest,ReplacementIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dgroups=integration' '-DexcludedGroups='
```

Behavioral red runs covered missing tombstones/authorization, daily scanning, external-operation abandonment, reconciliation closure and HTTP pagination. PostgreSQL tests cover original-command/abandon races, exception version races, unknown redispatch blocking, scoped pagination, immutable records, UUID preservation, live leases, atomic rollback and concurrent/crash-resumed cursors. Real HTTP tests use signed JWTs and PostgreSQL. Shared command-audit V7 hooks, shared order lookup ABANDONED precedence, and the fulfillment cancellation predicate excluding explicitly adjudicated CONFLICT observations (never PENDING evidence) are coordinator integration tasks. No browser UAT or production broker/provider reconciliation is established by these tests.
