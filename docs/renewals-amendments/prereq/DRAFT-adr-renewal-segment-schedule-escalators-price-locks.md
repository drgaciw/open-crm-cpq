# DRAFT ADR: Renewal segment schedule, multi-year escalators, and time-bounded price locks

**Status:** Accepted on 2026-10-17. On 2026-10-17 at 7:31 AM CT the project owner said, as relayed by Projects Manager, "yes to accepting ADR-017, ADR-024 to 027 and DRG-533 (#644)" and "proceed with any mergers required". The owner's merge of this pull request is the confirming act. This status takes effect on that merge. This acceptance supersedes the earlier "Proposed / not accepted wholesale" wording of #662 and #663 only for this file. Acceptance authorizes no code, migration, or deploy. The file stays DRAFT-prefixed and unnumbered. Numbering is an owner follow-up. DRG-568 and DRG-532 unblock only after this pull request merges. Each recommendation in this file stays as it is.
**Date:** 2026-10-16 (proposed). Accepted 2026-10-17.
**Deciders:** Project owner, by the 2026-10-17 7:31 AM CT statement above, as relayed by Projects Manager. Each recommendation below stays as written.
**Number:** none. This file stays under `docs/renewals-amendments/prereq/` with a `DRAFT-` prefix. It is not an entry in `docs/adr/` and it does not take a number. Do not add a row to `docs/adr/README.md` from this draft.
**Story:** [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533/adr-ramp-escalator-semantics-compounding-base-price-rounding-amending) (escalator base, compounding, rounding; an addendum to ADR-023). Linear status when read: Backlog.
**Unblocks:** [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568/multi-year-renewal-escalators-and-time-bounded-price-locks-in-renewal) only after the owner accepts this file and the status-change PR merges. Nothing in this file authorizes code.
**Informs:** [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532/ramp-schedule-authoring-generate-all-periods-from-term-escalator-no) (the generator that expands a term and an escalator), [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534/ramp-amendments-lock-elapsed-periods-amend-future-periods-re-govern-on) (amendments of stored periods).
**Reads, and does not edit:** Accepted [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-01, D-02, D-04, D-05, D-06, D-07. Accepted [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-01 through D-07, including D-06. Accepted [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md).
**Does not accept:** ADR-017, ADR-024, ADR-025, ADR-026, or ADR-027. Each of those files on this tree says Proposed on 2026-10-04 and not accepted. This draft does not change their status. It does not accept `prereq/specs/ramp-amendments.md`, `price-lock-policy-stub.md`, `DRAFT-openapi-amendments.yaml`, or the pricing-policy sanity check.
**Code:** Nothing in this file authorizes code. It authorizes no schema change, Drools edit, flag, route, deploy, or merge. `opencrm.pricing.ramp.enabled` stays default off. No `opencrm.jev.*.enabled` flag is turned on. Later renewal periods stay unpriced while this file is Proposed.

This file unblocks DRG-568 only after the owner accepts this file and the status-change PR merges. Until that separate pull request merges, DRG-568 stays blocked. This file is not that status change.

## Owner adoption record

Relayed, and to be confirmed by the status-change pull request. This file stays Proposed. It is accepted only after the status-change PR merges; DRG-568 stays blocked until then.

Owner (drg) said at 7:31 AM CT 2026-10-17, as relayed by Projects Manager, that he accepts ADR-017, ADR-024 to 027 and DRG-533 (#644) and authorized the status-change PRs.

That sentence is a relay. The confirming act is the owner's merge of the status-change pull request. This edit is not that pull request, and it does not copy the status line from the open status-change draft [#669](https://github.com/drgaciw/open-crm-cpq/pull/669).

[#663](https://github.com/drgaciw/open-crm-cpq/pull/663), in [delegated-renewals-amendments-decisions-2026-10-17.md](delegated-renewals-amendments-decisions-2026-10-17.md), says ADR-017, ADR-024 to ADR-027, and the DRG-533 proposal are not accepted wholesale. The 7:31 AM CT relay is later than that sentence. Until the status-change pull request merges, the status line at the top of this file stays Proposed, and D-06 of the accepted uplift record still holds: later periods do not grow.

The relay names ADR-017 and ADR-024 to ADR-027. This file does not change their status. On this tree each of those ADRs still says Proposed on 2026-10-04. A status change for any of them is its own pull request.

Number stays none. [docs/adr/README.md](../../adr/README.md) ends at ADR-035. The repo does not assign the next number to this draft. Do not add a catalog row from this edit.

[Definition of Done v0.3](https://app.notion.com/p/3fcf7cb4e4df8133860dcc8613c620eb) items 6 and 23: the options and the scale-4 default are already in "Owner decision needed"; the owner words and the time are the relay above. An ADR counts as accepted only after the owner accepts it and the status-change pull request merges. This file stays Proposed until that merge.

---

## Context

DRG-533 asks for the escalator procedure ADR-023 left open: which base a percent hits, whether the percent compounds, and at which scale each step rounds. DRG-568 cannot price a 36-month renewal escalator or a lock that ends inside a renewal until that procedure has a name. The accepted uplift record already says the later periods wait.

D-06 of [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) (Accepted 2026-10-16):

> A multi-year renewal escalator, when one exists, starts from the single price this decision already computed. It does not grow later periods until DRG-533 is accepted.

The same decision says DRG-568 owns the segment schedule and the time-bounded locks, consumes the first-period price and the per-step cap check, and does not choose a second compounding rule. This draft is that missing procedure. It stays Proposed, so D-06 still holds.

### What the tree does today

Checked on this branch's base, `origin/main` at `e3ee211b`. The Java citations below were read in this pass.

1. **A renewal is one flat period at the exit rate, times one uplift, at scale 2.** `QuoteContinuationPolicy.plan` treats `RENEWAL` and `EXTENSION` together (`QuoteContinuationPolicy.java`, the `renewal` branch). The start date is the day after the subscription end. `termMonths` defaults to 12 and must be from 1 to 1200. A null `upliftPercent` becomes zero. The unit is `money(exitNet × (1 + upliftPercent/100))` with `money` = scale 2, half-up. The plan contains one `Period` whose `months` equals the whole term. Extension rejects a non-zero percent. `QuoteContinuationPolicyTest.renewalUsesExitRateAndEightPercentUplift` asserts an 8% uplift on an exit rate of 50 yields unit 54 and TCV 32,400 for 50 seats and 12 months (`54 × 50 × 12`). The same formula, applied to a 36-month term, a 7% uplift, and an exit net of 40, is one period of 36 months at 42.80. No test in that class asserts the 36-month case. That path does not call `RampEscalatorExpander`.

2. **The eligibility draft writes that flat period at zero uplift.** `QuoteContinuationService.draftRenewal` builds a `RENEWAL` request with no `upliftPercent` and no `termMonths`, so the policy uses zero and 12 months. The audit row stores `upliftPercent` as the string `"0"`. `renewalSource` requires exactly one accepted recurring line. A second line, a second segment, and a non-zero policy percent are outside this method.

3. **The new-quote escalator compounds on the prior unit at scale 4, in equal blocks.** `RampEscalatorExpander.expand` is used by `RampScheduleAttacher` and `RampScheduleQueryService`. It is not used by continuation. Percent mode sets the next unit to `prior × (1 + amount/100)` at scale 4, half-up. Amount mode adds the currency amount at scale 4, half-up. The amount is percent points: the class comment says 5 means 5 percent. Period 1 is the start price, already rounded to scale 4; the percent applies from period 2 onward. Every period has the same `everyMonths`. If the term is not divisible by that length, the expander throws `RAMP_TERM_MISMATCH` and returns no steps (`RampEscalatorExpanderTest`, 14 months with a 12-month step). `RampEscalatorExpanderTest` asserts 5% on 40.0000 for 36 months yields 40.0000, 42.0000, 44.1000, and that 1.1125% on 2.0000 yields 2.0223. Explicit steps win over the escalator (`RampScheduleAttacher`). With the ramp flag off, `prepare` throws `RAMP_NOT_SUPPORTED` (HTTP 422) before expansion. ADR-023 D-01 already says the escalator inputs are display-only after expansion, and D-06 keeps the flag off.

4. **An absolute ramp step is then discounted again when the line type is a named seat.** `RampPeriodPricer.baseUnit` returns an `ABSOLUTE` unit price unchanged. `priceStep` then multiplies by `(1 − namedSeatFraction)` at scale 4, half-up. The fraction is 0.07 at quantity 100 or more, 0.12 at 500 or more, and 0.18 at 1,000 or more, and only when `lineItemType` is `SAAS_SEAT_NAMED`. Other line types use a fraction of zero. Continuation does not go through this pricer. A renewal net that was later submitted as an absolute ramp step on a named-seat line would be reduced a second time.

5. **ADR-023 stored the shape and left the procedure open.** D-01 stores explicit periods with an absolute unit price or a discount fraction off list, and treats an escalator as a generator. D-02 rounds unit prices to scale 4 and amounts to scale 2, half-up. D-04 uses whole months on anniversary dates from the term start, and day proration only inside a period. D-05 starts a renewal as one flat period at the exit rate unless a new ramp is authored. D-07 applies a volume tier from that period's own quantity. The accepted uplift record amends D-05 only by naming `ContractedNet` as the default basis and allowing a rung to select `CurrentList` or `ListLessContractedDiscount`. Its D-05 rounds the single renewal unit at scale 4 and leaves per-step escalator rounding to DRG-533. Its D-02 re-checks cap and floor on stored prices. Its D-06 forbids growing later periods until this procedure is accepted.

6. **The illustrations DRG-568 cites are not what either path produces.** Sanity-check scenario 1 (example numbers, not customer data): exit net 40, 7% a year, 36 months, per-year rounding to scale 2 gives 42.80, then 45.80, then 49.01, TCV 330,264 at 200 seats. The same note says compounding with no intermediate rounding gives a year-3 unit of 40 × 1.07³ = 49.00. DRG-568 repeats 42.80 / 45.80 / 49.01, and 49.00 when compounding is unrounded. Scenario 4: an AI Assist line at 20, locked until 2027-03-31 inside a 14-month renewal from 2026-11-01, then 7%, is 5 months at 20 and 9 months at 21.40, total 14,630 at 50 seats. The expander, given start price 42.8000 and 7%, would emit 42.8000, 45.7960, 49.0017. Given start price 40, it would emit 40.0000, 42.8000, 45.7960, because period 1 is the start price and the percent begins at period 2. A 14-month term with a 12-month step is `RAMP_TERM_MISMATCH`. Continuation emits one 36-month period at 42.80 (scale 2) when the caller passes 7, or one 12-month period at the exit rate when the eligibility draft passes nothing.

The arithmetic in the next section was recomputed with `Decimal` half-up in this pass. It is not a quote from a test, except where a test is named.

## Decision

Proposed. Nothing in this section amends another ADR. Nothing in this section authorizes code. A later status-change pull request, after the owner answers "Owner decision needed", is a separate change from this file.

### D-01 — Where a renewal escalator starts

**Period 1 is the single renewal unit the accepted uplift record already computes (its D-01 through D-05). Later periods start from the prior period's stored absolute unit.**

That unit is the default `ContractedNet` result unless a rung selected another basis. It is already cap-checked and floor-checked. The escalator does not apply the renewal percent a second time to produce period 1. `RampEscalatorExpander` today does the opposite when its start price is the exit net: period 1 stays at the exit net and the percent starts at period 2. A renewal generator that wants 42.8000 in year 1 passes that computed unit as the start price.

`Same` has no later growth: every segment stays on the exit net. `List` and `Override` are explicit segment modes below, not silent inputs to the percent. Extension still rejects a non-zero uplift, as the policy does today. This draft does not give an extension a multi-year escalator.

### D-02 — Compounding

**The default is compound-on-prior. Each later percent step is `storedPrior × (1 + percentPoints / 100)`. A stored flag `compound` may be false. False applies each step's percent to the period-1 unit.**

Percent points match the accepted uplift record and `QuoteContinuationRequest.upliftPercent`: `7` means 7 percent. An amount step adds the currency amount to the prior stored unit, at the scale in D-03, and does not compound. `RampEscalatorExpander` rejects a negative start price or a negative amount with `RAMP_NEGATIVE_VALUE`. This proposal keeps that refusal. A price cut is an explicit `Override` or a basis change on the segment, not a negative escalator amount.

`compound = false` is the procedure the DRG-533 issue attributes to Salesforce RCA: a stated 5% is 5% of the original base in every year, and a compound path is entered as a cumulative percent of that base (5% compounded twice is 10.25% in the third year). **Unverified:** this draft did not re-fetch the Salesforce `initiateRenewal` page. The sentence is the Linear issue's source note, read 2026-10-16.

The landed expander has no flag. It always compounds on the prior unit. That behavior matches the recommended default. It is not, by itself, the decision. DRG-532 is where the generator learns to take the flag. This draft does not build that generator.

### D-03 — Rounding

**Each expanded unit is half-up at scale 4. Each extended amount is half-up at scale 2. The scale-4 unit is what the next period compounds from, and what the next renewal will read as its exit net.**

Scale 4 is ADR-023 D-02 and the accepted uplift record's D-05. Fork 2 in "Owner decision needed" is the scale-4 versus scale-2 choice, and that fork affects money rounding. The products below were recomputed with decimal half-up in this pass.

Recommended scale (unit scale 4, amount scale 2), 200 seats, 12 months each year:

```text
Year 1 unit     = round4(40 × 1.07)           = round4(42.80)     = 42.8000
Year 1 monthly  = round2(42.8000 × 200)       = 8,560.00
Year 1 amount   = round2(8,560.00 × 12)       = 102,720.00
Year 2 unit     = round4(42.8000 × 1.07)      = round4(45.796)    = 45.7960
Year 2 monthly  = round2(45.7960 × 200)       = 9,159.20
Year 2 amount   = round2(9,159.20 × 12)       = 109,910.40
Year 3 unit     = round4(45.7960 × 1.07)      = round4(49.00172)  = 49.0017
Year 3 monthly  = round2(49.0017 × 200)       = 9,800.34
Year 3 amount   = round2(9,800.34 × 12)       = 117,604.08
TCV             = 102,720.00 + 109,910.40 + 117,604.08 = 330,234.48
```

The scale-2 illustration, the same chain rounded half-up to scale 2 at each unit, is a different amount of money:

```text
Year 1 unit     = round2(40 × 1.07)           = 42.80
Year 1 amount   = round2(round2(42.80 × 200) × 12) = 102,720.00
Year 2 unit     = round2(42.80 × 1.07)        = round2(45.796) = 45.80
Year 2 amount   = round2(round2(45.80 × 200) × 12) = 109,920.00
Year 3 unit     = round2(45.80 × 1.07)        = round2(49.006) = 49.01
Year 3 amount   = round2(round2(49.01 × 200) × 12) = 117,624.00
TCV             = 102,720.00 + 109,920.00 + 117,624.00 = 330,264.00
Difference      = 330,264.00 − 330,234.48 = 29.52
```

Rounding only the final unit of an unrounded `40 × 1.07³` (= 49.00172) to scale 2 yields 49.00. The scale-2 series and the single final rounding stay illustrations. The recommendation stores the scale-4 units above.

The expander's own 1.1125% case (2.0000 → 2.0223) is this same per-step scale-4 half-up. A renewal uses that rule. It does not round the unit to scale 2 the way `QuoteContinuationPolicy.money` does today.

### D-04 — Renewal segment schedule

**A multi-period renewal line is an ordered list of segments. A pure annual escalator expands into equal whole-month segments. A time-bounded lock is an explicit list of unequal segments. The expander's equal-block rule does not grow a lock.**

Segment fields, as DRG-568's suggested next step names them: `from`, `to`, `mode`, `value`. Modes used here are the uplift record's modes that name a price (`Same`, `List`, `UpliftPct`, `UpliftAmt`, `Override`) plus a lock, which is an absolute unit held through `to`. `Renegotiate` still has no unit price.

Equal-block expansion, for a percent or amount escalator with no lock:

- Period length is `everyMonths`, a positive whole number of months.
- The term months must be divisible by `everyMonths`. Otherwise the result is `RAMP_TERM_MISMATCH` and no segments, which is what `RampEscalatorExpander` does today.
- Boundaries follow ADR-023 D-04: segment `k` starts at `termStart.plusMonths(startMonth − 1)`. The boundary date belongs to the new segment. A term that starts on the 31st does not drift.
- Each segment stores an absolute unit at scale 4 (D-03). The percent or amount that produced it is kept for display, as ADR-023 D-01 already requires for escalator inputs.
- Cap and floor from the accepted uplift record are applied to every stored absolute, reading that stored unit. The check does not re-derive the generator. A generated `UpliftPct` or `UpliftAmt` segment clamps to the normalized bounds the way that record's D-02 clamps those modes. An explicit `Override` or an explicit lock that falls outside the bounds is `PRICE_PROTECTION_CONFLICT` and stores no price, matching that record's treatment of `Override`.

The 14-month AI Assist illustration is not this expansion. 14 divided by 12 leaves a remainder, so the expander rejects it. The schedule is two explicit segments:

| Segment | Dates in the illustration | Months | Mode | Unit |
| --- | --- | --- | --- | --- |
| Lock | 2026-11-01 through 2027-03-31 | 5 | lock, absolute 20 | 20.0000 |
| After the lock | 2027-04-01 through 2027-12-31 | 9 | `UpliftPct` 7 on the locked unit | 21.4000 |

The lock total, recomputed in this pass:

```text
Post-lock unit  = round4(20 × 1.07) = round4(21.40) = 21.4000
                  round2(20 × 1.07) = 21.40
Lock monthly    = round2(20.0000 × 50) = 1,000.00
Lock amount     = round2(1,000.00 × 5) = 5,000.00
Post-lock monthly = round2(21.4000 × 50) = 1,070.00
Post-lock amount  = round2(1,070.00 × 9) = 9,630.00
Line total      = 5,000.00 + 9,630.00 = 14,630.00
```

Scale 4 and scale 2 store the same post-lock unit here, so this illustration does not show the money-rounding fork. The 36-month example does. The post-lock segment applies the percent once to the locked unit. It does not compound across the lock, and it does not open a second compounding rule.

Both illustration boundaries fall on whole months from a term start of 2026-11-01. ADR-023 D-04 and the accepted proration record charge a whole-month boundary with no day stub. The day-stub in `QuoteContinuationPolicy` remains the amendment and expansion formula. A renewal does not call it.

A lock whose end is not a month boundary from the renewal start has no splitter in the tree. **Unverified** as a product rule: this draft does not choose day proration for that end. The owner decision section asks whether DRG-568 includes it.

`draftRenewal` still writes one line. A renewal with Analytics Cloud, AI Assist, and a non-renewed connector is several lines, each with its own segment list. DRG-568 builds that list. This draft only fixes the price on each segment.

### D-05 — Discount and cost

**The unit stored on a renewal segment is the net unit after the renewal procedure. The named-seat tier does not run on it again. Cost does not enter the escalator formula.**

`RampPeriodPricer` applies the named-seat fraction on top of an absolute base for `SAAS_SEAT_NAMED`. The scenario-1 line is described as seats. **Unverified:** the sanity check does not name `lineItemType`, so it is not established that Analytics Cloud is `SAAS_SEAT_NAMED`. If a later implementation priced these renewal absolutes through `RampPeriodPricer` on that line type, 200 seats would take a further 7% off 42.8000. The recommendation is that renewal-generated segments skip that subtraction. The discount fraction used for governance is `(list − storedNet) / list` when list is present, which is the fraction `RampPeriodPricer` already computes after the net is known. ADR-023 D-07 still applies the period's own quantity when a period is priced as a discount off list, rather than as a finished renewal net.

DRG-526 (read 2026-10-16, status In Progress) owns effective-dated cost and says each ramp period uses the cost effective on that period's start, failing closed when cost is missing. This escalator does not read cost, does not treat the placeholder margin `40.0` as a margin, and does not turn margin governance on. ADR-023 D-06 remains the guardrail. This draft does not accept DRG-526. The project doc `finance-decisions-cost-basis.md` was not opened in this pass. **Unverified** beyond the Linear issue text.

### D-06 — What stays a marker

**`ENABLE_12M_PRICE_PROTECTION` remains the retention marker the accepted uplift record describes. A time-bounded lock is an explicit segment with dates. This draft does not turn the marker into a lock.**

The uplift record assigns that choice to DRG-568. DRG-568 could make it only after the owner accepts this file and the status-change PR merges, and only in DRG-568's own change. Nothing in this file authorizes that code. A contract cap is still the price protection that wins, as that record's D-02 says.

## Options considered

| Topic | Option | Consequence | Verdict |
| --- | --- | --- | --- |
| Later-period base | **Prior stored absolute, period 1 = the accepted uplift unit (recommended)** | Year 2 of a 7% escalator grows from 42.8000. Matches D-06's "starts from the single price". | Recommended |
| Later-period base | Exit net as the expander start price, percent applied from period 2 | Periods are 40.0000, 42.8000, 45.7960. Year 1 never receives the renewal percent. | Rejected as the renewal default |
| Later-period base | Current list, or the term average | A list drop or the average can price below the exit rate. ADR-023 D-05 and the uplift record already refused the average and year-1 of the prior ramp. | Rejected |
| Compounding | **`compound = true` by default; `false` applies the percent to period 1 (recommended)** | "7% a year" yields the growing series the sanity check describes. RCA's non-compound entry stays available by setting the flag false and typing a cumulative percent when compounding is intended. | Recommended |
| Compounding | Always percent-of-original, no flag | Year 3 at a stated 7% is 7% above period 1 (45.7960 if period 1 is 42.8000), not 49.0017. The DRG-568 illustration does not match. | Rejected as the default |
| Compounding | Adopt the expander with no flag and no documented default | The class already compounds, and DRG-533 exists because that class was not a decision. Shipping it unlabeled leaves RCA-style contracts and the illustration on the same path. | Rejected |
| Rounding | **Per-step half-up at scale 4, amounts at scale 2 (recommended)** | Units 42.8000, 45.7960, 49.0017. TCV 330,234.48. The next renewal's exit net is the stored scale-4 unit. | Recommended |
| Rounding | Per-step half-up at scale 2 | Units 42.80, 45.80, 49.01. TCV 330,264.00. Matches DRG-568's cited illustration and stores a coarser exit rate than the accepted single-unit scale. | Open. Owner may replace the recommendation |
| Rounding | Full-precision compound, round the final unit once | Year 3 at scale 2 is 49.00. Intermediate units are not stable stored prices, so a cap check and the next period have no scale-4 absolute to read. | Rejected |
| Lock shape | **Explicit segments `{from, to, mode, value}`; post-lock percent applies once to the locked unit (recommended)** | Scenario 4 is 5 months at 20.0000 and 9 months at 21.4000, total 14,630.00. The equal-block expander is not on this path. | Recommended |
| Lock shape | Encode the lock as `everyMonths` on `RampEscalatorExpander` | 14 months with a 12-month step is `RAMP_TERM_MISMATCH`. Equal blocks cannot be 5 and 9. | Rejected |
| Lock vs marker | **Keep `ENABLE_12M_PRICE_PROTECTION` as a marker (recommended)** | Agrees with the uplift record. A later DRG-568 change could still propose a dated segment, only after the owner accepts this file and the status-change PR merges. | Recommended |
| Named-seat tier | **Renewal segment nets skip a second named-seat subtraction (recommended)** | Stops 200 seats taking another 7% off a unit that is already net. | Recommended |
| Named-seat tier | Run `RampPeriodPricer` unchanged on renewal absolutes | For `SAAS_SEAT_NAMED` at quantity ≥ 100, the net falls by the tier. Continuation does not do this today. | Rejected |
| Off-boundary lock end | Day-stub from the proration record | The proration record prices amendments and expansions. No renewal splitter exists. | Deferred. Owner decision |
| Off-boundary lock end | **DRG-568's first slice uses month-boundary locks only (recommended)** | Scenario 4 is in scope. A lock ending on the 15th waits. | Recommended |

## Consequences

- This file unblocks DRG-568 only after the owner accepts this file and the status-change PR merges. Until that merge, the priced renewal remains the one flat period ADR-023 D-05 and the uplift record require. Escalator inputs may be copied as intent. They are not expanded. Nothing in this file authorizes code.
- DRG-568 is the story that would implement the segment list, the lock dates, and the multi-line renewal, using D-01 through D-06, and it would not choose another compound rule or another unit scale. That implementation is not authorized here.
- DRG-532's generator, when it is built, takes `compound`, the period-1 unit, the percent or amount, and `everyMonths`, and emits the equal-block segments in D-04. It refuses a term the step length does not divide.
- DRG-534 would amend stored absolute periods. Regenerating them from the percent waits until the owner accepts this file and the status-change PR merges. The amendment story still owns elapsed-period locking. This draft does not accept `prereq/specs/ramp-amendments.md`, and nothing here authorizes that code.
- Fork 2 affects money rounding. On the 200-seat, 36-month example the scale-2 illustration is 330,264.00 and the scale-4 recommendation is 330,234.48. The difference is 29.52. A seller who has been shown 45.80 and 49.01 is looking at the scale-2 option. The default in the owner table is scale 4.
- `QuoteContinuationPolicy` rounding the renewal unit to scale 2, and `draftRenewal` writing `"0"`, stay as they are. This draft does not change them.
- ADR-023 is not edited. ADR-017 and ADR-024 through ADR-027 stay Proposed.

## Risks

- Choosing the scale-4 default while DRG-568's issue text cites 45.80 / 49.01 / 330,264 will look like a wrong implementation if a later writer follows the issue text instead of this procedure. The control is the worked arithmetic in D-03, and fork 2, which affects money rounding.
- `compound = true` prices a growing series. A contract written to the RCA note in DRG-533 (stated 5% means 5% of the original base unless the year-3 cell is entered as 10.25%) will be overpriced if the flag stays at the default. The control is the stored `compound` flag on the snapshot DRG-568 writes.
- Calling `RampEscalatorExpander` for the 14-month lock throws `RAMP_TERM_MISMATCH` and prices nothing. The control is D-04: locks are explicit segments.
- Sending a finished renewal net through `RampPeriodPricer` on `SAAS_SEAT_NAMED` takes the named-seat tier off again. The control is D-05.
- Expanding later periods before the status-change pull request merges contradicts the accepted uplift record's D-06.
- Treating this Proposed file, the price-lock stub, or the ramp-amendment proposal as an authorization to code would let an implementation merge against text the owner has not adopted. This file stays Proposed.

## What DRG-568 needs from this draft

DRG-568 (RA-34, read 2026-10-16, status Backlog) says a 36-month 7% escalator and the AI Assist lock are not expressible, and that it depends on DRG-533. This file unblocks DRG-568 only after the owner accepts this file and the status-change PR merges. Nothing in this file authorizes code. The inputs below are what that later story would use:

1. Period 1 is the uplift record's unit (42.8000 in the example). Later percent steps use D-02 and D-03.
2. Under the recommendation, the 36-month series is 42.8000, 45.7960, 49.0017, TCV 330,234.48, not 42.80 / 45.80 / 49.01, unless the owner replaces D-03 with scale 2.
3. The lock is two explicit segments, 20.0000 for five months and 21.4000 for nine, total 14,630.00. The post-lock mode applies the percent once to the locked unit.
4. Cap and floor are re-checked on every stored absolute. A generated step clamps. An explicit lock or override outside the bounds is `PRICE_PROTECTION_CONFLICT`.
5. The segment list is per line. `draftRenewal`'s single-line, zero-uplift, 12-month draft is the current writer. Replacing it is DRG-568's work, and it is not authorized by this Proposed file.
6. The retention marker is not a lock unless DRG-568 itself decides that, after this acceptance.
7. Month-boundary locks are the first slice if the owner keeps that recommendation. A lock ending mid-month is not specified here.
8. No second compounding rule, and no use of the equal-block expander for unequal lock segments.

## Owner decision needed

This file is Proposed. The seven recommendations are not in force. Each row has one recommendation and one default. The owner may keep the default or replace it. This file does not record that choice. Nothing in this file authorizes code.

This file unblocks DRG-568 only after the owner accepts this file and the status-change PR merges.

| # | Fork | Recommendation | Default |
| --- | --- | --- | --- |
| 1 | What "N% a year" means | Compound each later percent on the prior stored unit, and keep percent-of-period-1 available as `compound = false`. | `compound = true` |
| 2 | Unit scale versus the scale-2 illustration. **This fork affects money rounding.** | Store each unit half-up at scale 4 and each amount half-up at scale 2, which prices the 36-month example at 330,234.48 rather than 330,264.00. | Scale 4 on the unit, scale 2 on the amount |
| 3 | Price of the segment after a lock | Apply that segment's percent or amount once to the locked unit, and keep `List`, `Same`, and `Override` as explicit modes. | One uplift of the locked unit |
| 4 | Segments whose lengths differ | Author them as an explicit `{from, to, mode, value}` list, and leave the equal-block expander off that path. | Explicit segments only |
| 5 | Named-seat tier on a renewal net | Skip a second named-seat subtraction on a unit the renewal procedure already netted. | No second named-seat tier |
| 6 | A lock that ends off a month boundary | Leave that end out of DRG-568's first slice, and leave the amendment day-stub on the amendment path. | Month-boundary locks only |
| 7 | The retention marker | Leave `ENABLE_12M_PRICE_PROTECTION` as a marker, and leave any dated segment to a later DRG-568 change. | Marker, not a lock |

Until the owner accepts this file and the status-change PR merges, D-06 of the uplift record stands: later periods do not grow.

**Unblocks: DRG-568 only after the owner accepts this file and the status-change PR merges. Nothing in this file authorizes code.**

## Edge-case tests for DRG-532, DRG-568, and DRG-534

[Definition of Done v0.3](https://app.notion.com/p/3fcf7cb4e4df8133860dcc8613c620eb) item 4. This is the list those stories test. It does not change D-03. The rounding default merged in [#644](https://github.com/drgaciw/open-crm-cpq/pull/644) stays scale 4, half-up, compounding on the stored unit. On the 36-month 7% example that default stores year 3 as 49.0017.

A 2026-10-16 escalator-semantics draft recommended carrying each period forward at currency precision and cited the per-step 2 dp series ($49.01), and it left the unrounded $49.00 figure as an open question. That draft conflicts with D-03. It is background. It is not adopted here.

| Case | Stories | What the test asserts |
| --- | --- | --- |
| Rounding, scale-4 default | DRG-532, DRG-568 | Year-3 unit is 49.0017 (`round4(45.7960 × 1.07)`). Amounts stay scale 2. The 200-seat illustration totals 330,234.48. |
| Rounding, 2 dp per step | DRG-568 | Alternative, not the default. Per-step half-up at 2 dp yields $49.01, and the same illustration totals 330,264.00. Fork 2. |
| Rounding, unrounded compound, then 2 dp once | DRG-568 | Alternative, not the default. `40 × 1.07³` (= 49.00172) rounded half-up to 2 dp is $49.00. D-03 rejects this as the stored series. |
| Date boundary belongs to the new segment | DRG-532, DRG-568 | Segment `k` starts at `termStart.plusMonths(startMonth − 1)`. A term that starts on the 31st does not drift. |
| Month-boundary lock | DRG-568 | 2026-11-01 through 2027-03-31 at 20.0000, then 2027-04-01 through 2027-12-31 at 21.4000. Line total 14,630.00 at 50 seats. |
| Lock end off a month boundary | DRG-568 | Out of the first slice (fork 6). The test shows the case is refused or left unpriced, not day-prorated. |
| Negative escalator | DRG-532 | A negative start price or a negative amount is `RAMP_NEGATIVE_VALUE`. A price cut is an explicit `Override` or a basis change. |
| Zero escalator | DRG-532, DRG-568 | A zero percent, and a null continuation uplift (the policy treats null as zero), stores every period at the period-1 unit. |
| Proration on a whole-month renewal boundary | DRG-568 | No day stub. ADR-023 D-04 and the accepted proration record charge that boundary as a whole month. |
| Proration on an amendment or expansion | DRG-534 | The day-stub in the accepted proration record. A renewal escalator does not call that formula. |
| Elapsed-period lock | DRG-534 | Elapsed periods stay immutable. An amendment regenerates only future periods, from the last elapsed stored absolute. |
| Renewal uplift applied once | DRG-568 | Period 1 is the single unit the accepted uplift record already computed. The escalator does not apply that percent again. After a lock, the post-lock percent applies once to the locked unit. |
| CRM DealType handoff | DRG-532, DRG-534, DRG-568 | See the next section. `EXISTING_BUSINESS` becomes `EXPANSION`. `AMENDMENT` is reached only by typed continuation. |

Totals in each rounding row reconcile to the units that row stores. The default row is the scale-4 series. The two 2 dp rows are fixtures for fork 2, not a second default.

## DealType handling

CRM `EXISTING_BUSINESS` maps to quote `EXPANSION`. That row is a bot decision of 2026-10-16 under owner delegation, validated by CPQ Decision at confidence 0.7, and it is reversible: the single flip is `Source.EXISTING_BUSINESS`. The landed record is [crm-quote-dealtype-mapping.md](../crm-quote-dealtype-mapping.md) (DRG-538) and `CrmQuoteDealTypeMapping`.

`AMENDMENT` is not a CRM mapping target. It is reachable only through typed continuation (`QuoteContinuationRequest.Operation.AMENDMENT`), the only path that may replace a source line.

This draft does not change that map. A renewal escalator runs on a `RENEWAL` continuation. An amendment of stored ramp periods (DRG-534) runs on typed `AMENDMENT`. An `EXISTING_BUSINESS` CRM deal does not become an amendment through this file.

## Out of scope

One-action authoring of equal blocks (DRG-532). Elapsed-period locking on an amendment (DRG-534). Renewal mode inheritance beyond what the accepted uplift record already fixed (DRG-549). Price-book retrieval (DRG-539). The audit table (DRG-564). Cost ingestion (DRG-526). True-up, maintenance percent, conversion, and amendment proration. Any subscriptions user interface. Any flag. Any edit to an Accepted ADR. Accepting ADR-017 or ADR-024 through ADR-027.

## Unverified

- Salesforce RCA `initiateRenewal` / `rampOptionsDetails`, and the claim that a standard procedure does not compound. Source in this pass: the DRG-533 issue text. The Salesforce page was not re-fetched.
- Maxio Renewal Factor and Lock-in Date. Source: the DRG-568 issue and the sanity check. The Maxio page was not re-fetched.
- `lineItemType` of the illustrative Analytics Cloud and AI Assist lines. The double-discount risk is conditional on `SAAS_SEAT_NAMED`.
- `finance-decisions-cost-basis.md`. DRG-526's issue text was read. The project doc was not.
- `amendment-entity-field-schema.md` field names (`segment_schedule`, `policy_slot`). The price-lock stub cites them. That stub is a stub. The schema file was not re-opened, and the stub is not accepted.
- Day proration for a lock that ends off a month boundary. No renewal segment splitter was found in `QuoteContinuationPolicy` or `RampEscalatorExpander`.
- GitNexus impact on this draft. No GitNexus runner is present on this VM (`.gitnexus/run.cjs` is absent). Impact is UNKNOWN.
- Notion DoD page `verification.state` was `unverified` at fetch (2026-10-16). The page body that was fetched says v0.2 is approved. This draft does not edit that page.
- Definition of Done v0.3, fetched 2026-10-17. `verification.state` was `unverified`. The page body says the owner approved v0.3 at 7:42 AM CT on 2026-10-17. Items 4, 6, and 23 are the ones this follow-up records. The page was not edited.

## References

- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` — renewal branch, one period, scale-2 `money`.
- `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteContinuationPolicyTest.java` — `renewalUsesExitRateAndEightPercentUplift`.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationService.java` — `draftRenewal`, one source line, audit `upliftPercent` `"0"`.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorExpander.java` — compound-on-prior, scale 4, equal blocks, `RAMP_TERM_MISMATCH`.
- `backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampEscalatorExpanderTest.java` — 40.0000 / 42.0000 / 44.1000 and the 2.0223 half-up case.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleAttacher.java` — explicit steps win; flag off throws before expansion.
- `backend/services/pricing-service/src/main/java/com/opencrm/pricing/ramp/RampPeriodPricer.java` — absolute base, then named-seat fraction for `SAAS_SEAT_NAMED`.
- [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-01, D-02, D-04, D-05, D-06, D-07.
- [adr-renewal-uplift-basis-and-precedence.md](adr-renewal-uplift-basis-and-precedence.md) D-05 and D-06.
- [adr-amendment-proration-formula.md](adr-amendment-proration-formula.md) — day-stub for amendments and expansions.
- `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md` — scenarios 1 and 4.
- [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533/adr-ramp-escalator-semantics-compounding-base-price-rounding-amending), [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568/multi-year-renewal-escalators-and-time-bounded-price-locks-in-renewal), [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532/ramp-schedule-authoring-generate-all-periods-from-term-escalator-no), [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534/ramp-amendments-lock-elapsed-periods-amend-future-periods-re-govern-on), [DRG-526](https://linear.app/drgamtd-workspace/issue/DRG-526/bc-03-13-cost-basis-ownership-and-ingestion-from-erp-p-2).
- [crm-quote-dealtype-mapping.md](../crm-quote-dealtype-mapping.md) — CRM `EXISTING_BUSINESS` to quote `EXPANSION`; `AMENDMENT` only by typed continuation.
- [Definition of Done v0.3](https://app.notion.com/p/3fcf7cb4e4df8133860dcc8613c620eb) items 4, 6, and 23.
