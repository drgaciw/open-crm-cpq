# OMS pilot implementation contract

This directory records the implemented pilot interfaces. It supplements the approved
[pilot contract](../oms-pilot-v1.md); it does not expand the authorized pilot into automatic ATP,
production ERP integration, in-flight commercial amendments, or production recovery guarantees.
The accepted fixture and original source requirements are preserved.

* [openapi.json](openapi.json): domain routes under `/api/v1`, plus the separately scoped
  `/actuator/prometheus` service endpoint. Commands use DTOs inside `ApiResponse`, never entities.
* [state-permissions.md](state-permissions.md): permissions, state boundaries, replay and recovery.
* `*-v1.schema.json`: additive published event/snapshot shapes. Unsupported `schemaVersion` values
  remain invalid; unknown fields do not authorize new behavior.

## HTTP behavior

All order queries and commands enforce the verified deployment `scope_id`, the requested
permission and explicit `account_ids` membership. Domain APIs use bearer JWTs; browser gateway
session and CSRF behavior is a separate boundary. The monitoring endpoint requires `oms:monitor`
and the exact deployment scope; it exposes cached aggregate metrics, not customer records.

POST `/orders/from-quote` uses the `Idempotency-Key` header and a previously trusted immutable
snapshot receipt. Other commands carry `operationKey`, `expectedVersion` and `reason` in the body.
Assignment/resolution versions refer to the exception; all other command versions refer to the
order. Same-body replay retains the key. A changed body or stale new command returns 409.

Order, finance prepare, finance acknowledgment, submit, hold and cancellation commands return
200 with the current order DTO. Retry/reconcile return 202 `{orderId,status:ACCEPTED}`. Replacement
returns 202 `{operationKey,orderId,eventId,orderVersion,status:SUCCEEDED}`. Recovery commands return
202 `{operationKey,orderId,status,orderVersion}`. These are local transaction receipts, never claims
that remote fulfillment, subscription activation or invoicing succeeded.

Canonical `/orders/{id}/evidence` delegates non-finance kinds to the fulfillment ledger:
200 `{orderId,evidenceId,status:APPLIED,version}` or 202 with `PENDING`/`CONFLICT`. Retained pending
or contradictory facts do not increment fulfillment quantities. The compatibility route
`/fulfillment-evidence` has the same fulfillment receipt contract and excludes `FINANCE_ACK`.
Canonical `FINANCE_ACK` implies outcome `ACKNOWLEDGED` and returns an order DTO with 200.
`/finance-acknowledgments` requires an explicit `ACKNOWLEDGED`, `REJECTED` or `UNKNOWN` outcome.

Application failures use `ApiResponse.success=false`: 400 malformed/binding validation,
403 missing permission, non-disclosing 404 wrong account/resource, 409 version/identity/state conflict,
422 semantic rejection and 503 temporary new-intake backlog pressure. Authentication failures are
401 and may use the security framework response rather than an ApiResponse body. Error codes and
trace IDs are optional; canonical handlers use `OMS_<status>`. Preserve the operation identity across
uncertain delivery and temporary rejection. Existing durable intake receipts replay before tightened
byte/line/backlog checks; new conversion is disabled independently of draining existing orders.

GET `/orders` defaults to page 0, size 20 (maximum 100), with `q`, `status`, `customerId`,
`externalReference` and `hasExceptions`. Search is scoped before count/pagination. `q` matches order
or quote numbers; external reference is an exact match against fulfillment/allocation/shipment,
finance acknowledgment or audit references. `hasExceptions` means an OPEN exception. Exception and
exhausted-delivery lists default to size 25, maximum 100. Exception filters are status/category/owner;
there is no implemented severity filter. List envelopes contain an array in `data` and a separate
`pagination` object. Per-order timeline, plan, evidence, allocations and finance lists are arrays.

## Evidence and economics

The pilot is manual: authorized operators record real external references, quantities and occurrence
times. It does not manufacture carrier, provisioning, invoice or no-effect identifiers. Test fixtures
and local drills are fictional evidence used only in isolated tests, not operational authority.

ALLOCATION requires an expiry; its externalReference identifies the allocation lot. A new allocation
must expire in the future to apply; expired observations remain CONFLICT evidence. SHIPMENT and
RELEASE supply that lot identifier as allocationReference; DELIVERY references a shipment. Earlier delivery is retained pending its
causal shipment. Expiry opens an exception; it does not itself invent release or shipment proof.
Partial quantities and replay are cumulative and capped by accepted obligations.

Finance prepare requires released prerequisites and fulfilled due quantity; physical shipments must
be delivered. It freezes source line billing terms, tax evidence, milestones and integer-cent amounts
under a stable request key, and persists `oms.finance-requested.v1`. Order finance status becomes
REQUESTED, while the request remains PENDING until actual acknowledgment. No request is an invoice.
ACKNOWLEDGED observations accumulate positive quantities; discount/tax use cumulative integer-cent
allocation with deterministic remainder. Request statuses are PENDING/PARTIAL/ACKNOWLEDGED/REJECTED/
UNKNOWN; order finance status additionally includes REQUESTED. Rejection/unknown retains history and
opens an exception. Only full acknowledged quantity completes FINANCE_ACK; full fulfillment,
required activation and finance obligations gate order completion. Unknown outcomes cannot obtain
a fresh request identity to evade reconciliation.

## Shared events and ownership

| Topic | Publisher / consumer | Published shape |
| --- | --- | --- |
| `oms.quote-accepted.v1` | Quote / OMS and contract source registry | eventId, immutable snapshot, snapshotHash |
| `oms.activation-requested.v1` | OMS / contract-asset | Exact accepted source line, canonical identity, operation key and fulfillment proof |
| `oms.activation-acknowledged.v1` | Contract-asset / OMS | Correlated subscription acknowledgment and authoritative reference/time |
| `oms.quote-revision-requested.v1` | OMS / quote | Schema v1, event and operation identity, exact prior accepted revision, scope/customer and reason |
| `oms.finance-requested.v1` | OMS / external finance handoff | Schema v1, eventId, stable requestKey, order/line/source identity, snapshotHash, sourceLine, quantity, currency, integer-cent allocations, taxEvidence and milestones |

For finance request events, persisted request JSON omits only the eventId added to the outbox event;
the economics are frozen. The manual acknowledgment endpoint is the pilot's external finance proof.

Snapshot receipt verification hashes the compact raw nested snapshot JSON, including additive fields,
before typed decoding. Do not reserialize a DTO and use that as the original hash. Stored snapshot
payloads/hashes and accepted revisions are immutable. The quote service also exposes authenticated
`GET /internal/oms/accepted-snapshots/{snapshotId}` with canonical quote/revision selectors and
`oms:snapshot:read`; it is not an unauthenticated internal bypass.

Contract-asset is the sole subscription writer. Activation requires its registered trusted accepted
source, exact source line, canonical quote UUID/revision, alias legacy-overlap checks and source
recurring economics; monthly source charges annualize by 12, annual charges by 1. Broker topic ACL,
trusted principal and SASL_SSL attestation are deployment prerequisites, not something payload fields
can prove. Broker receipt/dedup and transactional outbox behavior do not make arbitrary publishers trusted.

## OMS migration allocation

Migrations belong to the `order_management` schema; their numbers are unrelated to quote/contract
service migration numbers. Use a separate migration owner, then apply the runtime grants policy.

| Version | File / ownership |
| --- | --- |
| V1 | `V1__orders.sql`: snapshots, orders/lines/tasks, identity receipts, order audit, outbox |
| V2 | `V2__execution.sql`: fenced task operations, exceptions, inbox, no-effect reconciliation |
| V3 | `V3__fulfillment.sql`: immutable facts/identities/receipts, application state and allocation lots |
| V4 | `V4__finance.sql`: immutable requests and acknowledgment ledger |
| V5 | `V5__recovery.sql`: tombstones, recovery receipts, daily cursors/findings/checkpoints and adjudications |
| V6 | `V6__replacement.sql`: source correction requests and replacement lineage |
| V7 | `V7__command_audit.sql`: immutable operation-correlated before/after version journal |
| V8 | `V8__operational_lookup_indexes.sql`: intake hash, pending outbox and reference lookup indexes |

CI/live acceptance results, production secrets, alert routing, load evidence and production restore
RPO/RTO are outside this interface contract. A schema or endpoint definition is not deployment proof.
