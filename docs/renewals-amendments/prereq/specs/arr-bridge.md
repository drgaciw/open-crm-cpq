# ARR bridge per change line: first-slice spec proposal

**Status:** Proposal only. This file does not accept a reporting model, write an ADR, or authorize implementation, a flag change, or a deploy. DRG-575 is priority Low: reporting accuracy, after pricing is correct.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `arr-bridge`. **Date:** 2026-10-16.

**Source.** [DRG-575](https://linear.app/drgamtd-workspace/issue/DRG-575) (RA-41). Worked numbers are illustrative Scenarios 3 and 4 in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data). The single `businessCategory` this bridge decomposes is specified in `lifecycle-intents.md` (DRG-552).

**Conventions.** **[S]** is the issue or the sanity-check arithmetic. **[I]** is a design inference carried forward as a proposal. Component definitions that the issue leaves unnamed are **Owner decisions**.

## Scope

### In this slice

1. `renewalDisposition` on the change line: `Renew`, `NonRenew`, `Replace`. A line that is simply not renewed uses `NonRenew`. It does not use `Remove`, which would invite a credit or termination-fee policy. **[S DRG-575]**
2. An `arrBridge` on the change line, computed when the line is priced: `base`, `priceEffect`, `qtyEffect`, `crossSell`, `conversion`, `churn`. **[S DRG-575]**
3. A renewal that is also uplifted and downsized stores one business category and a bridge that splits the price effect from the quantity effect.
4. A conversion stores its ARR movement on `conversion`. It does not store the ended on-prem amount as `churn` plus the new SaaS amount as new business. **[S DRG-575, Scenario 3]**
5. The Scenario 4 bridge totals, as a fixture the priced renewal must be able to explain.

### Out of this slice

The prices themselves (renewal uplift DRG-567, maintenance `maintenance.md`, conversion `conversions.md`, co-term dates `co-termination.md`). A reporting warehouse, a CRM forecast push (DRG-600), and Salesforce-style rollup fields. Those may read this bridge later. This slice stores the components on the line at pricing time.

### Proposed contract

```text
ChangeLine
  renewalDisposition   { Renew | NonRenew | Replace }   null on a non-renewal intent
  businessCategory     one value, from lifecycle-intents.md
  arrBridge
    base
    priceEffect
    qtyEffect
    crossSell
    conversion
    churn
    currency
    computedAt
    policyVersionIds[]
```

**Proposed meanings [I], not accepted.** Each component is an annualized run-rate delta in the line currency. `base` is the prior ARR of the line. The other fields are signed deltas. A bridge explains the movement; it does not replace TCV.

| Field | What it is proposed to hold |
|---|---|
| base | Prior ARR of this line |
| priceEffect | ARR change from price, uplift, or list reprice at the same quantity and product |
| qtyEffect | ARR change from quantity at the prior price |
| crossSell | ARR of a product added on this change that the customer did not hold |
| conversion | Net ARR movement of a restructure, kept on the new line, with zero churn on the surrendered line |
| churn | ARR removed by `NonRenew` |

Scenario 4's published bridge **[S sanity]**, which this proposal treats as the fixture to hit once the definitions above are accepted:

| Movement | Amount |
|---|---|
| Analytics uplift | +$6,720 |
| AI Assist uplift, annualized, after the lock | +$840 |
| Data Catalog cross-sell | +$16,200 |
| Maintenance uplift | +$2,000 |
| Reporting Server list reprice | +$2,000 |
| Legacy Connector non-renewal | −$3,000 |

Scenario 3's parity ARR is $99,993.60 against prior maintenance ARR of $40,000. The proposed classification puts the net movement on `conversion` of the new line and puts `churn` at 0 on the surrendered perpetual and terminated maintenance lines. The owner has not accepted that classification.

## Acceptance criteria

1. **Disposition stored.** A renewal line is `Renew`. A line the seller drops with no credit and no termination fee is `NonRenew`. A SKU substitution that keeps the commercial relationship is `Replace`. A `NonRenew` line has no credit amount and no termination fee from this slice.
2. **Remove is a different action.** A mid-term Remove or Terminate is not rewritten to `NonRenew`. Non-renewal is a renewal-time disposition.
3. **One category, split money.** A line with category Renewal, prior ARR $96,000, same quantity, uplift to $102,720, stores `base` 96000 and `priceEffect` 6720. `qtyEffect`, `crossSell`, `conversion`, and `churn` are 0.
4. **Quantity separated from price.** A line whose price and quantity both change stores the price delta and the quantity delta in different components. The fixture for the split formula waits on open question 2. The test fails if both deltas are stuffed into `priceEffect`.
5. **Scenario 4 roll-up.** Given the sanity-check prices (Analytics +$6,720, AI Assist annualized +$840, Data Catalog +$16,200, maintenance +$2,000, list reprice +$2,000, Legacy Connector −$3,000), the sum of `priceEffect` is +$11,560 ($6,720 + $840 + $2,000 + $2,000), `crossSell` is +$16,200, and `churn` is −$3,000. Maintenance uplift is `priceEffect`, not a fifth unnamed bucket.
6. **List reprice is visible.** The Reporting Server line, mode List, prior $28,000 to $30,000, stores `priceEffect` +$2,000 and a flag that the prior discount was dropped. The flag is the sanity check's visible marker. The field name is an owner decision (open question 4).
7. **Non-renewal is churn.** Legacy Connector $3,000 ARR with disposition `NonRenew` stores `churn` −3000, `priceEffect` 0, and does not create a credit line.
8. **Conversion is not churn plus new logo.** For the Scenario 3 fixture, the new SaaS line stores `conversion` equal to the priced year-1 ARR movement the owner selects (open question 3). The terminated maintenance line and the surrendered perpetual line store `churn` 0 and `crossSell` 0. The document-level sum of `churn` for that change is 0.
9. **Computed at pricing.** The bridge is written by the pricing response, with the policy version ids that priced the line. Quote-service does not recompute it. A reprice replaces the bridge on the new revision and leaves the previous revision's bridge unchanged.
10. **Currency.** Every component is in the line currency. A document that mixes currencies does not sum the components in this slice.
11. **Null bridge on an unpriced draft.** A draft line with no pricing trace has a null bridge. Presentation of a renewal or conversion with a null bridge on a priced line is rejected.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| `lifecycle-intents.md` (DRG-552) | `businessCategory` and action codes. This slice adds disposition and the bridge beside that category |
| DRG-567, DRG-568 | Uplift and the post-lock AI Assist annualization that produce +$6,720 and +$840 |
| `maintenance.md` | The +$2,000 maintenance uplift |
| `conversions.md` | The amounts AC-8 classifies |
| `co-termination.md` | Scenario 4's 14-month terms. The bridge is annualized; TCV for 14 months stays on the line |
| DRG-310 | Pricing decision trace the bridge's policy version ids point at |
| DRG-600 | Later CRM re-forecast may read the bridge. This slice does not push it |

## Open questions

1. **Owner decision — component dictionary.** The six names are DRG-575's list. Whether `base` is prior ARR or prior TCV, and whether deltas are annualized, is not settled. AC-3 and AC-5 assume annualized ARR because the sanity check states those figures as ARR. Confirm before implementation.
2. **Owner decision — price and quantity interaction.** When both price and quantity change, the order of the bridge (price-then-quantity, quantity-then-price, or a residual) changes `priceEffect` and `qtyEffect`. No Scenario 4 line exercises both. The owner supplies one worked line before AC-4 is a numeric test.
3. **Owner decision — conversion amount.** Scenario 3 can put $99,993.60, the net year-1 $59,830.50, or the delta against the old $40,000 maintenance ARR into `conversion`. Those are different stories. AC-8 checks that churn is zero and that one component holds the chosen figure. It does not choose the figure.
4. **Owner decision — list-reprice flag.** AC-6 needs a visible marker that List mode dropped a discount. The field is not in DRG-575. A proposed name is not accepted here.
5. **Owner decision — cross-sell versus upsell.** Data Catalog is cross-sell in the scenario. A quantity increase on an existing SKU is proposed as `qtyEffect`, not `crossSell`. The owner confirms that cut.
6. **Owner decision — Replace disposition.** A successor SKU at renewal might be `Replace` with `priceEffect`, or `NonRenew` plus `crossSell`. Those produce different churn. This proposal uses `Replace` and puts the movement in `priceEffect` only when the owner says the commercial relationship continued.
7. **Owner decision — document rollup.** DRG-575 asks for the bridge per line. Whether the document also stores the sum is unset. AC-5 sums in the test; it does not require a persisted rollup.
8. **Owner decision — timing.** Computed at pricing time is the issue's next step. Whether a later manual override recomputes the bridge in the same edit, or marks it stale, is unset.
