# Ramp amendments: first-slice spec proposal

**Status:** Proposal only. This file does not accept escalator semantics, amend ADR-023, or authorize implementation, a flag change, or a deploy. `opencrm.pricing.ramp.enabled` stays default off. Turning it on is outside this proposal and outside ADR-023's acceptance.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `ramp-amendments`. **Date:** 2026-10-16.

**Sources.** [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) (amend a ramped contract: lock elapsed periods, edit future periods, re-govern). [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) (open ADR: escalator compounding, base price, and rounding, amending ADR-023). Accepted constraints: `../../../adr/ADR-023-price-ramp-schedule-model.md` (D-01, D-04, D-05, D-06, D-08). Related landed-scope stories that this slice does not reopen: DRG-336, DRG-338, DRG-339.

**Conventions.** **[S]** is DRG-534, DRG-533, or an accepted ADR-023 decision. **[I]** is a design inference. Escalator compounding, base, and rounding are **Owner decisions** and stay open. This file does not choose them.

## Scope

### In this slice

1. An amendment of a subscription that already has explicit ramp periods. Elapsed periods are immutable. Future periods are editable. Removing a period is allowed only at the schedule end, so the remaining periods have no gap. **[S DRG-534]**
2. Any accepted change produces a new schedule fingerprint, supersedes the prior approval, and re-runs per-period governance. The worst period decides. **[S DRG-534; ADR-023 rules-port note]**
3. The amended schedule is emitted on the existing event contract as additive fields only. `QuoteAccepted` stays version 1. **[S DRG-534; ADR-023]**
4. Two compatibility tests: an old consumer accepts the amended event while ignoring additive fields, and a new consumer accepts a pre-change event lacking those fields. A separate replay test applies the amended schedule once. **[S DRG-534 acceptance; ADR-018]**
5. A boundary with DRG-533: this slice amends **stored explicit periods**. It does not define how an annual escalator compounds, which base it uses, or how it rounds. Regenerating periods from an escalator waits on that ADR.

### Out of this slice

Choosing escalator semantics (DRG-533). That decision is required before a generator or a governance rule depends on a percent escalator. Renewal uplift on the exit rate (ADR-023 D-05, DRG-568) and the renewal policy's own escalators. Cost-basis governance (DRG-526); until real per-period cost exists, governance stays the per-period discount and the step-down rule (ADR-023 D-06). Enabling the ramp flag. Usage tiers and credit draw-down (ADR-023 D-09). A subscriptions user interface (held).

### Proposed contract

ADR-023 D-01 already stores ordered explicit `RampStep`s (sequence, start month, length in whole months, quantity, and one price basis: absolute unit price or a discount fraction off list). This slice adds amendment rules on that schedule. It does not add a second schedule model.

```text
RampAmendment
  subscriptionId
  observedScheduleFingerprint
  asOfDate
  periodEdits[]
    sequence
    op                    { Replace | DeleteEnd }
    quantity              Replace only
    priceBasis            Replace only
  resultingSteps[]
  resultingFingerprint
  governance
    perPeriodOutcome[]
    worstPeriodSequence
    approvalSuperseded    true when the fingerprint changes
```

**Proposed rules [S DRG-534 plus accepted ADR-023 dates]:**

1. A period whose end date is on or before `asOfDate` is elapsed. Replace and delete of an elapsed period are rejected.
2. A period whose start date is after `asOfDate` is future and may be replaced.
3. A period that contains `asOfDate` is current. Its start, its already-elapsed days, and its price for those days stay. An edit that changes the current period's remaining days is an owner decision (open question 2). The default proposal is: the current period's commercial terms stay immutable, and the next period is the first editable one.
4. `DeleteEnd` applies only to the last period. Deleting a middle period is rejected. After a delete, sequences are contiguous and the last period still ends on the term end (ADR-023 D-04: boundaries from term start, no gaps).
5. The new fingerprint is a hash of the normalised schedule, term, and totals (ADR-023 D-01). A changed fingerprint supersedes the prior approval binding.
6. Governance runs once per resulting period. The worst period's outcome is the schedule outcome (the rule already stated for ramp governance).
7. The outbound event adds the amended ramp array and `exitArr` on v1. It does not register a v2. It does not bump `eventVersion`.

Day-level proration exists only inside a period, at that period's rate (ADR-023 D-04). An amendment on a whole-month boundary is not day-prorated.

## Acceptance criteria

DRG-534's acceptance is: an amendment in year 2 cannot change year 1; a year-3 change forces re-approval; an event-replay test covers it. The fixtures below use ADR-023's AC-01 shape: 36 months, three 12-month periods, unit prices $40, $45, $50, term start 2027-01-01. Period 1 is calendar 2027, period 2 is 2028, period 3 is 2029. `asOfDate` for a "year 2 amendment" is 2028-06-15.

1. **Elapsed period locked.** Replace of period 1's price or quantity, as of 2028-06-15, is rejected and names period 1. The stored period 1 price remains $40.
2. **Future period editable.** Replace of period 3's unit price from $50 to $55, as of 2028-06-15, is accepted. Periods 1 and 2 remain $40 and $45. `exitArr` is recomputed from period 3.
3. **Year-3 change re-approves.** The AC-2 edit changes the fingerprint. The prior approval is superseded. Per-period governance runs on the resulting three periods, and the schedule's decision is the worst period. A fingerprint match on a replay does not create a second approval.
4. **Delete only at the end.** `DeleteEnd` on period 3 shortens the schedule to two periods and sets the term end to the end of period 2. `DeleteEnd` on period 2 while period 3 exists is rejected. The result has no gap and no overlapping months.
5. **Current period.** As of 2028-06-15, period 2 contains the date. Under the proposed immutable-current rule, a replace of period 2 is rejected. This criterion is in force only if the owner accepts open question 2. Until then the test is marked pending.
6. **Whole-month boundary.** An amendment as of 2029-01-01 replaces period 3 with no day proration. An amendment as of 2028-06-15 does not invent a stub period inside period 2 unless the owner allows editing the current period.
7. **Event v1 additive.** The emitted payload includes the amended steps and `exitArr` and keeps `eventVersion` 1. The old consumer accepts the new payload with unknown fields, and the new consumer deserializes a pre-change payload lacking those fields. A replay of the amended payload produces one schedule write (idempotent on subscription, line, and sequence).
8. **Escalator inputs are not re-derived.** A schedule that was generated from an escalator is amended as explicit steps. The amendment does not re-expand the escalator, does not compound a percent, and does not change ADR-023's stored price basis. The original escalator inputs remain display-only, as D-01 already says.
9. **Flag off.** With `opencrm.pricing.ramp.enabled=false`, an amendment request that would price a schedule is rejected with the existing ramp-not-supported outcome. This slice does not turn the flag on and does not price the schedule as a flat line.
10. **Renewal base unchanged.** This slice does not change D-05. A later renewal still reads the exit period of the amended schedule. A fixture that amends period 3 to $55 then renews with an 8% uplift uses $55 as the exit rate only when the renewal policy asks for it. The uplift percent is not decided here.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| ADR-023 D-01, D-04, D-08 | Explicit steps, anniversary boundaries, and the runtime schedule on contract-asset-management |
| DRG-336, DRG-338, DRG-339 | New-quote ramps, per-period governance, and schedule application. DRG-534 says those tickets stay as they are |
| DRG-533 | Blocks any work that regenerates periods from an escalator percent. Does not block AC-1 through AC-4 on explicit steps |
| DRG-526 | Real per-period cost. Until it exists, AC-3 uses discount and step-down governance, and margin 40.0 stays non-authoritative (ADR-023 D-06) |
| `lifecycle-intents.md` | The amendment intent and the action date that supplies `asOfDate` |
| Approval fingerprint (ADR-013, DRG-338) | Supersede-on-change is a new binding, not a silent edit of the old one |
| ADR-018 | Additive v1 events and idempotent consumers for AC-7 |

## Open questions

1. **Owner decision — escalator semantics (DRG-533).** Compounding versus a stated cumulative percent, base (list, prior period, or exit), rounding stage, currency precision, and interaction with per-period discount and DRG-526 cost. ADR-023 D-01 says an escalator expands into explicit periods and is display-only after that. It does not say how the expansion is calculated. This proposal does not choose. AC-8 is the boundary: amendments of stored steps proceed; escalator regeneration does not.
2. **Owner decision — the period that contains the amendment date.** DRG-534 says elapsed periods are immutable and future periods are editable. It does not say whether the in-progress period is elapsed, future, or split. The proposal treats it as immutable (AC-5). Splitting it into an elapsed stub and an editable remainder is the other candidate and would add a period boundary ADR-023 D-04 does not currently create mid-period.
3. **Owner decision — quantity edits on a future period.** DRG-534 allows future segments to change. Whether quantity, price, or both may change, and whether a quantity change re-runs the volume tier on that period's own quantity (ADR-023 D-07), should be confirmed. The proposal allows both on a Replace of a future period and uses that period's own quantity for any tier.
4. **Owner decision — shortening the term.** `DeleteEnd` shortens the term to the new last period's end. Whether that is allowed without a renewal, and whether it creates a credit, is unset. AC-4 covers the schedule shape only.
5. **Owner decision — adding a period.** DRG-534 allows delete at the end and edits to future periods. It does not say a seller may append a period. Appending would extend the term. The proposal rejects an append until the owner allows term extension on an amendment.
6. **Owner decision — governance "worst period".** The phrase is in DRG-534 and in ADR-023's rules note. The comparison key (deepest discount, lowest price, failed rule) is the ramp-governance story (DRG-338). This slice calls that outcome and does not redefine it.
7. **Owner decision — approval when the fingerprint changes but every period is still inside policy.** DRG-534 says a year-3 change forces re-approval. Whether "re-approval" means a new human approval even when the worst period is inside policy, or a new binding that can auto-approve, is unset. AC-3 requires a new binding and a superseded prior approval. It does not require a human approver in every case.
8. **Owner decision — flag.** No part of this slice authorizes setting `opencrm.pricing.ramp.enabled` to true, or setting `opencrm.pricing.metrics.acv-version` away from `legacy`. Those remain ADR-023 D-03 and D-06.
