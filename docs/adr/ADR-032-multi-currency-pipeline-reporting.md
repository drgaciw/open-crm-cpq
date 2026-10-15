# ADR-032: Multi-currency deals and pipeline reporting with a frozen base amount

**Status:** Proposed on 2026-10-15. Not accepted. Recommended for decision first in Track CC1 because it fixes a live defect.
**Date:** 2026-10-15
**Deciders:** Project owner (pending).
**Relates to:** BC-03-07 (multi-currency and regional pricing), BC-13-05, the `Money` shared-kernel type (BC-12-01).
**Source pattern:** [trycompai/crm](https://github.com/trycompai/crm) `docs/currency.md` and `ExchangeRate` model, at `6d4793d`.
**Does not authorize:** any code or migration.

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
