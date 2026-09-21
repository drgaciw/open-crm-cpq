# OMS recovery pilot

All HTTP paths are under `/api/v1`. Assignment requires `oms:assign`; other commands require `oms:recover`; reads require `oms:read` and an authorized scope/account. Exception lists and exhausted-outbox lists filter scope/account before count and pagination, returning the common ApiResponse array plus pagination envelope.

| Route | Body / behavior |
| --- | --- |
| GET `/order-exceptions` | page, size (1..100), status, category, owner |
| POST `/order-exceptions/{id}/assign` | expectedVersion, operationKey, reason, owner |
| POST `/order-exceptions/{id}/resolve` | expectedVersion, operationKey, reason, action: RECONCILE / RETRY / WAIVE |
| GET `/order-exceptions/{id}/operations/{key}` | durable command receipt, authorized through its order and bound to this exception |
| GET `/order-outbox/exhausted` | page, size; no payload/error exposure |
| POST `/orders/{id}/outbox/{eventId}/recover` | expectedVersion, operationKey, reason |
| POST `/orders/{id}/tasks/{taskId}/no-effect` | expectedVersion, operationKey, reason, evidenceReference, occurredAt |
| POST `/orders/{id}/operations/{key}/abandon` | expectedVersion, separate recovery operationKey, reason |

Assignment/resolution expectedVersion means the **exception** version; all other recovery commands use the **order** version. Every mutation locks the order before the exception or delivery row. Nested execution commands use a deterministic child operation key and REQUIRED transactions. Same-body replay returns the current order version; a changed body or stale version is 409. Mutation responses are 202 with durable Operation receipts; SUCCEEDED means the transaction committed, not that an external effect completed.

WAIVE only accepts informational REVIEW_NOTE and records RESOLVED plus immutable WAIVE receipt. It never changes task/evidence/quantity/hold/finance state. Failure categories cannot be waived. RECONCILE invokes the executor and closes only categories whose persisted evidence/state proves resolution. Contradictory facts, source corrections, and terminal-state inconsistencies remain visible for their dedicated authoritative correction workflow. RETRY delegates all eligibility checks to the executor, including unknown-outcome and held-order fences. No-effect reconciliation requires an operator's authoritative downstream result reference and timestamp after the current dispatch generation; it does not dispatch. Subsequent retry preserves the original external operation key.

Outbox recovery conditionally updates the same unpublished exhausted event, retaining its UUID and payload. Live leases cannot be reset. Update, receipt, order audit and new notification outbox insert commit atomically; the common REQUIRES_NEW recovery helper is deliberately not used.

Abandonment only reserves an absent client command identity. It cannot cancel or prove absence of external task operations. The original `(scope_id,operation_key)` is inserted into order_idempotency with a non-command tombstone hash under the order lock, along with immutable recovery_command_tombstones. If the original command already committed, abandonment fails. If abandonment wins, late delivery of the original command receives 409. Tombstone lookup must take precedence over ordinary SUCCEEDED lookup (shared OrderOperationController integration owned by coordinator). Abandoning an old key does not authorize replay of unknown external effects.

## Daily reconciliation

Scheduling is opt-in: `oms.recovery.enabled=true`, explicit comma-separated `oms.recovery.scopes`, and optional `oms.recovery.scan-delay-ms` (default 60000). Empty scopes scan nothing. Every scheduler tick scans at most 50 orders; each UTC day has a persisted snapshot upper time and `(created_at,id)` cursor per scope, including terminal orders. Orders arriving after the day's snapshot are included on the next day. An incomplete prior day resumes first.

A database row lock with SKIP LOCKED fences concurrent scanners. The batch transaction commits cursor, immutable findings, queue exceptions, audit and outbox together; rollback/crash leaves the prior cursor intact. There is no synthetic JWT or user impersonation: configured service scope authorizes this internal finding-only job, audited as oms-reconciler. It does not retry, release stock, accept financial proof, or complete tasks. Findings cover unknown outcomes, deadlines, exhausted attempts/events, expired allocations, pending/conflicting evidence, cumulative finance quantity and monetary mismatches (partial acknowledgments sum exactly; earlier unknown/rejected facts remain immutable), late contradictory finance outcomes, terminal and activation-dependency inconsistencies.

## Verification

From `backend` (Docker required; no skip-on-missing-Docker):

```powershell
./mvnw.cmd -pl services/order-management -am test '-Dtest=RecoveryIntegrationTest,RecoveryHttpIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dgroups=integration' '-DexcludedGroups='
./mvnw.cmd -pl services/order-management -am test '-Dtest=RecoveryIntegrationTest,ExecutionIntegrationTest,FulfillmentIntegrationTest,FinanceIntegrationTest,OrderPostgresIntegrationTest,ReplacementIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dgroups=integration' '-DexcludedGroups='
```

Behavioral red runs covered missing tombstones/authorization, daily scanning, external-operation abandonment, reconciliation closure and HTTP pagination. PostgreSQL tests cover original-command/abandon races, exception version races, unknown redispatch blocking, scoped pagination, immutable records, UUID preservation, live leases, atomic rollback and concurrent/crash-resumed cursors. Real HTTP tests use signed JWTs and PostgreSQL. Shared command-audit V7 hooks and shared order lookup ABANDONED precedence are coordinator integration tasks. No browser UAT or production broker/provider reconciliation is established by these tests.
