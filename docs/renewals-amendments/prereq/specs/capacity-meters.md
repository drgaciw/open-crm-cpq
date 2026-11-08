# Capacity meters and true-up: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `capacity-meters`. **Date:** 2026-10-16.
**Decision brief (Proposed, DRG-570):** [`../story-drg-570-meter-provenance-decision-brief.md`](../story-drg-570-meter-provenance-decision-brief.md) lists owner forks OQ1–OQ8 (`MeterReading` source/attestation/status, `snapshotId`+hash freeze, correction→revision, dispute workflow) for CPQ Decision scoring. That brief cites Adopted Cluster C G cells without reopening them, cites draft PR #818 (DRG-569) without adopting its open OQs, and does not authorize an implementation brief or product code.

**Sources.** [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) (RA-23, meter model and TrueUp intent), [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) (RA-35, true-up pricing semantics), [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) (RA-36, provenance, dispute, frozen snapshot), [DRG-641](https://linear.app/drgamtd-workspace/issue/DRG-641) (Proposed parameter defaults and worked examples for the DRG-569 knobs). Worked numbers are the illustrative Scenario 2 in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data). The amendment spike's `delta_qty = max(0, peak − entitled)` is a sketch that picks Peak with no tolerance or backdating; this proposal does not adopt that sketch as the policy.

**Conventions.** **[S]** is taken from those issues or the files they cite. **[I]** is a design inference carried forward as a proposal. Every default below is an **Owner decision**.

## Scope

### In this slice

1. A capacity metric on the commitment line (core, PVU, TB, session, and an extensible code) and a `MeterReading` attached to that line. **[S DRG-557]**
2. A `TrueUp` intent whose initiation can be a true-up cycle or a threshold crossing. The intent selects a true-up policy; it does not compute the charge by itself. **[S DRG-557]**
3. A versioned `TrueUpPolicy`, resolved account, then agreement, then line, snapshotted onto the change line that it prices. **[S DRG-569; sanity §5]**
4. Pricing inputs that the Scenario 2 table shows move the invoice: aggregation, measurement window, tolerance, pack rounding, true-down, overage rate, backdating, and proration. **[S DRG-569]**
5. Reading provenance and a frozen snapshot. A presented true-up names the snapshot id and a hash of the readings it priced. A later correction creates a new revision. **[S DRG-570]**
6. A dry-run preview of the true-up before the quote is presentable to the customer. **[S DRG-569, DealHub `isDryRun`]**

### Out of this slice

Owned by sibling proposals or existing items: maintenance percent-of-licence pricing (`maintenance.md`, DRG-571); one-time and retroactive charge lines with their own service period (DRG-572); licence regeneration and fulfilment (DRG-558); idempotent usage ingestion (DRG-470); partial application of a mixed document (`partial-apply.md`, DRG-565); the intent and change-line vocabulary itself (`lifecycle-intents.md`).

### Proposed contract

Proposed shapes, not an accepted schema:

```text
CommitmentLine.metric            { Core | Pvu | Tb | Session | Other(code) }
MeterReading
  source                         licence-server log | customer declaration | other
  attestation                    actor, attestedAt, method
  status                         { Provisional | Attested | Disputed | Corrected }
  snapshotId                     set only when the reading is included in a frozen snapshot
  windowStart, windowEnd, value, unit

TrueUpSnapshot
  id, contentHash, readingIds[], status { Open | Frozen }
  frozenAt, frozenBy

TrueUpPolicy
  aggregation                    { Peak | P95 | Average | EndOfPeriod }
  window                         { start, end } or a named cycle
  tolerancePct
  packSize                       null means no pack rounding
  trueDown                       { Never | AtRenewal | Allowed }
  overageRate                    { Contracted | List | Multiplier(x) }
  backdate                       { None | FromFirstExceedance | FromWindowStart }
  prorationMethod                { Daily | MonthDay | None }
  policyVersionId
```

A true-up change line carries `quantitySemantics = Delta` or `Absolute`, `startQty`, `deltaQty`, `endQty`, the snapshot id, and the policy snapshot. **[S sanity Scenario 2]** Pricing reads an **attested, frozen** snapshot only. A `Disputed` or `Provisional` reading is excluded. A `Corrected` reading supersedes its predecessor and is eligible only in a new snapshot and a new change revision. **[S DRG-570]**

**Proposed measurement steps [I], applied in this order once the owner sets the knobs:**

1. Drop readings outside the window.
2. Drop readings whose status is not `Attested` inside the frozen snapshot.
3. Aggregate all remaining attested readings, including readings below the commitment. Do not filter out low readings before computing Average or P95.
4. Compare the aggregate with `committed × (1 + tolerancePct)` (a fractional tolerance, so 0.05 means 5%). At or below this threshold, excess is zero.
5. Above the threshold, excess = max(0, aggregated − committed) when `trueDown = Never`. Tolerance is an exceedance gate, not a deduction from billable excess. True-down behavior for the other two values is an owner decision (below).
6. Round excess up to `packSize` when `packSize` is set.
7. Price excess at the selected overage rate.
8. If `backdate` is not `None`, emit the arrears amount with its own service period. The charge-line type for that arrears amount is DRG-572.

## True-up policy parameters (DRG-641)

### Expected values

Fixture `fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`. DRG-570 frozen snapshot. A dispute raised after freeze leaves the snapshot unchanged. The named outcome is a credit note.

| Field | Expected |
|---|---|
| Fixture `status` | `Proposed` |
| Snapshot id | `snap-tu-641-frozen` |
| Snapshot status before the dispute and after it | `Frozen` |
| `frozenAt` | `2026-10-05T12:00:00Z` |
| Dispute `raisedAt` | `2026-10-20T15:00:00Z` |
| Reading the dispute names | `r-2026-09` |
| `r-2026-09` status inside the snapshot | `Attested` |
| `r-2026-09` value inside the snapshot | 80 |
| Content hash | `b9195936e93f70a81609974fd94c7cded5c00007c585011788b0395a2373c680` |
| Content hash after the dispute | the same 64 hex digits |
| Excess cores | 16 |
| Overage amount | 40000.00 USD |
| Arrears amount | 0.00 USD |
| Currency | USD |
| Scale | 2 |
| Rounding mode | `HALF_UP` |
| Reopened | false |
| New snapshot created | false |
| Backfill | false |
| S7 | gated |
| Proposed outcome name | `credit-note` |
| Proposed outcome status | Proposed |
| Proposed outcome applied | false |
| Owner decision | ___ (drg, time) |

Parameter defaults. Every amount is the subscription currency at scale 2, `RoundingMode.HALF_UP`, rounded once on the extended amount. The subscription currency in these rows is USD.

| Parameter | Proposed default | Status | Owner decision | Inputs | Rounding step | Result |
|---|---|---|---|---|---|---|
| Measurement window | Nine calendar months ending on the last day of the month before `contractEffective`. For `contractEffective` 2026-10-01 the bounds are 2026-01-01 through 2026-09-30, inclusive. | Status: Proposed | ___ (drg, time) | `contractEffective` 2026-10-01. Committed 64. Rate 2500.00 USD/core. `tolerancePct` 0. `packSize` unset. `backdate` None. Readings: 2025-12-01 value 90; monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80 from January through September 2026; 2026-10-01 value 99. | Drop 2025-12-01 and 2026-10-01. Maximum of the readings that remain is 80. Excess = max(0, 80 - 64) = 16. Extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` leaves 40000.00. | Window 2026-01-01 through 2026-09-30. Overage 40000.00 USD. |
| Tolerance | `tolerancePct = 0`. The value is an exceedance gate. | Status: Proposed | ___ (drg, time) | Committed 64. Aggregate 66. `tolerancePct` 0. `packSize` unset. Contracted rate 2500.00 USD/core. | Threshold = 64 × (1 + 0) = 64. 66 is above 64, so excess = max(0, 66 - 64) = 2. Extended = 2 × 2500.00 = 5000.00. `setScale(2, HALF_UP)` = 5000.00. | Excess 2 cores. Overage 5000.00 USD. |
| Tolerance clause check | A later clause may set 0.05. This row is not the Proposed default. | Status: Proposed | ___ (drg, time) | Committed 64. Aggregate 66. `tolerancePct` 0.05. Rate 2500.00 USD/core. | Threshold = 64 × 1.05 = 67.2. 66 is at or below 67.2, so excess = 0. Extended = 0. `setScale(2, HALF_UP)` = 0.00. | Excess 0. Overage 0.00 USD. |
| Pack rounding | `packSize` unset. Excess stays the integer the earlier steps produced. | Status: Proposed | ___ (drg, time) | Excess before pack = 13. `packSize` unset. Rate 2500.00 USD/core. | No pack multiple is applied. 13 stays 13. Extended = 13 × 2500.00 = 32500.00. `setScale(2, HALF_UP)` = 32500.00. | 13 cores. Overage 32500.00 USD. |
| Pack rounding clause illustration | `packSize` 8. This row is not the Proposed default. | Status: Proposed | ___ (drg, time) | Excess before pack = 13. `packSize` 8. Rate 2500.00 USD/core. | Packs = ceil(13 / 8) = ceil(1.625) = 2. Billed cores = 2 × 8 = 16. Extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00. | 16 cores. Overage 40000.00 USD. |
| Overage rate | `Contracted`. The unit rate on the commitment line. | Status: Proposed | ___ (drg, time) | Excess 16. Contracted rate 2500.00 USD/core. List rate 3200.00 USD/core is present and unused. No multiplier. | Extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00. The list product 16 × 3200.00 = 51200.00 is unused. | Overage 40000.00 USD. |
| Backdating | `None`. Arrears amount 0.00. | Status: Proposed | ___ (drg, time) | `backdate` None. Forward excess 16 at 2500.00 USD/core. | Arrears extended amount is 0. `setScale(2, HALF_UP)` = 0.00. Forward extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00. | Arrears 0.00 USD. Overage 40000.00 USD. |
| Backdating limit | Any arrears service period contains only dates inside the measurement window. Under `None` the period is empty. | Status: Proposed | ___ (drg, time) | Window 2026-01-01 through 2026-09-30. A December 2025 peak of 90. | The December 2025 date is outside the window, so it adds 0 core-months and 0.00 USD. `setScale(2, HALF_UP)` on 0 = 0.00. | December 2025 contributes 0.00 USD. |
| Backdating clause illustration | `FromFirstExceedance`, bounded by the limit above. This row is not the Proposed default. | Status: Proposed | ___ (drg, time) | Commitment 64. Monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80. Maintenance 500.00 USD per core-year. Window 2026-01-01 through 2026-09-30. | Core-months = 0+0+0+2+6+8+12+14+16 = 58. Extended = 58 × (500.00 / 12) = 29000 / 12 = 2416.666.... `setScale(2, HALF_UP)` looks at the third decimal digit 6 and yields 2416.67. | Arrears 2416.67 USD. |

### Prose

This section is the DRG-641 record for the [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) knobs named in the expected-value table. It links to [cluster-c-trueup-and-meters-decision-frame.md](../cluster-c-trueup-and-meters-decision-frame.md) and does not edit that file. Conflicts with that frame are open items at the end of this section. Worked numbers use the illustrative Scenario 2 readings in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data). The fixture file is `fixtures/capacity-meters/frozen-snapshot-dispute.proposed.json`. No other snapshot fixture is replaced.

**Money rule.** The currency of every overage amount is the subscription currency. `Subscription.currencyCode` defaults to `"USD"` in `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/domain/Subscription.java`. The proposed `TrueUpPolicy` shape in this file names no other currency, so each worked example uses USD. Each money result is a decimal at scale 2. Rounding is `RoundingMode.HALF_UP`, applied once to the extended amount. That format does not fill an owner slot.

#### Measurement window

Proposed default: when the true-up cycle does not name both bounds, the window is the nine calendar months that end on the last day of the calendar month before `contractEffective`. The start is the first day of the earliest of those nine months. Both bounds are inclusive. January through September 2026 is nine months. For `contractEffective` 2026-10-01 the window is 2026-01-01 through 2026-09-30.

Status: Proposed

Owner decision: ___ (drg, time)

Worked example.

Inputs. `contractEffective` 2026-10-01. The action date 2026-10-05 is not an input to the bounds. Committed cores 64. Contracted rate 2500.00 USD per core. `tolerancePct` 0. `packSize` unset. `backdate` None. Readings: 2025-12-01 value 90; monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80 on the months January through September 2026; 2026-10-01 value 99.

Rounding step. Drop every reading outside 2026-01-01 through 2026-09-30. The December 2025 reading and the October 2026 reading drop. The maximum of the nine readings that remain is 80. Excess = max(0, 80 - 64) = 16. Extended overage = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` leaves 40000.00 because the product is already at scale 2.

Result. Window 2026-01-01 through 2026-09-30 inclusive. Excess 16 cores. Overage 40000.00 USD.

#### Tolerance

Proposed default: `tolerancePct = 0`, a fraction, so the threshold equals the commitment. Tolerance is an exceedance gate. After the gate opens, billable excess is max(0, aggregated - committed) when `trueDown = Never`. A positive fraction remains a clause value.

Status: Proposed

Owner decision: ___ (drg, time)

Worked example.

Inputs. Committed 64. Aggregate presented to the gate: 66. `tolerancePct` 0. `packSize` unset. Contracted rate 2500.00 USD per core. Currency USD.

Rounding step. Threshold = 64 × (1 + 0) = 64. 66 is above 64, so the gate opens. Excess = max(0, 66 - 64) = 2. The gate adds no further subtraction. Extended = 2 × 2500.00 = 5000.00. `setScale(2, HALF_UP)` = 5000.00.

Result. Excess 2 cores. Overage 5000.00 USD.

Clause check, same owner slot, not the Proposed default. Inputs: committed 64, aggregate 66, `tolerancePct` 0.05, rate 2500.00 USD per core. Rounding step: threshold = 64 × 1.05 = 67.2. 66 is at or below 67.2, so excess = 0 and `setScale(2, HALF_UP)` on 0 yields 0.00. Result: overage 0.00 USD.

#### Pack rounding

Proposed default: `packSize` unset. The excess integer is unchanged by a pack multiple.

Status: Proposed

Owner decision: ___ (drg, time)

Worked example.

Inputs. Excess before the pack step = 13 cores. `packSize` unset. Contracted rate 2500.00 USD per core. Currency USD.

Rounding step. The pack step does not run. 13 stays 13. Extended = 13 × 2500.00 = 32500.00. `setScale(2, HALF_UP)` = 32500.00.

Result. 13 cores. Overage 32500.00 USD.

Clause illustration, same owner slot, not the Proposed default. Inputs: excess 13, `packSize` 8, rate 2500.00 USD per core. Rounding step: 13 / 8 = 1.625, and ceil(1.625) = 2 packs. Billed cores = 2 × 8 = 16. Extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00. Result: 16 cores, overage 40000.00 USD. This is the sanity-check pack row. It is a different input from the 16-core peak of the base readings.

#### Overage rate

Proposed default: `Contracted`, the unit rate on the commitment line. List and `Multiplier(x)` remain clause values. A missing list, where a clause has selected List, yields no price.

Status: Proposed

Owner decision: ___ (drg, time)

Worked example.

Inputs. Excess 16 cores. Contracted unit rate 2500.00 USD. A list unit rate of 3200.00 USD is on the example and is unused. No multiplier is set. Currency USD. Scale 2.

Rounding step. Extended = 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00. The unused list product is 16 × 3200.00 = 51200.00.

Result. Overage 40000.00 USD.

#### Backdating

Proposed default: `None`. The true-up bills the forward overage from the contract effective date. The arrears amount is 0.00 in the subscription currency.

Proposed limit: an arrears service period contains only dates inside the measurement window. Under `None` that period is empty. A later replacement of `None` still starts no earlier than `windowStart` and ends no later than `windowEnd`. On the Scenario 2 dates the bound is 2026-01-01 through 2026-09-30. Backdating includes no backfill. The limit writes no subscription header, no amendment document, and no meter reading. It does not call `contract_header_backfill_apply`. S7 / [DRG-585](https://linear.app/drgamtd-workspace/issue/DRG-585) stays gated. The owner sequence in [s7-v1-8-header-backfill.md](../../s7-v1-8-header-backfill.md) is unchanged. The charge-line type for a later arrears amount remains DRG-572. This section does not start DRG-572.

Status: Proposed

Owner decision: ___ (drg, time)

Worked example.

Inputs. `backdate` None. Forward excess 16 cores at the contracted rate 2500.00 USD per core. Currency USD. The Scenario 2 monthly excesses are not billed as arrears.

Rounding step. The arrears extended amount is 0. `setScale(2, HALF_UP)` = 0.00. The forward extended amount is 16 × 2500.00 = 40000.00. `setScale(2, HALF_UP)` = 40000.00.

Result. Arrears 0.00 USD. Overage 40000.00 USD.

Limit check. Inputs: the same window, plus a December 2025 peak of 90. Rounding step: 2025-12-01 is before `windowStart`, so the limit adds 0 core-months. `setScale(2, HALF_UP)` on 0 yields 0.00. Result: that reading contributes 0.00 USD, and no header row is written.

Clause illustration, same owner slot, not the Proposed default. Inputs: `FromFirstExceedance`, commitment 64, window 2026-01-01 through 2026-09-30, monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80, maintenance 500.00 USD per core-year. Rounding step: monthly excess core-months are Jan 0, Feb 0, Mar 0, Apr 2, May 6, Jun 8, Jul 12, Aug 14, Sep 16. The sum is 2+6+8+12+14+16 = 58. Extended arrears = 58 × (500.00 / 12) = 29000 / 12. 12 × 2416 = 28992, remainder 8, so the quotient is 2416 + 8/12 = 2416.666.... One rounding is applied to that extended amount at scale 2, `HALF_UP`. The third decimal digit is 6, so the second decimal digit rounds from 6 to 7. Result: 2416.67 USD. A core-month rate pre-rounded to 41.67 and then multiplied is 41.67 × 58 = 2416.86 USD. This illustration uses 2416.67 USD. The pre-rounded path stays open question 7. The illustration includes no backfill.

#### Frozen snapshot (DRG-570)

[DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) is the provenance, dispute, and frozen-snapshot story. The fixture `frozen-snapshot-dispute.proposed.json` is the check. Its name carries `proposed`. Its `status` is `Proposed`.

The snapshot `snap-tu-641-frozen` is `Frozen` at `2026-10-05T12:00:00Z` with nine `Attested` readings, values 60, 62, 64, 66, 70, 72, 76, 78, 80. The priced block records excess 16, overage 40000.00 USD, arrears 0.00 USD, scale 2, `HALF_UP`. The aggregation input on that priced block is Peak, with `aggregationStatus` Proposed and `aggregationOwnerDecision` `___ (drg, time)`. Peak is an input of the frozen example so the 16 can be checked by hand. It is frame fork F1. It is not a DRG-641 default row, and the owner slot is blank.

At `2026-10-20T15:00:00Z` a dispute names reading `r-2026-09` and carries dispute status `Disputed`. `writesSnapshot` is false. `reopensSnapshot` is false. Inside the snapshot, `r-2026-09` stays `Attested` with value 80. `snapshot.status` stays `Frozen`. `snapshot.contentHash` stays `b9195936e93f70a81609974fd94c7cded5c00007c585011788b0395a2373c680`. `snapshot.priced` stays on the amounts above. `reopened` is false. `newSnapshotCreated` is false. `backfill` is false. S7 stays gated.

Each reading carries an illustrative source and attestation actor, time and method; these synthetic values are fixture data, not a selected production policy. The content hash is SHA-256 over the UTF-8 canonical JSON in `contentHashCanonical`: the snapshot object with `contentHash` removed, keys sorted, separators comma and colon, no extra whitespace, written as 64 lowercase hex. That encoding is the shape the Cluster C frame proposes for a snapshot hash. The algorithm row in the fixture is Status: Proposed, and its owner decision is `___ (drg, time)`. This section does not call `AmendmentEventHasher`.

The named outcome is `credit-note`.

Status: Proposed

Owner decision: ___ (drg, time)

`applied` is false. The credit note does not rewrite the snapshot, does not reopen it, and includes no backfill.

### Open items

These conflicts stay open. This section leaves the decision frame unchanged. Every new label in this section is Status: Proposed. Every owner slot above is blank.

1. **Delegation record and the blank slots.** [delegated-renewals-amendments-decisions-2026-10-17.md](../delegated-renewals-amendments-decisions-2026-10-17.md) section C gives forks F1, F2, F3, F5, F6, and F7 the disposition cell `Adopt baseline default.` The decision frame status line says decisions were adopted under owner delegation on 2026-10-17. The matching rows in this section are Status: Proposed, and each owner decision is `___ (drg, time)`. DRG-641 requires drg's words and a time before any other label. The 2026-10-17 record is not written into those slots.
2. **The frame's description of this file.** The frame's "How to read a fork" paragraph says this spec leaves the knobs unset and says no default is proposed as accepted. This section now records Proposed defaults for the measurement window, tolerance, pack rounding, overage rate, and backdating. The frame file is unchanged, so that paragraph and this section disagree.
3. **Measurement window length.** The frame has no fork for the window length. Scenario 2 states a nine-month window as the example setup. The nine-month Proposed default is not a row in the frame's summary table.
4. **Dispute after freeze, frame G3, and AC-8.** Frame G3 and AC-8 in this file refuse pricing of a snapshot that contains a `Disputed` or `Provisional` reading. The fixture keeps `r-2026-09` as `Attested` inside the frozen snapshot and records the dispute beside it. The priced amount stays 40000.00 USD. The named outcome is a credit note at Status: Proposed. The fixture records the dispute beside the snapshot and leaves the frozen reading `Attested`. A definition of dispute that rewrites the frozen reading to `Disputed` would meet G3's refusal and would change the priced quote. That definition and the fixture's Proposed reading both remain on the record. The owner slot is blank.
5. **Arrears rounding stage.** AC-2 and open question 7 leave end-stage rounding (2416.67) and a pre-rounded core-month rate (2416.86) for the owner. The backdating clause illustration uses the extended amount at scale 2 `HALF_UP` and gets 2416.67 USD. That illustration is not the Proposed default (`None`). The owner slot is blank.
6. **Rung order.** This file still sketches resolution as account, then agreement, then line, and leaves it as an owner decision. Frame F8 proposes clause, then line, then agreement, then account, then the frame defaults. The delegated record's F8 cell is `Adopt baseline default.` This section does not set a rung order.
7. **Proration method.** Open question 4 still asks which proration method. The frame says that question is closed by the proration record and does not edit this spec. This section adds no proration default.
8. **Backdating and S7.** AC-2 stores an arrears service period that can start before the change effective date. The Proposed backdating limit bounds any such period by the measurement window and includes no backfill. Reading AC-2 as a run of `contract_header_backfill_apply` disagrees with that limit. S7 stays gated.

## Acceptance criteria

Fixtures use Scenario 2 **[S sanity]**: perpetual capacity line, metric core, committed 64, contracted licence rate $2,500/core, maintenance $500/core/year, window 2026-01-01 through 2026-09-30, monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80. Action date 2026-10-05. Forward maintenance uses 92/365 of the annual rate from a 2026-10-01 effective date. These criteria assert the arithmetic for a **configured** policy. They do not choose the configuration. The DRG-641 expected-value tables record Status: Proposed defaults. An owner decision with drg's words and a time is what would configure a policy for these criteria. The owner slots in that section are blank.

1. **Peak, no tolerance, no pack, backdate None, overage Contracted.** Excess 16. Licence charge $40,000. Forward maintenance $2,016.44. No back-maintenance line. The change stores snapshot id and content hash. Re-pricing the same snapshot id and policy version returns the same amounts.
2. **Peak, backdate FromFirstExceedance.** Same licence and forward maintenance as AC-1. Back-maintenance service period 2026-04-01 through 2026-09-30. Excess core-months 2+6+8+12+14+16 = 58. The sanity check states the amount as $2,416.67, which is 58 × ($500 / 12) rounded to cents at the end. A core-month rate pre-rounded to $41.67 and then multiplied yields $2,416.86. The fixture uses $2,416.67 only when the owner picks end-stage rounding (open question 7). The arrears service period is stored on the charge and may start before the change effective date.
3. **Average, readings rounded to a whole core (69.8 → 70).** Excess 6. Licence $15,000. Forward maintenance $756.16.
4. **Peak with a stated 13-core excess variant and packSize 8.** Excess rounds to 16 and the licence charge is $40,000. (The variant is the sanity-check pack row, separate from the 16-core peak of the base readings.)
5. **Tolerance 5% (threshold 67.2) on the base readings.** The 80 peak still yields 16 excess. A fixture whose peak is 66 and whose other readings stay at or below 64 yields 0 excess.
6. **Method spread.** The AC-3 total ($15,756.16) and the AC-2 total ($44,433.11) both come from the same readings. The spec records that the measurement and backdating choices move the charge by about 2.8×. A quote preview shows which knobs produced the figure.
7. **Snapshot freeze.** After a true-up is presented, correcting a reading in the snapshot does not change that quote. The correction creates a new snapshot and a new revision. The old revision still names the old hash.
8. **Dispute.** A snapshot that contains a `Disputed` or `Provisional` reading is refused for pricing. The refusal names the reading id and status.
9. **Threshold trigger.** With commitment 64, the March reading of 64 is the first reading at the threshold. A threshold trigger may open a true-up candidate; it does not present a price before the dry-run in AC-10.
10. **Dry run.** A true-up with no completed dry-run cannot move to a customer-visible state. The dry-run result includes excess, rate source (the $2,500 state period), maintenance trace, snapshot hash, and policy version.
11. **True-down Never.** A window whose aggregate is below 64 produces delta 0 and no credit.
12. **Idempotent re-presentation.** Presenting the same document, snapshot id, policy version, and content digest twice does not create a second priced revision.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| `lifecycle-intents.md` (DRG-548, DRG-552) | `TrueUp` intent, `Update` action, Delta and Absolute quantity semantics |
| `maintenance.md` (DRG-556, DRG-571) | The forward maintenance line is derived from the licence line; this slice emits the quantity delta and the maintenance policy owns the percent |
| DRG-572 | One-time retroactive charge with its own service period, required for AC-2 |
| DRG-470 | Ingestion may deliver readings; attestation, dispute, and freeze stay in this slice |
| DRG-551 | Dry-run and revert machinery, if the platform dry-run is shared |
| `partial-apply.md` (DRG-565) | A true-up document that also carries a one-time charge applies as separate units |
| Pricing trace (DRG-310, DRG-311) | The preview and the presented quote need a replayable decision |

Resolution order account > agreement > line, and "contract clause beats account default", is proposed in the sanity check and is an owner decision shared with renewal uplift (DRG-567). This slice stores the level that won; it does not invent a new precedence.

## Open questions

Conflicts with the Cluster C frame are listed as open items in [True-up policy parameters (DRG-641)](#true-up-policy-parameters-drg-641). This list stays open beside that section.

1. **Owner decision — aggregation default.** Peak, P95, Average, or EndOfPeriod. The spike sketch assumes Peak. Scenario 2 shows Peak versus Average moves the charge from about $15,756 to about $44,433 before back-maintenance. No default is proposed as accepted. The frozen-snapshot fixture carries Peak as an input with Status: Proposed and a blank owner slot. That input does not close this question.
2. **Owner decision — tolerance, pack size, and true-down.** Whether tolerance is 0, whether pack rounding is off, and whether true-down is Never, AtRenewal, or Allowed. AtRenewal needs a renewal hook this slice does not define. DRG-641 records `tolerancePct = 0` and `packSize` unset as Status: Proposed, with the owner slot blank. True-down is unchanged.
3. **Owner decision — overage rate.** Contracted, list, or a multiplier. Scenario 2 prices the contracted $2,500. List requires a price-book read (DRG-539) this slice does not add. DRG-641 records `Contracted` as Status: Proposed, with the owner slot blank.
4. **Owner decision — backdating.** None, from first exceedance, or from window start, and which proration method. AC-2 is the from-first-exceedance fixture only. DRG-641 records `None` and a window-bounded limit as Status: Proposed, with the owner slot blank. Backdating includes no backfill. S7 stays gated.
5. **Owner decision — who may attest or dispute.** Licence-server logs and customer declarations are both in scope as sources. The actor, the dispute window, and whether a disputed true-up blocks renewal are unset.
6. **Owner decision — threshold versus cycle.** DRG-557 asks for both a threshold trigger and a true-up cycle. Whether a threshold opens a draft, a warning only, or nothing until the cycle date is unset.
7. **Owner decision — rounding of the average and of money.** AC-3 assumes half-up to a whole core (69.8 → 70) and money to 2 decimal places. The $41.67 core-month rate is the sanity-check figure; the owner confirms the rounding stage (unit versus extended) before AC-2 is a build fixture. The DRG-641 clause illustration rounds the extended arrears amount 2416.666... USD with `HALF_UP` at scale 2 and gets 2416.67 USD. The 2416.86 path stays unselected. The owner slot is blank.
8. **Owner decision — P95 and EndOfPeriod fixtures.** The vocabulary includes both. Worked rows exist for Peak and Average only. P95 and EndOfPeriod need an owner-supplied example before they are acceptance tests.
9. **Owner decision — where the snapshot lives.** The proposal puts the snapshot with the change that priced it. ADR-035 settles amendment-document placement in CAM. The owner still selects the authoritative metering-snapshot store and reference; this proposal does not reopen document placement.
