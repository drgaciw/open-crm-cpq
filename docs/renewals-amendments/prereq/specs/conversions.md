# On-prem to SaaS conversion: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `conversions`. **Date:** 2026-10-16.

**Sources.** [DRG-559](https://linear.app/drgamtd-workspace/issue/DRG-559) (RA-25, restructure in one lifecycle change), [DRG-573](https://linear.app/drgamtd-workspace/issue/DRG-573) (RA-39, conversion pricing), [DRG-574](https://linear.app/drgamtd-workspace/issue/DRG-574) (RA-40, perpetual surrender and revocation). Worked numbers are the illustrative Scenario 3 in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data). DRG-341 is a related hybrid-ramp credit, not a duplicate of this policy.

**Conventions.** **[S]** is taken from those issues or the files they cite. **[I]** is a design inference carried forward as a proposal. Every default below is an **Owner decision**.

## Scope

### In this slice

1. A `Restructure` intent that, in one lifecycle change, ends the source maintenance commercial term, records surrender of the perpetual licence, and adds the target subscription lines. The new agreement stores `priorAgreementId`. **[S DRG-559]**
2. A versioned `ConversionPolicy`, distinct from agreement-level restructure and from a metric-only `ConvertMetric`. The policy carries the quantity map, price basis, optional parity ramp, credit stack, cross-agreement credit transfer, and price-lock term. **[S DRG-573]**
3. Surrender of a perpetual line: action code `Surrender`, commitment status `Surrendered`, `entitlementEndDate` separate from the commercial end, and `convertedFromLineIds` on the new line. Revocation is requested from fulfilment after the dual-use window; this slice does not implement the licence server. **[S DRG-574]**
4. One policy shape that can express both the Scenario 3 big-bang conversion and the DRG-341 phased hybrid credit, selected by the credit `application` target. **[S DRG-573]**
5. A priced quote whose audit row records mapping version, parity multiple, rounding choice, each credit's basis, cap, source agreement, and target agreement.

### Out of this slice

Owned elsewhere: the shared intent and change-line vocabulary (`lifecycle-intents.md`); catalogue relationship rows that declare an allowed conversion (`catalog-model.md`); maintenance percent pricing (`maintenance.md`); fulfilment execution and the PendingActive-to-Terminated state machine (DRG-558); the DRG-341 ramp schedule itself; ARR classification of the conversion (`arr-bridge.md`); partial application of the three units (`partial-apply.md`).

Today a continuation keeps inherited unit rates and the pricing service rejects a net-price move, so a conversion cannot be a continuation of the current kind. **[S DRG-573]** This slice proposes a new intent and policy. It does not widen the existing expansion path to carry a conversion.

### Proposed contract

```text
Intent Restructure
  priorAgreementId
  creditPolicyRef -> ConversionPolicy

ConversionPolicy
  sourceModel, targetModel
  quantityMap
    ratio, rounding { Down | Nearest | Up }, minimum, version
  priceBasis
    { SpendParity(multiple) | TargetListLessDiscount | Override }
  parityRamp[]                 optional { year, multiple }
  credits[]
    type { UnusedPrepaid | TradeInPctOfBook | Fixed | MigrationBookPct }
    basis, cap, application { FirstInvoice | Spread | DecliningLine }
  creditTransfer
    sourceAgreement, targetAgreement, expiry
  priceLockMonths
  policyVersionId

ChangeLine
  actionCode Surrender | Terminate | Add
  entitlementEndDate           perpetual line only
  convertedFromLineIds[]       on the new SaaS line
CommitmentLine.status          adds Surrendered, distinct from Terminated and Expired
```

**Proposed Scenario 3 line plan [S sanity], still a proposal:**

| Line | Action | Date |
|---|---|---|
| Maintenance | Terminate | commercial end 2026-06-30 |
| Perpetual cores | Surrender | entitlement end 2026-12-31, after dual use |
| New SaaS line | Add, Absolute quantity | contract and service start 2026-07-01 |

Action date 2026-05-15 and customer acceptance 2026-06-10 stay on the change. Four per-line dates do not fit in three change-level dates; the per-line dates above are part of this slice. **[S DRG-574]**

DRG-341's credit (30% of book on the declining line's first period, capped at that line's TCV, never placed on the SaaS line) is proposed as `MigrationBookPct` with `application = DecliningLine`. Scenario 3's trade-in is `TradeInPctOfBook` with a cap against year-1 fees and `application = FirstInvoice`. Both are rows of `credits[]`. **[I from DRG-573]**

## Acceptance criteria

Fixtures use Scenario 3 **[S sanity]**: 80 perpetual cores, historical book $200,000, maintenance $40,000 for 2026 paid in advance, target list $70 per compute unit per month, map 1 core = 2 CU at quantity-map version v3, term 36 months from 2026-07-01, dual use through 2026-12-31.

1. **Quantity map.** 80 cores at ratio 2 yield 160 CU. The priced line stores map version v3. A second map version with a different ratio does not change a quote that pinned v3.
2. **Spend parity, unit price rounded half-up to $0.01, ARR derived from the rounded unit.** Multiple 2.5 × $40,000 = $100,000. Unit = 100,000 / 12 / 160 = $52.0833…, stored as $52.08. ARR = 160 × 52.08 × 12 = $99,993.60. The decision records a −$6.40 drift against the unrounded $100,000 and states that the unit price was held fixed.
3. **List comparison.** List ARR = 160 × 70 × 12 = $134,400. Effective discount of the parity ARR is 25.6% (1 − 99,993.60 / 134,400). The approval payload includes that percentage. This slice does not choose the approval threshold.
4. **Unused prepaid maintenance.** Credit = 40,000 × 184/365 = $20,164.38, basis the prepaid maintenance on the source agreement, consumed on the target agreement.
5. **Trade-in cap.** min(10% × 200,000 = 20,000, 20% × 99,993.60 = 19,998.72) = $19,998.72.
6. **Year-1 net.** 99,993.60 − 20,164.38 − 19,998.72 = $59,830.50. Years 2 and 3 are $99,993.60 each when `parityRamp` is empty.
7. **Parity ramp.** With multiples 1.5 / 2.0 / 2.5 the year-1, year-2, and year-3 ARR figures are 1.5, 2.0, and 2.5 times $40,000 before credits, and AC-6's single parity price is not used. Credits still apply on the schedule the credit row names.
8. **Cross-agreement credit.** Each credit row stores source agreement LM-2002 and target agreement SaaS-3003. The source maintenance line is the one terminated; the credit does not also reduce a surviving line on LM-2002.
9. **Surrender versus terminate.** The perpetual line's status becomes `Surrendered` at entitlement end 2026-12-31. It does not become `Terminated` or `Expired`. From 2026-07-01 through 2026-12-31 the perpetual entitlement remains active (dual use) while the SaaS line is active. Maintenance is commercially ended 2026-06-30.
10. **Revocation request.** When the dual-use window ends, the change emits a fulfilment request that names the perpetual line, `entitlementEndDate`, and `convertedFromLineIds`. This slice's test asserts the request payload. Key revocation on the licence server is DRG-558.
11. **Phased hybrid credit uses the same policy.** A fixture with `MigrationBookPct`, basis 30% of book, `application = DecliningLine`, cap = declining line TCV, produces the credit on that line's first period and a zero credit on the SaaS line. It does not use AC-5's trade-in formula.
12. **Inherited-rate guard.** A restructure quote whose net price differs from the source line is accepted by this policy. The current continuation rate-changed rejection is not applied to a `Restructure` intent.
13. **Pinning.** The quote stores policy version, map version, price basis, and each credit cap. Changing the account's conversion policy after presentation does not reprice the presented revision.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| `lifecycle-intents.md` (DRG-548, DRG-552, DRG-553) | `Restructure` intent, action codes, action date versus effective dates |
| `catalog-model.md` (DRG-576) | Allowed source-to-target relationship and a quantity mapping the catalogue can pin. Which store is authoritative is an owner decision below |
| `maintenance.md` (DRG-556) | The maintenance line linked to the perpetual line is what Terminate ends |
| DRG-341 | Phased hybrid credit and dual-entitlement window; AC-11 must match its rule once the owner maps the fields |
| DRG-558 | Fulfilment consumes the revocation request; state vocabulary PendingActive through Terminated gains `Surrendered` here as a proposal |
| `partial-apply.md` (DRG-565) | Create SaaS, end maintenance, and revoke entitlement are separate application units, create before terminate |
| `arr-bridge.md` (DRG-575) | The conversion must not be reported as churn plus new business; that classification is the other slice |
| Price book read (DRG-539) | `TargetListLessDiscount` needs a list price and as-of date |

## Open questions

1. **Owner decision — price basis for the first customer-facing conversion.** Spend parity at a stated multiple, target list less a discount, or an override. AC-2 through AC-6 are the parity fixture only. The 2.5 multiple is an illustrative assumption in the sanity check, not a standard multiple.
2. **Owner decision — which side of rounding is held fixed.** AC-2 holds the rounded unit price and lets ARR drift by $6.40. Holding ARR at $100,000 and letting the unit price carry the fraction is the other candidate. The policy must record the choice; this proposal does not pick it.
3. **Owner decision — credit stack and caps.** Unused prepaid, trade-in percent of book, fixed amount, and the DRG-341 migration percent. Caps (20% of year-1 fees in the example), application target, and whether credits may be combined are unset. AC-4 through AC-6 and AC-11 are fixtures for two combinations, not the allowed set.
4. **Owner decision — credit expiry and transfer.** Whether a credit that is not consumed on the first invoice expires, and whether it can move to a later invoice on the target agreement only.
5. **Owner decision — price-lock length.** `priceLockMonths` is on the proposed policy. The duration, and whether it suppresses renewal uplift, belongs with the price-lock policy (DRG-568) and is unset here.
6. **Owner decision — quantity-map home.** DRG-573 puts `quantityMap` on `ConversionPolicy`. DRG-576 puts `quantityMapping` on a catalogue `ProductRelationship`. One of those is the source of truth; the other stores a pin. This slice does not choose.
7. **Owner decision — rounding direction and minimum quantity on the map.** The vocabulary includes Down, Nearest, Up, and a minimum. Scenario 3 divides evenly (80 × 2 = 160) and does not exercise them.
8. **Owner decision — dual-use window.** Scenario 3 uses six months (2026-07-01 through 2026-12-31). Whether the window is a policy field, a per-deal date, or forbidden for big-bang conversions is unset. DRG-341 already has a dual-entitlement window for the phased case.
9. **Owner decision — approval trigger.** AC-3 surfaces a 25.6% discount and the credit amounts. Who must approve, and whether the cap breach blocks presentation, is an approvals decision (DRG-591 is parked and is not decided here).
10. **Owner decision — `ConvertMetric` versus `Restructure`.** A metric change that stays on the same licence model is outside this policy. The owner confirms that `ConvertMetric` remains a different intent so this slice does not absorb it.
