# Unheld asset-write window: #713 → #735

**Linear:** DRG-932 (residual DoD AM09, FU-16 under DRG-652 W0-3)
**Reconciliation owner:** DRG-657 (one-off installed-base migration from subscriptions)
**Data action:** NO-GO. S7 stays NO-GO until G04 (drg). This record is read-only documentation.

## Window

| Event | Commit | Timestamp (CT) |
| --- | --- | --- |
| #713 merged (DRG-654: create asset when an order activates) | `aeb1993d` | 2026-11-01 09:13:54 CST (15:13:54 UTC) |
| #735 merged (fix: hold acceptance-time writes by default) | `0e931728` | 2026-11-01 11:42:24 CST (17:42:24 UTC) |
| **Duration** | | **2 hours 28 minutes 30 seconds** |

## What was unheld

PR #713 introduced `AssetActivation` as an unconditional `@Service`. On every quote acceptance
processed by `QuoteAcceptedEventConsumer`, the `AcceptedQuoteSubscriptionService` called
`AssetActivation.onActivated()`, which:

1. Created one `Asset` row per distinct `acceptedLineUuid` with `status = ACTIVE`.
2. Published an `AssetCreated` outbox event per asset (`cpq.asset.created`).
3. Staged an `AssetAuditRecorded` outbox row per asset (`cpq.asset.audit`).

PR #735 added `@ConditionalOnProperty(prefix = "opencrm.assets", name =
"legacy-acceptance-write-enabled", havingValue = "true")` with default off, disabling the writer.

### Known identity issues in the #713 writer

These were raised in the #713 review and are documented in the residual evidence:

- **Synthetic line IDs:** rate periods without an `acceptedLineUuid` were skipped, but periods
  carrying `legacy-aggregate` as the line UUID produced assets with that synthetic identity.
- **productName fallback:** when `productExternalId` was null or blank, `productName` was used as
  `productRef`, which is not a canonical product identity.
- **No predecessor reconciliation:** the writer created new assets without checking or linking to any
  preexisting asset for the same subscription and contract line.
- **No activation receipt:** the legacy writer did not create an `asset_activation_receipt` row. The
  `EffectiveAssetActivation` path (which does) was not active during the window. Any acceptance
  processed during this window will surface as `ACTIVATION_RECEIPT_MISSING` in the acceptance-level
  reconciliation findings, and affected assets as `ASSET_APPLIED_RECEIPT_MISSING` in the asset-level
  findings.
- **Timing:** assets were created at quote acceptance, before ERP or manual subscription activation,
  and before future-effective rate periods could be verified.

## Environments

No deployed (staging, production, or shared) environment ran this code — the project's Azure AKS
infrastructure is target-state, not landed. Whether any local development instance processed a quote
acceptance during the window is unknown; no execution evidence has been collected.

Commits between #713 and #735 that also touched `contract-asset-management/`:

| Commit | PR | Description |
| --- | --- | --- |
| `abafd089` | #719 | feat(DRG-656): queryable asset history and supersede chain |
| `5b1261bf` | #722 | fix(apply): persist replay evidence and refresh locked state |
| `47e34e5d` | #723 | fix: require installed-base read capability |
| `26a93f29` | #728 | test(cam): include replay evidence in schema guard |

## Read-only inventory queries

An operator can count affected rows using the existing `asset-reconciliation.sql` procedure against
an authorized nonproduction CAM database with the `V1_23` migration applied. See
[asset-reconciliation-inventory.md](asset-reconciliation-inventory.md) for the full procedure.

The following supplementary queries count rows specific to this window. They are **read-only** and
must be run inside a `REPEATABLE READ READ ONLY` transaction against the CAM schema. They do not
modify data and must not be run against a production database by a bot (S7 NO-GO).

### Count assets created during the window

```sql
-- Assets created between the #713 merge (2026-11-01 15:13:54 UTC) and
-- the #735 merge (2026-11-01 17:42:24 UTC).
SELECT count(*) AS asset_count,
       count(*) FILTER (WHERE contract_line_id = 'legacy-aggregate') AS legacy_aggregate_count
FROM assets
WHERE created_at >= '2026-11-01T15:13:54Z'
  AND created_at < '2026-11-01T17:42:24Z'
  AND NOT is_deleted;
```

### Count outbox events from the window

```sql
SELECT topic, status, count(*) AS event_count
FROM outbox_events
WHERE created_at >= '2026-11-01T15:13:54Z'
  AND created_at < '2026-11-01T17:42:24Z'
  AND topic IN ('cpq.asset.created', 'cpq.asset.audit')
GROUP BY topic, status
ORDER BY topic, status;
```

### Assets without an activation receipt

The legacy writer never created `asset_activation_receipt` rows. This query finds all assets
lacking a receipt (unwindowed), then flags which fall inside the write window.

```sql
SELECT count(*) AS total_missing_receipt,
       count(*) FILTER (WHERE a.created_at >= '2026-11-01T15:13:54Z'
                           AND a.created_at < '2026-11-01T17:42:24Z') AS in_write_window
FROM assets a
JOIN subscriptions s ON s.id = a.subscription_id
LEFT JOIN asset_activation_receipts r
       ON r.subscription_id = s.id AND r.contract_line_id = s.contract_line_id
WHERE NOT a.is_deleted
  AND r.activation_id IS NULL;
```

### List affected subscriptions

```sql
SELECT s.id AS subscription_id, s.customer_id, s.contract_line_id, s.status,
       a.uuid AS asset_id, a.contract_line_id AS asset_line_id, a.product_ref, a.status AS asset_status
FROM subscriptions s
JOIN assets a ON a.subscription_id = s.id
WHERE a.created_at >= '2026-11-01T15:13:54Z'
  AND a.created_at < '2026-11-01T17:42:24Z'
  AND NOT a.is_deleted
ORDER BY s.id, a.contract_line_id;
```

## Reconciliation

This window's affected rows are within scope of DRG-657 (one-off installed-base migration from
subscriptions). The hold on the legacy writer ([asset-write-hold.md](asset-write-hold.md)) neither
removes nor repairs incorrect asset rows or events already written.

Before production reliance, inventory these rows alongside the full reconciliation procedure in
[asset-reconciliation-inventory.md](asset-reconciliation-inventory.md). At the asset level,
`LEGACY_ASSET_LINE_ID` (synthetic `legacy-aggregate` contract line) and
`ASSET_APPLIED_RECEIPT_MISSING` (no receipt linked to the asset) are the most likely findings for
window-created rows. At the acceptance level, `ACTIVATION_RECEIPT_MISSING` flags subscriptions
whose acceptance was processed without a receipt. Record unresolved cases and do not fabricate identities, delete history,
or publish retrospective events without the approved process.

## References

- [asset-write-hold.md](asset-write-hold.md) — the hold itself
- [asset-reconciliation-inventory.md](asset-reconciliation-inventory.md) — full reconciliation SQL
- [effective-asset-activation.md](effective-asset-activation.md) — the durable activation path
- [s4-asset-lifecycle-hold.md](s4-asset-lifecycle-hold.md) — S4 lifecycle hold
- DRG-654 — create asset when an order activates (the story behind #713)
- DRG-657 — one-off installed-base migration (reconciliation owner)
- DRG-652 — epic: asset management / installed base bounded context
