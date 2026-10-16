# Partial application of mixed-line amendments: first-slice spec proposal

**Status:** Proposal only. This file does not accept the spike's partial-apply rules, write an ADR, or authorize implementation, a flag change, or a deploy. Mid-term policy OQ5 (partial application) stays open. The acceptance criteria below are the proposed rules from DRG-565. They apply only if the owner adopts partial application.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `partial-apply`. **Date:** 2026-10-16.

**Source.** [DRG-565](https://linear.app/drgamtd-workspace/issue/DRG-565) (RA-31), which records the amendment-document spike §4 (`../../amendment-document-design-spike.md`). TMF622's `partial` order state is cited there as precedent, not as an API to copy.

**Conventions.** **[S]** is the issue or the spike section it quotes. **[I]** is the spike's own inference, still a proposal. The choice to allow partial apply at all is an **Owner decision**.

## Scope

### In this slice

1. Grouping of change lines into `ApplicationUnit`s. A unit is the largest set of lines that must commit together, normally one target subscription or one entitlement. **[S spike §4]**
2. Document states `PARTIALLY_APPLIED` and `APPLY_BLOCKED`, and the transitions among those, `EXECUTED`, and `APPLIED`. **[S DRG-565]**
3. Rules the spike proposes: signature is all-or-nothing; each unit commits or rolls back as a whole; units run in dependency order (create before terminate); each unit checks its own observed revision; a committed unit is not silently undone when a later unit fails; future-dated lines are recorded at apply time and take effect on their start date. **[S spike §4]**
4. Test scenarios DRG-565 names: restructure, true-up, and future-dated lines.
5. Recovery by a rebase revision or a correcting amendment. History stays append-only.

### Out of this slice

The commercial policies that fill the lines (`capacity-meters.md`, `conversions.md`, `maintenance.md`, `co-termination.md`). Dry-run and revert of a whole lifecycle change (DRG-551), except that this slice names rebase as the recovery when apply has already committed a unit. The ledger change that lets one document record several subscription acceptances (DRG-563, RA-29). One-time charge routing (DRG-572). SKU-mapping holds (DRG-599). Document placement is CAM under ADR-035, and OQ2 is resolved: apply starts only after EXECUTED. Partial-application policy (OQ5) remains a proposal; S4 implementation remains gated.

### Proposed contract

```text
ApplicationUnit
  documentId, unitNo
  targetType           { SUBSCRIPTION | ENTITLEMENT | ONE_TIME | NONE }
  targetId
  lineIds[]
  dependsOnUnitNos[]
  observedRevision
  contentDigest
  state                { PENDING | APPLIED | BLOCKED }

AmendmentDocument.status
  EXECUTED | APPLIED | PARTIALLY_APPLIED | APPLY_BLOCKED
  (one status column; no parallel applyStatus)
```

**Proposed rules [S spike §4], pending the owner decision:**

1. The customer signs one document. There is no partial signature.
2. One unit writes its rate periods, end dates, revision bump, and ledger row in one transaction, or it writes nothing.
3. A unit runs after every unit in `dependsOnUnitNos`. The default order for a restructure is create, then terminate, then revoke.
4. Future-dated lines are written at apply as future rate periods. Applied means recorded. In-effect is the period start.
5. If unit N fails after an earlier unit committed, the document becomes `PARTIALLY_APPLIED`. If no unit has committed, it becomes `APPLY_BLOCKED`. The failure raises a task. The fix is a rebase (re-observe, re-price, new revision, re-approval when the price changed) or a correcting amendment.
6. Each unit stores its own `observedRevision`. A stale revision fails that unit only.
7. The unique business identity is `(documentId, unitNo)`. Replaying that identity with the same content digest is a no-op; a different digest for that identity is rejected and audited. The digest is replay-validation data, not part of the unique key.

**Proposed grouping for the named scenarios [S spike §4]:**

| Scenario | Units |
|---|---|
| True-up | ENTITLEMENT for the capacity raise, then ONE_TIME for the true-up charge |
| Restructure | SUBSCRIPTION create for the SaaS line; then end-date maintenance; then ENTITLEMENT revoke after the dual-use window |
| Mixed document | One unit per target subscription or entitlement, plus ONE_TIME lines that go to order/billing |
| Future-dated line | The unit applies immediately and the rate period's start is the future date |

## Acceptance criteria

These criteria describe the proposed rules. If the owner rejects partial application, the build stops at open question 1 and these tests are not the specification.

1. **Signature.** A document with three units is signed once. An attempt to execute a single unit while the others remain unsigned is rejected.
2. **Unit atomicity.** A unit whose ledger write fails leaves that target's revision, rate periods, and acceptance row unchanged. A sibling unit that already committed stays committed.
3. **Create before terminate.** In the restructure grouping, the SaaS-create unit has no dependency, the maintenance-end unit depends on the create unit, and the revoke unit depends on the maintenance-end unit. A planner that orders terminate first is rejected.
4. **Restructure dual-use.** After the create unit and before the revoke unit, both the new subscription and the perpetual entitlement exist. The perpetual line is not surrendered by the create unit.
5. **True-up units.** The entitlement unit and the one-time charge unit are separate. The charge unit depends on the entitlement unit. Failure of the charge unit after the entitlement unit committed yields `PARTIALLY_APPLIED`, with the entitlement revision bumped and the charge unbilled.
6. **Future-dated line.** A line with start date after the apply timestamp is stored as a rate period with that start date during apply. The subscription's current in-effect rate is unchanged until that date. The unit's state is `APPLIED`.
7. **Own revision.** Unit A observes revision 4 and unit B observes revision 9. A concurrent bump of B to 10 fails unit B and leaves unit A applicable. The document does not fail solely because an unrelated subscription changed.
8. **Stale unit.** Unit A observed revision 4 and the target is already at 5. Unit A fails, no rows of unit A are written, and the document is `APPLY_BLOCKED` if A was the only unit, or `PARTIALLY_APPLIED` if another unit already applied.
9. **No silent rollback.** After AC-5, nothing deletes the committed entitlement unit. Recovery creates a new revision or a new correcting document.
10. **Idempotency.** A second apply of the same document id, unit number, and content digest does not write a second ledger row. A second apply with a different digest and the same unit number is rejected.
11. **All units applied.** When every unit reaches `APPLIED`, the document status is `APPLIED`.
12. **Rebase price change.** A rebase that changes a priced amount produces a new revision in a pre-execution state and requires approval again. A rebase that changes only the observed revision, with the same prices and digest of commercial fields, records the new observation. Which fields sit inside the digest is the spike's content-digest rule; this slice does not redefine it.
13. **Mixed targets.** A document with a SaaS seat change, an on-prem capacity raise, and a services fee produces three units with three target types. One signature covers all three.

## Dependencies

| Dependency | Why this slice needs it |
|---|---|
| DRG-535, DRG-536 | The amendment document and multi-line lines that get grouped |
| DRG-563 | Today's acceptance row is one subscription per quote. One document with several units needs that ledger shape |
| DRG-582 | Document-driven apply after EXECUTED is the neighboring story; this slice is the unit rules it would enforce |
| `lifecycle-intents.md` | Action codes and dates the dependency order reads |
| `conversions.md`, DRG-341 | Restructure scenario and the dual-entitlement window |
| `capacity-meters.md`, DRG-557 | True-up scenario |
| DRG-572 | ONE_TIME target for arrears, services fees, and true-up charges |
| DRG-551 | Revert of an unapplied change. After a unit commits, recovery is rebase or a correcting amendment |
| ADR-035 and resolved OQ2 | CAM owns the document; apply waits for EXECUTED. This proposal does not reopen either decision |

## Open questions

1. **Owner decision — allow partial apply.** Spike decision 5 and mid-term OQ5 are open. The alternative is all-or-nothing apply of the whole document, with no `PARTIALLY_APPLIED` state. This proposal specifies the partial rules and does not close the decision. Implementation of AC-2 through AC-13 waits on a yes.
2. **Owner decision — APPLY_BLOCKED versus PARTIALLY_APPLIED.** The spike uses both. The cut proposed here is: partial when some units have committed, blocked when none have. That cut is part of the same owner decision.
3. **Owner decision — dependency authoring.** Whether the seller orders units, or the system derives order from action codes (Add before Terminate, entitlement before one-time charge). AC-3 states the required order for a restructure. Who writes `dependsOnUnitNos` is unset.
4. **Owner decision — revoke timing.** The restructure revoke unit may be recorded now with a future entitlement end, or delayed until that date. AC-4 requires the entitlement to survive the create unit. It does not choose when the revoke unit is submitted to fulfilment.
5. **Owner decision — task and actor.** A failed unit raises a task. Which queue, and whether the seller or a contract admin rebases, is unset.
6. **Owner decision — re-approval on rebase.** The spike says re-approval when the price changed. Whether a revision-only rebase can proceed on the existing approval is unset. AC-12 states both fixtures and leaves the gate to the owner.
7. **Owner decision — NONE targets.** A perpetual line that is referenced and not changed (maintenance renewal) is a NONE unit in the spike table. Whether NONE units are stored and skipped, or omitted from the unit list, is unset.
8. **Owner decision — signature policy per document.** True-up is often described as notice-only and restructure as e-sign. That choice is the amendment policy's signature policy, not this slice. Partial apply still requires one signature outcome for the whole document.
