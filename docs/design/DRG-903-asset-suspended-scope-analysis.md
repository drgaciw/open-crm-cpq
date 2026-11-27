# DRG-903 — Write-Side Scope Analysis: Asset `SUSPENDED` Status, Transitions, and Events

**Linear:** [DRG-903](https://linear.app/drgamtd-workspace/issue/DRG-903/asset-suspended-status-transitions-and-events) (`Todo`, unassigned, parent `DRG-652`)
**Linked Notion:** [DRG-903 Workbench Task](https://app.notion.com/p/422f7cb4e4df8114af0ef5207a29d812) · [DRG-652 Epic Backlog](https://app.notion.com/p/408f7cb4e4df815eb022f371cbbb1de9) · [DRG-902 Workbench](https://app.notion.com/p/422f7cb4e4df8105bfe4e78021bd82ce) · [DRG-904 Workbench](https://app.notion.com/p/422f7cb4e4df817daa55d61016b1fad1)
**Codebase baseline:** `origin/main` (`ed6ecdbd`)
**Document scope:** Write-side scope analysis and single-source-of-truth recommendation only. **No production or test code changes.** Read-side `AssetHistoryProjection.fold()` and `AssetReportService.churnForAsset()` are verified separately and kept strictly outside `DRG-903` implementation scope. All existing holds remain active.

---

## 1. Linear & Linked Notion Evidence

### Linear `DRG-903`
- **Title:** `Asset SUSPENDED status, transitions, and events`
- **Status:** `Todo` (unstarted since `2026-11-24T00:49:16Z`; 0 comments; no PRs)
- **Relations:**
  - **Blocked by:** `DRG-902` (*Durable effective-date asset lifecycle, ordered replay, and reconciliation*) — marked `Done` in Linear (`2026-11-24T15:07Z`), while its Notion workbench task still shows `In progress` and its runtime write path on `main` remains inert (see §4).
  - **Blocks:** `DRG-904` (*UI lifecycle actions on `/assets` — activate, suspend, terminate, amendments; lift the `#1003` forge hold*).
  - **Related:** `DRG-655` (ChangeLine asset amend/terminate/supersede), `DRG-558` (commitment states — not duplicated), `DRG-548` (LifecycleChange intent model — not duplicated), `DRG-657` & `DRG-660` (both S7-class **NO-GO**, parked).
- **Acceptance criteria (verbatim):**
  1. `AssetStatus` includes `SUSPENDED` (and any documented resume path back to an active state).
  2. A transition table exists; every allowed transition has a test, and invalid transitions from every state are refused.
  3. Events go through the outbox and are registered only in this implementing story; event-replay test passes.
  4. Commercial audit entries are written for suspend and resume decisions, including refusals.
  5. Does not lift write holds; if writes are still held, tests cover the held path and the intended path.
  6. DoD v0.4 + asset-management AM-xx. Named tests with pass counts.

### Linked Notion Evidence
- **DRG-903 Workbench Task (`422f7cb4e4df8114af0ef5207a29d812`):** `Not started` (`Bot: Feature`): *"Owner (drg), voice, 6:45 PM CT Nov 23, put suspending assets in scope."*
- **DRG-652 Epic Backlog (`408f7cb4e4df815eb022f371cbbb1de9`):** Records owner direction (`2026-11-23 6:45 PM CT`): *"proceed with filing the missing issues and syncing to Notion; move ADR-037 to Accepted; suspending assets and lifecycle actions in the UI are in scope; DRG-657 and DRG-660 stay parked."* Standing rules recorded in the discussion thread: *"code changes ship as PRs, drg merges by PR number, holds are lifted only by a PR drg merges, and DRG-657 and DRG-660 stay parked."*
- **DRG-904 Workbench Task (`422f7cb4e4df817daa55d61016b1fad1`):** `Not started` — depends on both a real effective-date write path (`DRG-902`) and `AssetStatus.SUSPENDED` (`DRG-903`), and lifts the `#1003` UI write hold (`docs/forge/42-contracts-ui-hold-exception.md:18-22`) for those actions only.

---

## 2. Comparison of All Three Terminal / Transition Rule Implementations on `main`

Three separate places in `contract-asset-management` currently encode asset lifecycle / terminal-state rules. They do not share a common implementation and diverge in callers, failure behavior, and edge-case coverage:

| Dimension | #1 `AssetLifecycleTransitions` | #2 `AssetChangeApply` | #3 `OrderedAssetHistoryReplay` |
| --- | --- | --- | --- |
| **Location (`main`)** | `asset/AssetLifecycleTransitions.java:7-20` | `asset/AssetChangeApply.java:62-70, 76, 86-92, 112, 131, 185-187` | `asset/OrderedAssetHistoryReplay.java:207-221, 224-237` |
| **Rule encoded** | Pure 4-status matrix: `ACTIVE`/`AMENDED` → `{AMENDED, TERMINATED, SUPERSEDED}`; `SUPERSEDED`/`TERMINATED` → `false` for all targets (including self); `null` → `false`. | Private `closed(asset)` (`TERMINATED \|\| SUPERSEDED`) guards `amend`, `terminate`, and `supersede`. `amend` also guards identical `AMENDED` field values (`:86-92`). `SUSPEND`/`RESUME` fall into empty `case` (`:66-68`). | `requireValidEffectiveOrder` sorts retained `PUBLISHED` events by `(effectiveDate, sourceOutboxId)`, flips `closed = true` on `ASSET_TERMINATED`/`ASSET_SUPERSEDED`, and rejects `ASSET_AMENDED` when `closed` (`:230-233`). Also rejects closed successor receipts (`:207-221`). |
| **Production callers (`src/main`)** | **None (0)** — verified via GitNexus `context` (`incoming.calls`: `AssetLifecycleTransitionsTest` only). | `DocumentApplyOrchestrator.setAssetChanges(...)` (`@Autowired(required = false)`, `DocumentApplyOrchestrator.java:107-110`). | **None (0)** — verified via GitNexus `context` (`incoming`: empty; `OrderedAssetHistoryReplayIntegrationTest` only). |
| **Current hold context** | Inert `DRG-902` scaffolding (`#1078`); `AssetLifecycleDueRunner` is gated on `AssetLifecycleReadiness.isReady() == false` and has 0 registered `AssetLifecycleChangeApplier` beans. | Behind S4 `ASSET_LIFECYCLE_HOLD` (`AssetLifecycleReadiness.isReady() == false`); `DocumentApplyPeriods` also blocks `ActionCode.SUSPEND`/`RESUME` as `ACTION_NOT_IN_SLICE` (`:71, :81`). | Internal capture/reconstruction service only; no operator route or Kafka consumer; never feeds `asset_history`. |
| **Behavior on illegal move from terminal state** | Returns `false` (caller decides disposition). | **Silent no-op `return`** — no exception, no commercial refusal audit, no outbox event. | **Throws `IllegalArgumentException`** (`"An amendment effective after closure requires explicit reconciliation"`) and rolls back journal capture. |
| **Duplicate terminal event (`TERMINATED`→`TERMINATED`, `SUPERSEDED`→`TERMINATED`, etc.)** | **Refused** (`false`). | **Silent no-op** (`return` at `:112`, `:131`). | **Not checked** (`:231` sets `closed = true` without `require(!closed, ...)`). |
| **`SUSPENDED` / `SUSPEND` / `RESUME` support** | Explicitly absent (`Javadoc:5`: *"SUSPENDED is not a status in this story"*). | Silently ignored (`:66-68`); `ActionCode.SUSPEND`/`RESUME` exist in enum but are blocked upstream by `DocumentApplyPeriods:81`. | Absent; SQL (`:79-80`) and switch (`:114-120`) accept only the 4 existing v1 topics (`CREATED`, `AMENDED`, `TERMINATED`, `SUPERSEDED`). |
| **Existing test coverage** | `AssetLifecycleTransitionsTest` (2 unit tests: all 16 pairs of 4 statuses + null). | `AssetChangeApplyTest` (6 unit tests, mocked publisher/repo); `DocumentApplyAssetReplayTest` (3 unit tests); `AssetChangeOutboxAndReplayIntegrationTest` (Draft PR `#1100`, S1/S2 ITs on Postgres 16). | `OrderedAssetHistoryReplayIntegrationTest` (13 CI test runs, incl. `:193` same-day amend after terminate, `:206` backdated terminate vs future amend, `:218` amend after supersede, `:234` same-day amend before terminate). |

### Key Contradictions Surfaced by the Comparison
1. **Unused transition matrix:** `AssetLifecycleTransitions` was added in `DRG-902` (`#1078`) with 100% unit test coverage of the 4×4 matrix, yet **neither `AssetChangeApply` nor `OrderedAssetHistoryReplay` calls it**.
2. **Silent no-op vs explicit failure/audit:** `AssetChangeApply` swallows illegal moves on closed assets silently (writing **zero** refusal audit), whereas:
   - `DRG-903` AC4 explicitly requires *"Commercial audit entries are written for suspend and resume decisions, including refusals."*
   - `AssetLifecycleDueRunner.runDue()` (`AssetLifecycleDueRunner.java:81-89`) catches `RuntimeException` from `AssetLifecycleChangeApplier.apply(...)` to mark the scheduled row `FAILED`, block subsequent rows on that line, and stage an `ASSET_LIFECYCLE_FAILED` commercial audit row. Silent returns would mark illegal scheduled moves `APPLIED` instead of `FAILED`.
3. **Duplicate-terminal blind spot in `OrderedAssetHistoryReplay`:** `requireValidEffectiveOrder` (`:228-236`) blocks `AMENDED` after closure, but allows `TERMINATED` followed by `TERMINATED` or `SUPERSEDED` without failing capture.

---

## 3. Single Source of Truth (SSOT) Recommendation (for Architect / Jev Review)

**Recommendation: Designate `AssetLifecycleTransitions` as the single authoritative domain state-machine SSOT**, with strict separation between **transition legality** and **caller disposition**:

1. **Authoritative rule (`AssetLifecycleTransitions`):**
   - Owns the canonical transition matrix over `AssetStatus` (expanded for `SUSPENDED` in `DRG-903`).
   - Provides both status-to-status legality (`allowed(AssetStatus from, AssetStatus to)`) and transition-event/kind mapping (`targetStatus(AssetStatus from, AssetLifecycleChangeKind / AssetHistoryKind)` or `apply(from, kind) -> Optional/Result`) so callers cannot drift on which event maps to which target status.
   - Enforces unconditionally that terminal states (`TERMINATED`, `SUPERSEDED`) refuse every outgoing move, including self-transitions and `SUSPEND`/`RESUME`.
2. **Write-side consumers (in `DRG-903` + replay-wiring follow-up):**
   - All future `AssetLifecycleChangeApplier` implementations (including `SUSPEND`/`RESUME` and the replay-wiring appliers for `AMEND`/`TERMINATE`/`SUPERSEDE`) and `AssetChangeApply` (if retained or unified behind appliers) delegate legality checks exclusively to `AssetLifecycleTransitions` rather than duplicating `closed(asset)`.
   - **Idempotency vs illegal transition:** Replay deduplication belongs to the idempotency layer (`document_apply_ledger` digests and `asset_lifecycle_changes.dedupe_key`), **not** to silent swallowing inside domain mutation methods. An illegal transition reaching the write layer must produce a typed refusal and stage a commercial audit entry (`AssetAuditOutbox`, satisfying `DRG-903` AC4 and `AssetLifecycleDueRunner`'s `FAILED` contract).
3. **Replay-journal consumer (`OrderedAssetHistoryReplay`):**
   - `OrderedAssetHistoryReplay.requireValidEffectiveOrder` delegates its effective-order state walk to `AssetLifecycleTransitions` (starting from `ACTIVE` on `ASSET_CREATED`), which automatically preserves all existing closure tests and closes the duplicate-terminal blind spot (`TERMINATED`→`TERMINATED`/`SUPERSEDED`) while extending cleanly to `SUSPENDED`/`RESUMED` once registered.
4. **Read-side consumers (OUTSIDE `DRG-903` implementation scope):**
   - The separate **terminal-state fix in read folds** follow-up can reuse the same `AssetLifecycleTransitions` helper in `AssetHistoryProjection.fold()` and `AssetReportService.churnForAsset()`, eliminating all copy-pasted terminal rules across write, journal, and read paths.

---

## 4. Write-Side `SUSPENDED` / Terminal Coverage, Proposed Behavior & Dependencies

### Current Schema, Enum, Contract, and Test Inventory on `main`

| Layer | Current State on `main` | Required Change for `DRG-903` Implementation (not in this doc PR) |
| --- | --- | --- |
| **`AssetStatus` enum** (`asset/AssetStatus.java:4-9`) | `ACTIVE`, `AMENDED`, `SUPERSEDED`, `TERMINATED` | Add `SUSPENDED` (AC1). |
| **`assets` table** (`V1_13__create_assets.sql:26`) | `chk_asset_status CHECK (status IN ('ACTIVE', 'AMENDED', 'SUPERSEDED', 'TERMINATED'))` | Forward migration (`V1_26+`) + guarded rollback script expanding `chk_asset_status` to include `'SUSPENDED'`. |
| **`AssetHistoryKind` enum & `asset_history`** (`AssetHistoryKind.java:4-9`, `V1_14__create_asset_history.sql:19`) | `CREATED`, `AMENDED`, `SUPERSEDED`, `TERMINATED` | Expand enum + `chk_asset_history_type` for `SUSPENDED` and `RESUMED` (if history tracks suspend/resume). |
| **`AssetLifecycleChangeKind` & `asset_lifecycle_changes`** (`AssetLifecycleChangeKind.java:4-9`, `V1_25__create_asset_lifecycle_changes.sql:37`) | `CREATE`, `AMEND`, `TERMINATE`, `SUPERSEDE`; comment explicitly states *"SUSPEND and RESUME are a later story"*. `AssetLifecycleScheduleIntegrationTest.java:285-287` explicitly asserts `"SUSPEND"` fails the `CHECK` constraint. | Expand enum + `chk_asset_lifecycle_kind` to include `'SUSPEND'`, `'RESUME'` and update `AssetLifecycleScheduleIntegrationTest:285-287` to test another invalid kind (e.g. `"UNKNOWN"`). |
| **`EventContractRegistry` & `KafkaTopics`** (`EventContractRegistry.java:218-235`) | `ASSET_CREATED_V1`, `ASSET_AUDIT_RECORDED_V1`, `ASSET_AMENDED_V1`, `ASSET_TERMINATED_V1`, `ASSET_SUPERSEDED_V1`. Zero suspend/resume contracts. | Register `ASSET_SUSPENDED_V1` (`cpq.asset.suspended`, `AssetSuspendedEvent`) and `ASSET_RESUMED_V1` (`cpq.asset.resumed`, `AssetResumedEvent`) **only in the implementing PR** (ADR-018 D5, AC3). |
| **Audit outbox** (`AssetAuditOutbox.java:35-55`) | Stages `ASSET_AUDIT_RECORDED_V1` for successful changes and `ASSET_LIFECYCLE_FAILED` on due-runner failure; zero audit on `AssetChangeApply` refusals. | Stage commercial audit entries for suspend/resume decisions **and refusals** (AC4). |
| **Amendment apply slice** (`DocumentApplyPeriods.java:71,81`; `DocumentApplyOrchestrator.java:313`) | `ActionCode.SUSPEND` and `ActionCode.RESUME` exist in `ActionCode.java:14-15`, but `DocumentApplyPeriods` blocks them as `ACTION_NOT_IN_SLICE` and `DocumentApplyOrchestrator.hasPendingAssetWork` returns `false` for them (`:313`). | **Critical subtlety:** If `ActionCode.SUSPEND`/`RESUME` are ever admitted to `DocumentApplyPeriods`, `hasPendingAssetWork` (`:313`) MUST also be updated to return `true` so S4 `ASSET_LIFECYCLE_HOLD` continues to hold pending suspend/resume units! |
| **Write path / appliers** (`AssetLifecycleDueRunner.java`, `AssetLifecycleChangeApplier.java`) | Zero `AssetLifecycleChangeApplier` beans exist on `main`; nothing enqueues into `asset_lifecycle_changes`; `AssetLifecycleReadiness.isReady()` is hardcoded `false`. | Implement `SUSPEND`/`RESUME` write-path appliers and/or command service under the active hold, with tests covering both the **held path** and the **intended path** (AC5). |

### Proposed Transition Table for Write-Side Review (Draft for Owner / Architect Confirmation)

| From \ To | `ACTIVE` | `AMENDED` | `SUSPENDED` | `TERMINATED` | `SUPERSEDED` |
| --- | --- | --- | --- | --- | --- |
| **`ACTIVE`** | Refuse | Allow (`AMEND`) | Allow (`SUSPEND`) | Allow (`TERMINATE`) | Allow (`SUPERSEDE`) |
| **`AMENDED`** | Refuse | Allow (`AMEND`) | Allow (`SUSPEND`) | Allow (`TERMINATE`) | Allow (`SUPERSEDE`) |
| **`SUSPENDED`** | Allow (`RESUME`) *(see Q1)* | **Decision Q1/Q4** | Refuse *(idempotency via dedupe key)* | Allow (`TERMINATE`) *(see Q2)* | **Decision Q3** |
| **`TERMINATED`** | Refuse | Refuse | Refuse | Refuse | Refuse |
| **`SUPERSEDED`** | Refuse | Refuse | Refuse | Refuse | Refuse |

---

## 5. Boundary with Read-Side Terminal Integrity and Replay Wiring (Outside `DRG-903` Scope)

### Separate Verification of Read-Side Folds on `main` (Verified, Outside `DRG-903` Implementation Scope)

Both read-side folds were independently inspected on `origin/main` (`ed6ecdbd`) and confirmed to lack terminal-state enforcement:

1. **`AssetHistoryProjection.fold()` (`asset/AssetHistoryProjection.java:311-372`):**
   - `case AMENDED` (`lines 339-340`) unconditionally sets `installed = true` without checking whether an earlier `TERMINATED` or `SUPERSEDED` row already closed the asset. A late or mis-ordered amendment resurrects a closed asset in `GET /api/v1/assets?accountId&asOf`.
   - `fold()` records `effectiveEnd` (`lines 332, 348`) but does not exclude expired assets (`asOf.isAfter(end)`) — governed separately by the open **lapse/`effectiveEnd` policy decision**.
   - Switches only on the 4 existing `AssetHistoryKind` values (`CREATED`, `AMENDED`, `SUPERSEDED`, `TERMINATED`).
2. **`AssetReportService.churnForAsset()` (`asset/AssetReportService.java:115-156`):**
   - `case TERMINATED` (`lines 145-149`) emits a `TERMINATED` churn event without setting a `terminated` guard or zeroing `quantity`. A duplicate `TERMINATED` row double-counts full churn, and a late `AMENDED` row after `TERMINATED` emits a phantom `DOWNSIZED` churn event against the pre-termination quantity.
   - `supersededBefore()` (`lines 190-192`) compares `supersededOn.isBefore(effective)` strictly, ignoring same-day `appliedOrder`.
   - Switches only on the 4 existing `AssetHistoryKind` values.

### Explicit Scope Boundaries

- **Outside `DRG-903` — Terminal-state fix in read folds:** Fixing `AssetHistoryProjection.fold()` and `AssetReportService.churnForAsset()` belongs to its own dedicated follow-up ticket (not `DRG-903`). That follow-up can consume `AssetLifecycleTransitions` once designated as SSOT.
- **Outside `DRG-903` — Replay wiring into `asset_history`:** Implementing `AssetLifecycleChangeApplier` beans for `CREATE`/`AMEND`/`TERMINATE`/`SUPERSEDE`, enqueuing rows from trusted producers into `asset_lifecycle_changes`, and wiring producer-ordered replay into `asset_history` (retiring `JpaAssetHistoryStore`'s `maxAppliedOrder() + 1` race) remain separate prerequisites to lifting holds (`DRG-902` unfinished runtime wiring).
- **Outside `DRG-903` — Lapse/`effectiveEnd` policy decision:** Business decision for `drg` governing `fold()` and `upcomingRenewals()` semantics when `asOf > effectiveEnd`.
- **Outside `DRG-903` — UI lifecycle actions (`DRG-904`) & report screens (`DRG-905`):** Browser writes and report screens remain held under `docs/forge/42-contracts-ui-hold-exception.md:18-22`.

---

## 6. Concrete Blockers & Decisions Needed Before `DRG-903` Implementation

| # | Blocker / Decision | Owner | Why It Blocks Implementation |
| --- | --- | --- | --- |
| **B1** | **Inert `DRG-902` write path (status drift)** | `drg` / Architect | Linear marks `DRG-902` `Done`, clearing `DRG-903`'s tracker blocker, yet `main` has 0 `AssetLifecycleChangeApplier` implementations, 0 enqueuers into `asset_lifecycle_changes`, and `AssetLifecycleReadiness.isReady() == false`. `DRG-903` says *"Depends on the durable effective-date lifecycle story for a real write path"*. Decide whether `DRG-903` ships against `AssetLifecycleChangeApplier` + `AssetLifecycleSchedule` (held-path + intended-path tests per AC5) or waits for replay wiring into `asset_history`. |
| **B2** | **SSOT adoption & refusal-audit semantics** | Architect / Jev | Confirm `AssetLifecycleTransitions` as SSOT and confirm whether illegal write-side transitions throw a typed refusal + stage commercial audit (`AssetAuditOutbox`, matching AC4 and `AssetLifecycleDueRunner`) rather than `AssetChangeApply`'s silent no-op return. |
| **B3 (Q1–Q4)** | **Exact `SUSPENDED` transition rules** | `drg` / Architect | Confirm: **Q1** does `RESUME` always transition `SUSPENDED -> ACTIVE` (even if the asset was `AMENDED` before suspension) or restore prior status? **Q2** is `SUSPENDED -> TERMINATED` allowed (recommended: yes, matches `Subscription.java:138-139`)? **Q3** is `SUSPENDED -> SUPERSEDED` allowed or refused? **Q4** is `SUSPENDED -> AMENDED` allowed or refused? |
| **B4 (Q5)** | **Write entry point & `ActionCode.SUSPEND`/`RESUME` boundary** | Architect / `drg` | Confirm whether `DRG-903` adds a scheduled/service command path (for `DRG-904` UI actions) or also opens `ActionCode.SUSPEND`/`RESUME` in `DocumentApplyPeriods` (which requires updating `DocumentApplyOrchestrator.hasPendingAssetWork:313` so the S4 hold stays intact). |
| **B5 (Q6)** | **Read-side semantics of `SUSPENDED` (cross-boundary check)** | `drg` | Even though read-fold fixes stay outside `DRG-903`, adding `SUSPENDED`/`RESUMED` to `AssetHistoryKind` requires knowing whether a `SUSPENDED` asset is included or excluded from `installedBase(asOf)`, `upcomingRenewals`, and `churn` so `AssetHistoryProjection` / `AssetReportService` switches remain exhaustive without silent misreporting. |

---

## 7. Holds Preserved

- `AssetLifecycleReadiness.isReady()` remains `false` (S4 `ASSET_LIFECYCLE_HOLD` intact; `docs/runbooks/s4-asset-lifecycle-hold.md`).
- `opencrm.assets.legacy-acceptance-write-enabled` remains default off (`docs/runbooks/asset-write-hold.md`).
- Browser write / asset-report holds remain intact (`docs/forge/42-contracts-ui-hold-exception.md:18-22`).
- `DRG-657` and `DRG-660` remain S7-class **NO-GO** (parked).
- `approvals.legacy-local-read-enabled`, `opencrm.jev.*.enabled`, Activities API, and NgRx holds remain untouched.
