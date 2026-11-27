# DRG-903 — Write-Side Scope Analysis: Asset `SUSPENDED` Status, Transitions, and Events

**Linear:** [DRG-903](https://linear.app/drgamtd-workspace/issue/DRG-903/asset-suspended-status-transitions-and-events) (`Todo`, unassigned, parent `DRG-652`)
**Linked Notion:** [DRG-903 Workbench Task](https://app.notion.com/p/422f7cb4e4df8114af0ef5207a29d812) · [DRG-652 Epic Backlog](https://app.notion.com/p/408f7cb4e4df815eb022f371cbbb1de9) · [DRG-902 Workbench](https://app.notion.com/p/422f7cb4e4df8105bfe4e78021bd82ce) · [DRG-904 Workbench](https://app.notion.com/p/422f7cb4e4df817daa55d61016b1fad1)
**Codebase baseline:** `origin/main` (`ed6ecdbd`)
**Document scope:** Write-side scope analysis, single-source-of-truth recommendation, engineering recommendations, and open owner questions only. **No production or test code changes.** Read-side `AssetHistoryProjection.fold()` and `AssetReportService.churnForAsset()` are verified separately and kept strictly outside `DRG-903` implementation scope. All existing holds remain active.

---

## 1. Linear & Linked Notion Evidence

### Linear `DRG-903`
- **Title:** `Asset SUSPENDED status, transitions, and events`
- **Status:** `Todo` (unstarted since `2026-11-24T00:49:16Z`; 0 comments; no PRs)
- **Relations:**
  - **Blocked by:** `DRG-902` (*Durable effective-date asset lifecycle, ordered replay, and reconciliation*) — marked `Done` in Linear (`2026-11-24T15:07Z`), while its Notion workbench task still shows `In progress` and its runtime write delivery on `main` is partial (see §4.1).
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
| **Location (`main`)** | `asset/AssetLifecycleTransitions.java:7-20` | `asset/AssetChangeApply.java:20, 62-70, 76, 86-92, 112, 131, 185-187` | `asset/OrderedAssetHistoryReplay.java:207-221, 224-237` |
| **Rule encoded** | Pure 4-status matrix: `ACTIVE`/`AMENDED` → `{AMENDED, TERMINATED, SUPERSEDED}`; `SUPERSEDED`/`TERMINATED` → `false` for all targets (including self); `null` → `false`. | Private `closed(asset)` (`TERMINATED \|\| SUPERSEDED`) guards `amend`, `terminate`, and `supersede`, alongside `asset == null` missing-asset no-op (deliberate while legacy create writer stays off). `amend` also guards identical `AMENDED` field values (`:86-92`). `SUSPEND`/`RESUME` fall into empty `case` (`:66-68`). | `requireValidEffectiveOrder` sorts retained `PUBLISHED` events by `(effectiveDate, sourceOutboxId)`, flips `closed = true` on `ASSET_TERMINATED`/`ASSET_SUPERSEDED`, and rejects `ASSET_AMENDED` when `closed` (`:230-233`). Also rejects closed successor receipts (`:207-221`). |
| **Production callers (`src/main`)** | **None (0)** — verified via GitNexus `context` (`incoming.calls`: `AssetLifecycleTransitionsTest` only). | `DocumentApplyOrchestrator.setAssetChanges(...)` (`@Autowired(required = false)`, `DocumentApplyOrchestrator.java:107-110`). | **None (0)** — verified via GitNexus `context` (`incoming`: empty; `OrderedAssetHistoryReplayIntegrationTest` only). |
| **Current hold context** | Inert `DRG-902` scaffolding (`#1078`); `AssetLifecycleDueRunner` is gated on `AssetLifecycleReadiness.isReady() == false` and has 0 registered `AssetLifecycleChangeApplier` beans. | Behind S4 `ASSET_LIFECYCLE_HOLD` (`AssetLifecycleReadiness.isReady() == false`); `DocumentApplyPeriods` also blocks `ActionCode.SUSPEND`/`RESUME` as `ACTION_NOT_IN_SLICE` (`:71, :81`). | Internal capture/reconstruction service only; no operator route or Kafka consumer; never feeds `asset_history`. |
| **Behavior on illegal move from terminal state** | Returns `false` (caller decides disposition). | **Silent no-op `return`** — no exception, no commercial refusal audit, no outbox event. | **Throws `IllegalArgumentException`** (`"An amendment effective after closure requires explicit reconciliation"`) and rolls back journal capture. |
| **Duplicate / post-supersede terminal event (`TERMINATED`→`TERMINATED`, `SUPERSEDED`→`TERMINATED`)** | **Refused** (`false`). | **Silent no-op** (`return` at `:112`, `:131`). | **Not checked** (`:231` sets `closed = true` without rejecting a second terminal event; see contradiction with `AssetReportService` below). |
| **`SUSPENDED` / `SUSPEND` / `RESUME` support** | Explicitly absent (`Javadoc:5`: *"SUSPENDED is not a status in this story"*). | Silently ignored (`:66-68`); `ActionCode.SUSPEND`/`RESUME` exist in enum but are blocked upstream by `DocumentApplyPeriods:81`. | Absent; SQL (`:79-80`) and switch (`:114-120`) accept only the 4 existing v1 topics (`CREATED`, `AMENDED`, `TERMINATED`, `SUPERSEDED`). |
| **Existing test coverage** | `AssetLifecycleTransitionsTest` (2 unit tests: all 16 pairs of 4 statuses + null). | `AssetChangeApplyTest` (6 unit tests, mocked publisher/repo); `DocumentApplyAssetReplayTest` (3 unit tests); `AssetChangeOutboxAndReplayIntegrationTest` (Draft PR `#1100`, S1/S2 ITs on Postgres 16). | `OrderedAssetHistoryReplayIntegrationTest` (13 CI test runs, incl. `:193` same-day amend after terminate, `:206` backdated terminate vs future amend, `:218` amend after supersede, `:234` same-day amend before terminate). |

### Key Contradictions Surfaced by the Comparison

1. **Unused transition matrix:** `AssetLifecycleTransitions` was added in `DRG-902` (`#1078`) with full unit test coverage of the 4×4 matrix, yet **neither `AssetChangeApply` nor `OrderedAssetHistoryReplay` calls it**.
2. **Existing `AssetChangeApply` missing-asset no-op vs new refusal/audit contract:**
   - `AssetChangeApply` intentionally treats `asset == null` as a silent no-op (`AssetChangeApply.java:20, 76, 112, 131`: *"Absent assets are left alone so the legacy acceptance-time writer can stay disabled"*), and also returns silently on `closed(asset)`.
   - By contrast, `DRG-903` AC4 requires *"Commercial audit entries are written for suspend and resume decisions, including refusals."* Leaving `AssetChangeApply`'s existing missing-asset no-op untouched and scoping typed refusal + commercial audit to the new scheduled `SUSPEND`/`RESUME` path avoids breaking legacy amendment apply behavior.
3. **Journal vs churn contradiction on `TERMINATED` after `SUPERSEDED` (must be resolved before recommending a replay-journal rule):**
   - `AssetLifecycleTransitions.java:17` refuses `SUPERSEDED -> TERMINATED` (`false`), and `AssetChangeApply.java:112` no-ops `terminate()` when `status == SUPERSEDED`.
   - `OrderedAssetHistoryReplay.requireValidEffectiveOrder` (`:228-236`) does **not** reject `TERMINATED` after `SUPERSEDED` (both set `closed = true` with no `!closed` guard).
   - Meanwhile, **`AssetReportService` explicitly models and tests `TERMINATED` on or after `SUPERSEDED` for the same asset as a valid, non-churn historical pair**:
     - `AssetReportService.java:26-28`: *"A supersede is not churn. A termination on or after a supersede of the same asset is not churn."*
     - `AssetReportService.java:145-149, 194-197` (`supersedeExplainsExit`): suppresses churn when `supersededOn != null && !supersededOn.isAfter(effective)`.
     - `AssetReportServiceTest.java:145-158` (`supersedeIsNotChurnEvenWhenTheSuccessorQuantityIsLower`): explicitly applies `ASSET_SUPERSEDED` (`evt-s`) and then `ASSET_TERMINATED` (`evt-t`) to the same asset on `2026-05-01` and asserts zero churn.
   - **Consequence:** Do **not** blindly tighten `OrderedAssetHistoryReplay` to reject `TERMINATED` after `SUPERSEDED` until the owner settles whether post-supersede termination is forbidden everywhere (retiring that churn-report assumption) or tolerated as historical swap-exit evidence while forbidden on new writes (see §7, Owner Question 6).

---

## 3. Single Source of Truth (SSOT) & Write-Path Recommendations (Architect Engineering Recommendations)

The following are **engineering recommendations** for `DRG-903` write-side design. Open business/product policy questions are separated in §7 for owner (`drg`) resolution.

1. **Authoritative rule — `AssetLifecycleTransitions` with an event-kind API:**
   - Designate `AssetLifecycleTransitions` as the single authoritative domain state-machine SSOT.
   - Expand its API beyond raw `(AssetStatus from, AssetStatus to)` to an **event-kind API** (e.g., `nextStatus(AssetStatus from, AssetLifecycleChangeKind kind)` / `allowed(AssetStatus from, AssetLifecycleChangeKind kind)` returning an `Optional<AssetStatus>` or typed transition result) alongside status-pair checks. An event-kind API prevents callers from duplicating `kind -> targetStatus` switches and handles multi-event transitions (such as `SUSPEND`, `RESUME`, `AMEND`, `TERMINATE`, `SUPERSEDE`) unambiguously.
2. **Write entry point — scheduled enqueue/appliers only:**
   - Implement `DRG-903` on the durable schedule path only: enqueue `SUSPEND` / `RESUME` rows via `AssetLifecycleSchedule` and apply them via dedicated `AssetLifecycleChangeApplier` beans invoked by `AssetLifecycleDueRunner`.
   - Do **not** open `ActionCode.SUSPEND` / `ActionCode.RESUME` in `DocumentApplyPeriods` in `DRG-903`. *(Guard note for any future story that opens them in `DocumentApplyPeriods`: `DocumentApplyOrchestrator.hasPendingAssetWork:313` currently returns `false` for `SUSPEND`/`RESUME` and would have to return `true` so S4 `ASSET_LIFECYCLE_HOLD` continues to hold pending suspend/resume units.)*
3. **Typed refusal and commercial audit scoped to the new `SUSPEND`/`RESUME` path:**
   - Scope typed transition refusal and commercial refusal audit (`AssetAuditOutbox`, satisfying `DRG-903` AC4) exclusively to the new `SUSPEND`/`RESUME` scheduled path.
   - **Existing `AssetChangeApply` missing-asset no-op stays strictly outside `DRG-903` scope** (`AssetChangeApply.java:20, 76, 112, 131` exists deliberately so amendment apply does not fail while `opencrm.assets.legacy-acceptance-write-enabled` is off).
   - Replay idempotency belongs to `asset_lifecycle_changes.dedupe_key` (`AssetLifecycleSchedule.enqueue`), not to silent status-swallowing inside `SUSPEND`/`RESUME` appliers.
4. **Refusal disposition in `AssetLifecycleDueRunner` — `FAILED` blocking vs `SKIPPED` with audit (decision input for §7):**
   - **Current `FAILED` behavior (head-of-line blocking):** In `AssetLifecycleDueRunner.runDue()` (`:78-89`), any `RuntimeException` from an applier calls `change.markFailed(...)`, adds `(subscription_id, contract_line_id)` to `blockedLines`, and stages `ASSET_LIFECYCLE_FAILED`. Both `AssetLifecycleChangeRepository.lockDue()` (`:39-46`) and `countEarlierBlocking()` (`:56-66`) exclude/block **every later `PENDING` row on that same `(subscription_id, contract_line_id)`** while an earlier row is `FAILED`. Thus, throwing on an invalid `SUSPEND`/`RESUME` transition blocks later valid changes on that line (for example, a scheduled `TERMINATE`) until an operator manually clears the `FAILED` row.
   - **Alternative `SKIPPED` with commercial audit (non-blocking):** `V1_25__create_asset_lifecycle_changes.sql:39` and `AssetLifecycleChangeStatus.java:7` already allow `status = 'SKIPPED'`, and `lockDue()` / `countEarlierBlocking()` only block on `PENDING` and `FAILED` (not `SKIPPED`). Marking an illegal `SUSPEND`/`RESUME` transition `SKIPPED` (adding `markSkipped(...)` on `AssetLifecycleChange` and an explicit refusal-audit outcome in `AssetLifecycleDueRunner`) records the commercial refusal via `AssetAuditOutbox` without head-of-line blocking later valid changes on that line.
   - Because `FAILED` (halt line for operator triage) vs `SKIPPED` + audit (refuse row, keep line moving) is an operational policy choice, it is routed to Owner Question 5 (§7).
5. **Engineering recommendations on `SUSPENDED` outgoing transitions (`Q2`, `Q3`, `Q4`):**
   - **Q2 (`SUSPENDED -> TERMINATED`): Allow (Recommended).** Aligns with `Subscription.cancel()` (`Subscription.java:137-139`, which allows cancelling a `SUSPENDED` subscription directly) so customers/operators can terminate a suspended asset without an artificial resume step.
   - **Q3 (`SUSPENDED -> SUPERSEDED`): Refuse (Recommended).** Require resuming before a swap/upgrade onto a successor asset so replacement never creates ambiguity over whether the successor opens `ACTIVE` or `SUSPENDED`.
   - **Q4 (`SUSPENDED -> AMENDED`): Refuse (Recommended).** Require resuming before amending quantity, product, or term so amendments cannot silently reactivate a suspended asset or leave an asset simultaneously amended and suspended.
6. **Replay journal (`OrderedAssetHistoryReplay`) deferred on post-supersede termination:**
   - Once the owner settles post-supersede termination (§7, Owner Question 6), `OrderedAssetHistoryReplay.requireValidEffectiveOrder` should delegate to `AssetLifecycleTransitions` (or explicitly codify any owner-approved historical exception for `SUPERSEDED -> TERMINATED`).

---

## 4. Write-Side Inventory, `DRG-902` Partial Delivery, Quote Renewals & Subscription Coupling

### 4.1 `DRG-902` Effective-Date / Partial Delivery on `main`
Although Linear marks `DRG-902` `Done` (`2026-11-24T15:07Z`), Notion still lists it `In progress`, and actual runtime delivery on `main` (`ed6ecdbd`) is partial and split across two un-unified mechanisms:

1. **Effective-date `CREATE` path exists on its own table, off by default, with 0 production callers:**
   - `EffectiveAssetActivation` (`asset/EffectiveAssetActivation.java:21-153`) stages and applies `asset_activation_receipts` on effective start date and refuses to run if `opencrm.assets.legacy-acceptance-write-enabled=true` (`:176-180`).
   - Its poller, `EffectiveAssetActivationJob` (`asset/EffectiveAssetActivationJob.java:11`), is **off by default** (`@ConditionalOnProperty(name = "opencrm.assets.effective-activation.enabled", havingValue = "true")`).
   - `EffectiveAssetActivation.stage(...)` has **0 `src/main` callers** (only `EffectiveAssetActivationIntegrationTest` and `OrderedAssetHistoryReplayIntegrationTest`).
2. **Durable lifecycle schedule (`V1_25` `asset_lifecycle_changes`) has 0 appliers and 0 enqueuers:**
   - `V1_25__create_asset_lifecycle_changes.sql`, `AssetLifecycleSchedule`, `AssetLifecycleDueRunner`, `AssetLifecycleDueJob`, and `AssetLifecycleTransitions` landed in PR `#1078`.
   - **All other appliers are absent:** 0 `AssetLifecycleChangeApplier` implementations (`CREATE`, `AMEND`, `TERMINATE`, `SUPERSEDE`) exist on `main`.
   - 0 production callers call `AssetLifecycleSchedule.enqueue(...)`, and both `AssetLifecycleDueRunner` and `AssetLifecycleDueJob` return immediately while `AssetLifecycleReadiness.isReady() == false`.
3. **Reconciliation and replay journal are internal-only:**
   - `AssetReconciliationService` (PR `#1074`, read-only inventory) and `OrderedAssetHistoryReplay` have 0 production endpoints/schedulers and do not feed `asset_history`.

### 4.2 Quote Renewal Eligibility (`quote-service` Cross-Service Impact)
- In `quote-service`, `AssetRenewalDrafts` (`quote-service/.../quote/AssetRenewalDrafts.java:182, 268-271, 295-297, 312-314`) builds `FROM_ACTIVE_ASSETS` renewal quotes by reading `assets.installedBase(schedule.getCustomerId(), asOf)` (`:182`) and filtering every `InstalledAssetView` through:
  ```java
  private static boolean installedNow(String status) {
      return "ACTIVE".equals(status) || "AMENDED".equals(status);
  }
  ```
- **Consequences for `SUSPENDED`:**
  - If `installedBase(asOf)` excludes `SUSPENDED`, `AssetRenewalDrafts` never receives suspended assets (failing with `"No active assets to renew as of the term end"` at `:296` if no other assets qualify, or failing validation on an already-authored draft with `"Active assets changed; author a fresh continuation"` at `:135`).
  - Even if `installedBase(asOf)` includes `SUSPENDED`, `AssetRenewalDrafts.installedNow()` silently filters `"SUSPENDED"` out unless `quote-service` is also updated.
  - At the subscription level, `AcceptedQuoteSubscriptionService.mutate()` (`:165-166`) already **forbids `RENEWAL`** against a `SUSPENDED` subscription (`target.getStatus() == ACTIVE || (!renewal && target.getStatus() == SUSPENDED)`), while permitting non-renewal mutations.

### 4.3 Asset vs Subscription Suspension
- `Subscription` already implements `SubscriptionStatus.SUSPENDED` (`SubscriptionStatus.java:6`), `Subscription.suspend()` (`ACTIVE -> SUSPENDED`, `Subscription.java:129-134`), `Subscription.cancel()` (`PENDING | ACTIVE | SUSPENDED -> CANCELLED`, `Subscription.java:136-142`), and `POST /api/v1/subscriptions/{id}/suspend` (`SubscriptionController.java:74-78`, `SubscriptionService.java:80-86`).
- **Key gaps and asymmetries on `main`:**
  - `Subscription` has **no `resume()` method or endpoint** (`activate()` only allows `PENDING -> ACTIVE`, `Subscription.java:122-127`).
  - `SubscriptionService.suspend()` updates only the `subscriptions` row; it does **not** touch `assets` or enqueue `asset_lifecycle_changes`.
  - A subscription can own multiple contract lines / assets, so asset-level suspension and subscription-level suspension are distinct granularities. `DRG-903` must not implicitly cascade subscription suspend ↔ asset suspend (or implicitly allow/block asset resume on a suspended/cancelled subscription) without an explicit owner ruling (§7, Owner Question 4).

### 4.4 Current Schema, Enum, Contract, and Test Inventory on `main`

| Layer | Current State on `main` | Required Change for `DRG-903` Implementation (not in this doc PR) |
| --- | --- | --- |
| **`AssetStatus` enum** (`asset/AssetStatus.java:4-9`) | `ACTIVE`, `AMENDED`, `SUPERSEDED`, `TERMINATED` | Add `SUSPENDED` (AC1). |
| **`assets` table** (`V1_13__create_assets.sql:26`) | `chk_asset_status CHECK (status IN ('ACTIVE', 'AMENDED', 'SUPERSEDED', 'TERMINATED'))` | Forward migration (`V1_26+`) + guarded rollback script expanding `chk_asset_status` to include `'SUSPENDED'`. |
| **`AssetHistoryKind` enum & `asset_history`** (`AssetHistoryKind.java:4-9`, `V1_14__create_asset_history.sql:19`) | `CREATED`, `AMENDED`, `SUPERSEDED`, `TERMINATED` | Expand enum + `chk_asset_history_type` for `SUSPENDED` and `RESUMED` (if history tracks suspend/resume). |
| **`AssetLifecycleChangeKind` & `asset_lifecycle_changes`** (`AssetLifecycleChangeKind.java:4-9`, `V1_25__create_asset_lifecycle_changes.sql:37`) | `CREATE`, `AMEND`, `TERMINATE`, `SUPERSEDE`; comment explicitly states *"SUSPEND and RESUME are a later story"*. `AssetLifecycleScheduleIntegrationTest.java:285-287` asserts `"SUSPEND"` fails `chk_asset_lifecycle_kind`. | Expand enum + `chk_asset_lifecycle_kind` to include `'SUSPEND'`, `'RESUME'` and update `AssetLifecycleScheduleIntegrationTest:285-287` to test another invalid kind (e.g. `"UNKNOWN"`). |
| **`EventContractRegistry` & `KafkaTopics`** (`EventContractRegistry.java:218-235`) | `ASSET_CREATED_V1`, `ASSET_AUDIT_RECORDED_V1`, `ASSET_AMENDED_V1`, `ASSET_TERMINATED_V1`, `ASSET_SUPERSEDED_V1`. Zero suspend/resume contracts. | Register `ASSET_SUSPENDED_V1` (`cpq.asset.suspended`, `AssetSuspendedEvent`) and `ASSET_RESUMED_V1` (`cpq.asset.resumed`, `AssetResumedEvent`) **only in the implementing PR** (ADR-018 D5, AC3). |
| **Audit outbox** (`AssetAuditOutbox.java:35-55`) | Stages `ASSET_AUDIT_RECORDED_V1` for successful changes and `ASSET_LIFECYCLE_FAILED` on due-runner failure; zero audit on `AssetChangeApply` refusals. | Stage commercial audit entries for suspend/resume decisions **and refusals** on the new `SUSPEND`/`RESUME` path (AC4). |
| **Write path / appliers** (`AssetLifecycleDueRunner.java`, `AssetLifecycleChangeApplier.java`) | 0 `AssetLifecycleChangeApplier` beans; 0 enqueuers; `AssetLifecycleReadiness.isReady() == false`. | Implement scheduled `SUSPEND`/`RESUME` enqueue + `AssetLifecycleChangeApplier` beans under the active hold, with tests covering both the **held path** and the **intended path** (AC5). Keep `AssetChangeApply` missing-asset no-op outside scope. |

### 4.5 Proposed Write-Side Transition Table (Incorporating Engineering Recommendations)

| From \ Event Kind | `AMEND` | `SUSPEND` | `RESUME` | `TERMINATE` | `SUPERSEDE` |
| --- | --- | --- | --- | --- | --- |
| **`ACTIVE`** | Allow → `AMENDED` | Allow → `SUSPENDED` | Refuse | Allow → `TERMINATED` | Allow → `SUPERSEDED` |
| **`AMENDED`** | Allow → `AMENDED` | Allow → `SUSPENDED` | Refuse | Allow → `TERMINATED` | Allow → `SUPERSEDED` |
| **`SUSPENDED`** | **Refuse** *(Arch rec Q4)* | Refuse *(dedupe handles idempotency)* | Allow → **Owner Q2** (`ACTIVE` vs prior status) | **Allow → `TERMINATED`** *(Arch rec Q2)* | **Refuse** *(Arch rec Q3)* |
| **`TERMINATED`** | Refuse | Refuse | Refuse | Refuse | Refuse |
| **`SUPERSEDED`** | Refuse | Refuse | Refuse | Refuse *(see §2.3 & Owner Q6)* | Refuse |

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
   - `supersedeExplainsExit()` (`lines 194-197`, tested at `AssetReportServiceTest.java:145-158`) treats `TERMINATED` on or after `SUPERSEDED` as non-churn rather than illegal (see §2.3 & Owner Question 6).
   - Switches only on the 4 existing `AssetHistoryKind` values.
3. **Reconciliation cross-check (`AssetReconciliationService.mismatchDetail()`, `asset/AssetReconciliationService.java:267-275`):**
   - Treats only `TERMINATED` and `SUPERSEDED` as legitimately absent from `installedBase(asOf)`. If `SUSPENDED` assets are excluded from `installedBase(asOf)`, `AssetReconciliationService` will flag them as `STATE_MISMATCH` unless updated alongside the read-side follow-up.

### Explicit Scope Boundaries

- **Outside `DRG-903` — Terminal-state fix in read folds:** Fixing `AssetHistoryProjection.fold()` and `AssetReportService.churnForAsset()` belongs to its own dedicated follow-up ticket (not `DRG-903`).
- **Outside `DRG-903` — Existing `AssetChangeApply` missing-asset no-op:** Leaving absent assets untouched in `AssetChangeApply` so the legacy acceptance-time writer stays disabled is outside `DRG-903`.
- **Outside `DRG-903` — Replay wiring into `asset_history`:** Implementing `AssetLifecycleChangeApplier` beans for `CREATE`/`AMEND`/`TERMINATE`/`SUPERSEDE`, enqueuing rows from trusted producers into `asset_lifecycle_changes`, and wiring producer-ordered replay into `asset_history` remain separate prerequisites to lifting holds (`DRG-902` unfinished runtime wiring).
- **Outside `DRG-903` — Lapse/`effectiveEnd` policy decision:** Business decision for `drg` governing `fold()` and `upcomingRenewals()` semantics when `asOf > effectiveEnd`.
- **Outside `DRG-903` — UI lifecycle actions (`DRG-904`) & report screens (`DRG-905`):** Browser writes and report screens remain held under `docs/forge/42-contracts-ui-hold-exception.md:18-22`.

---

## 6. Architect Engineering Recommendations Summary

| Topic | Engineering Recommendation | Rationale |
| --- | --- | --- |
| **SSOT API** | `AssetLifecycleTransitions` with event-kind API (`(AssetStatus, AssetLifecycleChangeKind)`) | Eliminates 3 divergent rule copies and prevents `kind -> targetStatus` drift across appliers and replay. |
| **Write path** | Scheduled enqueue (`AssetLifecycleSchedule`) + `AssetLifecycleChangeApplier` beans for `SUSPEND`/`RESUME` only | Matches `DRG-902` durable schedule design; avoids widening `DocumentApplyPeriods` or disturbing S4 hold semantics in `DocumentApplyOrchestrator.hasPendingAssetWork:313`. |
| **Refusal & audit scope** | Typed refusal + `AssetAuditOutbox` scoped strictly to new `SUSPEND`/`RESUME` path; leave `AssetChangeApply` missing-asset no-op untouched | Meets `DRG-903` AC4 without breaking amendment apply while `legacy-acceptance-write-enabled` remains off. |
| **Q2 (`SUSPENDED -> TERMINATED`)** | **Allow** | Matches `Subscription.cancel()` (`Subscription.java:137-139`); avoids requiring a dummy resume before cancellation/termination. |
| **Q3 (`SUSPENDED -> SUPERSEDED`)** | **Refuse** | Avoids ambiguity on whether a replacement/upgrade successor opens `ACTIVE` or `SUSPENDED`; require explicit resume first. |
| **Q4 (`SUSPENDED -> AMENDED`)** | **Refuse** | Prevents silent reactivation or ambiguous suspended-amendment semantics; require explicit resume first. |

---

## 7. Open Owner (`drg`) Questions (Blocking `DRG-903` Implementation)

1. **Sequencing (`DRG-902` partial delivery):** `DRG-902` is marked `Done` in Linear (`In progress` in Notion), but on `main` its effective-date create path (`EffectiveAssetActivationJob`) is off by default with 0 production `stage()` callers, and the `asset_lifecycle_changes` schedule has 0 appliers (`CREATE`/`AMEND`/`TERMINATE`/`SUPERSEDE`) and 0 enqueuers. Should `DRG-903` implement `SUSPEND`/`RESUME` on the schedule now (with held-path and intended-path tests per AC5), or wait for the remaining effective-date appliers and replay wiring into `asset_history`?
2. **Resume target status:** When resuming a `SUSPENDED` asset, should `RESUME` always transition to `ACTIVE` (even if the asset was `AMENDED` before suspension), or restore the prior status (`ACTIVE` vs `AMENDED`)?
3. **Installed / renewable / churn semantics (including quote renewal eligibility):** Should a `SUSPENDED` asset be included in or excluded from:
   - `AssetHistoryProjection.installedBase(asOf)` (`GET /api/v1/assets?accountId&asOf`),
   - `AssetReportService.upcomingRenewals(...)`,
   - `quote-service` `FROM_ACTIVE_ASSETS` renewal eligibility (`AssetRenewalDrafts.installedNow()`, currently `"ACTIVE"` and `"AMENDED"` only; note `AcceptedQuoteSubscriptionService:165-166` already blocks renewals on `SUSPENDED` subscriptions),
   - `AssetReportService.churn(...)` (is suspension non-churn, with churn counted only on termination/downsize)?
   *(Also determines whether `AssetReconciliationService.mismatchDetail()` treats `SUSPENDED` as legitimately absent from `installedBase`.)*
4. **Subscription coupling:** Should asset suspension/resume be independent of `Subscription.suspend()` (`POST /api/v1/subscriptions/{id}/suspend`), or should subscription suspension cascade to child assets — and should asset `RESUME` be allowed or refused while the parent subscription is `SUSPENDED` or `CANCELLED` (noting `Subscription` currently has no `resume()` method or endpoint)?
5. **Refusal disposition in `AssetLifecycleDueRunner`:** When a scheduled `SUSPEND`/`RESUME` row is refused by `AssetLifecycleTransitions`, should the due runner mark the row **`FAILED`** (blocking later due changes on that `(subscription_id, contract_line_id)` until operator intervention, with `ASSET_LIFECYCLE_FAILED` audit) or **`SKIPPED`** with a commercial refusal audit (allowing later valid rows on the same line, e.g., `TERMINATE`, to proceed)?
6. **Post-supersede termination (journal vs churn contradiction):** `AssetLifecycleTransitions` refuses `SUPERSEDED -> TERMINATED`, `OrderedAssetHistoryReplay` does not reject it, and `AssetReportService` (`supersedeExplainsExit()` + `AssetReportServiceTest:145-158`) explicitly expects `TERMINATED` on or after `SUPERSEDED` on the same asset as non-churn. Should `TERMINATED` after `SUPERSEDED` be forbidden everywhere (retiring that churn-report expectation) or tolerated in historical replay/churn while refused on new writes?

---

## 8. Holds Preserved

- `AssetLifecycleReadiness.isReady()` remains `false` (S4 `ASSET_LIFECYCLE_HOLD` intact; `docs/runbooks/s4-asset-lifecycle-hold.md`).
- `opencrm.assets.legacy-acceptance-write-enabled` remains default off (`docs/runbooks/asset-write-hold.md`).
- `opencrm.assets.effective-activation.enabled` remains default off (`EffectiveAssetActivationJob.java:11`).
- Browser write / asset-report holds remain intact (`docs/forge/42-contracts-ui-hold-exception.md:18-22`).
- `DRG-657` and `DRG-660` remain S7-class **NO-GO** (parked).
- `approvals.legacy-local-read-enabled`, `opencrm.jev.*.enabled`, Activities API, and NgRx holds remain untouched.
