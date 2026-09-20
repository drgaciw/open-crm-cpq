# Quote snapshot producer (disabled by default)

Set `oms.quote.enabled=true`, a nonempty `oms.quote.scope-id`, and an explicit
`oms.quote.customer-ids` collection only after the legacy ownership fence is deployed.
No historical conversion. Disabling intake does not change previously persisted OMS ownership.

Quote UUID is the canonical identity; quote number is a stored alias. Each successful
acceptance persists its revision, immutable payload, UTF-8 SHA-256, holds, ownership,
and OMS outbox event in the acceptance transaction. PostgreSQL rejects snapshot updates/deletes.
An optimistic quote version and unique quote/revision constraint fence concurrent acceptance.

OMS metadata is captured only from the approved source quote/line `metadata.oms` object:
quote: address, taxEvidence, configurationReference; approvalReference is server generated on approval;
line: fulfillmentType, chargeType, tax (decimal string), startDate, endDate.
Existing source fields supply prices, discounts, totals, quantity, UOM and billingFrequency.
Missing facts are null and recorded as holds; no product catalog lookup or tax invention.
Missing facts require a newly approved quote; snapshots cannot be patched.

`GET /internal/oms/accepted-snapshots/{snapshotId}?quoteId={uuid-or-number}&acceptedRevision={n}`
returns `ApiResponse` with `data: {payload, snapshotHash, holds}`. Payload is the exact stored JSON
string and holds is a JSON array string. The endpoint is fail-closed, including in dev.
Configure `oms.quote.snapshot-issuer-uri`; verified JWT requires permissions containing
`oms:snapshot:read`, scope_ids containing deployment scope, and account_ids containing customer.
Read access remains available after disabling new intake. Wrong identity/account returns 404.

Docker integration tests are required to establish PostgreSQL transaction rollback, migration,
trigger and concurrent acceptance behavior. Unit tests do not establish production readiness.

Source command: `POST /internal/oms/quotes/{numericId}/source-terms` requires JWT
`oms:snapshot:write`, deployment scope and account membership. Body has expectedVersion,
address, taxEvidence, configurationReference and lines [{sourceLineId,fulfillmentType,
chargeType,tax,startDate,endDate}]. It requires DRAFT/REJECTED and exactly all current lines,
resets approval state/reference and requires subsequent approval. The ordinary approve command
persists an OMS approval reference; other approval paths lacking such reference remain held.

Approved classification is server configuration: `oms.quote.product-fulfillment-types` maps
product ID to PHYSICAL/DIGITAL/SERVICE; `oms.quote.classification-mapping-version` is mandatory.
Missing or mismatched requested classification becomes null in the snapshot plus a hold.
The configured version is captured in configurationReference alongside source evidence.

## Preexecution commercial correction

`oms.quote-revision-requested.v1` is consumed only when `oms.quote.revision-consumer-enabled=true`.
The disabled default cannot reopen a quote. Startup additionally requires
`oms.quote.revision-trusted-producer-configured=true`,
`spring.kafka.properties.security.protocol=SASL_SSL`, an explicit
`oms.quote.revision-producer-principal` naming the OMS producer, and the deployment `oms.quote.scope-id`.
This is an operational attestation gate: the broker must actually restrict topic WRITE to that OMS
principal and configure authenticated consumer/group READ. The application cannot remotely prove ACLs.

Only the exact canonical OMS-owned accepted revision with matching deployment/customer may reopen.
OMS must first prove no external work and cancel the old obligations; quote service trusts only that
producer contract, not an HTTP caller. Event ID and operation key bind the normalized command hash in
immutable durable receipt tables. Replays after later acceptance never reopen the source again.

Reopening preserves quote UUID, source line IDs and acceptedRevision, resets approval state/tier/reference,
and records a correction permit. Source terms may then be captured in corrected DRAFT state, and ordinary
CPQ editing/pricing and fresh review/approval/send/accept produce revision N+1. Capture consumes the permit.
Previous snapshots are immutable. No in-flight amendment, source payload patch, or browser-supplied economics
are introduced. V1_7 owns the correction receipt schema; V1_6 is reserved for the shared outbox relay upgrade.
