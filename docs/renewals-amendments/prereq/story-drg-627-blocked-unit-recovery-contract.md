# DRG-627 — Blocked-unit recovery contract (Decision brief)

**Status:** Proposal only. Decision / recovery-contract brief for CPQ Decision scoring. This file does **not** accept [`specs/partial-apply.md`](specs/partial-apply.md), accept [`specs/apply-authorization.md`](specs/apply-authorization.md), authorize product code, authorize an implementation brief, flip a flag, release `#749` / `ASSET_LIFECYCLE_HOLD`, release the [asset write hold](../../runbooks/asset-write-hold.md), enable dual-read / Jev / ramp, open a gateway public route, or deploy. Every open fork below is `Status: Proposed`. No new Adopted cells. Adoption of an open fork requires the owner's words and a time: `Owner decision: ___ (drg, time)`.

**Product code NO-GO.** Until the owner accepts the recovery policy in this brief (or an explicitly superseding owner record), **no** DRG-627 implementation story may change CAM apply sources, add a recovery CLI/endpoint, mutate unit status from operator tooling, or treat test-fixture resets as a supported path. A draft PR that lands only this document does not lift that NO-GO.

**Date:** 2026-11-06. **Project:** OPEN-CRM-CPQ. **Team:** DRG. **Cluster:** `partial-apply` / S4 recovery. **Story:** [DRG-627](https://linear.app/drgamtd-workspace/issue/DRG-627/s4-tooling-to-retryre-apply-a-blocked-unit). Linear read this pass: **Backlog** (wave-2 refill comment present). Related: [DRG-582](https://linear.app/drgamtd-workspace/issue/DRG-582), [DRG-623](https://linear.app/drgamtd-workspace/issue/DRG-623), [DRG-628](https://linear.app/drgamtd-workspace/issue/DRG-628).

**Base:** `main` at `0a3f0f8e7552aaf5b9fc1321c357e2fe4e32b225` (2026-11-06 read).

**GitNexus:** `query` / `impact` against this checkout returned `No indexed repositories`. Blast radius for future code is therefore **UNKNOWN** until `node .gitnexus/run.cjs analyze` (or equivalent) is run in the implementation checkout. An empty graph is not evidence that apply symbols are unused. This docs-only brief edits no product symbols.

## How a claim is marked

| Mark | Meaning |
|---|---|
| `read` | The file, Linear issue, or GitHub artifact named in the same sentence was opened this pass. |
| `Linear read` | Status/relations from Linear API this pass. Not a git fact. |
| `Adopted (first-slice)` | Already selected for the first slice (OQ5 option A / OQ6 rule). Restated for orientation; **not** re-opened here. |
| `Status: Proposed` | Recommendation or residual fork only. Not adopted by this file. Slot stays `Owner decision: ___ (drg, time)`. |

## Spec / frame authority (cite, do not treat as retry authorization)

| Artifact | Role |
|---|---|
| [`docs/plans/cpq-next-wave-readiness-20261101.md`](../../plans/cpq-next-wave-readiness-20261101.md) § DRG-627 | Names the gap: BLOCKED units return unchanged on replay; fixture resets are not operator tooling; recovery contract required before code; must preserve `#749` |
| [`specs/partial-apply.md`](specs/partial-apply.md) | **Proposed** frame. Names rebase / correcting amendment as recovery. **Not** authorization to reset signed evidence or invent a retry API |
| [`partial-apply-owner-decision.md`](partial-apply-owner-decision.md) §9 | **Adopted (first-slice):** OQ5 option A — atomic units, no silent rollback, PARTIALLY_APPLIED / APPLY_BLOCKED cut, **correcting amendment** as first-slice remedy; rebase not adopted for that slice |
| [`oq6-revision-model-owner-decision.md`](oq6-revision-model-owner-decision.md) §2 rule 2 | **Adopted:** executed documents (`EXECUTED` / apply outcomes) are **immutable**; new revision refused `409 IMMUTABLE_EXECUTED`; remedy is a new correcting amendment document |
| [`specs/apply-authorization.md`](specs/apply-authorization.md) | **Proposed** role frame (DRG-628). Options A/B/C. **Not** an accepted recovery-tool role matrix |
| [`docs/runbooks/s4-asset-lifecycle-hold.md`](../../runbooks/s4-asset-lifecycle-hold.md) | Operational hold: new apply that would mutate installed assets refuses `503 ASSET_LIFECYCLE_HOLD`; identical completed replay still works; no runtime switch |
| [`AssetLifecycleReadiness`](../../../backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/apply/AssetLifecycleReadiness.java) + `#749` | Production readiness returns `false`; releasing the hold requires a verified lifecycle code change |

## Problem

S4 can leave a document `PARTIALLY_APPLIED` (or `APPLY_BLOCKED`) with one or more units persisted `BLOCKED` and an `APPLY_UNIT_BLOCKED` audit. A later call to the existing apply route is a **replay** of terminal attempt evidence: blocked units stay blocked; applied siblings stay applied; no second block audit is written for the same terminal evidence. There is **no** supported operator tool to clear a block and re-enter the unit loop.

What looks like recovery in tests is **not** product tooling:

- `DocumentApplyPartialTest.staleSecondUnitLeavesTheFirstAppliedWithoutRollbackOrAnotherPass` asserts the orchestrator source does not contain `"retry"`, then re-invokes apply and expects the blocked unit to remain `BLOCKED` with the same reason and no new `APPLY_UNIT_BLOCKED` row (`read`).
- `DocumentApplyAssetReplayTest.clearedBlockAppliesTheBlockedUnitWithoutRepeatingTheAppliedUnit` manually sets the blocked unit back to `PENDING`, clears `blockReason` / digests / attempt evidence, bumps `observedCommercialRevision`, and forces the document status back to `EXECUTED` before applying again (`read`). That is a **test fixture** that mutates in-memory rows. Emulating those resets in operator tooling, SQL, or an admin API is **forbidden** by this brief (see Hard refusals).

The readiness plan therefore requires a **recovery policy** first: which block reasons may be retried unchanged, which require a rebase/new revision path (if any, given OQ6 immutability), and which require a correcting amendment — plus dry-run shape, evidence rules, concurrency, roles, and hold precedence — before any code story.

## Verified behaviour on this base (`read`)

| Fact | Where |
|---|---|
| Apply route is internal-only: `POST /internal/v1/amendments/{amendmentId}:apply`; `/internal/**` requires `Internal.Invoke`; not on the gateway; not rate-limited | `DocumentApplyController` class comment and `PATH` |
| `AssetLifecycleReadiness.isReady()` returns `false`; comment says no runtime override | `AssetLifecycleReadiness.java` |
| Preflight returns `ASSET_LIFECYCLE_HOLD` before the unit loop when pending asset-mutating work exists and readiness is false; HTTP `503 ASSET_LIFECYCLE_HOLD`; units stay `PENDING` | `DocumentApplyOrchestrator.classifyUnderLock` / `refuseForAssetLifecycleHold`; runbook |
| When no unit is `PENDING`, classify returns `REPLAY` (may finalize document outcome if still `EXECUTED`) | `classifyUnderLock` |
| A unit already `BLOCKED` with matching digest and attempt evidence returns `false` from `applyUnit` (no re-write) | `DocumentApplyOrchestrator.applyUnit` |
| Persisted block reasons observed in apply path include at least: `V3`, `V4`, `V9`, `CO_TERM_MISSING`, `START_DATE_MISSING`, `ACTION_NOT_IN_SLICE`, `TARGET_CONTRACT_MISMATCH`, `DIGEST_CONFLICT` | `DocumentApplyPeriods.blockReason` / `detail`; orchestrator membership + digest paths |
| DRG-623 residual: missing `subscriptions.contract_id` should become `TARGET_CONTRACT_UNSET` (accepted owner decision cited by readiness plan); code on this base still maps membership failure to `TARGET_CONTRACT_MISMATCH` only | readiness plan + `applyUnit` membership branch |
| Attempt evidence stamps `attemptRevisionNo` and `attemptPinFingerprint`; blocked units also require a non-blank `blockReason` for evidence match | `stampAttempt` / `hasAttemptEvidence` |
| Amendment document events are append-only (UPDATE/DELETE/TRUNCATE rejected) | `V1_7__contract_documents.sql` (cited by prior briefs; not re-opened) |

## Hard refusals (binding on any later code story)

These are **not** open forks. A later implementation that violates them is out of contract even if some OQ below is accepted.

1. **Do not emulate fixture resets.** Forbidden: direct SQL or admin mutation that sets `application_units.status` from `BLOCKED` → `PENDING`, nulls digests/attempt columns, rewrites `observed_commercial_revision`, or force-sets document status to `EXECUTED` to “unlock” apply. The fixture in `DocumentApplyAssetReplayTest` proves asset idempotency under laboratory conditions; it is **not** a recovery design.
2. **Do not bypass `#749` / `ASSET_LIFECYCLE_HOLD`.** Recovery dry-run and recovery commit must treat readiness refusal as authoritative. A held commit must not mutate rates, ledger, commercial revision, or assets. Dry-run may explain the hold; it must not write. Recovery tooling must not flip `AssetLifecycleReadiness`, invent a runtime override, or route around `classifyUnderLock`'s hold branch.
3. **Do not rewrite prior attempt evidence or signed commercial content.** Prior `APPLY_UNIT_BLOCKED` / `APPLY_UNIT_APPLIED` rows, digests, and pin fingerprints stay append-only history. Changed signed content is refused or follows the accepted revision/approval policy — never silent overwrite.
4. **Do not silently roll back APPLIED siblings.** OQ5 first-slice adoption: committed units stay committed.
5. **Do not invent roles, rate limits, or a public gateway route** in implementation without an accepted fork below (and DRG-628 where roles apply).
6. **Do not release** Flyway holds, asset-write hold, dual-read, Jev, or ramp enablement under this story.

## Serialization with DRG-623 (binding when code starts)

When product code begins, **DRG-623 and DRG-627 must not have parallel writers** in the same CAM apply files. One recovery/membership owner (or a strict serial handoff) owns changes to at least:

- `documents/apply/DocumentApplyOrchestrator.java`
- `documents/apply/DocumentApplyGateway.java` / `JdbcDocumentApplyGateway.java`
- `documents/apply/DocumentApplyPeriods.java`
- matching apply unit / PostgreSQL tests for those types

A second agent may own **new** recovery fixtures and review after the API contract stabilizes. DRG-623's residual (`TARGET_CONTRACT_UNSET` audit distinction) and DRG-627's recovery transition both touch membership / block-reason mapping; parallel edits risk conflicting reason codes and hold-precedence tests. This brief does not schedule either code story.

## Owner-decision table (open forks)

Every row is `Status: Proposed`. Recommended default is marked **Recommended (Proposed)**. Alternatives are listed. No Adopted cells in this table. Product ACs for a later code story stay blocked until the owner fills the slots that the story claims.

| ID | Fork | Recommended (Proposed) | Alternatives | Blocks product AC? |
|---|---|---|---|---|
| OQ1 | Per-reason recovery class: unchanged retry vs observation refresh vs correcting amendment | Taxonomy in §OQ1; default most reasons → **correcting amendment**; narrow **unchanged retry** only for externally remediable membership/data faults after dry-run GO; no in-place revision of an executed document | Allow unchanged retry for V3/V9; adopt rebase-on-executed (contradicts OQ6); one path for all reasons | Yes |
| OQ2 | Dry-run shape | Dedicated recovery dry-run returns document/unit identity, original block reason, proposed observation/pin delta (if any), expected effects, hold explanation; **writes nothing** | Reuse apply with a flag; CLI-only print; skip dry-run | Yes |
| OQ3 | Evidence immutability and audit of recovery attempts | Append-only recovery decision events; preserve prior block/apply evidence; record actor, correlation, old/new observations, reason, outcome | Mutate unit row in place without new event; allow digest rewrite | Yes |
| OQ4 | Concurrency / locking | Same document advisory lock as apply; concurrent recovery requests deterministic; conflicting observation refused | Separate lock; last-write-wins; optimistic-only | Yes |
| OQ5 | Roles and surface (CLI vs internal endpoint) | Keep recovery on **internal** surface requiring `Internal.Invoke` until DRG-628 accepts a wider matrix; rate limit and public gateway stay out | DealDesk/Admin operator UI; gateway route; unauthenticated ops CLI against prod | Yes |
| OQ6 | Hold precedence vs recovery | `#749` hold outranks recovery commit; dry-run may describe hold; commit under hold → `503 ASSET_LIFECYCLE_HOLD` with audit, no mutation | Allow recovery commit while held for non-asset units only; runtime readiness override | Yes — hold ACs |
| OQ7 | Relation to OQ5 correcting-amendment remedy and OQ6 immutability | Do not reopen OQ5/OQ6; any “retry unchanged” is a **new supported transition** beside correcting amendment, not a revision of the executed document | Reopen OQ5 to add rebase as first-slice remedy; allow `IMMUTABLE_EXECUTED` exception for recovery | Soft if OQ1 stays amendment-only; hard if retry-unchanged is chosen |
| OQ8 | Tooling scope vs real-data execution | Implementation may land behind internal-only + tests; **no** recovery runs against real customer data under the first code story without a separate owner GO | Bundle production operator runbook execution into the first code PR | Soft for merge; hard for production use |
| OQ9 | Interaction with DRG-623 `TARGET_CONTRACT_UNSET` | Recovery taxonomy treats `TARGET_CONTRACT_UNSET` (once landed) as externally remediable membership fault → dry-run then unchanged retry **only after** contract id is correctly set by authorized data repair **outside** apply; apply never infers contract | Infer contract from asset/chain during recovery (forbidden by DRG-623 accepted decision) | Yes once both stories code |

`Owner decision: ___ (drg, time)` for every row above.

---

## Fork detail

### OQ1 — Which blocked reasons retry unchanged vs observation refresh vs correcting amendment

**Status: Proposed.**

| Recovery class | Meaning | Candidate reasons (illustrative, not adopted) |
|---|---|---|
| **R1 — Unchanged retry** | Same signed unit content and same commercial observation; external precondition was fixed; supported transition re-enters apply for that unit only | After owner acceptance: narrow set such as remediable `TARGET_CONTRACT_MISMATCH` / future `TARGET_CONTRACT_UNSET` once membership data is corrected **outside** apply |
| **R2 — Observation / pin refresh** | Signed commercial fields unchanged, but observed revision or pricebook pin must be refreshed | Historically “rebase”; **conflicts with OQ6 executed immutability** if done on the same executed document. Recommended: **not** offered in-slice; use R3 |
| **R3 — Correcting amendment** | New LifecycleChange / new document (OQ5 first-slice remedy; OQ6 rule 2) | `V3`, `V4`, `V9`, `CO_TERM_MISSING`, `START_DATE_MISSING`, `ACTION_NOT_IN_SLICE`, `DIGEST_CONFLICT`, and any reason that implies content or observation change |

| Option | What the owner would be choosing |
|---|---|
| **A. Taxonomy above (Recommended, Proposed)** | Most blocks → R3. R1 only for named externally remediable faults after dry-run. R2 not in-slice on the executed document. |
| **B. R1 for V3 after operator bumps observation on the unit row** | Emulates the fixture; **rejected by Hard refusals** even if selected — would need an explicit superseding decision that also revises OQ6. |
| **C. All reasons → R3 only** | No retry tooling beyond “create correcting amendment”; DRG-627 shrinks to documentation + maybe a dry-run that only recommends R3. |
| **D. Adopt rebase-on-executed** | Would reopen OQ6; out of scope unless the owner supersedes that record with words and a time. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ2 — Dry-run shape

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Dedicated recovery dry-run (Recommended, Proposed)** | Identifies exact `documentId` / `unitNo`, original `blockReason`, whether class is R1 or R3, any proposed observation/pin change, expected rate/ledger/asset effects **if** readiness allowed commit, and hold explanation when readiness is false. Zero writes. |
| **B. Apply endpoint with `dryRun=true`** | Couples recovery to the apply route; still must not write; must not bypass hold. |
| **C. No dry-run** | Operator commits blind; rejected by readiness proposed acceptance. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ3 — Evidence immutability

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Append-only recovery audit (Recommended, Proposed)** | Prior block/apply evidence remains. Recovery records actor, correlation id, old/new observations (if any), reason class, and outcome on the document event chain (or an explicitly accepted sibling append-only store). No UPDATE of historical event rows. |
| **B. In-place clear of block columns** | Matches the fixture; **Hard refusal**. |
| **C. Delete and recreate unit row** | Destroys identity `(documentId, unitNo)`; forbidden. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ4 — Concurrency

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Same document advisory lock as apply (Recommended, Proposed)** | Recovery classify/commit shares `classifyUnderLock`'s lock family. Concurrent recovery + apply are deterministic. Changed terminal evidence → conflict (`DIGEST_CONFLICT` or successor code), not partial silent success. |
| **B. Separate recovery lock** | Risks lock-order deadlocks with apply. |
| **C. No lock; rely on row versions only** | Weaker than current apply; not recommended. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ5 — Roles and tool surface

**Status: Proposed.** Cross-reads [`specs/apply-authorization.md`](specs/apply-authorization.md) (**Proposed**, not accepted).

| Option | What the owner would be choosing |
|---|---|
| **A. Internal-only + `Internal.Invoke` (Recommended, Proposed)** | Matches today's apply controller gate until DRG-628 accepts B/C. Recovery is not on the Angular gateway. Rate limits remain undecided and must be named in the code story if the surface changes. |
| **B. DRG-628 option B person roles** | Requires accepted apply-authorization decision first. |
| **C. Break-glass Admin UI that mutates unit rows** | **Hard refusal** if it emulates fixture resets. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ6 — Hold precedence (`#749` / `ASSET_LIFECYCLE_HOLD`)

**Status: Proposed.** Restates the runbook; does not release the hold.

| Option | What the owner would be choosing |
|---|---|
| **A. Hold outranks recovery commit (Recommended, Proposed)** | Dry-run explains hold. Commit while held → audited `503 ASSET_LIFECYCLE_HOLD`, units unchanged, siblings unchanged. Identical completed replay of already-applied units remains allowed (existing behaviour). Recovery must not call a test seam that forces `isReady()==true` in deployed code. |
| **B. Allow R1 commit for units with only `NO_CHANGE` lines while held** | Today's hold already treats pure non-slice actions differently; do not widen that into asset-mutating recovery. |
| **C. Runtime flag to release readiness for recovery** | Contradicts `AssetLifecycleReadiness` design and the runbook; **out of scope**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ7 — Alignment with OQ5 / OQ6 adopted remedies

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Keep OQ5 correcting amendment + OQ6 immutability; add R1 only as a separate transition (Recommended, Proposed)** | R1 is not a document revision. R3 remains the default commercial remedy. |
| **B. Reopen OQ5 to adopt rebase as first-slice remedy** | Requires an explicit superseding owner decision; not performed by this file. |
| **C. Collapse DRG-627 to “operators must always open a correcting amendment”** | Compatible with C under OQ1; still needs dry-run/docs so operators do not use fixture-like SQL. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ8 — Real-data execution gate

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Code + tests only; no real-data recovery runs in the story (Recommended, Proposed)** | Matches readiness plan sentence: “Recovery tooling does not run against real data under this story.” |
| **B. Include a named non-prod drill** | Needs environment, tenant, restore point, and owner GO (S7-class discipline). |
| **C. Production operator execution in the same PR** | **NO-GO** without a separate execution decision. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

### OQ9 — DRG-623 `TARGET_CONTRACT_UNSET`

**Status: Proposed.**

| Option | What the owner would be choosing |
|---|---|
| **A. Serialize code; treat UNSET as R1 only after external contract population (Recommended, Proposed)** | Apply never infers contract from asset or another chain (DRG-623 accepted decision). Recovery dry-run distinguishes UNSET vs MISMATCH once DRG-623 lands. |
| **B. Parallel writers in orchestrator/gateway** | Forbidden by Serialization section. |
| **C. Infer contract during recovery** | Contradicts DRG-623; **NO-GO**. |

**Recommended (Proposed):** A. `Owner decision: ___ (drg, time)`.

---

## Proposed acceptance (only after owner acceptance)

These bullets are the readiness plan's proposed post-acceptance bar, restated for the later code story. They are **not** authorized by this file alone.

1. Dry-run identifies exact document/unit, original failure, proposed observation/pin change (if any), and expected effects; it does not write.
2. A permitted retry affects only the blocked target. APPLIED siblings and their ledger, event, and asset-history identities remain unchanged.
3. Retry preserves immutable prior attempt evidence and records actor, correlation, old/new observations, reason, and outcome append-only. It never emulates the fixture's manual row resets.
4. Repeated and concurrent requests are deterministic under the document/subscription lock; changed signed content is refused or follows the accepted revision/approval policy.
5. Foreign tenant, refused role, invalid state, and unsupported target requests have negative tests.
6. Readiness hold remains authoritative, including dry-run explanations and no mutation on held commit.
7. PostgreSQL failure injection proves rollback of one retry unit and reconciles before/after counts.
8. Recovery tooling does not run against real customer data under the first code story without a separate owner GO.
9. Later code scope states whether the tool is an internal CLI or endpoint; rate limits and roles come from accepted OQ5 (this brief) / DRG-628 — not invented in the PR.

## Holds confirmation (unchanged by this brief)

| Hold | Confirmation |
|---|---|
| S4 asset-lifecycle hold `#749` / `ASSET_LIFECYCLE_HOLD` | **Preserved.** Recovery must not bypass. Readiness stays false until a verified lifecycle release story. |
| Asset write hold (`opencrm.assets.legacy-acceptance-write-enabled`) | **Not flipped.** |
| Dual-read / NgRx / Activities holds | **Not touched.** |
| Jev enablement (`opencrm.jev.*.enabled`) | **Not enabled.** |
| Ramp (`opencrm.pricing.ramp.enabled`) | **Stays false.** |
| Flyway / schema | **No migration in this brief or its draft PR.** |
| Flag flips / gateway public apply | **None.** |
| Product code for DRG-627 | **NO-GO** until owner accepts the recovery policy. |

## What this brief does not do

- Does not mark DRG-627 Done.
- Does not merge itself by policy; draft PR only.
- Does not implement recovery, edit orchestrator/gateway/periods, or change tests except by a later authorized story.
- Does not accept the Proposed partial-apply or apply-authorization specs.
- Does not schedule DRG-623 code; only serializes shared-file ownership when that code starts.

## Verification this pass

Nothing in this pass was executed against a running stack. No Maven run, no Testcontainer, no HTTP apply call, no Flyway, no flag change.

| Check | Result |
|---|---|
| Linear DRG-627 | `read` — Backlog; related DRG-582 / DRG-623 tracking links present |
| Apply orchestrator / periods / controller / readiness | `read` on `0a3f0f8e` |
| Partial + asset replay tests | `read` |
| S4 hold runbook + asset write hold | `read` |
| Partial-apply + apply-authorization specs | `read` (Proposed) |
| OQ5 / OQ6 owner decisions | `read` (first-slice adoption / immutability) |
| Next-wave readiness plan § DRG-627 | `read` |
| GitNexus impact | Unavailable (`No indexed repositories`) → future code risk **UNKNOWN** until re-index |
