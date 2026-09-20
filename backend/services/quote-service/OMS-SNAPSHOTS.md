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
