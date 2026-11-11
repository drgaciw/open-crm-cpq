# ADR-037: Asset / installed base as its own bounded context

**Status:** Proposed on 2026-11-11. drg can change this to Accepted at review or merge. Placement vs CAM (DRG-561) stays open.
**Owner acceptance of the DRG-653 decision (context only, quoted verbatim):**
- [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653), issue description: "**Status: Accepted** (drg, 2026-10-29 14:11 Dublin, relayed by Projects Manager on [the delegation page](<https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82>))."
- Notion [Delegation of authority](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82) (page 402f7cb4e4df8167be33f86e92adfc82), "Owner approvals relayed 2026-10-29 14:11 Dublin": "ADR DRG-653 (asset management): Accepted."
- This file writes down that decision as a Proposed ADR. It accepts nothing beyond it.
**Date:** 2026-10-29 (decision); written up 2026-11-11.
**Deciders:** Project owner (drg amtd). The decision was accepted 2026-10-29 14:11 Dublin, relayed by Projects Manager. The status of this ADR file is for drg to set at review or merge.
**Backlog:** [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653) (parent DRG-652). Blocks DRG-654, DRG-655, DRG-656, DRG-657, DRG-660, DRG-665.
**Relates to:** [ADR-035](../renewals-amendments/adr-035-amendment-document-placement.md) (DRG-561, amendment document in CAM), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (outbox, envelope, registry), [ADR-010](ADR-010-kafka-outbox-events.md), [ADR-008](ADR-008-postgres-schema-per-service.md), [ADR-024](ADR-024-order-orchestration-context.md) to [ADR-027](ADR-027-compensation-and-point-of-no-return.md) (order model; DRG-577 reconciliation), DRG-558 (entitlement and commitment states), DRG-661 (subscription taxonomy).
**Does not authorize:** any code, migration, migration-number reservation, registry entry, flag change, backfill or deploy. Event schemas are registered only through their implementing stories after CPQ Decision scores the brief (DRG-653). DRG-657 and DRG-660 stay S7 NO-GO (delegation page, same entry).

---

## Context

- CPQ needs one record per thing a customer owns, so amendments, renewals and reports can act on it. DRG-653 calls this the Asset; the installed base is the set of assets per account.
- When DRG-653 was accepted, no asset code existed. Since then, implementing stories have added an asset package inside contract-asset-management (CAM). This ADR does not re-decide any of that work. It records what is there:
  - `Asset` entity, `assets` table: `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/Asset.java:20-77`, `.../db/migration/V1_13__create_assets.sql` (DRG-654).
  - Asset history projection, `asset_history` table: `V1_14__create_asset_history.sql`, [docs/design/DRG-656-asset-history.md](../design/DRG-656-asset-history.md) (DRG-656).
  - Read API: `AssetController.java:17,23,32` (`GET /api/v1/assets`, `/{id}`), `AssetQueryController.java:18,28,34,42` (history, supersede chain, as-of installed base), `AssetReportController.java:19,35,46` (upcoming renewals, churn).
  - Events registered by DRG-654 and DRG-655: `EventContractRegistry.java:175-193` (`AssetCreated`, `AssetAuditRecorded`, `AssetAmended`, `AssetTerminated`, `AssetSuperseded`), topics `KafkaTopics.java:54-64`.
  - Outbox emitter: `AssetEventPublisher.java:17,25` (uses `OutboxEventPublisher`).
  - Holds: [asset write hold](../runbooks/asset-write-hold.md) (`opencrm.assets.legacy-acceptance-write-enabled` off by default) and the [S4 asset-lifecycle hold](../runbooks/s4-asset-lifecycle-hold.md). Read access: [asset read access](../runbooks/asset-read-access.md) (`InstalledBase.Read`; coarse, not account isolation).
- Placement is still open. `Asset.java:22` says the entity "Lives in contract-asset-management until placement versus CAM (DRG-561) is decided", and [DRG-654 design](../design/DRG-654-asset-on-activation.md):8 says "The package may move later." ADR-035 (DRG-561) placed the amendment document and contract header in CAM. It does not place the Asset.

## Decision (the DRG-653 decision drg accepted on 2026-10-29)

The following is the decision text of DRG-653. Nothing is added to it.

### D-01: Aggregate
`Asset` (id, accountId, contractLineId, subscriptionId, entitlementId, productRef, quantity, status, effective dates, supersededBy/supersedes). Installed base = set of assets per account.

### D-02: Open Host Service API
Read/query + lifecycle commands, published language for other contexts.

### D-03: Schema-registry events
`AssetCreated`, `AssetAmended`, `AssetSuperseded`, `AssetTerminated`. This ADR holds Proposed schemas only. Each is registered only by its implementing story, after CPQ Decision scores that story's brief.

Proposed v1 field sets. These are the shapes already published by the implementing stories (`backend/libs/common/src/main/java/com/opencrm/common/event/dto/`). The ADR adopts no other shape:

| Event | Fields | Source |
|---|---|---|
| `AssetCreated` | assetId, accountId, contractLineId, subscriptionId, entitlementId, productRef, quantity, status, effectiveStart, effectiveEnd | `AssetCreatedEvent.java:15-25` |
| `AssetAmended` | assetId, accountId, contractLineId, subscriptionId, entitlementId, productRef, quantity, status, effectiveStart, effectiveEnd, effectiveDate, sourceAmendmentId, sourceApplyUnit | `AssetAmendedEvent.java:12-25` |
| `AssetSuperseded` | assetId, accountId, successorAssetId, predecessorAssetId, status, effectiveDate, sourceAmendmentId, sourceApplyUnit | `AssetSupersededEvent.java:13-21` |
| `AssetTerminated` | assetId, accountId, contractLineId, subscriptionId, productRef, status, effectiveDate, sourceAmendmentId, sourceApplyUnit | `AssetTerminatedEvent.java:11-20` |

`AssetAuditRecorded` (`EventContractRegistry.java:179-181`) came from DRG-654 and is not one of the four events in DRG-653.

### D-04: Outbox
Events are written transactionally through the outbox pattern (ADR-010, ADR-018).

### D-05: Related decisions
Placement vs CAM (DRG-561), relation to entitlement and commitment states (DRG-558), and the order model ADR-024 to ADR-027 (DRG-577). DRG-653 lists these as related. This ADR resolves none of them.

## What this ADR does not decide

- Whether the asset context becomes its own service or schema, or stays a package in CAM. It is open. ADR-008 authorizes no new schema.
- Migration numbers. None are reserved (DRG-653 AC).
- Lifting either asset hold, the installed-base migration (DRG-657) or lineage backfill (DRG-660).
- Account- or tenant-scoped read authorization (see [asset read access](../runbooks/asset-read-access.md) and ADR-036).

## Consequences

- Asset stories (DRG-654, 655, 656, 700) cite this ADR for the aggregate, events and outbox. They no longer cite the Linear issue alone.
- Because the code already lives in CAM, a future placement decision may have to move the `com.opencrm.contract.asset` package. Moving it is a separate story.
- The four DRG-653 events are already registered by their implementing stories (D-03). Changing their shapes needs a new event version under ADR-018.

## Alternatives considered

DRG-653 does not record any alternatives. CAM's existing subscriptions and entitlements came up as "Placement vs CAM", and that is still open (D-05). This ADR adds no alternatives of its own.

## References

- Linear [DRG-653](https://linear.app/drgamtd-workspace/issue/DRG-653) description and comments (2026-10-29 14:12 and 14:14 UTC), read 2026-11-11.
- Notion [Delegation of authority](https://app.notion.com/p/402f7cb4e4df8167be33f86e92adfc82), "Owner approvals relayed 2026-10-29 14:11 Dublin".
- Notion hub [Project OPEN-CRM-CPQ](https://app.notion.com/p/3e3f7cb4e4df8197abf7d804245c5df4) (Notion = design, Linear = status, Git = files).
- Repo at `d3256ae4`: files cited above; [docs/renewals-amendments/prereq/story-drg-654-asset-create-decision-brief.md](../renewals-amendments/prereq/story-drg-654-asset-create-decision-brief.md):13 ("Placement vs CAM (DRG-561) remains open").
