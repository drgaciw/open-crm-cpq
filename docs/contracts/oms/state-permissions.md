# OMS state, permission and recovery contract

| Boundary | Required permission |
| --- | --- |
| Order, plan, timeline, operations, evidence, finance and exception reads | `oms:read` |
| Create from accepted quote / submit | `oms:submit` |
| Set/release manual hold | `oms:hold` |
| Quantity cancellation | `oms:cancel` |
| Pre-execution source replacement | both `oms:submit` and `oms:cancel` |
| Allocation, release, shipment, delivery, provision and service evidence | `oms:evidence` |
| Finance prepare / acknowledgment / canonical FINANCE_ACK | `oms:finance` |
| Exception assignment | `oms:assign` |
| Retry, reconcile, no-effect proof, outbox recovery, client-command abandonment | `oms:recover` |
| Conflicting evidence adjudication | both `oms:recover` and `oms:evidence` |
| Prometheus service scrape | `oms:monitor` plus exact deployment scope |

All domain boundaries also require verified issuer/audience, deployment scope and account membership.
Monitor access is deployment-scoped aggregate access, not an account wildcard. Internal executor,
expiry and reconciliation jobs use explicit service scope allowlists; no synthetic user JWT is used.
Feature enablement and trusted-broker prerequisites are independent of endpoint permissions.

## Quantities and completion

`ordered = cancelled + fulfilled + remaining`. Physical `delivered <= shipped <= ordered-cancelled`;
allocation cannot exceed unfulfilled quantity. Accepted commercial fields cannot be edited through
order commands. A new commercial revision requires pre-execution replacement, quote correction,
fresh source terms/recalculation/approval and a new acceptance; the former snapshot/order remain.

DRAFT submits after source/plan validation. Work or retained operational evidence makes the order
IN_PROGRESS. COMPLETED requires every obligation, including recurring activation and real finance
acknowledgment. PARTIALLY_COMPLETED additionally reflects explicit cancellation of the remainder.
CANCELLED requires no completed fulfillment and reconciled outstanding allocations/operations.
Manual holds block new dispatch; they do not delete history or override source holds.

Operation keys bind scope, target and command body; persisted receipts precede replay version checks.
An order version can advance more than once within a command that delegates to execution; use the
actual returned/current version. Evidence receipts use `version`; operation receipts use `orderVersion`.
Order status, task state, evidence application state and finance status are separate values.

## Unknown outcomes and adjudication

GET operation lookup returns the current order version together with durable receipt state.
SUCCEEDED means a local command committed. NOT_FOUND does not prove absence of an in-flight client
request or downstream effect. UNKNOWN/PENDING remain conservative client states requiring inquiry.
ABANDONED is a durable tombstone for an absent **client command**: recovery reserves its old key so
late delivery conflicts. The abandonment command itself uses a separate key. It cannot cancel or
prove absence of an external task operation. Tombstones must take precedence over ordinary receipt
lookup when the same old key has an idempotency row.

Unknown external effects need an authoritative provider no-effect reference and occurrence time at
or after the latest dispatch generation. Recording this proof does not dispatch. A later authorized
retry retains the original external operation identity and still passes holds, versions and leases.
Outbox recovery similarly requeues the existing unpublished exhausted UUID/payload and rejects a
live lease; operator recovery is not proof that the prior delivery had no effect.

An unapplied CONFLICT observation can be disavowed through append-only adjudication with reason,
authority reference/time, current order version and both permissions. The original fact and CONFLICT
state remain. Applied facts cannot be retracted. Quantities and task completion do not change.
All unadjudicated conflicts must be addressed before their contradiction queue closes; pending causal
facts still block unsafe cancellation. Correct replacement evidence uses a new external reference.
This route does not waive finance contradictions or source/terminal consistency checks.

RECONCILE closes only findings absent in persisted facts. RETRY invokes executor eligibility.
WAIVE is limited to REVIEW_NOTE and cannot complete work or bypass unknown-outcome safeguards.
Daily reconciliation scans configured scopes with bounded batches and durable cursors/checkpoints,
including terminal orders. It records findings atomically with audit/outbox; it does not manufacture
fulfillment or financial proof. Sampling and capacity/production alert validation are separate concerns.
