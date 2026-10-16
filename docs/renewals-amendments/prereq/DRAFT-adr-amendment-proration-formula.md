# Draft ADR: Amendment proration formula

**Status:** Proposed. Not accepted. The owner accepts ADRs. Until this status changes to Accepted, [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula) stays blocked.
**Date:** 2026-10-16
**Deciders:** Project owner (pending).
**Number:** none. Do not assign one in this draft. Do not copy this file into `docs/adr/` and do not add a row to `docs/adr/README.md` until the owner accepts it and assigns a number.
**Story:** [DRG-603](https://linear.app/drgamtd-workspace/issue/DRG-603/adr-draft-proposed-single-amendment-proration-formula). This draft unblocks [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula) only after acceptance. It informs [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550/amendment-price-policy-proration-and-credittermination-fee-policies).
**Does not authorize:** deleting or editing rules, a Flyway migration, a pricing or continuation flag, a merge, or a deploy.

---

## Context

Two amendment proration formulas are in the tree. They do not compute the same charge.

Quote continuation is the path that runs. `QuoteContinuationService.preview` and `validate` call `QuoteContinuationPolicy.plan`. `QuoteService` uses that plan when it prices an amendment or expansion. `AcceptedQuoteSubscriptionService` in contract-asset-management reads the resulting `ProrationSnapshot` on the accepted quote. It does not calculate days or money.

The Drools rule is seeded and does not run on that path. Quote-service never sets `remainingTermDays`, `fullTermDays`, `amendmentDeltaAmount`, or `masterContractCoTermDate`. Those names appear in production Java only inside the rule text and its seed migrations. The only Java that sets them is `ManagedSeedOptionalInputsIntegrationTest`.

Accepted [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-04 already requires day-level proration inside one billing period, at that period's rate, and no day proration when the effective date is a whole-month boundary. The continuation policy matches D-04. The Drools rule does not.

## The two formulas

### Day-stub in `QuoteContinuationPolicy`

File: `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java`, in `plan`, when the operation is an amendment or expansion and the effective date is after the billing-period start.

The billing period is one step of the source line's billing anchor (`line.billingAnchorDate`, or the subscription start when the line has none). `offset` is the last whole anchor-month that starts on or before the effective date. `billingStart = anchor.plusMonths(offset)`. `end = anchor.plusMonths(offset + 1).minusDays(1)`.

```text
monthly = money(netUnitPrice × requestedQuantity)     // scale 2, HALF_UP
stubDays = inclusive days from effective through end
periodDays = inclusive days from billingStart through end
periodAmount = monthly × stubDays / periodDays         // scale 2, HALF_UP
```

`money` is `setScale(2, RoundingMode.HALF_UP)`. Inclusive days are `ChronoUnit.DAYS.between(start, end) + 1`.

The stub is stored as `QuoteAcceptedEvent.ProrationSnapshot` (`periodAmount`, billing-period bounds, quantity, net unit price, net monthly). Each later whole month through the subscription end is charged in full at the accepted rate that covers that month. Those months are `Plan.periods`. Total contract value is the stub amount plus those months. The plan end date is the subscription end date.

When the effective date equals `billingStart`, `initialProration` is null and every remaining month is a full month. A whole-month boundary is not day-prorated.

The requested quantity is the replacement total at the accepted unit rate. An amendment to 70 seats on the fixture below is priced as 70, not as a delta of 20 from the source quantity of 50.

Worked figures from `QuoteContinuationPolicyTest` (subscription 2027-01-01 through 2029-12-31, rates $40 / $45 / $50, source quantity 50):

| Effective date | Quantity | Stub | Following full months | TCV |
|---|---:|---:|---|---:|
| 2028-07-01 | 20 | none (boundary) | 6 × $45 + 12 × $50 | 17,400.00 |
| 2028-07-15 | 20 | 900.00 × 17/31 = 493.55 | Aug–Dec 2028 at 900, 2029 at 1,000 | 16,993.55 |
| 2028-07-01 | 70 | none | 6 × 3,150 + 12 × 3,500 | 60,900.00 |
| 2029-12-15 | 20 | 1,000.00 × 17/31 = 548.39 | none (nominal term 1, no zero-month step) | 548.39 |

Leap clip from the same test: anchor 2028-01-31, effective 2028-03-15, billing period 2028-02-29 through 2028-03-30, unit price $40, quantity 20. `800.00 × 16/31 = 412.90`.

### Full-term ratio in `amendment-proration.drl`

File: `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl`, rule `Amendment Proration`, salience 540. The same arithmetic is stored as `pricing.rules` uuid `rule-007` by `V2__seed_default_rules.sql`. `V8__guard_managed_seed_optional_inputs.sql` adds a guard so the database copy fires only when `dealType` is `AMENDMENT`, the three inputs are present, and `fullTermDays > 0`. The classpath file does not have that guard. Execute loads `ACTIVE` rules from the database (`PricingExecutionService`), not the classpath file.

```text
prorated = amendmentDeltaAmount × remainingTermDays / fullTermDays
           // scale 4, HALF_UP
```

The rule writes `prorationAdjustment` and copies `masterContractCoTermDate` onto `coTermDate`. It does not read quantity, unit price, a billing anchor, or later rate periods. It does not add the unprorated months after a stub.

`ManagedSeedOptionalInputsIntegrationTest` fires the seeded rule with delta 1,200, remaining 90, full term 360 and expects `prorationAdjustment` 300.0000 (`1200 × 90 / 360`). With `fullTermDays` 0, or with the inputs absent, the guarded rule leaves `prorationAdjustment` null.

The same July 2028 continuation, if the rule were given day counts of the whole subscription (1,096 inclusive days from 2027-01-01 through 2029-12-31; 535 inclusive days from 2028-07-15 through 2029-12-31), does not reproduce 16,993.55:

| Delta the caller would have to invent | Rule output (scale 4) | Policy TCV |
|---|---:|---:|
| One month at $45 × 20 = 900 | 900 × 535/1096 = 439.3248 | 16,993.55 |
| Boundary TCV of the 20-seat add = 17,400 | 17,400 × 535/1096 = 8,493.6131 | 16,993.55 |
| One month, but with the policy's own 17/31 fraction | 900 × 17/31 = 493.5484 | stub 493.55, then the rest of the term |

Even a matching day fraction disagrees by scale (4 versus 2). On 2028-07-01 the policy charges 17,400 with no stub. The rule still multiplies by `remainingTermDays / fullTermDays` whenever its inputs are present, including on a billing boundary.

`cpq-pricing-policy-sanity-check.md` scenario 2 shows the other coincidence: `16 × 500 × 92/365 = 2,016.44` equals this rule only when the delta is an annual amount and the full term is 365 days. A monthly billing period is a different denominator.

## Decision (Proposed — not Accepted)

### D-01 — One formula

The amendment and expansion proration formula is the day-stub above.

Charge the requested quantity at the accepted net unit price of the period that covers the stub. Prorate only the partial billing period, by inclusive days in that period, at scale 2 half-up. Charge each later whole month in full at the accepted rate for that month, through the subscription end. On a billing-period boundary, do not prorate by days.

### D-02 — One place

The only implementation is `QuoteContinuationPolicy.plan` in quote-service: the partial-period branch that builds `ProrationSnapshot`, and the following full-month loop.

Callers stay as they are:

- `QuoteContinuationService.preview` and `validate`
- `QuoteContinuationController` (preview)
- `QuoteService.validateContinuation`, which asks pricing-service to re-check the already accepted unit rate for that stub (`priceInitialInterval`) and then adds `periodAmount` to TCV

That pricing call sends a one-month absolute schedule at the accepted unit price. It is a rate check. It is not a second proration.

contract-asset-management keeps consuming the snapshot. It does not grow a copy of the arithmetic.

pricing-service does not compute `prorationAdjustment` for amendments.

### D-03 — What acceptance unblocks

Acceptance authorizes [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula) to remove the full-term ratio, using the delete list below. Acceptance does not itself change code.

### D-04 — What this tells DRG-550

[DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550/amendment-price-policy-proration-and-credittermination-fee-policies) can add an amendment price policy (`RetainContracted`, `List`, `Override`), a credit policy, and a termination fee. The current continuation already retains the contracted unit rate, which is the `RetainContracted` behaviour.

The day arithmetic those policies call is this stub, in this class. If a later story moves evaluation into pricing-service, it moves this method. It does not reimplement it beside `rule-007`.

`None` and `NextCycle`, if DRG-550 adds them, mean the stub is skipped. They are switches around this method. The full-term ratio is not a `Daily` option.

A credit for unused days of the old quantity, and a termination fee, are DRG-550. This formula prices the requested quantity forward. It does not emit that credit. DRG-550 does not implement the credit by turning `rule-007` back on.

## Rejected

| Option | Why it is rejected |
|---|---|
| Keep `delta × remainingTermDays / fullTermDays` as the formula | It ignores the billing period, later rate steps, and scale-2 money. It prorates on a whole-month boundary. ADR-023 D-04 forbids that. The July 2028 figures above do not match. |
| Wire the three Drools inputs from quote-service and keep both formulas | Two live formulas. DRG-542 exists because that split prices the same amendment two ways. |
| Implement the day-stub a second time in pricing-service or in a new shared helper now | The running method is already `QuoteContinuationPolicy.plan`. A second body is another split. |
| Delete `QuoteContinuationPolicy`'s stub and leave `rule-007` | Removes the path quote calculate and the accepted-quote consumer actually use. |
| Rewrite `V2__seed_default_rules.sql`, `V7__add_managed_seed_rule_imports.sql`, or `V8__guard_managed_seed_optional_inputs.sql` | Those migrations have already been applied. History stays. Retirement is a new migration. |

## Code to delete

This list is the DRG-542 change, after the owner sets this draft to Accepted. This draft does not delete it.

**Delete the unused rule body**

- `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl` — the whole file.
- In `backend/libs/rules-engine/src/test/java/com/opencrm/rules/drools/PricingDrlCorpusCompileTest.java`, the `PHASE0_DRLS` entry `"amendment-proration.drl"`, and the `hasSize(8)` expectation in `phase0EightDrlsArePresent` that counts this file among the eight phase-0 rules. The rest of the corpus compile test stays.
- In `backend/services/pricing-service/src/test/java/com/opencrm/pricing/rule/ManagedSeedOptionalInputsIntegrationTest.java`, the amendment setup and assertions that exist only for this rule: `remainingTermDays`, `fullTermDays`, `amendmentDeltaAmount`, and `prorationAdjustment` (the null checks, the expected `300`, and the malformed `fullTermDays` throw). The same tests still cover overage, cores, maintenance, volume, migration credit, margin, churn, renewal uplift, TCV, and ACV. Those assertions stay.

**Retire the database row without rewriting history**

- Add a new Flyway migration. Set `pricing.rules` uuid `rule-007` to `DISABLED` only when `created_by = 'system'` and the normalized body is still the managed seed (the same owner-edit guard V7 and V8 use). Execute loads `ACTIVE` rules only, so `DISABLED` removes it from pricing. An owner-edited `rule-007` is left unchanged.
- Do not delete or edit `V2__seed_default_rules.sql`, `V7__add_managed_seed_rule_imports.sql`, or `V8__guard_managed_seed_optional_inputs.sql`.

**Leave in place**

- `QuoteContinuationPolicy`, `QuoteContinuationPolicyTest`, `QuoteContinuationService`, `QuoteContinuationController`, and the continuation methods on `QuoteService`.
- `QuoteAcceptedEvent.ProrationSnapshot` and `AcceptedQuoteSubscriptionService`'s read of that snapshot.
- `renewal-uplift.drl` and `rule-008`. Renewal uplift is a different rule. ADR-023 D-05 still owns the exit-rate base.

Prose that still describes the rejected formula (`agents/term-coterming.md`, `agents/cpq-agent-020.md`, `cpq-pricing-prd.md`, `docs/phase0/phase0-rule-catalog-reconciliation.md`) is not this delete list. Those files are not on the calculate path.

## Consequences

- One charge for a mid-term quantity change: the stub plus full months at accepted rates, co-termed to the subscription end.
- `prorationAdjustment` stops being a pricing output once `rule-007` is disabled. Nothing in quote-service reads that key today.
- DRG-550 builds price mode, credit, and termination fee around this method.
- ADR-023 is unchanged. D-04 is the rule this draft follows. ADR-023's context still names `amendment-proration.drl` as an adjacent seed. That citation becomes historical when DRG-542 deletes the file. Editing ADR-023 is a separate owner decision.

## Explicit non-goals

- No code, migration, flag, merge, or deploy in the pull request that adds this draft.
- No ADR number, and no edit to `docs/adr/README.md`.
- No change to renewal uplift, ramp schedules, credit policy, or termination fees.
- No implementation of `List` or `Override` amendment pricing.
- No customer-cancellation refunds ([DRG-589](https://linear.app/drgamtd-workspace/issue/DRG-589/parked-mid-term-customer-cancellation-proration-and-refunds-out-of) stays parked).

## References

- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationPolicy.java` — day-stub and full-month loop.
- `backend/services/quote-service/src/test/java/com/opencrm/quote/quote/QuoteContinuationPolicyTest.java` — boundary, mid-month, final partial, leap clip, replacement quantity.
- `backend/services/pricing-service/src/main/resources/rules/amendment-proration.drl` — full-term ratio.
- `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql` — `rule-007`.
- `backend/services/pricing-service/src/main/resources/db/migration/V8__guard_managed_seed_optional_inputs.sql` — optional-input guard on `rule-007`.
- `backend/services/pricing-service/src/test/java/com/opencrm/pricing/rule/ManagedSeedOptionalInputsIntegrationTest.java` — `1200 × 90 / 360 = 300`.
- [ADR-023](../../adr/ADR-023-price-ramp-schedule-model.md) D-04 — in-period day proration; no day proration on a whole-month boundary.
- [DRG-542](https://linear.app/drgamtd-workspace/issue/DRG-542/amendment-proration-rule-conflicts-with-continuation-formula), [DRG-550](https://linear.app/drgamtd-workspace/issue/DRG-550/amendment-price-policy-proration-and-credittermination-fee-policies), [DRG-603](https://linear.app/drgamtd-workspace/issue/DRG-603/adr-draft-proposed-single-amendment-proration-formula).
