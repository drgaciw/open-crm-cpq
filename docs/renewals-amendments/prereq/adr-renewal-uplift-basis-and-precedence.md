# Renewal uplift basis, cap and floor, price-as-of date, and precedence

**Status:** Accepted 2026-10-16 (owner approval in chat, 4:26 PM CT)
**Acceptance scope:** This status-change record implements the owner approval recorded in PR #623. Pre-acceptance workflow references below do not override the Accepted status. Runtime implementation, migrations and flag rollout remain separate work; this record does not accept ADR-024 through ADR-027.
**Date:** 2026-10-16
**Deciders:** Project owner (accepted 2026-10-16).
**Number:** none. This file is a draft proposal under `docs/renewals-amendments/prereq/`. It is not an entry in `docs/adr/` and it does not take a number.
**Unblocks:** [DRG-567](https://linear.app/drgamtd-workspace/issue/DRG-567/renewal-uplift-basis-caps-price-as-of-date-and-policy) (RA-33, gap G1), once the owner accepts this text. Until then the text is the decision DRG-567 implements against, and that implementation stays unmerged.
**Informs:** [DRG-541](https://linear.app/drgamtd-workspace/issue/DRG-541/renewal-uplift-drools-rule-never-executes) (where the seeded uplift rule sits), [DRG-549](https://linear.app/drgamtd-workspace/issue/DRG-549/renewal-pricing-policy-hierarchy-same-list-uplift-percent-uplift) (the mode walk uses this order).
**Builds on:** [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533/adr-ramp-escalator-semantics-compounding-base-price-rounding) (escalator base, compounding, rounding). That addendum is not on `main`. This proposal derives the idea from the Linear issue and from the tree, and it does not treat `RampEscalatorExpander` as the decision.
**Relates to:** [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-01, D-02, D-05 (exit-period rate; explicit periods); [ADR-008](../../adr/ADR-008-postgres-schema-per-service.md); [ADR-012](../../adr/ADR-012-rules-engine-port-abstraction.md); [DRG-532](https://linear.app/drgamtd-workspace/issue/DRG-532/ramp-schedule-authoring-generate-all-periods-from-term-escalator-no); [DRG-539](https://linear.app/drgamtd-workspace/issue/DRG-539); [DRG-564](https://linear.app/drgamtd-workspace/issue/DRG-564); [DRG-568](https://linear.app/drgamtd-workspace/issue/DRG-568). Renewal policy stub RN3. Sanity check scenario 1 and G1.
**On acceptance, amends:** ADR-023 D-05, only as D-01 below states. Acceptance is the owner's act. This draft does not amend anything.
**Does not authorize:** code, schema, Drools edits, a flag, a route, a deploy, or a merge. The ramp flag stays off. No `opencrm.jev.*.enabled` flag is turned on. Extension pricing is unchanged.

---

## Context

A renewal percent has four defensible results on one illustrative line, and the product has no rule that picks one.

Illustrative scenario 1 in `docs/renewals-amendments/cpq-pricing-policy-sanity-check.md` (example numbers, not customer data): Analytics Cloud, 200 seats, contracted net $40, prior list $50, new list $52 from 2026-11-01, account uplift 7%. The same words yield $42.80 (contracted net), $41.60 (new list less the contracted discount), $52.00 (new list), or $41.20 (the seeded rule at a churn score above 70). A contract can also cap the increase, and the price book can change between the day the quote is opened (2026-08-02) and the day the new term starts (2026-11-01).

What the tree does today:

1. **The caller supplies the percent, and the default is zero.** `QuoteContinuationPolicy` reads `upliftPercent`, substitutes zero when it is null, and multiplies the last rate period's net unit price. The result is one flat period. Extension rejects a non-zero percent. See `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` (the renewal branch, including `movePointLeft(2)` and `money` at scale 2).
2. **pricing-service does not compute a renewal price.** `QuoteService.verifyContinuationPeriods` and the partial-period check throw `SUBSCRIPTION_CONTINUATION_RATE_CHANGED` when the priced net unit differs from the unit the continuation plan already chose (`QuoteService.java` in quote-service). The service can reject a changed net. It cannot apply a list basis, a cap, or an as-of date.
3. **The seeded rule never runs, and its unit is a fraction.** `backend/services/pricing-service/src/main/resources/rules/renewal-uplift.drl` sets `renewalUpliftPct` to `0.08`, or to `0.03` when `churnScore > 70`, and may set `retentionOffer` to `ENABLE_12M_PRICE_PROTECTION`. It fires only when `dealType` is `RENEWAL`. Continuation pricing does not send `dealType` or churn (DRG-541). The continuation request treats the percent as percent points (`8` means 8 percent). The rule stores a fraction under a field named `Pct`.
4. **ADR-023 already fixed the ramp's renewal base, and left the escalator procedure open.** D-05 applies renewal uplift to the exit-period rate and starts a renewal as one flat period. An 8% uplift on an exit rate of $50 is $54.00. The same uplift on the three-year average is $46.44, below the price in force, which is why the exit rate won. D-01 stores explicit absolute unit prices, or a discount off list, and treats an escalator as a generator that expands into those periods.
5. **DRG-533, not on `main`, is the open procedure decision.** The Linear issue records the RCA observation: a standard pricing procedure does not compound, so a compound 5% a year is entered as 10.25% in year 3 (`1.05² − 1 = 0.1025`), and compounding is a separate procedure change. ADR-023 does not choose percent-of-list versus percent-of-prior, compounding, or currency rounding for that generator. `RampEscalatorExpander` on `main` does compound: each percent step is `prior × (1 + amount/100)` at scale 4, half-up (`backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampEscalatorExpander.java`). DRG-533 exists because that class is not an accepted semantic. DRG-532 asks the generator to take an explicit compound flag. DRG-567 defers escalator rounding to DRG-533.

The renewal policy stub already reserves the slot: basis, cap, floor, price-as-of, rounding, and the order contract clause, then line, then agreement, then account, then the rules default, snapshotted per line (RN3). This proposal fills that slot for a single renewal price. It leaves multi-year expansion to DRG-533 and DRG-568.

## Decision

pricing-service is the only component that merges the rungs and computes the renewal unit price. quote-service sends the five rungs as separate facts, plus the exit net, the prior list, the list at the as-of date, the price-book version, the clause id, currency, and quantity. A single caller `upliftPercent` is not an input. pricing-service stays stateless and does not read another service's database (ADR-008). The rules rung, when it runs, runs through the rules port (ADR-012). Basis, cap, floor, and as-of arithmetic stay in pricing-service, beside the port, so a clause can cap a percent the rule only supplied.

### D-01 — Basis, and the default

**The default basis is `ContractedNet`: the exit-period net unit price. Two other bases exist and apply only when a rung sets them.**

| Basis | Amount the percent or the uplift amount hits |
| --- | --- |
| `ContractedNet` | Exit-period net unit price. The last non-retired rate period whose end date is the subscription end date, which is the period `QuoteContinuationPolicy` already requires. On a ramp, that is the exit rate in ADR-023 D-05. It is the year-1 rate only when the schedule has one period. It is never the term average. |
| `CurrentList` | List unit price of this product, in the subscription currency, on the price book whose effective interval contains the price-as-of date (D-03). |
| `ListLessContractedDiscount` | Current list times one minus the contracted discount fraction: `currentList × (1 − discountFraction)`. The fraction is `max(0, 1 − exitNet / priorList)`, where `priorList` is the list unit pinned on the accepted source line that produced the exit rate. A net above the prior list contributes a fraction of zero, so the result equals current list. |

Policy percents in this decision are **percent points**. `7` means 7 percent, matching `QuoteContinuationRequest.upliftPercent` (0 through 100, then `movePointLeft(2)`) and the account policy in the sanity check. The seeded rule stores a fraction, `0.08` and `0.03`, which is the same convention as the 7 percent named-seat discount (`0.07` in `volume-discount-named-seat.drl`). `ManagedSeedOptionalInputsIntegrationTest` asserts the churn path equals `0.03`. The rules rung converts that fraction to percent points before the merge (`0.08` becomes `8`). Passing `0.08` through as percent points would price a 0.08 percent uplift. Applying `movePointLeft(2)` a second time would shrink it again. DRG-541 does the conversion at this boundary. This draft does not edit the DRL.

Formulas, before cap, floor, and rounding:

- `UpliftPct`: `basisAmount × (1 + percentPoints / 100)`.
- `UpliftAmt`: `basisAmount + amount`, with `amount` a signed currency delta per unit (the same unit the net price uses, not an annual lump).
- `Same`: the exit net. Basis and percent are ignored.
- `List`: current list at the price-as-of date. The basis field is ignored because the mode already names the list.
- `Override`: the explicit unit price on the line, with actor and reason as the amendment spine already requires for an override.
- `Renegotiate`: no unit price. The snapshot still records cap, floor, and price-as-of so a later human price is checked against them. DRG-549 owns when the mode is allowed.

`List` and `ListLessContractedDiscount` need a price book. DRG-539 is the read. A missing list, a missing price-book version, or a prior list that is missing or zero while the basis is `ListLessContractedDiscount`, yields `UNRESOLVED_BASIS` and no price.

On acceptance, D-05 of ADR-023 is amended only in this way: the exit-period rate remains the default renewal base, under the name `ContractedNet`, and a rung may select `CurrentList` or `ListLessContractedDiscount` instead. Extension still holds the exit rate and still rejects a non-zero uplift. Year-1 and the term average stay rejected for the reason D-05 gives.

### D-02 — Cap and floor

**Caps and floors are constraints on the exit net and on list. They are applied after the basis formula. A constraint that contradicts another constraint produces no price.**

Cap, both optional:

- `maxIncreasePct`: percent points. The ceiling is the exit net times `(1 + maxIncreasePct / 100)`. Zero means the renewal unit cannot exceed the exit net. The ceiling is measured from the exit net, including when the basis is list, because a price-protection clause limits the increase from the price in force.
- `notAboveList`: the ceiling is also the list unit at the price-as-of date. When this flag is set and that list is missing, the result is `UNRESOLVED_BASIS`.

Floor:

- `notBelowExitNet`: when no rung sets it, the default is **true**. The renewal unit is at least the exit net. This is the floor that matches D-05's reason for refusing a base that undercuts the price in force. A rung may set it to false; a contract clause that sets it to true beats an account that sets it to false (D-04).
- `minIncreasePct`: optional percent points above the exit net. The floor is the exit net times `(1 + minIncreasePct / 100)`.
- `minUnitAmount`: optional absolute currency floor on the unit price.

Among floors, the higher bound is the floor. Among caps, the lower bound is the cap. Order:

1. Compute the candidate and each monetary constraint using decimal arithmetic before unit-scale rounding.
2. Normalize every cap to scale 4 with `RoundingMode.FLOOR`, and every floor to scale 4 with `RoundingMode.CEILING`. This keeps the stored unit at or below each exact maximum and at or above each exact minimum. Combine the normalized bounds: the cap is their minimum and the floor their maximum.
3. If the normalized floor is above the normalized cap, return `PRICE_PROTECTION_CONFLICT` for every mode, before selecting a price. This includes an exact interval that contains no representable scale-4 unit.
4. Round the candidate half-up to scale 4 (D-05). For `List`, `UpliftPct`, and `UpliftAmt`, clamp down to the normalized cap, then raise to the normalized floor. The snapshot records `capApplied` and `floorApplied` with both the exact bound and its normalized bound.
5. `Same` stays on the stored scale-4 exit net. If that unit is outside either normalized bound, return `PRICE_PROTECTION_CONFLICT`. A clause that wants the price down at list sets mode `List`, or sets `notBelowExitNet` to false.
6. `Override` is an explicit number, normalized half-up to scale 4 under D-05. Clamping it would publish a unit the seller did not enter. If the normalized candidate is outside the normalized cap or floor, return `PRICE_PROTECTION_CONFLICT` and store no price. Record the entered and normalized values in the snapshot.

No rounding after clamping may move the unit outside these bounds. For example, exit net `40.0000` and `maxIncreasePct = 3.3333` give an exact cap of `41.33332`, normalized down to `41.3333`. The same exact amount as a floor normalizes up to `41.3334`. With both constraints, no scale-4 unit satisfies the interval, so the result is `PRICE_PROTECTION_CONFLICT`. These are boundary acceptance cases for a later implementation.

The rules rung's `ENABLE_12M_PRICE_PROTECTION` string is a retention marker. It is not a cap, a floor, or a lock. DRG-568 decides whether a marker becomes a time-bounded lock. A contract clause that sets a cap is the price protection that wins.

### D-03 — Price-as-of date

**The default price-as-of is `EffectiveDate`. The choice is per line. The price-book version that contains that date is pinned on the decision.**

| Value | Calendar date used to select the list |
| --- | --- |
| `ActionDate` | The lifecycle change's action date. In scenario 1 that is 2026-08-02, while the book that lists $52 is not yet effective. |
| `EffectiveDate` | The renewal's contract effective date. The renewal policy stub RN1 sets that to the day after the current term end, unless a co-term anchor (DRG-554) applies. In scenario 1 that is 2026-11-01. |
| `LineStart` | That line's renewed `effective_from`. It differs from `EffectiveDate` when lines in one renewal do not share a start. |

`ContractedNet` does not read the book for its basis amount. `notAboveList`, `CurrentList`, and `ListLessContractedDiscount` do. The snapshot stores the enum, the calendar date, and the price-book version id. A later recalculation uses the pin. A fresh pricing run, explicitly requested, resolves the book again and writes a new snapshot.

The schema note that defaults `price_as_of` to `EffectiveDate` agrees with this decision (`docs/renewals-amendments/amendment-entity-field-schema.md`, line field `price_as_of`).

### D-04 — Precedence

**Each field is taken from the first rung that sets it: contract clause, then line, then agreement, then account, then the rules default. Unset fields fall through. The defaults in D-01, D-02, and D-03 apply only when every rung leaves the field unset.**

A contract clause is a price-protection term identified by id on the contract. It is not the agreement's commercial renewal method. The agreement rung is that method (mode, percent, amount, basis), which is the Salesforce contract-level renewal field in the research behind DRG-549. The line rung is the subscription-level override. A line `Same` therefore beats an account `UpliftPct` of 7%, and a clause that only sets `maxIncreasePct` leaves that mode in place and then caps the result.

The rules rung may supply `upliftPct` in percent points, and the retention marker. It does not supply mode, basis, cap, floor, or price-as-of. When the rule does not run, those fields are simply absent at that rung. An `UpliftPct` mode whose percent is absent at every rung, including a rule that did not run, is `UNRESOLVED_UPLIFT`. The price is not the exit net with a silent zero percent. That zero is the current caller default, and it is the gap. A mode that no rung sets is `UNRESOLVED_RENEWAL_MODE`. The rules rung does not invent a mode. DRG-549's resolver uses this order and these two unresolved results.

quote-service sends the rungs unmerged. pricing-service merges them. The snapshot names, for every field, the value and the rung that supplied it, plus the clause id when the clause rung contributed.

### D-05 — Rounding of the single renewal unit

**The candidate renewal unit price is half-up at scale 4, then validated or clamped against the directionally normalized scale-4 bounds in D-02. The final unit always has scale 4. The extended amount is half-up at scale 2. Per-step rounding inside an escalator is DRG-533's decision, and this proposal does not make it.**

Scale 4 on the unit is ADR-023 D-02. The unit here is the monthly net unit price. Scale 2 on the amount is the same decision: monthly amount is unit times quantity, and the flat period amount is that monthly amount times the term months, half-up at scale 2, via `BigDecimal`. Today's continuation path rounds the unit itself to scale 2. On acceptance, the renewal unit follows scale 4 so the next term's exit rate is the number this decision stored. The illustrative 7% on $40 is $42.8000 either way.

The sanity check's three-year variant ($42.80, then $45.80, then $49.01 with per-year rounding, versus $49.00 with no intermediate rounding) is a per-step question. DRG-533 owns it, together with the compound-versus-cumulative choice and the currency precision of the generator. This decision's scale 4 rounds one candidate and normalizes its constraints as D-02 specifies. It does not license expanding later years.

### D-06 — What a later escalator is allowed to assume

**A multi-year renewal escalator, when one exists, starts from the single price this decision already computed. It does not grow later periods until DRG-533 is accepted.**

DRG-533's idea, taken from the issue and checked against the tree:

- A percent is undefined until the procedure names the base it hits.
- Compounding is a procedure choice. RCA's standard procedure applies each period's percent to the original base, so a compound path is entered as a cumulative percent (5% compounded once is 10.25% in the third year). It is not implied by the words "5% a year."
- Rounding and currency scale are part of that procedure.
- Governance has to be able to run on stored absolute prices, because the generator formula is not yet a decision. ADR-023 D-01 already stores those absolutes. `RampEscalatorExpander` compounds on the prior unit anyway. That is tree behavior, and it is not this proposal's rule.

Applied here:

- D-01 is the renewal percent's named base. The default, `ContractedNet`, is the price already in force (the exit period), which is the renewal counterpart of a prior-period base.
- The first period of a renewal is the unit price after D-01 through D-05. That unit is the start price a future DRG-533 procedure receives, along with the escalator percent or amount and that procedure's own compound flag (the flag DRG-532 asks the generator to take).
- Cap and floor are checked again on every expanded absolute unit. The check reads the stored price. It does not re-derive the generator.
- While DRG-533 remains unaccepted, the priced renewal is the one flat period ADR-023 D-05 already requires. Escalator inputs may be copied onto the snapshot as intent. They are not expanded, and later periods are not priced from them.

DRG-568 owns the segment schedule and time-bounded locks. It consumes this start price and this per-step cap check. It does not choose a second compounding rule.

### D-07 — Snapshot

Every priced renewal line stores a policy decision containing: mode; basis; basis amount; percent or amount; exit net; prior list; list; price-as-of enum, calendar date, and price-book version; each cap and floor field with the rung that set it; `capApplied` and `floorApplied`; unit price at scale 4; extended amount at scale 2; clause id; rules retention marker when the rules rung returned one; and, on failure, `UNRESOLVED_BASIS`, `UNRESOLVED_UPLIFT`, `UNRESOLVED_RENEWAL_MODE`, or `PRICE_PROTECTION_CONFLICT`.

RN3 already requires basis, cap applied, price-as-of, rounding, and the precedence level. DRG-564 owns the audit store. This proposal lists the fields that store keeps. Churn score is an input to the rules rung only. The customer-facing document does not gain a churn field from this decision. The trace records whether the rules rung supplied the percent and the existing explanation code `RENEWAL_UPLIFT_POLICY`.

Currency is the subscription currency. This decision does not convert currency.

## Worked example

Scenario 1 numbers. Exit net $40.0000, prior list $50, list $52 effective 2026-11-01, action date 2026-08-02, effective date and line start 2026-11-01. Account rung: mode `UpliftPct`, percent `7`. No clause. No line override on the seat line. Support line: line rung sets `Same`.

Seat line, as this proposal resolves it:

- Mode and percent come from the account. Basis falls through to `ContractedNet`. Price-as-of falls through to `EffectiveDate` (2026-11-01), so the pinned list is $52.
- Candidate `40 × 1.07 = 42.8000`. No cap is set. The default floor `notBelowExitNet` holds and does not move the price.
- Extended amount at 200 seats for 12 months is $102,720.00. Prior ARR at $40 is $96,000.00.

Support line: the line rung's `Same` beats the account percent. The unit stays the exit net.

The other three figures are what a different basis or a missing account percent would do. They are not the resolved price of this setup:

| Setup | Unit | ARR at 200 seats |
| --- | --- | --- |
| Resolved: account 7% on `ContractedNet` | 42.8000 | 102,720.00 |
| Basis forced to `ListLessContractedDiscount` (fraction 0.20, list 52), account 7% retained | 44.5120 | 106,828.80 |
| Basis forced to `CurrentList`, account 7% retained | 55.6400 | 133,536.00 |
| No account percent, rules rung at churn above 70, percent points `3` on `ContractedNet` | 41.2000 | 98,880.00 |

With the account percent present, the rules rung's `3` does not replace `7`.

A clause that sets only `maxIncreasePct` of `5` leaves the account mode and the 7% in place, then caps the unit at `40 × 1.05 = 42.0000` and records `capApplied`. ARR is $100,800.00. A line override of 7%, or an override unit of $44, does not beat that clause. The $44 override is `PRICE_PROTECTION_CONFLICT`.

`ActionDate` would pin the pre-change list of $50. The resolved `ContractedNet` price stays $42.8000. With the same `UpliftPct` mode and 7%, `CurrentList` would yield $53.5000 (`50 × 1.07`), and `ListLessContractedDiscount` would yield $42.8000 (`50 × 0.80 × 1.07`). The default `EffectiveDate` is what makes the $52 book the one list bases use.

A 36-month escalator at 7% is not given a year-2 or year-3 price here. The first period, if the term were priced under this decision, would still be $42.8000, and that figure is the start price DRG-533 would receive. The sanity check's $45.80 and $49.01 stay illustrations of why per-step rounding has to be decided there.

## Options considered

| Topic | Option | Verdict |
| --- | --- | --- |
| Basis default | **`ContractedNet`, the exit-period net (selected)** | Matches ADR-023 D-05 and the price the customer already pays. List bases remain available when a rung sets them. |
| Basis default | `CurrentList` | A new book becomes a silent reprice, and a list drop undercuts the exit rate. Rejected as the default. |
| Basis default | `ListLessContractedDiscount` | Keeps the old discount on the new list ($41.60 in the example) and ignores the contracted net unless a floor saves it. Rejected as the default. |
| Basis default | Term average or year-1 rate | ADR-023 D-05 already rejected both. An 8% uplift on the average can price below the exit rate. |
| Cap shape | **`maxIncreasePct` from the exit net, plus optional `notAboveList` (selected)** | A "capped at 5%" clause has one ceiling, whichever basis produced the candidate. |
| Cap shape | Cap as a percent of the chosen basis | A 5% cap on a list basis of $52 is $54.60, which is a 36.5% increase on the $40 in force. That is not price protection. Rejected. |
| Floor default | **`notBelowExitNet` true when unset (selected)** | Stops a list drop or a 3% rule from publishing a price below the exit rate unless a rung opts out. |
| Floor default | No floor | List and the churn rule can cut the price with no clause saying so. Rejected. |
| Price-as-of default | **`EffectiveDate` (selected)** | The book in force on the new term is the book the new price uses. Scenario 1 then sees $52. |
| Price-as-of default | `ActionDate` | Freezes the lead-time book (scenario 1 action date 2026-08-02, list $50). Allowed as an explicit rung value. Rejected as the default. |
| Precedence | **Sparse overlay, clause then line then agreement then account then rules (selected)** | A protection clause beats a line. A line `Same` still beats an account percent. Unset fields fall through. |
| Precedence | One whole policy from the single highest rung | A clause that only sets a 5% cap would wipe the line's `Same` and the account's mode. Rejected. |
| Precedence | Line above the contract clause | Matches a blanket reading of subscription-over-contract, and lets a line breach a contracted cap. Rejected. The agreement rung remains below the line, which keeps the subscription override for ordinary commercial fields. |
| Missing percent | **`UNRESOLVED_UPLIFT` (selected)** | A renewal with no resolved percent does not price at zero. |
| Missing percent | Caller default 0 | Current behavior. A 7% policy and a forgotten policy look the same. Rejected. |
| Escalator growth | **Flat first period until DRG-533 is accepted; later steps re-check cap on stored absolutes (selected)** | Names the base now, and keeps governance off an undecided compounder. |
| Escalator growth | Adopt `RampEscalatorExpander`'s prior-period compound at scale 4 as the renewal rule | That class is on `main` and is the behavior DRG-533 says was never decided. Rejected. |
| Where the percent is merged | **pricing-service merges separate rungs (selected)** | A caller cannot collapse a clause away before the cap runs. |
| Where the percent is merged | quote-service multiplies, pricing-service echoes | Current continuation path. List bases and caps never run. Rejected. |

## Consequences

- DRG-567 can implement basis, cap, floor, price-as-of, and the five-rung merge against this text after the owner accepts it. The implementation sends rungs unmerged, stops treating a null percent as zero, and returns the four unresolved-or-conflict codes above. It does not expand a multi-year escalator.
- DRG-541 wires `dealType` and churn so the rules rung can run, and it converts the rule's fraction to percent points before the merge (`0.08` becomes `8`, `0.03` becomes `3`). The rule stays the lowest rung. It is not deleted, and it is not the component that applies a basis or a cap. The retention marker stays a marker.
- DRG-549 keeps the mode set `Same`, `List`, `UpliftPct`, `UpliftAmt`, `Override`, `Renegotiate`. Its walk is this order, with the clause above the line and the rules rung below the account. An account method does not outrank a line method.
- DRG-533 remains the owner of compounding, of percent-of-original versus percent-of-prior inside a generator, and of per-step rounding. DRG-568 consumes the first-period price from this decision.
- DRG-564's per-line snapshot gains the D-07 fields. DRG-539's price-book read is a required input for any list basis and for `notAboveList`, and it is not designed here.
- The commercial integrity risk is a wrong rung order: a line that outranks a clause publishes a price the contract forbids. The control is the merge inside pricing-service plus the clause id on the snapshot. This is not an authentication change.
- While this file is Proposed, ADR-023 D-05 stands as accepted, the continuation default of zero stands in code, and the expander's compounding stands in code. None of those are authorized to change from this draft.

## Out of scope

Mode catalog and inheritance defaults beyond the unresolved-mode rule (DRG-549). Multi-year segment schedules and locks (DRG-568). Escalator compounding and per-step rounding (DRG-533) and one-action generation (DRG-532). Price-book retrieval (DRG-539). The audit table (DRG-564). True-up, maintenance percent, conversion, and amendment proration. Any subscriptions user interface. Any flag.
