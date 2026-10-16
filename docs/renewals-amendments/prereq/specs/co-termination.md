# Co-termination and agreement merge: first-slice spec proposal

**Status:** Proposal only. This file does not accept a policy, write an ADR, or authorize implementation, a flag change, or a deploy.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `co-termination`. **Date:** 2026-10-16.

**Source.** [DRG-554](https://linear.app/drgamtd-workspace/issue/DRG-554) (RA-20). It overlaps [DRG-461](https://linear.app/drgamtd-workspace/issue/DRG-461) (BC-07-04, amendments and co-termination). This proposal extends that item. It does not open a second co-term implementation. Scenario 4 in `../../cpq-pricing-policy-sanity-check.md` is the worked illustration (example figures, not customer data).

**Conventions.** **[S]** is taken from DRG-554 or that scenario. **[I]** is a design inference carried forward as a proposal. The mode set and the merge rules are **Owner decisions**.

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
| `lifecycle-intents.md` (DRG-548, DRG-553) | The change that carries target agreements and per-line effective dates |
| `catalog-model.md` (DRG-576) | `coTermable` flag used by AC-7 |
| DRG-536 | Multi-line, multi-agreement changes are the point at which co-term becomes testable |
| DRG-585 | Contract header for agreements that do not yet have an end date to align to |
| Renewal and price-lock policies (DRG-567, DRG-568) | Scenario 4's 14-month price, the AI Assist lock through 2027-03-31, and the segment schedule |
| `maintenance.md` | On-prem lines in Scenario 4 are maintenance and term licence; their commercial renew rules stay in that slice |
| `partial-apply.md` | A merge that creates one agreement and retires another applies as ordered units |

## Open questions

1. **Owner decision — mode set.** DRG-554 names LatestEnd, EarliestEnd, None, and AlignToAgreement. Scenario 4 needs a date that is none of those results. `AlignToDate` is proposed and not accepted. Without it, AC-1 has no mode to call.
2. **Owner decision — default mode.** No default is proposed. A missing policy can mean None, AlignToAgreement, or a refusal until a mode is chosen. The mid-term rule (keep the current end) covers a single-agreement amendment only.
3. **Owner decision — which lines move.** All lines on the target agreements, or only lines the seller marked. Renewal of a subset (Scenario 4 leaves Legacy Connector unrenewed) must leave a line that is not in the change on its current end. Confirming that boundary is required before AC-1 is implemented.
4. **Owner decision — shortening a term.** Aligning to EarliestEnd can end a line before its current end. Whether that is allowed, and whether it creates a credit, is a commercial rule this slice does not set. AC-3 states the date only.
5. **Owner decision — lengthening past the master.** AlignToDate can extend a line past every current end. Whether that extension is a renewal intent, an amendment, or forbidden mid-term is unset. The mid-term policy currently forbids term extension.
6. **Owner decision — merge versus co-term.** Co-term can align dates across agreements that remain separate. Merge folds agreements and writes `mergedFromIds`. Whether Scenario 4 is a co-term inside a Renew (agreements stay separate) or a merge is unset. The scenario text keeps MSA-1001 and LM-2002 as two targets.
7. **Owner decision — source agreement after merge.** `mergedFromIds` preserves identity. Whether the source becomes read-only, cancelled, or left active until its lines move is unset. This proposal does not delete it.
8. **Owner decision — proration when the anchor is mid-period.** The date choice is in scope. The day-count formula is the amendment proration rule (already tracked as a formula conflict, RA-08). This slice must not add a third formula.
9. **Owner decision — DRG-461 scope cut.** How much of BC-07-04 this first slice covers, and how much stays on DRG-461, needs an owner mark so the two items are not built twice.
