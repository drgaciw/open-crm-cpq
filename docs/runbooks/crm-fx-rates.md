# Runbook — CRM FX rates for pipeline totals (ADR-032 interim)

**Applies to:** `crm-service` from DRG-509 (Flyway V6). **Retire when** BC-03-07 ships a rate service and a new `FxRateProvider` adapter replaces the table (ADR-032 acceptance note 2).

## What uses the rates
Each deal freezes `base_amount = amount × fx_rate` on create, when amount or currency changes, on any edit while unconverted, and at win/lose. Pipeline totals (`GET /api/v1/deals/pipeline`) sum base amounts only. A deal with no rate is **not** treated as 1:1; it is excluded and counted in `unconvertedCount`.

## Base currency
`CRM_BASE_CURRENCY` (default `USD`) sets both `opencrm.crm.fx.base-currency` and the Flyway placeholder `crm_base_currency`. Set it **before** V6 first runs. Changing it later re-bases a deal only on its next edit or close; until then the deal counts as unconverted.

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
