# DRG-654 — Asset created when an accepted quote activates

Placement is the open ADR item **Placement vs CAM (DRG-561)**. This story puts the asset package inside `contract-asset-management`. The package may move later. No new module and no root `pom.xml` change.

## What is created

On a successful apply in `AcceptedQuoteSubscriptionService` (new sale and subscription change), one `Asset` is written per distinct accepted contract line (`acceptedLineUuid` on the rate periods just saved). Ramp steps of the same line stay one asset. Replay of the same accepted quote returns before that call, and the asset write itself is idempotent on `(subscription_id, contract_line_id)`, so a second call does not emit a second `AssetCreated` or a second audit row.

`accountId` is `QuoteAcceptedEvent.customerId` (`CustomerSnapshot`). That is the same key CAM subscriptions store. There is no `customer_id` column on `assets`.

`entitlementId` is set only when `EntitlementLookup` returns an id. The production lookup is empty. Follow-up: [DRG-700](https://linear.app/drgamtd-workspace/issue/DRG-700).

## Events

Both rows are written with `OutboxEventPublisher` in the activation transaction.

| Contract | Topic | Producer |
| --- | --- | --- |
| `AssetCreated` v1 | `cpq.asset.created` | `AssetActivation` |
| `AssetAuditRecorded` v1 | `cpq.asset.audit` | `AssetAuditOutbox` |

`AssetCreated.accountId` uses the account field named on the accepted DRG-653 aggregate (`accountId`, `contractLineId`, `subscriptionId`, `entitlementId`, `productRef`, `quantity`, `status`, effective dates). The ADR does not publish a separate event-field list. `accountId` is that aggregate field, not an extra name.

`AssetAuditOutbox` follows `AmendmentAuditOutbox`: `Propagation.MANDATORY`, one `publish` of `EventContractRegistry.ASSET_AUDIT_RECORDED_V1` inside the domain transaction.

## API

`GET /api/v1/assets` and `GET /api/v1/assets/{id}` on contract-asset-management. The existing security chain already requires an authenticated token for `/api/v1/**`.

## Migration

`V1_13__create_assets.sql` is the next free contract-asset-management version after `V1_12`, assigned when this change was written. Nothing was reserved ahead of the build.
