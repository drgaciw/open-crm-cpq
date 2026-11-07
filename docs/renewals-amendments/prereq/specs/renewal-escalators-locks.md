# Renewal escalators and time-bounded price locks — AC spec (DRG-568)

**Status:** Proposed. Encodes Linear acceptance criteria 568-1..568-7 with fixtures. Authorizes no code, schema, flag, route, or deploy. `opencrm.pricing.ramp.enabled` stays false. **Does not implement DRG-534.**

**Story note:** [../story-drg-568-renewal-escalators-and-price-locks.md](../story-drg-568-renewal-escalators-and-price-locks.md)

**Reuse:** DRG-533 Accepted ADR (`../DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md`, blob `4922b07b4ce374b8ee9f88ecec593c5699b82fea`) for D-01..D-06 semantics; DRG-532 Done generator (`RampEscalatorExpander`) for equal-block expansion. Period 1 unit is the uplift result (42.8000 in the worked example), passed as escalator `startPrice` — the expander must not apply the renewal percent a second time to produce period 1.

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225`.

**Illustrative numbers** are the pricing-policy sanity check examples (not customer data).

Every open OWNER FORK in the story note stays **Proposed**. Owner decision slots stay blank.

---

## Acceptance criteria

| ID | Given | Expected | Fixture |
|---|---|---|---|
| AC-568-1 | Renewal term 36 months. Exit net 40.0000. Account uplift 7% → period-1 unit **42.8000** (DRG-604 / uplift D-01..D-05). Annual escalator 7% compound-on-prior, `everyMonths` 12, quantity 200. Unit half-up scale 4; amounts half-up scale 2 (ADR D-03). | Three segments. Units **42.8000**, **45.7960**, **49.0017**. Period amounts 102720.00 + 109910.40 + 117604.08. **TCV 330234.48**. Ticket illustrations 49.01 (scale-2 per step) and 49.00 (unrounded compound then round once) are **not** expected. | [fixtures/renewal-escalators/ac-568-1-escalator-36m-7pct.json](fixtures/renewal-escalators/ac-568-1-escalator-36m-7pct.json) |
| AC-568-2 | Renewal 2026-11-01 through 2027-12-31 (14 months). Promotional lock absolute 20.00 through **2027-03-31 inclusive**. Post-lock mode `UpliftPct` 7 applied **once** to the locked unit. Quantity 50. | Segment A: 2026-11-01..2027-03-31, 5 months, unit 20.0000. Segment B: **2027-04-01**..2027-12-31, 9 months, unit 21.4000. Boundary date belongs to the new segment. AC text asserts no total; fixture records line total 14630.00 for reconciliation only. | [fixtures/renewal-escalators/ac-568-2-price-lock-14m.json](fixtures/renewal-escalators/ac-568-2-price-lock-14m.json) |
| AC-568-3 | Renewal term 12 months. No escalator. Exit net 50.00. Uplift 8% (landed test shape). | One flat period, months 12, unit 54.00 at today's scale-2 continuation money (unchanged from `QuoteContinuationPolicyTest.renewalUsesExitRateAndEightPercentUplift`). Segment count 1. | [fixtures/renewal-escalators/ac-568-3-flat-12m-regression.json](fixtures/renewal-escalators/ac-568-3-flat-12m-regression.json) |
| AC-568-4 | Any multi-segment renewal from AC-568-1 or AC-568-2. | Segments contiguous: no gaps, no overlaps; first `from` = renewal start; last `to` = renewal end; adjacent `to`+1 day = next `from`. | [fixtures/renewal-escalators/ac-568-4-contiguous-segments.json](fixtures/renewal-escalators/ac-568-4-contiguous-segments.json) |
| AC-568-5 | Edge matrix (zero escalator; negative amount; leap year / month-end start; whole-month boundary; period-1 not double-uplifted; totals reconcile). | See fixture cases. Negative escalator amount → refuse (`RAMP_NEGATIVE_VALUE` / equivalent). Zero → every period at period-1 unit. No day stub at whole-month renewal boundary. Mid-month lock end → out of first slice (OF-568-7). | [fixtures/renewal-escalators/ac-568-5-edge-cases.json](fixtures/renewal-escalators/ac-568-5-edge-cases.json) |
| AC-568-6 | Generated segments from AC-568-1. | Each segment's PolicyDecision stores generator inputs (start unit, percent, compounding, everyMonths, lock bounds when present) and the **policy version** used. | [fixtures/renewal-escalators/ac-568-6-policy-decision-audit.json](fixtures/renewal-escalators/ac-568-6-policy-decision-audit.json) |
| AC-568-7 | Committed application config and `RampPricingProperties` default. | `opencrm.pricing.ramp.enabled` is **false** on quote-service and pricing-service. No schedule is priced while the flag is off. | [fixtures/renewal-escalators/ac-568-7-flag-off.json](fixtures/renewal-escalators/ac-568-7-flag-off.json) |

### AC-568-1 arithmetic (ADR D-03, scale-4 default)

```text
Y1 unit = round4(42.8000)              = 42.8000   (period 1 = uplift unit; escalator not reapplied)
Y2 unit = round4(42.8000 × 1.07)       = 45.7960
Y3 unit = round4(45.7960 × 1.07)       = 49.0017
Y1 amount = round2(round2(42.8000×200)×12) = 102720.00
Y2 amount = round2(round2(45.7960×200)×12) = 109910.40
Y3 amount = round2(round2(49.0017×200)×12) = 117604.08
TCV = 330234.48
```

Generator check (DRG-532 reuse): `RampEscalatorExpander.expand(36, {startPrice:42.80, mode:PERCENT, amount:7, everyMonths:12, compounding:true}, 200)` emits the three units above. Already covered by `RampEscalatorExpanderTest.thirtySixMonthYearlySevenPercent`.

### Owner forks referenced by these ACs

Status: **Proposed** for each. Owner decision: ___ (drg, time)

| ID | Maps to story fork |
|---|---|
| OF-568-1 | ADR body wording vs Accepted; scale-4 finality |
| OF-568-2 | Scale-4 vs scale-2 money |
| OF-568-3 | Compounding default |
| OF-568-4 | Post-lock percent once |
| OF-568-5 | Explicit unequal segments |
| OF-568-6 | No second named-seat tier |
| OF-568-7 | Month-boundary locks only |
| OF-568-8 | Retention marker ≠ lock |
| OF-568-9 | Product enablement of time-bounded locks |

---

## Out of scope

DRG-534. Flag on. Changing continuation policy in this docs PR. Mid-month lock day proration.
