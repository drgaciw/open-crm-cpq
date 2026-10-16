# CRM DealType to quote DealType (DRG-538)

Applied in `QuoteService.create` by `CrmQuoteDealTypeMapping`. The CRM name is not stored. `quotes.deal_type` stays one of `NEW`, `AMENDMENT`, `RENEWAL`, `EXPANSION`. No migration.

| CRM DealType | Quote DealType | Status |
|---|---|---|
| `NEW_BUSINESS` | `NEW` | Closed |
| `RENEWAL` | `RENEWAL` | Closed |
| `UPSELL` | `EXPANSION` | Closed |
| `CROSS_SELL` | `EXPANSION` | Closed |
| `EXISTING_BUSINESS` | `EXPANSION` | Closed. Reversible decision under owner delegation (CPQ Decision validated). Flip only `Source.EXISTING_BUSINESS`. |

`AMENDMENT` is not a CRM mapping target. It is reached only by typed continuation (`QuoteContinuationRequest.Operation.AMENDMENT`), which is the only path that may replace a source line. If CRM data shows `EXISTING_BUSINESS` deals that are really amendments, the owner should reopen this row.

Null or omitted `crmDealType` keeps the previous default `NEW` (or an explicit quote `dealType`). An unknown CRM name fails with `CRM_DEAL_TYPE_UNMAPPED`. Blank fails with `CRM_DEAL_TYPE_REQUIRED`.

A bare `EXISTING_BUSINESS` quote is stored as `EXPANSION`. Calculate and accept then fail with `EXPANSION quote is missing a target subscription` (`SUBSCRIPTION_CONTINUATION_INVALID`) until a target subscription is set.
