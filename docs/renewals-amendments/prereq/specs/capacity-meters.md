# Capacity meters and true-up: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `capacity-meters`. **Date:** 2026-10-16.

**Sources.** [DRG-557](https://linear.app/drgamtd-workspace/issue/DRG-557) (RA-23, meter model and TrueUp intent), [DRG-569](https://linear.app/drgamtd-workspace/issue/DRG-569) (RA-35, true-up pricing semantics), [DRG-570](https://linear.app/drgamtd-workspace/issue/DRG-570) (RA-36, provenance, dispute, frozen snapshot). Worked numbers are the illustrative Scenario 2 in `../../cpq-pricing-policy-sanity-check.md` (example figures, not customer data). The amendment spike's `delta_qty = max(0, peak − entitled)` is a sketch that picks Peak with no tolerance or backdating; this proposal does not adopt that sketch as the policy.

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

## Acceptance criteria

Fixtures use Scenario 2 **[S sanity]**: perpetual capacity line, metric core, committed 64, contracted licence rate $2,500/core, maintenance $500/core/year, window 2026-01-01 through 2026-09-30, monthly peaks 60, 62, 64, 66, 70, 72, 76, 78, 80. Action date 2026-10-05. Forward maintenance uses 92/365 of the annual rate from a 2026-10-01 effective date. These criteria assert the arithmetic for a **configured** policy. They do not choose the configuration.

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

1. **Owner decision — aggregation default.** Peak, P95, Average, or EndOfPeriod. The spike sketch assumes Peak. Scenario 2 shows Peak versus Average moves the charge from about $15,756 to about $44,433 before back-maintenance. No default is proposed as accepted.
2. **Owner decision — tolerance, pack size, and true-down.** Whether tolerance is 0, whether pack rounding is off, and whether true-down is Never, AtRenewal, or Allowed. AtRenewal needs a renewal hook this slice does not define.
3. **Owner decision — overage rate.** Contracted, list, or a multiplier. Scenario 2 prices the contracted $2,500. List requires a price-book read (DRG-539) this slice does not add.
4. **Owner decision — backdating.** None, from first exceedance, or from window start, and which proration method. AC-2 is the from-first-exceedance fixture only.
5. **Owner decision — who may attest or dispute.** Licence-server logs and customer declarations are both in scope as sources. The actor, the dispute window, and whether a disputed true-up blocks renewal are unset.
6. **Owner decision — threshold versus cycle.** DRG-557 asks for both a threshold trigger and a true-up cycle. Whether a threshold opens a draft, a warning only, or nothing until the cycle date is unset.
7. **Owner decision — rounding of the average and of money.** AC-3 assumes half-up to a whole core (69.8 → 70) and money to 2 decimal places. The $41.67 core-month rate is the sanity-check figure; the owner confirms the rounding stage (unit versus extended) before AC-2 is a build fixture.
8. **Owner decision — P95 and EndOfPeriod fixtures.** The vocabulary includes both. Worked rows exist for Peak and Average only. P95 and EndOfPeriod need an owner-supplied example before they are acceptance tests.
9. **Owner decision — where the snapshot lives.** The proposal puts the snapshot with the change that priced it. ADR-035 settles amendment-document placement in CAM. The owner still selects the authoritative metering-snapshot store and reference; this proposal does not reopen document placement.
