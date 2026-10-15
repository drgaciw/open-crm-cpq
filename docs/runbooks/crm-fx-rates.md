# Runbook — CRM FX rates for pipeline totals (ADR-032 interim)

**Applies to:** `crm-service` from DRG-509 (Flyway V6). **Retire when** BC-03-07 ships a rate service and a new `FxRateProvider` adapter replaces the table (ADR-032 acceptance note 2).

## What uses the rates
Each deal freezes `base_amount = amount × fx_rate` on create, when amount or currency changes, on any edit while the deal is still open and unconverted, and at win/lose. A deal that is already won or lost keeps that conversion: a later edit does not look up a new rate, even when the base fields are null or were frozen against an older base currency, and changing its amount or currency is rejected (`INVALID_STATE_TRANSITION`). Pipeline totals (`GET /api/v1/deals/pipeline`) sum base amounts only. A deal with no rate is **not** treated as 1:1; it is excluded and counted in `unconvertedCount`.

## Base currency
`CRM_BASE_CURRENCY` (default `USD`) sets both `opencrm.crm.fx.base-currency` and the Flyway placeholder `crm_base_currency`. Set it **before** V6 first runs. V6 compares `UPPER(TRIM(currency))` with `UPPER(TRIM(crm_base_currency))` and stores the trimmed upper-case code, so `eur` and `EUR` backfill the same deals. Changing the base currency later re-bases an open deal on its next edit or when it closes. A deal already won or lost keeps the conversion it froze at close.

## Load a rate
Convention: `amount_in_to_currency = amount_in_from_currency × rate`. Only direct pairs are used (no inverse or cross rates), so load every deal currency → base currency.

```sql
INSERT INTO crm.fx_rates (from_currency, to_currency, rate, as_of, source)
VALUES ('EUR', 'USD', 1.0850000000, '2026-10-15T00:00:00Z', 'MANUAL');
```

The lookup takes the latest `as_of` at or before the moment of conversion. Rows are history; do not update old ones — insert a new `as_of`.

## Convert deals that are waiting for a rate
Loading a rate does not touch existing deals (closed deals must never move, ADR-032 D-03). Open deals convert on their next edit. To list what is waiting:

```sql
SELECT id, name, stage, amount, currency FROM crm.deals
WHERE NOT is_deleted AND amount IS NOT NULL
  AND (base_amount IS NULL OR base_currency <> 'USD');
```
