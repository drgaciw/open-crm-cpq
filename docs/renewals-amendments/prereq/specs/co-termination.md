# Co-termination and agreement merge: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `co-termination`. **Date:** 2026-10-16.

**Source.** [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20). It overlaps [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) (BC-07-04, amendments and co-termination). This proposal extends that item. It does not open a second co-term implementation. Scenario 4 in `../../cpq-pricing-policy-sanity-check.md` is the worked illustration (example figures, not customer data).

**Conventions.** **[S]** is taken from DRG-554 or that scenario. **[I]** is a design inference carried forward as a proposal. The mode set and the merge rules are **Owner decisions**.

## Worked fixtures (DRG-638)

| Fixture | File | Mode | Prior ends | Resulting end | Effective | Line | Monthly | Stub | Full months | TCV | Credit |
|---|---|---|---|---|---|---|---:|---|---|---:|---:|
| Same end | [same-end.json](fixtures/co-termination/same-end.json) | LatestEnd | both 2026-12-31 | 2026-12-31 | 2026-07-15 | line-saas, 20 × 40.00, in the change | 800.00 | 800.00 × 17/31 = 438.71 | Aug–Dec, 5 × 800.00 = 4000.00 | 4438.71 | 0.00 |
| Same end | same file | LatestEnd | both 2026-12-31 | 2026-12-31 | 2026-07-15 | line-assist, not in the change | — | — | — | 0.00 | 0.00 |
| Earlier | [earlier-end.json](fixtures/co-termination/earlier-end.json) | EarliestEnd | 2026-12-31 and 2026-09-30 | 2026-09-30 | 2026-07-15 | line-saas, 20 × 40.00 | 800.00 | 800.00 × 17/31 = 438.71 | Aug–Sep, 2 × 800.00 = 1600.00 | 2038.71 | 0.00. Status: Proposed (depends on OQ-4) |
| Earlier | same file | EarliestEnd | 2026-12-31 and 2026-09-30 | 2026-09-30 | 2026-07-15 | line-onprem, 10 × 25.00 | 250.00 | 250.00 × 17/31 = 137.10 | Aug–Sep, 2 × 250.00 = 500.00 | 637.10 | 0.00 |
| Later | [later-end.json](fixtures/co-termination/later-end.json) | LatestEnd | 2026-10-31 and 2026-12-31 | 2026-12-31 | 2026-07-15 | line-saas, 20 × 40.00 | 800.00 | 800.00 × 17/31 = 438.71 | Aug–Dec, 5 × 800.00 = 4000.00 | 4438.71 | 0.00 |
| Later | same file | LatestEnd | 2026-10-31 and 2026-12-31 | 2026-12-31 | 2026-07-15 | line-onprem, 10 × 25.00 | 250.00 | 250.00 × 17/31 = 137.10 | Aug–Dec, 5 × 250.00 = 1250.00 | 1387.10 | 0.00 |
| Merge of 2 | [merge-of-2.json](fixtures/co-termination/merge-of-2.json) | LatestEnd | agr-a 2026-10-31, agr-b 2026-12-31 | 2026-12-31 on survivor agr-a | 2026-08-10 | line-a, 20 × 40.00 | 800.00 | 800.00 × 22/31 = 567.74 | Sep–Dec, 4 × 800.00 = 3200.00 | 3767.74 | 0.00 |
| Merge of 2 | same file | LatestEnd | agr-a 2026-10-31, agr-b 2026-12-31 | 2026-12-31; `mergedFromIds` is [agr-b] | 2026-08-10 | line-b, 10 × 25.00 | 250.00 | 250.00 × 22/31 = 177.42 | Sep–Dec, 4 × 250.00 = 1000.00 | 1177.42 | 0.00 |

**Co-term mode named on each row.** Options remain LatestEnd, EarliestEnd, None, AlignToAgreement, and AlignToDate. No default mode is selected. Status: Proposed. Owner decision: ___ (drg, time)

**Same-end `line-assist` left out of the change.** Status: Proposed. Owner decision: ___ (drg, time)

**Merge survivor end, and any source disposition beyond keeping the row.** Status: Proposed. Owner decision: ___ (drg, time)

Example figures, not customer data. Each row is the expected forward charge of the replacement quantity from the effective date through the resulting end. Machine-readable copies are one JSON file per fixture under `fixtures/co-termination/`.

The amounts use the day-stub in `QuoteContinuationPolicy.plan`. DRG-603 D-01 names that method. Its status on this tree is per DRG-603 Accepted ([PR #623](https://github.com/drgaciw/open-crm-cpq/pull/623)), recorded in [`../adr-amendment-proration-formula.md`](../adr-amendment-proration-formula.md). Rounding is `RoundingMode.HALF_UP` at scale 2: `money(...)` and `divide(..., 2, HALF_UP)` in that method.

```text
monthly = money(netUnitPrice × requestedQuantity)     // scale 2, HALF_UP
stubDays = inclusive days from effective through the billing-period end
periodDays = inclusive days from billingStart through the billing-period end
periodAmount = monthly × stubDays / periodDays         // scale 2, HALF_UP
```

Inclusive days are `ChronoUnit.DAYS.between(start, end) + 1`. Each later whole month through the resulting end is charged in full at `monthly`. A whole month is not day-prorated. Total contract value is the stub plus those months. The ADR's worked row for this fraction is `900.00 × 17/31 = 493.55`. `800.00 × 17/31 = 438.71` and `250.00 × 17/31 = 137.10` use the same rounding. `800.00 × 22/31 = 567.74` and `250.00 × 22/31 = 177.42` are the August stubs.

`plan` prices the requested quantity forward to the subscription end it is given, and it requires that end to equal `billingAnchor.plusMonths(n).minusDays(1)`. These fixtures set that end to the co-term result, then apply the day-stub. The billing anchor on every line is 2026-01-01, so a period runs from the first of a month through the last. July and August 2026 have 31 days. The July stub starts 2026-07-15 (17 inclusive days). The August stub starts 2026-08-10 (22 inclusive days). September and November appear only as full months, so their shorter length leaves the monthly amount unchanged.

The day-stub emits no credit. The 0.00 credit on the shortened earlier-end line-saas row is Status: Proposed (depends on OQ-4). Question 4 still owns whether a shorter end must create one. The full-term ratio in `amendment-proration.drl` is the option DRG-603 rejects. These four fixtures do not calculate it.

Merge of 2 writes `mergedFromIds: ["agr-b"]` on survivor `agr-a`, and `agr-b` records `agr-a` as the survivor. The source row stays. The two forward charges sum to 3767.74 + 1177.42 = 4945.16. The other three fixtures leave the agreements separate. Each fixture file carries `"status": "per DRG-603 Accepted (PR #623)"`. S8 (DRG-586) copies them later. Nothing in this slice is placed under `src/test`.

## Scope

### In this slice

1. A `CoTermPolicy` the change resolves and snapshots. Proposed modes: `LatestEnd`, `EarliestEnd`, `None`, `AlignToAgreement`. **[S DRG-554]**
2. An explicit anchor date, proposed as `AlignToDate`, because Scenario 4's anniversary is neither the latest nor the earliest existing end. **[S sanity Scenario 4]** Adding this mode is an owner decision; it is not in the DRG-554 enum as written.
3. `mergedFromIds` on the surviving agreement when a merge intent combines agreements. **[S DRG-554]**
4. Proration of lines whose start or end moves to the chosen anchor. The proration method stays the amendment or renewal policy's method. This slice chooses the date, not the day-count formula.
5. A refusal when a line's product is not co-termable (`catalog-model.md` flag `coTermable`).

### Out of this slice

The amendment document and partial apply (`partial-apply.md`). Renewal uplift, price locks, and the ARR bridge (DRG-567, DRG-568, `arr-bridge.md`). Maintenance percent math (`maintenance.md`). DRG-461's broader amendment behavior beyond the co-term date and the merge identity. Contract-header backfill (DRG-585). Approvals (DRG-591, parked).

Mid-term amendments co-term to the existing end date unless a co-term policy says otherwise (`../../mid-term-amendment-policy-spec.md` V5). This slice is that policy.

### Proposed contract

```text
CoTermPolicy
  mode             { LatestEnd | EarliestEnd | None | AlignToAgreement | AlignToDate }
  anchorDate       required for AlignToDate
  anchorAgreementId  required for AlignToAgreement
  policyVersionId

Agreement
  mergedFromIds[]  identifiers of agreements folded into this one
  endDate          the date the mode produced, when the mode changes an end
```

**Proposed resolution [I]:**

| Mode | End date used for lines the change co-terms |
|---|---|
| LatestEnd | The latest `endDate` among the change's target lines |
| EarliestEnd | The earliest `endDate` among those lines |
| None | Each line keeps its own end |
| AlignToAgreement | The named agreement's end |
| AlignToDate | `anchorDate` |

Lines with `coTermable = false` keep their end under every mode except when the seller included them in a mode that moves dates, in which case validation fails. `None` accepts those lines.

Merge writes `mergedFromIds` on the survivor and does not delete the source agreement rows. Source agreements record the survivor id. Commercial close of the source is an owner decision below.

## Acceptance criteria

Scenario 4 dates **[S sanity]**: SaaS term ends 2026-10-31, on-prem maintenance and term licence end 2026-12-31, requested anniversary 2027-12-31. AI Assist was added 2026-04-01 and co-termed to the SaaS end, with a price lock until 2027-03-31. The lock is priced by the price-lock policy; this slice only moves dates.

1. **AlignToDate.** Mode `AlignToDate`, anchor 2027-12-31, moves the renewed SaaS lines to end 2027-12-31 and the renewed on-prem lines to end 2027-12-31. The decision stores the anchor and the policy version.
2. **LatestEnd is a different result.** The same lines with `LatestEnd` and no explicit anniversary end on 2026-12-31 (the later of 2026-10-31 and 2026-12-31). They do not end on 2027-12-31.
3. **EarliestEnd.** The same lines end on 2026-10-31.
4. **None.** SaaS lines keep 2026-10-31 and on-prem lines keep 2026-12-31.
5. **AlignToAgreement.** Anchor agreement is the SaaS agreement ending 2026-10-31. On-prem lines that are co-termed take 2026-10-31. Lines already on that agreement keep 2026-10-31.
6. **Partial term is visible.** A SaaS line renewed from 2026-11-01 to 2027-12-31 has a 14-month term. The line stores start and end. The price for those 14 months is the renewal policy's job; this slice's assertion is the date span.
7. **Non-co-termable SKU.** A line whose eligibility flag `coTermable` is false, included in an `AlignToDate` change, fails validation and names the SKU. The same line under mode `None` is accepted.
8. **Merge identity.** Merging agreement B into agreement A sets A's `mergedFromIds` to include B, and B records A as the survivor. Reading A returns B in `mergedFromIds`. A second merge of B into A does not duplicate the id.
9. **Snapshot.** A presented quote stores the mode, anchor, and policy version. Changing the account co-term mode afterward does not move the dates on that revision.
10. **Single-agreement amendment.** A mid-term amendment with no co-term policy still ends changed lines on the agreement's current end (mid-term V5). This slice does not require a `CoTermPolicy` row for that case.
11. **Empty target set.** A policy in `LatestEnd` or `EarliestEnd` with no dated lines is rejected. `AlignToDate` without `anchorDate` is rejected. `AlignToAgreement` without `anchorAgreementId` is rejected.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| DRG-461 | Existing BC-07-04 home for amendments and co-termination. Implement there, or link that item to this proposal, rather than a parallel model |
| DRG-603, accepted in [PR #623](https://github.com/drgaciw/open-crm-cpq/pull/623) | Day-stub formula the DRG-638 fixtures apply. Acceptance of that formula does not select a co-term mode |
| `lifecycle-intents.md` (DRG-548, DRG-553) | The change that carries target agreements and per-line effective dates |
| `catalog-model.md` (DRG-576) | `coTermable` flag used by AC-7 |
| DRG-536 | Multi-line, multi-agreement changes are the point at which co-term becomes testable |
| DRG-585 | Contract header for agreements that do not yet have an end date to align to |
| Renewal and price-lock policies (DRG-567, DRG-568) | Scenario 4's 14-month price, the AI Assist lock through 2027-03-31, and the segment schedule |
| `maintenance.md` | On-prem lines in Scenario 4 are maintenance and term licence; their commercial renew rules stay in that slice |
| `partial-apply.md` | A merge that creates one agreement and retires another applies as ordered units |

## Open questions

1. **Owner decision — mode set.** DRG-554 names LatestEnd, EarliestEnd, None, and AlignToAgreement. Scenario 4 needs a date that is none of those results. `AlignToDate` is proposed and not accepted. Without it, AC-1 has no mode to call.
2. **Owner decision — default mode.** No default is proposed. A missing policy can mean None, AlignToAgreement, or a refusal until a mode is chosen. The mid-term rule (keep the current end) covers a single-agreement amendment only. Status: Proposed. Owner decision: ___ (drg, time)
3. **Owner decision — which lines move.** All lines on the target agreements, or only lines the seller marked. Renewal of a subset (Scenario 4 leaves Legacy Connector unrenewed) must leave a line that is not in the change on its current end. Confirming that boundary is required before AC-1 is implemented.
4. **Owner decision — shortening a term.** Aligning to EarliestEnd can end a line before its current end. Whether that is allowed, and whether it creates a credit, is a commercial rule this slice does not set. AC-3 states the date only.
5. **Owner decision — lengthening past the master.** AlignToDate can extend a line past every current end. Whether that extension is a renewal intent, an amendment, or forbidden mid-term is unset. The mid-term policy currently forbids term extension.
6. **Owner decision — merge versus co-term.** Co-term can align dates across agreements that remain separate. Merge folds agreements and writes `mergedFromIds`. Whether Scenario 4 is a co-term inside a Renew (agreements stay separate) or a merge is unset. The scenario text keeps MSA-1001 and LM-2002 as two targets.
7. **Owner decision — source agreement after merge.** `mergedFromIds` preserves identity. Whether the source becomes read-only, cancelled, or left active until its lines move is unset. This proposal does not delete it.
8. **Owner decision — proration when the anchor is mid-period.** The date choice is in scope. DRG-603 Accepted ([#623](https://github.com/drgaciw/open-crm-cpq/pull/623)) resolves the RA-08 formula conflict. The day-count formula the DRG-638 fixtures apply is the day-stub in `QuoteContinuationPolicy.plan`. That method prorates only a partial billing period, by inclusive days, at scale 2 half-up, then charges each later whole month in full, and it requires the end to equal `anchor.plusMonths(n).minusDays(1)`. Whether a co-term anchor may fall inside a billing period stays open. This slice adds no third formula. Whether a shorter end creates a credit stays question 4.
9. **Owner decision — DRG-461 scope cut.** How much of BC-07-04 this first slice covers, and how much stays on DRG-461, needs an owner mark so the two items are not built twice.
