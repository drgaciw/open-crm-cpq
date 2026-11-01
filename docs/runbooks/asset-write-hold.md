# Asset write hold

The accepted-quote asset writer is disabled by default. The legacy writer creates
ACTIVE assets at quote acceptance, before ERP or manual subscription activation and
before future-effective rate periods, and can use synthetic line identities. It does
not implement replacement, termination, or ordered lifecycle projection. Do not set
`opencrm.assets.legacy-acceptance-write-enabled=true` in a deployed environment as a
way to enable the target lifecycle.

While the hold is active, quote acceptance still creates subscriptions and rate
periods and records the accepted-quote deduplication entry, but it creates no assets
or AssetCreated/AssetAuditRecorded events. Replaying the same acceptance after the
hold is lifted will be deduplicated and will **not** backfill those missing assets.
The hold neither removes nor repairs incorrect asset rows or events already written.
Pending outbox rows can still be published by the separately configured relay; review
them under the existing operational controls before enabling or resuming that relay.

Before production reliance, an operator must inventory affected acceptances, existing
assets and published events; reconcile them using verified line/product identities;
and deploy and verify the durable effective-date lifecycle and ordered replay path.
Record the reconciliation result and unresolved cases. Do not fabricate identities,
delete history, or publish retrospective events without that approved process.
