# Perpetual licence and maintenance: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `maintenance`. **Date:** 2026-10-16.

**Sources.** [DRG-556](https://linear.app/drgamtd-workspace/issue/DRG-556) (RA-22, perpetual licence plus maintenance linkage), [DRG-571](https://linear.app/drgamtd-workspace/issue/DRG-571) (RA-37, maintenance pricing, recalculation, reinstatement). Worked numbers are illustrative Scenarios 2 and 4 in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data).

**Conventions.** **[S]** is taken from those issues or the files they cite. **[I]** is a design inference carried forward as a proposal. Every default below is an **Owner decision**.

**Landed today [S DRG-556].** Continuation models recurring subscription lines. It has no perpetual line, no maintenance line linked to a licence, and no derived percent-of-related-line price.

## Scope

### In this slice

1. `CommitmentLine.licenseModel` with `Perpetual`, `TermLicense`, and `Maintenance`, plus `isPermanent` and `relatedPerpetualLineId` on the maintenance line. **[S DRG-556]**
2. A renewal of an on-prem pair renews the maintenance line (and a term-licence line, when that is what was sold). The perpetual line is referenced and is not renewed, upsized by the renewal, or given a new term. **[S DRG-556]**
3. A versioned `MaintenancePolicy`: percent of the related licence, basis net or list, recalculation when the licence quantity or net changes, an uplift percent of its own, and a reinstatement block for a lapsed period. **[S DRG-571]**
4. When a true-up changes the licence quantity, the maintenance line is recomputed from the licence line in the same change. The true-up measurement itself is `capacity-meters.md`.
5. A renewal can apply a maintenance uplift that differs from the SaaS account uplift on another agreement in the same change.

### Out of this slice

True-up aggregation, tolerance, and meter snapshots (`capacity-meters.md`). Surrender of the perpetual line during a conversion (`conversions.md`). The one-time back-maintenance charge type (DRG-572) — this slice computes the amount and the service period once that charge type exists. Catalogue `MAINTENANCE_FOR` relationships (`catalog-model.md`) — this slice stores the instance link `relatedPerpetualLineId`. Co-term of the maintenance anniversary (`co-termination.md`).

### Proposed contract

```text
CommitmentLine
  licenseModel     { Perpetual | TermLicense | Maintenance | Subscription }
  isPermanent      true only for Perpetual
  relatedPerpetualLineId    required when licenseModel = Maintenance

MaintenancePolicy
  pctOfLicenceNet
  basis            { LicenceNet | LicenceList }
  recalcOnLicenceChange    { Yes | No }
  upliftPct
  reinstatement
    backMaintenance { Yes | No }
    feePct
    maxLapseMonths
  policyVersionId
```

**Proposed pricing steps [I]:**

1. Resolve the policy account, then agreement, then line. Snapshot the winner on the maintenance change line.
2. Base = related licence net, or list, per `basis`.
3. Annual maintenance = base × quantity × `pctOfLicenceNet`, unless a stored annual amount is the contract's own figure and `recalcOnLicenceChange` is No.
4. On a licence quantity or net change in the same document, recompute when `recalcOnLicenceChange` is Yes. Forward maintenance for a partial year uses the proration method the true-up or amendment policy names.
5. On renewal, apply `upliftPct` to the maintenance annual amount. Do not apply the SaaS uplift percent to this line.
6. A perpetual line on a Renew intent is rejected.
7. Reinstatement, when the owner turns it on: if the lapse is within `maxLapseMonths`, add back-maintenance for the gap and `feePct` of the reinstated annual amount. A longer lapse is refused for automatic reinstatement.

Illustrative anchors **[S sanity]**, not defaults: 20% of a $2,500/core licence net is $500/core/year. A true-up of +16 cores at that rate, 92/365 forward, is $2,016.44. A renewal uplift of 5% on a $40,000 maintenance annual is $42,000.

## Acceptance criteria

1. **Linkage.** A maintenance line without `relatedPerpetualLineId` is rejected. A line with `licenseModel = Perpetual` and `isPermanent = false` is rejected. A subscription SaaS line is `Subscription` and has no perpetual link.
2. **Renew the maintenance.** A Renew change for a perpetual-plus-maintenance pair contains a Renew line for maintenance and no Renew, Update, or quantity delta on the perpetual line. The perpetual line may appear as `NoChange` so the document can show the link.
3. **Percent of net.** Policy 20% of licence net, 64 cores at $2,500, produces $500/core/year and $32,000/year. The decision stores the licence state-period id used as the base.
4. **Recalc on true-up.** The same policy, licence quantity moving from 64 to 80, `recalcOnLicenceChange = Yes`, produces maintenance for 80 cores. Forward amount for 92/365 of a year at $500/core on the 16-core delta is $2,016.44. The maintenance change line records the licence change line as its driver.
5. **No recalc.** The same quantity move with `recalcOnLicenceChange = No` leaves the maintenance annual amount unchanged and records that the policy forbade the recompute.
6. **Own uplift.** Renewal of $40,000 maintenance with agreement uplift 5%, while the account SaaS uplift is 7%, yields $42,000 for the maintenance year. The SaaS lines in the same change still use 7%. The maintenance decision records agreement as the level that supplied 5%.
7. **Basis list.** `basis = LicenceList` prices the percent off the licence list, and the decision stores the price-book version. A missing list price fails the line and names the SKU.
8. **Reinstatement inside the cap.** Lapse of 3 months, `maxLapseMonths = 6`, `backMaintenance = Yes`, `feePct = 10`, annual $32,000. Back-maintenance is 3/12 × 32,000 = $8,000 and the fee is $3,200, as separate amounts. This fixture assumes the owner chose those three knobs; it is not a default.
9. **Reinstatement outside the cap.** The same policy with a 9-month lapse is refused for automatic reinstatement and does not invent a fee.
10. **Term licence still renews.** A `TermLicense` line is eligible for Renew. `isPermanent` is false. It does not require `relatedPerpetualLineId`.
11. **Pinning.** A presented renewal stores `policyVersionId`, percent, basis, and uplift. A later edit to the account policy does not change that revision.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| `lifecycle-intents.md` (DRG-548, DRG-552) | Renew versus NoChange, and a maintenance policy slot |
| `capacity-meters.md` (DRG-557, DRG-569) | AC-4 is the maintenance consequence of a true-up delta |
| `catalog-model.md` (DRG-576) | `MAINTENANCE_FOR` says which SKUs may be linked; this slice stores the instance link |
| DRG-572 | Back-maintenance and reinstatement fees need a one-time charge with its own service period |
| DRG-539 | `LicenceList` needs a price-book read |
| DRG-567 | Precedence when a contract clause, a line override, and an agreement uplift disagree. This slice records the winning level and does not define the order |
| `co-termination.md` | Scenario 4 renews maintenance onto a shared anniversary; the anniversary rule is that slice |

## Open questions

1. **Owner decision — percent and basis.** 20% of licence net is the illustrative contract, not a platform default. The owner sets the seeded percent, and whether the basis is net or list, per account or only per agreement.
2. **Owner decision — recalculation default.** Whether `recalcOnLicenceChange` defaults to Yes. AC-4 and AC-5 cover both settings.
3. **Owner decision — maintenance uplift versus SaaS uplift.** Scenario 4 uses 5% on the licence agreement and 7% on the SaaS account. The owner confirms that maintenance uplift is a separate field, and where it sits in the precedence stack (DRG-567).
4. **Owner decision — reinstatement.** Whether the first slice includes lapse reinstatement at all. `backMaintenance`, `feePct`, and `maxLapseMonths` have no proposed defaults. AC-8 and AC-9 run only after those three are set. A lapse longer than the cap may be a manual quote; that path is unset.
5. **Owner decision — what a version-date refresh is.** DRG-556 notes that some permanent lines can refresh a version date without a commercial renewal. Whether that refresh is a fulfilment action on the perpetual line or a maintenance entitlement is unset. This proposal still rejects a commercial Renew of the perpetual line.
6. **Owner decision — quantity match.** Whether the maintenance quantity must equal the perpetual quantity. AC-4 assumes it follows the licence quantity when recalc is Yes. A maintenance line sold for a subset of cores is not specified.
7. **Owner decision — list versus net when the licence was sold at a discount.** Percent of net follows the discounted licence. Percent of list ignores that discount. The owner picks the commercial meaning before AC-7 is a production default.
8. **Owner decision — proration of the forward stub.** AC-4 uses 92/365 because the sanity check does. Daily versus month-day proration is shared with the true-up policy and is not chosen here.

## DRG-642 fixtures (provisional)

Fixture addendum, 2026-10-27. Checked against `main` `bf41c230f6309964a25ef5c2c12e84c41b0c37de`. These files do not accept a policy. Each file has `"status": "provisional"` because the maintenance ADR is still pending, and each file has `"licenceRenewed": false`.

### Expected amounts

| Fixture | Case | Expected maintenance amounts | Rounding |
|---|---|---|---|
| `fixtures/maintenance/renewal-maintenance-only.json` | Renew the maintenance line. Prior annual `40000.00`. Agreement uplift 5%. The perpetual line is `NoChange`. | Renewed annual `42000.00`. Uplift amount `2000.00`. Equation: `40000.00 × 1.05 = 42000.00`. | `HALF_UP`. Money scale 2. The factor `0.05` is exact. |
| `fixtures/maintenance/licence-count-change.json` | Licence quantity 64 to 80. `recalcOnLicenceChange = Yes`. 20% of licence net `2500.00`. | Per core `500.00`. Annual at 64 cores `32000.00`. Annual at 80 cores `40000.00`. Forward amount on the 16-core delta `2016.44`. | `HALF_UP`. Money scale 2. The single quotient `(16 × 500.00 × 92) / 365` is `2016.438356164383561643835616`. Scale 4 of that quotient is `2016.4384`. |
| `fixtures/maintenance/reinstatement-after-lapse.json` | Lapse of 3 months, cap 6 months, on annual `32000.00`. Fixture inputs: back-maintenance on, fee 10%. | Back-maintenance `8000.00`. Fee `3200.00`. The two amounts stay separate. Equations: `(32000.00 × 3) / 12 = 8000.00` and `32000.00 × 0.10 = 3200.00`. | `HALF_UP`. Money scale 2. The ratio `3/12` is the exact `0.25`. The fee rate `0.10` is exact. |

The 20% rate, the 5% uplift, the 7% SaaS account rate, and the three reinstatement knobs are the illustrative figures already printed in AC-3, AC-4, AC-6, and AC-8. They are fixture inputs. They are not platform defaults.

The renewal file prices the maintenance `Renew` line. The perpetual line is on the document as `NoChange`. The 7% SaaS account rate is recorded on the fixture and is not applied to the maintenance amount.

The licence-count file prices the maintenance line from the linked licence after the quantity moves from 64 to 80. The licence line is the driver of that quantity. It is not a `Renew` line. The forward `2016.44` is the scale-2 result of the equation AC-4 already prints. Open question 8 is unchanged: this addendum does not choose daily or month-day proration.

The reinstatement file prices maintenance amounts for a lapse inside the cap. The perpetual line is `NoChange`. The fee percent, the cap, and the back-maintenance flag are inputs of this fixture, as AC-8 already says.

### Rounding observed in pricing-service

OQ-1: `HALF_UP` stays on these fixtures. Money in the table is shown at 2 dp. Intermediate scales are named in each JSON file and below.

| Location on `main` `bf41c230` | What it does |
|---|---|
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/MetricsCalculator.java` lines 26–27 and 266 | `MONEY_SCALE = 2`, `MONEY_ROUNDING = HALF_UP`, applied with `setScale`. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java` line 269 | Discount amount `setScale(2, RoundingMode.HALF_UP)`. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ramp/RampPeriodPricer.java` lines 32–35 and 111–116 | `UNIT_SCALE = 4`, `RATE_SCALE = 4`, `MONEY_SCALE = 2`, all `HALF_UP`. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/renewal/RenewalUpliftResolver.java` lines 23–24, 114, and 293–299 | Unit scale 4 `HALF_UP`. Amount scale 2 `HALF_UP`. `increase` shifts percent points two places (`movePointLeft(2)`) and then scales the unit. |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/engine/PriceCalculationService.java` lines 65 and 79 | A discount ratio is divided at scale 4 `HALF_UP`. That ratio is not a money amount. |
| `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql` lines 167–169 (`rule-007`, Amendment Proration) | `delta × remainingDays / fullTermDays` at scale 4 `HALF_UP`. |
| `backend/services/pricing-service/src/main/resources/rules/perpetual-maintenance.drl` lines 11–13 | `maintenanceAmount = listPrice × 0.22` with no `RoundingMode` and no `setScale`. |

`backend/libs/common/src/main/java/com/opencrm/common/valueobject/Money.java` lines 26–27, 52, 116, and 133 is outside pricing-service. It uses scale 2 `HALF_UP` on construction, on `divide`, and on the percentage factor. It is cited here because a later maintenance amount may pass through it.

### Open items (scale)

These fixtures keep `HALF_UP` and show money at 2 dp. Where two scales disagree, the disagreement stays open.

1. **Quotient scale for the forward delta.** The AC-4 equation rounded once at scale 2 is `2016.44`. The same quotient at scale 4, which is the scale `rule-007` passes to `divide`, is `2016.4384`. A per-step `Money.divide` of `8000.00 / 365` at scale 2 is `21.92`, and `21.92 × 92` is `2016.64`. The fixture's expected money amount is the published AC-4 figure `2016.44`. The store scale and the order of the multiply and the divide stay open.
2. **Legacy 22% product.** `perpetual-maintenance.drl` does not round. A scale-2 list price times `0.22` keeps the product scale of that multiplication. These fixtures use the 20% linked-line illustration from AC-3. They do not use the 22% rule, and they do not choose a scale for it.
3. **Unit scale 4 beside money scale 2.** `RampPeriodPricer` and `RenewalUpliftResolver` keep a unit at scale 4 and a money amount at scale 2, both `HALF_UP`. The units in these three fixtures (`500.00`, `42000.00`, `8000.00`, `3200.00`) are exact at both scales, so the displayed money does not move. The maintenance unit scale stays open.
4. **Month step on reinstatement.** `(32000.00 × 3) / 12` is `8000.00` at every scale. `Money.divide` of `32000.00 / 12` at scale 2 is `2666.67`, and `2666.67 × 3` is `8000.01`. The fixture's expected back-maintenance amount is the published AC-8 figure `8000.00`. The order of that division stays open. Pricing-service has no reinstatement calculation that picks an order.

### Reinstatement basis

Step 7 sketches back-maintenance for the gap together with `feePct` of the reinstated annual amount. AC-8 prints both amounts. Open question 4 leaves `backMaintenance`, `feePct`, and `maxLapseMonths` without defaults. This file therefore has no adopted reinstatement basis.

Options:

1. Back-maintenance for the lapse.
2. A reinstatement fee percent.
3. Both, as separate amounts.

**Status: Proposed.** Proposed default: option 3, both, as separate amounts. The fee percent, the lapse cap, and whether back-maintenance is on stay unset. AC-8's 10%, 6 months, and 3-month lapse are inputs of the provisional fixture only.

**Owner decision: ___ (drg, time)**
