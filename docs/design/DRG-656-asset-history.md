# DRG-656 — Queryable asset history and supersede chain

Placement versus CAM is still the open item DRG-561, so this read model stays in `contract-asset-management`. No new module.

## Queries

`AssetQueryController` adds three reads under `/api/v1/assets`:

- `GET /api/v1/assets/{assetId}/history`
- `GET /api/v1/assets/{assetId}/supersede-chain?direction=predecessors|successors|both` (default `both`)
- `GET /api/v1/assets?accountId={accountId}&asOf={yyyy-MM-dd}`

The list without `asOf` stays on `AssetController` from DRG-654. The as-of mapping requires the `asOf` parameter.

`accountId` is the asset field DRG-654 stores from `QuoteAcceptedEvent.customerId` and puts on `AssetCreated`. The projection reads that value from the fact into `asset_history` in this service. It does not call crm-service and it does not read another service's database.

## Projection

`asset_history` is append-only. `AssetHistoryProjection.apply(AssetCreatedEvent, eventId)` records a created fact from the published event. Amended, superseded, and terminated facts use the same row shape. Tests build those three with `AssetAmendedFixture`, `AssetSupersededFixture`, and `AssetTerminatedFixture`. Those fixtures are not in `EventContractRegistry`, they have no `KafkaTopics` entry, and this story adds no producer. DRG-658 and DRG-659 are the stories that register and produce them.

A second apply of the same `eventId` does not add a row. `dropProjection` deletes the rows so the same facts can be applied again. The integration test compares the history before the drop with the history after the replay.

An asset is in the account's installed base on `asOf` when a created or amended fact is effective on or before that date and no supersede or termination is effective on or before that date. A date before the created effective date returns an empty list. On the termination date, and after it, the asset is out. A superseded asset leaves on the supersede date. The successor is in the base from its own created date.

The supersede walk follows predecessor and successor links recorded on superseded facts, including links past the immediate neighbor. `direction` limits which side is returned.

## Migration

`V1_14__create_asset_history.sql` is the next free contract-asset-management version after `V1_13`, assigned when this change was written.

## Acceptance

- History query: read-side met, no producer yet (659/658).
- Supersede chain, both directions: read-side met, no producer yet (659/658).
- As-of installed base, including a date before activation: met on this read side.
- Replay: the integration test drops the projection and compares the rebuilt history.
