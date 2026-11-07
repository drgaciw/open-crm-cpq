# Ramp amendments: first-slice spec proposal

**Status:** Proposal only. This file does not accept escalator semantics, amend ADR-023, or authorize implementation, a flag change, or a deploy. `opencrm.pricing.ramp.enabled` stays default off. Turning it on is outside this proposal and outside ADR-023's acceptance.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `ramp-amendments`. **Date:** 2026-10-16. **Aligned:** 2026-10-27 (DRG-639).
**Decision brief (Proposed):** [`../story-drg-534-ramp-amendments-decision-brief.md`](../story-drg-534-ramp-amendments-decision-brief.md) lists owner forks OQ2–OQ7 and blank Defaults slots for CPQ Decision scoring. That brief does not adopt any fork and does not authorize an implementation brief or product code.

**Sources.** [DRG-534](https://linear.app/drgamtd-workspace/issue/DRG-534) (amend a ramped contract: lock elapsed periods, edit future periods, re-govern). [DRG-533](https://linear.app/drgamtd-workspace/issue/DRG-533) ADR as on main `bf41c230f6309964a25ef5c2c12e84c41b0c37de`: [`DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md`](../DRAFT-adr-renewal-segment-schedule-escalators-price-locks.md), blob `4922b07b4ce374b8ee9f88ecec593c5699b82fea`. The sections this spec depends on are named under "DRG-533 ADR sections". Accepted constraints: `../../../adr/ADR-023-price-ramp-schedule-model.md` (D-01, D-04, D-05, D-06, D-08). Related landed-scope stories that this slice does not reopen: DRG-336, DRG-338, DRG-339.

**Conventions.** **[S]** is DRG-534, a cited DRG-533 ADR section, or an accepted ADR-023 decision. **[I]** is a design inference. Escalator compounding, base, and rounding stay **Owner decisions**. This file does not choose them. Every default in this file is `Status: Proposed`. The slot `Owner decision: ___ (drg, time)` stays blank.

## Locked elapsed period (expected values first)

DRG-639 AC2. A mid-term change leaves elapsed `subscription_rate_periods` rows byte-identical. The fixture is [`fixtures/ramp-amendments/locked-elapsed-period.json`](fixtures/ramp-amendments/locked-elapsed-period.json) (`"status": "provisional"`). `expectedDiff` is `[]`.

Schedule shape is ADR-023 AC-01: 50 seats, 36 months, term start 2027-01-01, three 12-month periods, unit prices 40.0000, 45.0000, 50.0000. `asOfDate` is 2028-06-15. The change replaces future period 3 from 50.0000 to 55.0000. Period 1 ends 2027-12-31, which is on or before `asOfDate`, so it is the elapsed row. Period 2 contains `asOfDate` and is not in this fixture.

Byte-identity covers every column of that elapsed row except the audit timestamps `created_at` and `updated_at`. `created_by` and `updated_by` are compared. `version` and `is_deleted` are compared. Columns added by `V1_7__contract_documents.sql` (`amendment_document_line_id`, `revision_no`) are compared.

| Column | Before | After |
|---|---|---|
| id | 9001 | 9001 |
| uuid | 00000000-0000-4000-8000-000000000001 | 00000000-0000-4000-8000-000000000001 |
| subscription_id | 501 | 501 |
| accepted_line_uuid | 00000000-0000-4000-8000-0000000000a1 | 00000000-0000-4000-8000-0000000000a1 |
| source_line_uuid | null | null |
| accepted_quote_id | fixture-quote-ramp-ac01 | fixture-quote-ramp-ac01 |
| product_external_id | fixture-ramp-seat | fixture-ramp-seat |
| product_name | Ramp seat | Ramp seat |
| product_type | null | null |
| line_item_type | null | null |
| billing_frequency | null | null |
| sequence | 1 | 1 |
| start_month | 1 | 1 |
| months | 12 | 12 |
| start_date | 2027-01-01 | 2027-01-01 |
| billing_anchor_date | 2027-01-01 | 2027-01-01 |
| end_date | 2027-12-31 | 2027-12-31 |
| effective_end_date | 2027-12-31 | 2027-12-31 |
| quantity | 50.0000 | 50.0000 |
| net_unit_price | 40.0000 | 40.0000 |
| net_monthly | 2000.00 | 2000.00 |
| annual_run_rate | 24000.00 | 24000.00 |
| period_amount | 24000.00 | 24000.00 |
| gross_period_amount | null | null |
| migration_credit | null | null |
| discount_rate | null | null |
| explanation_codes | null | null |
| currency_code | USD | USD |
| provenance | ACCEPTED_PRICED | ACCEPTED_PRICED |
| applied | true | true |
| retired | false | false |
| billing_period_start | null | null |
| billing_period_end | null | null |
| created_by | quote-accept | quote-accept |
| updated_by | null | null |
| version | 0 | 0 |
| is_deleted | false | false |
| amendment_document_line_id | null | null |
| revision_no | null | null |

Excluded from the diff, and absent from both rows: `created_at`, `updated_at`.

**Expected diff:** empty.

Priced output is a secondary assertion. It is not the byte-identity claim. Period 3 `net_unit_price` 50.0000 becomes 55.0000, `net_monthly` 2500.00 becomes 2750.00, `period_amount` 30000.00 becomes 33000.00, and `exitArr` 30000.00 becomes 33000.00 (`55.0000 × 50 × 12`). `Status: Proposed`. `Owner decision: ___ (drg, time)`.

Money figures are the ADR-023 AC-01 worked example (monthly 2,000 / 2,250 / 2,500; period totals 24,000 / 27,000 / 30,000; `exitArr` 30,000) with the period-3 replace from acceptance criterion 2 below. Quantities and unit prices are stored at scale 4 because `net_unit_price` is `DECIMAL(19,4)` and amounts at scale 2 because those columns are `DECIMAL(19,2)`. That scale is the column. It does not adopt the DRG-533 rounding fork.

## DRG-533 ADR sections

Cited file, on main `bf41c230f6309964a25ef5c2c12e84c41b0c37de`, blob `4922b07b4ce374b8ee9f88ecec593c5699b82fea`. This list is what DRG-639 AC1 requires. Citing a section does not adopt its recommendation.

| Section | What this spec uses it for |
|---|---|
| Edge-case tests, row "Elapsed-period lock" (DRG-534) | Elapsed periods stay immutable. An amendment regenerates only future periods, from the last elapsed stored absolute. The fixture above locks the elapsed row. This spec does not regenerate. |
| Consequences, DRG-534 sentence | DRG-534 amends stored absolute periods. Regenerating them from the percent waits. The amendment story owns elapsed-period locking. The ADR does not accept this file. |
| D-01 — Where a renewal escalator starts | Later periods start from the prior stored absolute unit. The elapsed-lock row names the last elapsed stored absolute as the base of any future regeneration. This slice still amends stored steps. |
| D-02 — Compounding | The ADR's default is compound-on-prior. A stored-step amendment does not apply it. |
| D-03 — Rounding | The ADR's default stores units at scale 4 and amounts at scale 2. This slice does not expand an escalator, so it does not apply that procedure. |
| D-04 — Renewal segment schedule | Equal-block expansion and explicit lock segments are renewal generator rules. An amendment of stored periods does not call that expander. Boundaries stay ADR-023 D-04. |
| D-05 — Discount and cost | Cost does not enter the escalator formula. This slice does not read cost and does not run a second named-seat subtraction while regenerating, because it does not regenerate. |
| Edge-case tests, row "Proration on an amendment or expansion" (DRG-534) | The day-stub stays the amendment path. A renewal escalator does not call that formula. Whole-month boundaries stay acceptance criterion 6. |
| DealType handling | `AMENDMENT` is reached only by typed continuation. This spec does not map CRM `EXISTING_BUSINESS` to an amendment. |
| Out of scope | Elapsed-period locking on an amendment stays DRG-534, which is this file. |
| Owner decision needed | The seven forks are recommendations with defaults. The ADR says they are not in force and that the file does not record the choice. This spec does not record it either. |

## Pending #684 (owner-gated)

[#684](https://github.com/drgaciw/open-crm-cpq/pull/684) is merged (`30a7f47eef4bfced0d4aaf881b11b1bf50fa2f92`, head `6d010b912a0ab2a85de8991d83bcd6638c52c213`). That commit is an ancestor of main `bf41c230f6309964a25ef5c2c12e84c41b0c37de`. The ADR blob on main is the #684 blob `4922b07b4ce374b8ee9f88ecec593c5699b82fea`. The parent blob is `6323621d7bafbb048904c8cce413e445b6840e3f`.

#684 differs from that parent only in status wording. D-01 through D-06, the edge-case list, DealType, the rounding arithmetic, and the sentence that the recommendations are not in force are unchanged. The wording delta is:

- "This status takes effect on that merge" and "this file stays Proposed" become "This file is Accepted" with the 7:31 AM CT 2026-10-17 relay.
- "Unblocks … only after the owner accepts this file and the status-change PR merges" becomes "only after the status-change PR merges", plus the Accepted citation.
- "Later renewal periods stay unpriced while this file is Proposed" drops the "while Proposed" clause and adds the Accepted citation.
- "Owner adoption record" no longer says the file stays Proposed until the status-change PR merges.
- The Decision heading changes from "Proposed" to "Accepted", citing the same relay.
- The same Accepted citation is repeated at D-06, the lock-vs-marker option, Consequences, Risks, "What DRG-568 needs", and Owner decision needed.

This spec cites the file as it is on main. It does not adopt the #684 wording delta. It does not treat "Accepted" as a choice of any fork, and it does not treat that wording as acceptance of this file. No `Owner decision` slot is filled from #684.

## Defaults

| Default | Status | Owner decision |
|---|---|---|
| A period whose `end_date` is on or before `asOfDate` is elapsed, and that stored row is not updated | Status: Proposed | Owner decision: ___ (drg, time) |
| The period that contains `asOfDate` stays immutable; the next period is the first editable one | Status: Proposed | Owner decision: ___ (drg, time) |
| Future regeneration, when a later story adds it, starts from the last elapsed stored absolute and does not run in this slice | Status: Proposed | Owner decision: ___ (drg, time) |
| `compound = true` (ADR fork 1) | Status: Proposed | Owner decision: ___ (drg, time) |
| Unit scale 4 and amount scale 2 for an escalator expansion (ADR fork 2) | Status: Proposed | Owner decision: ___ (drg, time) |
| One uplift of the locked unit after a lock (ADR fork 3) | Status: Proposed | Owner decision: ___ (drg, time) |
| Explicit segments when lengths differ (ADR fork 4) | Status: Proposed | Owner decision: ___ (drg, time) |
| No second named-seat tier on a renewal net (ADR fork 5) | Status: Proposed | Owner decision: ___ (drg, time) |
| Month-boundary locks only (ADR fork 6) | Status: Proposed | Owner decision: ___ (drg, time) |
| `ENABLE_12M_PRICE_PROTECTION` stays a marker (ADR fork 7) | Status: Proposed | Owner decision: ___ (drg, time) |
| `exitArr` after the period-3 replace is 33000.00 | Status: Proposed | Owner decision: ___ (drg, time) |

## Scope

### In this slice

1. An amendment of a subscription that already has explicit ramp periods. Elapsed periods are immutable. Future periods are editable. Removing a period is allowed only at the schedule end, so the remaining periods have no gap. **[S DRG-534]**
2. Any accepted change produces a new schedule fingerprint, supersedes the prior approval, and re-runs per-period governance. The worst period decides. **[S DRG-534; ADR-023 rules-port note]**
3. The amended schedule is emitted on the existing event contract as additive fields only. `QuoteAccepted` stays version 1. **[S DRG-534; ADR-023]**
4. Two compatibility tests: an old consumer accepts the amended event while ignoring additive fields, and a new consumer accepts a pre-change event lacking those fields. A separate replay test applies the amended schedule once. **[S DRG-534 acceptance; ADR-018]**
5. A boundary with the DRG-533 ADR (Consequences, and the elapsed-period lock row): this slice amends **stored explicit periods**. It does not regenerate future periods from a percent. The ADR's compound, base, and rounding defaults stay in the table above as `Status: Proposed`. `Owner decision: ___ (drg, time)`.

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

1. **Elapsed period locked.** Replace of period 1's price or quantity, as of 2028-06-15, is rejected and names period 1. The stored period 1 row stays byte-identical on every compared column in the fixture above. The price remains 40.0000. `expectedDiff` is empty.
2. **Future period editable.** Replace of period 3's unit price from 50.0000 to 55.0000, as of 2028-06-15, is accepted. Periods 1 and 2 remain 40.0000 and 45.0000. `exitArr` is recomputed from period 3 (30000.00 to 33000.00). That priced-output comparison is secondary to the empty elapsed-row diff. `Status: Proposed`. `Owner decision: ___ (drg, time)`.
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
| DRG-533 ADR, sections in the table above, on main `bf41c230f6309964a25ef5c2c12e84c41b0c37de` | Elapsed rows stay immutable (edge-case row). Regenerating future periods from a percent waits (Consequences). Does not block the explicit-step checks in acceptance criteria 1 through 4. Forks stay `Status: Proposed`. `Owner decision: ___ (drg, time)` |
| DRG-526 | Real per-period cost. Until it exists, AC-3 uses discount and step-down governance, and margin 40.0 stays non-authoritative (ADR-023 D-06) |
| `lifecycle-intents.md` | The amendment intent and the action date that supplies `asOfDate` |
| Approval fingerprint (ADR-013, DRG-338) | Supersede-on-change is a new binding, not a silent edit of the old one |
| ADR-018 | Additive v1 events and idempotent consumers for AC-7 |

## Open questions

1. **Owner decision — escalator semantics (DRG-533 ADR D-01, D-02, D-03, and Owner decision needed).** The ADR on main `bf41c230f6309964a25ef5c2c12e84c41b0c37de` records compound-on-prior, a period-1 unit taken from the uplift record, and scale-4 units as recommendations. It also says those recommendations are not in force. This proposal does not choose them. `Status: Proposed`. `Owner decision: ___ (drg, time)`. AC-8 is the boundary: amendments of stored steps proceed; escalator regeneration does not. The #684 status wording is listed under "Pending #684 (owner-gated)" and is not adopted.
2. **Owner decision — the period that contains the amendment date.** DRG-534 says elapsed periods are immutable and future periods are editable. It does not say whether the in-progress period is elapsed, future, or split. The proposal treats it as immutable (AC-5). Splitting it into an elapsed stub and an editable remainder is the other candidate and would add a period boundary ADR-023 D-04 does not currently create mid-period.
3. **Owner decision — quantity edits on a future period.** DRG-534 allows future segments to change. Whether quantity, price, or both may change, and whether a quantity change re-runs the volume tier on that period's own quantity (ADR-023 D-07), should be confirmed. The proposal allows both on a Replace of a future period and uses that period's own quantity for any tier.
4. **Owner decision — shortening the term.** `DeleteEnd` shortens the term to the new last period's end. Whether that is allowed without a renewal, and whether it creates a credit, is unset. AC-4 covers the schedule shape only.
5. **Owner decision — adding a period.** DRG-534 allows delete at the end and edits to future periods. It does not say a seller may append a period. Appending would extend the term. The proposal rejects an append until the owner allows term extension on an amendment.
6. **Owner decision — governance "worst period".** The phrase is in DRG-534 and in ADR-023's rules note. The comparison key (deepest discount, lowest price, failed rule) is the ramp-governance story (DRG-338). This slice calls that outcome and does not redefine it.
7. **Owner decision — approval when the fingerprint changes but every period is still inside policy.** DRG-534 says a year-3 change forces re-approval. Whether "re-approval" means a new human approval even when the worst period is inside policy, or a new binding that can auto-approve, is unset. AC-3 requires a new binding and a superseded prior approval. It does not require a human approver in every case.
8. **Owner decision — flag.** No part of this slice authorizes setting `opencrm.pricing.ramp.enabled` to true, or setting `opencrm.pricing.metrics.acv-version` away from `legacy`. Those remain ADR-023 D-03 and D-06.
