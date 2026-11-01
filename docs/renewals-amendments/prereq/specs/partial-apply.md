# Partial application of mixed-line amendments: first-slice spec proposal

**Status:** Proposal only. This file does not accept the spike's partial-apply rules, write an ADR, or authorize implementation, a flag change, or a deploy. Mid-term policy OQ5 (partial application) stays open. The acceptance criteria below are the proposed rules from DRG-565. They apply only if the owner adopts partial application. The DRG-637 section records merged apply behaviour. It does not rewrite the rules in this file.
**Project:** OPEN-CRM-CPQ (Linear team DRG). **Cluster:** `partial-apply`. **Date:** 2026-10-16. **DRG-637 reading base:** `6ec091b1f9ca1262b87f8c890a1c52a2ec7cd861`.

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

## DRG-637 reconciliation

Reading base for every file and line below: `main` `6ec091b1f9ca1262b87f8c890a1c52a2ec7cd861`. DRG-621 merged as pull request #688, merge commit `71d080e7f257d80d4dd7fc83d71979d212087571`, at 2026-11-01T14:36:47Z (08:36 CT). Pull request #718, merge commit `cda0d3348b7a127f2dc9457499e3b6458770893a`, at 2026-11-01T15:08:30Z (09:08 CT), and pull request #741, merge commit `81ed9752aacf178ffe7e5d688efd11b13c3a6734`, at 2026-11-01T18:50:53Z (12:50 CT), are inputs to the open-items table. This section does not accept this file. Every open item is `Status: Proposed`. `Owner decision: ___ (drg, time)`.

### Expected values

These rows are the DRG-621 branches as they stand at the reading base. The sentences under the table restate the same values. The fixtures restate them again. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

| id | fixture | precondition | expected |
|---|---|---|---|
| AC1-200 | [drg-621-partial-200.json](fixtures/partial-apply/drg-621-partial-200.json) | A later unit's digest conflicts after an earlier unit has applied, and the asset-lifecycle hold is not the path taken | HTTP 200. Document status `PARTIALLY_APPLIED`. The conflicting unit is persisted `BLOCKED` with reason `DIGEST_CONFLICT`. Audit event type `APPLY_UNIT_BLOCKED`. The applied unit stays applied |
| AC1-409 | [drg-621-zero-applied-409.json](fixtures/partial-apply/drg-621-zero-applied-409.json) | Every unit's ledger digest conflicts and zero units are `APPLIED` | HTTP 409 `DIGEST_CONFLICT`. Document status stays `EXECUTED`. Unit rows stay `PENDING`. One `APPLY_UNIT_BLOCKED` audit per unit. No `APPLY_REFUSED` |
| AC1-unit | same fixtures | A unit that the partial path blocks | The persisted unit state is `BLOCKED`. That value is the `APPLY_BLOCKED` unit state from the DRG-621 merge. The enum has no `APPLY_BLOCKED` constant |
| AC1-audit | same fixtures | Either fixture's block or all-conflict audit | The event type string is `APPLY_UNIT_BLOCKED`, written by `appendApplyDecision` |

### DRG-621 as merged

AC1-200. `DocumentApplyController.apply` returns `ApiResponse.success` with the orchestrator status name (`DocumentApplyController.java` lines 43–51). That method has no `@ResponseStatus`, so a returned result is HTTP 200. Once a unit has applied, `DocumentApplyOrchestrator.apply` blocks the digest conflict and continues (lines 177–179), then returns the finalized result (line 201). `blockDigestConflict` persists `ApplicationUnitStatus.BLOCKED`, reason `DIGEST_CONFLICT`, and appends `EVENT_UNIT_BLOCKED` (lines 416–422). `outcome` returns `AmendmentDocumentStatus.PARTIALLY_APPLIED` when the applied count is greater than zero and less than the unit count (lines 639–647). `DocumentApplyMidLoopConflictTest.controllerBodyKeepsDocumentFieldsAndListsUnitOutcomes` expects HTTP 200 and `PARTIALLY_APPLIED` (`DocumentApplyMidLoopConflictTest.java` lines 108–118). The class comment states the same contract (lines 35–38).

AC1-409. When every unit already has a conflicting ledger row, `classifyUnderLock` returns `ALL_LEDGER_CONFLICT` (lines 294–296), ahead of the asset-lifecycle hold (lines 298–299), and `apply` appends one `APPLY_UNIT_BLOCKED` row per unit, then throws `DocumentApplyException` with `HttpStatus.CONFLICT` and error code `DIGEST_CONFLICT` (lines 135–144). The in-loop digest conflict takes the same 409 when nothing has committed yet (lines 172–175). `GlobalExceptionHandler.handleBusinessException` in contract-asset-management writes the exception status (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/config/GlobalExceptionHandler.java` lines 30–35). `DocumentApplyException` carries that status (`DocumentApplyException.java` lines 7–10). `everyUnitConflictReturns409LeavesExecutedAndWritesBlockedChainAudit` expects `HttpStatus.CONFLICT`, document `EXECUTED`, both units `PENDING`, and `APPLY_UNIT_BLOCKED` with reason `DIGEST_CONFLICT` (`DocumentApplyMidLoopConflictTest.java` lines 128–156).

AC1-unit. The orchestrator comment states that `BLOCKED` is the persisted form of the OQ5 `APPLY_BLOCKED` unit state (`DocumentApplyOrchestrator.java` lines 204–206). `ApplicationUnitStatus` is `PENDING`, `APPLIED`, `BLOCKED` (`ApplicationUnitStatus.java` lines 4–8). Document status `APPLY_BLOCKED` is `AmendmentDocumentStatus` line 14.

AC1-audit. `EVENT_UNIT_BLOCKED` is the string `APPLY_UNIT_BLOCKED` (`DocumentApplyOrchestrator.java` line 51). The three append sites are the all-conflict loop (lines 139–141), the digest block (lines 421–422), and the period-block path (lines 517–518). `AmendmentEventWriter.appendApplyDecision` writes that chain row (lines 90–93).

These four rows match pull request #688, merge `71d080e7f257d80d4dd7fc83d71979d212087571`. They do not replace rules 1–7 or acceptance criteria 1–13.

### Open items

Differences from the rules above, including #718 and #741, stay in this table. `Status: Proposed`. `Owner decision: ___ (drg, time)`.

| id | input | merged observation at the reading base | spec sentence left in place | status | owner decision |
|---|---|---|---|---|---|
| OI-718-recovery | #718 `cda0d3348b7a127f2dc9457499e3b6458770893a`. The lines at head were last written by #722 `5b1261bf52304e8a9cefbf60e0c669bc39df7a5a` | When no unit is `PENDING` and the document is still `EXECUTED`, `classifyUnderLock` records `outcome(ordered)` and returns a replay (`DocumentApplyOrchestrator.java` lines 282–292, `recordOutcomeLocked` lines 591–606). `recoveryFinalizesCommittedUnitsAndWritesOneOutcomeEvent` expects document `APPLIED` and one `APPLY_DOCUMENT_APPLIED` when every unit is already `APPLIED` (`DocumentApplyIntegrationTest.java` lines 336–349) | Rule 5 and AC-9 name recovery as a rebase or a correcting amendment | Status: Proposed | ___ (drg, time) |
| OI-718-committed-digest | #718 `cda0d3348b7a127f2dc9457499e3b6458770893a` | A committed unit whose digest or attempt evidence does not match returns `PreflightKind.CONFLICT` before a pending sibling runs (lines 270–275 and 331–352). `apply` then throws HTTP 409 `DIGEST_CONFLICT` (lines 127–129). `interruptedApplyRejectsChangedCommittedUnitBeforePendingSibling` leaves the pending sibling `PENDING` and keeps the one ledger row (`DocumentApplyIntegrationTest.java` lines 274–303) | Rule 6 and AC-7 say a stale observation fails that unit only | Status: Proposed | ___ (drg, time) |
| OI-718-pending-ledger | #718 `cda0d3348b7a127f2dc9457499e3b6458770893a` | A `PENDING` unit whose ledger row already matches the digest is `PreflightKind.CONFLICT` (lines 277–279). Inside the loop, `PendingLedgerInconsistency` is thrown as HTTP 409 `DIGEST_CONFLICT` without the zero-applied check (lines 165–167, 431–440, and 496–497). `pendingMatchingLedgerDoesNotApplyEarlierSibling` leaves both units `PENDING` (`DocumentApplyIntegrationTest.java` lines 307–332) | Rule 7 and AC-10 say the same `(documentId, unitNo)` and the same digest are a no-op | Status: Proposed | ___ (drg, time) |
| OI-718-409-width | #718 `cda0d3348b7a127f2dc9457499e3b6458770893a` | OI-718-committed-digest returns 409 when an `APPLIED` unit is already on the document. The DRG-621 in-loop comment still limits its own 409 to the case where nothing has committed (lines 172–175). AC1-409 is that DRG-621 branch | AC1 states 409 for the DRG-621 zero-applied digest conflict. It does not state the #718 409s | Status: Proposed | ___ (drg, time) |
| OI-741-assets | #741 `81ed9752aacf178ffe7e5d688efd11b13c3a6734` | After rate periods are written, `applyUnit` calls `AssetChangeApply.onUnitApplied` in the unit transaction (`DocumentApplyOrchestrator.java` lines 522–527). The call is `MANDATORY` (`AssetChangeApply.java` lines 39–40 and 49–51). `UPDATE` amends, `REMOVE` and `TERMINATE` terminate, `SWAP` and `UPGRADE` supersede (lines 62–69). A missing asset returns without blocking (lines 19–21, 76–77, 112–113, 131–132, and 177–182) | Rule 2 names rate periods, end dates, the revision bump, and the ledger row as the unit write | Status: Proposed | ___ (drg, time) |
| OI-741-actions | #741 `81ed9752aacf178ffe7e5d688efd11b13c3a6734` | `DocumentApplyPeriods` writes `TERMINATE`, `SWAP`, and `UPGRADE` with Add, Update, and Remove (line 17, lines 55–66, and lines 71–74). `TERMINATE` shares `REMOVE`'s close path and is not `CO_TERM_MISSING` when `effectiveTo` is null (lines 79–89) | The restructure row in the grouping table is create, then end-date maintenance, then entitlement revoke | Status: Proposed | ___ (drg, time) |
| OI-722-attempt | #722 `5b1261bf52304e8a9cefbf60e0c669bc39df7a5a` | A non-pending replay also requires `attemptRevisionNo` and `attemptPinFingerprint` (lines 553–573). A pin mismatch fails `matchesTerminalAttempt` | Rule 7 names the content digest as the replay check | Status: Proposed | ___ (drg, time) |
| OI-737-target | #737 `965ef54c11c45d881f6bc34eabe2b12ac679cc3d` | A subscription that is not on the document contract blocks the unit with `TARGET_CONTRACT_MISMATCH`, status `BLOCKED`, and audit `APPLY_UNIT_BLOCKED` (`DocumentApplyOrchestrator.java` lines 507–518). The detail string is `DocumentApplyPeriods.detail` (line 39) | AC-8 names a stale observed revision as the unit failure | Status: Proposed | ___ (drg, time) |
| OI-749-hold | #749 `8f0a0cabab8947e1d5e25dea05bc9c12e6263e0a` | `AssetLifecycleReadiness.isReady` returns false (`AssetLifecycleReadiness.java` lines 12–14). Pending Add, Update, Remove, Terminate, Swap, Upgrade, or empty line evidence returns `ASSET_LIFECYCLE_HOLD` before the unit loop (`DocumentApplyOrchestrator.java` lines 298–316). The refusal is HTTP 503 `ASSET_LIFECYCLE_HOLD` (lines 319–327). Units stay `PENDING`. `productionReadinessIsHardHeld` and `allSupportedAssetMutationsAreHeld` expect that hold (`AssetLifecycleApplyHoldTest.java` lines 26–39). A document with no `PENDING` units still takes the replay branch before the hold (lines 282–292); `completedIdenticalReplayWorksWhileHoldIsActive` expects that replay (`AssetLifecycleApplyHoldTest.java` lines 58–64). `NO_CHANGE` alone is not held and becomes document `APPLY_BLOCKED` with `ACTION_NOT_IN_SLICE` (lines 49–54) | AC1-200 is the DRG-621 branch. This hold sits in front of that branch for the actions the test names | Status: Proposed | ___ (drg, time) |
| OI-rule5-zero | DRG-621 merge `71d080e7f257d80d4dd7fc83d71979d212087571`, compared with rule 5 | AC1-409 leaves the document `EXECUTED` on a zero-applied digest conflict. `outcome` still returns document `APPLY_BLOCKED` when the applied count is zero on the success path (lines 644–645), which is HTTP 200 from the controller | Rule 5 and AC-8 say zero committed units become document `APPLY_BLOCKED` | Status: Proposed | ___ (drg, time) |

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

Questions 1–8 are unchanged. DRG-637 open items are in the reconciliation section. Each of those items is `Status: Proposed` with `Owner decision: ___ (drg, time)`.

1. **Owner decision — allow partial apply.** Spike decision 5 and mid-term OQ5 are open. The alternative is all-or-nothing apply of the whole document, with no `PARTIALLY_APPLIED` state. This proposal specifies the partial rules and does not close the decision. Implementation of AC-2 through AC-13 waits on a yes.
2. **Owner decision — APPLY_BLOCKED versus PARTIALLY_APPLIED.** The spike uses both. The cut proposed here is: partial when some units have committed, blocked when none have. That cut is part of the same owner decision.
3. **Owner decision — dependency authoring.** Whether the seller orders units, or the system derives order from action codes (Add before Terminate, entitlement before one-time charge). AC-3 states the required order for a restructure. Who writes `dependsOnUnitNos` is unset.
4. **Owner decision — revoke timing.** The restructure revoke unit may be recorded now with a future entitlement end, or delayed until that date. AC-4 requires the entitlement to survive the create unit. It does not choose when the revoke unit is submitted to fulfilment.
5. **Owner decision — task and actor.** A failed unit raises a task. Which queue, and whether the seller or a contract admin rebases, is unset.
6. **Owner decision — re-approval on rebase.** The spike says re-approval when the price changed. Whether a revision-only rebase can proceed on the existing approval is unset. AC-12 states both fixtures and leaves the gate to the owner.
7. **Owner decision — NONE targets.** A perpetual line that is referenced and not changed (maintenance renewal) is a NONE unit in the spike table. Whether NONE units are stored and skipped, or omitted from the unit list, is unset.
8. **Owner decision — signature policy per document.** True-up is often described as notice-only and restructure as e-sign. That choice is the amendment policy's signature policy, not this slice. Partial apply still requires one signature outcome for the whole document.
