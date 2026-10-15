# ADR-032: Multi-currency deals and pipeline reporting with a frozen base amount

**Status:** Accepted on 2026-10-15 by the project owner. Authorizes DRG-509 (BC-13-05) only, within the acceptance notes below.
**Date:** 2026-10-15
**Deciders:** Project owner.
**Relates to:** BC-03-07 (multi-currency and regional pricing), BC-13-05, the `Money` shared-kernel type (BC-12-01).
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `docs/currency.md` and `ExchangeRate` model, at `6d4793d`.
**Authorizes:** DRG-509 — `crm-service` columns, migration, rate lookup and pipeline-summary change described here. Nothing else.

---

## Context

The `deals` table has `amount` and `currency`. The pipeline summary query in `DealRepository` is `SUM(d.amount) … GROUP BY d.stage`. It adds amounts in different currencies as one number. With one EUR 1,000,000 deal and one USD 1,000,000 deal the stage shows 2,000,000 of nothing. The source repo names the same failure in `docs/currency.md` and designs it out.

Converting at today's rate on every read is also wrong: last quarter's won revenue would change every day.

## Decision

### D-01 — Each tenant has one base currency
Set at tenant provisioning (BC-19-02). Changing it is an admin migration that re-bases all open deals and records the event.

### D-02 — Each deal stores both amounts and the rate used
Columns: `amount`, `currency` (deal currency), `base_amount`, `base_currency`, `fx_rate`, `fx_rate_as_of`, `fx_source`.

### D-03 — When the rate freezes
- On create, and whenever `amount` or `currency` changes: convert with the current rate and freeze it.
- On close (won or lost): convert once more and freeze for good. Closed deals never move.
- A nightly job may refresh open deals' `base_amount` only if the tenant enables "float open pipeline"; default off.

### D-04 — Missing rate is not 1:1
If no rate exists, `base_amount` is null. Totals exclude the deal and return a count of unconverted deals with the response. The UI shows it.

### D-05 — One rate source for CRM and CPQ
Rates come from the same source BC-03-07 uses (manual, provider, or both, with source recorded). CRM does not get its own provider.

### D-06 — Reports sum base amounts only
Pipeline and forecast queries sum `base_amount` and group by stage. Per-currency breakdowns sum `amount` grouped by `currency`, never across.

## Consequences
- Good: totals are honest and historical numbers are stable.
- Cost: one migration; a backfill for existing deals with a recorded "backfill" rate source.

## Alternatives considered
- **Convert on read at the latest rate.** Rejected: history drifts.
- **Require one currency per tenant.** Rejected: cross-border sellers are a core segment.

## Acceptance notes (2026-10-15)

The owner accepted this ADR with three interim choices, because the pieces D-01 and D-05 point to do not exist yet:

1. **Base currency (D-01).** BC-19-02 tenancy is not built and `crm-service` has no tenant. Until it is, the base currency is the configuration property `crm.fx.base-currency` (default `USD`, the existing deal default). When per-tenant base currency arrives, it replaces the property; the columns do not change.
2. **Rate source (D-05).** BC-03-07 has no rate service. `crm-service` reads rates through an `FxRateProvider` interface. The first adapter reads a `fx_rates` table in `crm-service`'s schema (from, to, rate, as-of, source). Rates are loaded by an operator; there is no rate endpoint and no external provider in this change. When BC-03-07 ships a rate service, a new adapter replaces the table and the table is retired. This does not give CRM its own provider.
3. **Floating open pipeline (D-03).** Not built. The default was off; it needs its own story if wanted.

Backfill: existing deals in the base currency get `base_amount = amount`, rate 1, source `IDENTITY`. Other existing deals stay unconverted (null) until a rate exists and the deal is next edited or closed, and they are counted as unconverted in totals.
