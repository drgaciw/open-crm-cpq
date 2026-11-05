# OMS pilot contract v1

All APIs use ApiResponse DTOs, never entities; /api/v1/orders and /api/v1/order-exceptions.
Dates ISO-8601; USD amounts exact decimal strings in snapshot, integer positive quantities.
Status is derived by server commands. 409 version/idempotency conflict, 422 semantic failure,
403 missing permission, non-disclosing 404 for wrong scope/account, 202 persisted async command.

## Snapshot published language

Common package com.opencrm.common.order. AcceptedOrderSnapshot Java record fields:
schemaVersion(int=1), snapshotId(String), quoteId(String canonical UUID), quoteNumber(String alias),
acceptedRevision(int), scopeId(String), customerId(String), acceptedAt(Instant), currency(String),
subtotal(String), discount(String), tax(String), total(String), address(String),
approvalReference(String), taxEvidence(String), configurationReference(String), lines(List<Line>).
Nested Line record: sourceLineId(String), productId(String), description(String),
fulfillmentType(String PHYSICAL/DIGITAL/SERVICE), chargeType(String ONE_TIME/RECURRING),
quantity(int), uom(String), unitPrice(String), discount(String), tax(String), total(String),
billingFrequency(String ONE_TIME/MONTHLY/ANNUAL), startDate(LocalDate), endDate(LocalDate).
Snapshot JSON stored verbatim with SHA-256 UTF-8 hash, not reconstructed from mutable masters.
Order intake consumes exact stored payload plus hash. Null evidence yields explicit hold, never defaults.
Missing commercial facts require newly approved source revision, not editing snapshot. In-flight
amendments held. Duplicate sourceLineId invalid. Sum lines and header economics must reconcile.

AcceptedSnapshotEvent record: eventId(String), snapshot(AcceptedOrderSnapshot), snapshotHash(String).
Topic cpq.oms.quote-accepted.v1. Legacy quote accepted event gets additive fulfillmentOwner=OMS|LEGACY;
OMS cohort must never activate through legacy path. Intake marker and event persist atomically.
ActivationRequest record: eventId, operationKey, orderId, scopeId, customerId, quoteId (String),
acceptedRevision(int), line(AcceptedOrderSnapshot.Line), evidenceReference(String), completedAt(Instant).
Topic oms.activation-requested.v1. ActivationAcknowledgment: eventId, operationKey, orderId,
scopeId, subscriptionId, status, evidenceReference (String), occurredAt(Instant).
Topic oms.activation-acknowledged.v1. Contract service is sole subscription writer. Identity:
scope+quoteId+acceptedRevision+sourceLineId. No order-total-as-ARR; monthly annualization *12,
annual *1; source dates mandatory. Idempotent durable inbox/outbox and persisted acknowledgments.

## REST DTOs (new contracts)

POST /orders/from-quote: Idempotency-Key header, {snapshotId,quoteId,acceptedRevision}; authenticated
source lookup or persisted event inbox supplies snapshot, never trust client commercial fields.
GET /orders?page=0&size=20&q=&status=&customerId=; GET /orders/{id}; plan and timeline subresources.
Commands carry expectedVersion(long), reason(String), operationKey(String).
POST /orders/{id}/submit; /holds {active:boolean}; /cancellation-requests {lineId,quantity};
/evidence {lineId,kind:ALLOCATION|SHIPMENT|DELIVERY|SERVICE|FINANCE_ACK,quantity,externalReference,occurredAt};
/reconcile; /tasks/{taskId}/retry. GET /order-exceptions; POST /order-exceptions/{id}/assign {owner};
POST /order-exceptions/{id}/resolve {action:RECONCILE|RETRY|WAIVE,reason,expectedVersion,operationKey}.
GET /orders/{id} yields id,orderNumber,quoteId,quoteNumber,customerId,scopeId,status,version,
snapshot,lines,holds,financeStatus. Line projections: id,description,ordered,cancelled,shipped,
delivered,remaining,allocated,status. Timeline entries actor,at,action,reason,version,evidenceReference.

## State, identity and security

DRAFT -> SUBMITTED after validation -> IN_PROGRESS at first work -> COMPLETED only when every
required obligation and activation/finance acknowledgment succeeds. CANCELLED only with no
fulfillment and all releases reconciled. PARTIALLY_COMPLETED only after remaining work explicitly
cancelled. Holds prevent new dispatch; no unknown-outcome retry without reconciliation.
ordered=cancelled+grossFulfilled+remaining; delivered<=shipped<=ordered-cancelled; allocation
cannot exceed unfulfilled. Delivery before shipment is retained as pending evidence, not discarded.
Each evidence operation key binds body hash; same body replay succeeds, different body conflicts.
Mutation/audit/outbox in one transaction. Active lease fencing prevents stale worker commits.

JWT verified by configured issuer. Trusted claims: scope_id (deployment), account_ids (array),
permissions (array of oms:read, oms:submit, oms:hold, oms:cancel, oms:evidence, oms:finance,
oms:recover, oms:assign). Missing claims deny; no wildcard customer access. Match configured scope
and account membership for queries and commands. Service identities have explicit service permission
and scope allowlist, never derive authority from payload alone. Domain services stateless; gateway CSRF.
No production auth bypass. Finance evidence needs oms:finance; other evidence oms:evidence.
Audit cannot be changed/deleted by normal application role. Payloads/addresses not in logs.

## Adapter policy

Manual capability: operator-assigned allocation, release, dispatch evidence, status reconciliation.
No automatic ATP guarantee, no synthetic invoice/provisioning identifiers. Unknown response never
counts as success. Exceptions include category, owner, next action, first/last timestamps. Daily
reconciliation and pre-redispatch reconciliation preserve contradictory facts for operator review.

## Additive pilot correction and evidence contracts

Evidence kinds additionally include RELEASE and PROVISION. Allocation evidence carries expiresAt
and allocationReference; shipment/release consumes that allocationReference. DELIVERY references
shipmentReference, retaining a pending fact if the shipment has not arrived yet. No pending fact
increments delivered quantities until its valid causal shipment is reconciled.

POST /orders/{id}/replacement-requests carries the standard versioned Command. OMS requires no
external dispatch, allocation, fulfillment, activation or finance effect and no unresolved operation.
It atomically cancels the original obligations and emits oms.quote-revision-requested.v1 with
{schemaVersion:1,eventId,operationKey,orderId,quoteId,acceptedRevision,scopeId,customerId,reason}.
Quote service verifies trusted producer/scope and locks the exact OMS-owned ACCEPTED revision;
a durable inbox makes replay harmless. It returns the source quote to DRAFT and invalidates prior
approval. acceptedRevision increments only at the next fresh approved acceptance. Old snapshots
and the cancelled order remain immutable. A new order for that revision records replacement lineage.
This contract grants no in-flight amendment or cancellation of externally started work.

GET /orders/{id}/operations/{operationKey} and /order-exceptions/{id}/operations/{operationKey}
return {operationKey,orderId,status,orderVersion}. SUCCEEDED means the command transaction was
committed, not that external effects succeeded. NOT_FOUND is not permission to resend using a new
key. UNKNOWN/PENDING require reconciliation. Same-body retries retain the original operation key.
