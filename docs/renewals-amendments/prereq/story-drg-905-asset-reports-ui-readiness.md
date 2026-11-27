# DRG-905 Readiness Assessment: Installed-Base Report Screens (Upcoming Renewals & Asset Churn)

- **Linear issue:** [DRG-905](https://linear.app/drgamtd-workspace/issue/DRG-905/installed-base-report-screens-upcoming-renewals-and-asset-churn) (`Backlog`, labels `Open-CRM-CPQ`, `area:ui`, parent `DRG-652`, 0 comments)
- **Notion workbench task:** [DRG-905 workbench task](https://app.notion.com/p/422f7cb4e4df81eebf7ed541be005063) (`Not started`, last edited `2026-11-24T00:49:45Z`, 1-sentence tracking stub)
- **Evaluated against:** `origin/main` `ed6ecdbd172670b9c7aeb4421e0d7e9269c021e4` (read-only assessment; zero implementation or UI scaffolding)
- **Related issues:** `DRG-659` (Done, PR `#740`), `DRG-761` (Done, PR `#1003`), `DRG-902` (Done, PRs `#1074`/`#1078`), `DRG-906` (Done partial, PR `#1077`), `DRG-926` (Draft PR `#1101` complete and distinct), `DRG-657` & `DRG-660` (parked, S7 NO-GO)

---

## 1. Gate 0 — Explicit Asset-Reports UI Hold (Blocks All Implementation & Scaffolding)

**Asset report screens are explicitly held on `main` today.** No UI implementation — including Angular components, services, routes, or sidebar entries — is authorized until `drg` merges an explicit hold-lift PR.

- [`docs/forge/42-contracts-ui-hold-exception.md`](../../forge/42-contracts-ui-hold-exception.md) (line 21, `## Still held`):
  > "Amendments, asset history and supersede-chain views, and **asset reports**."
- [`AGENT.md`](../../../AGENT.md) (line 4):
  > "Second narrow exception (owner, 2026-11-15, DRG-761; `42-contracts-ui-hold-exception.md`): **read-only** Contracts screens (`/contracts`, `/contracts/:id` over `GET /api/v1/subscriptions`) and an Assets screen (`/assets` over `GET /api/v1/assets`, which shows the `InstalledBase.Read` access state). Subscription lifecycle actions (activate, suspend, cancel), amendments and **asset reports stay held**."
- **Linear & Notion authorization gap:**
  - `DRG-905` contains **no hold-lift wording** (unlike `DRG-904`, which scopes its hold lift to lifecycle actions only).
  - The recorded owner voice direction on the [DRG-652 Notion epic](https://app.notion.com/p/408f7cb4e4df815eb022f371cbbb1de9) (`2026-11-23 6:45 PM CT`, comment 1) states: *"proceed with filing the missing issues and syncing to Notion; move ADR-037 to Accepted; suspending assets and lifecycle actions in the UI are in scope; DRG-657 and DRG-660 stay parked"* and *"holds are lifted only by a PR drg merges."* It does **not** lift the asset-reports hold.
  - The [DRG-905 Notion workbench page](https://app.notion.com/p/422f7cb4e4df81eebf7ed541be005063) contains only one sentence (*"Workbench tracking for Linear DRG-905 (parent DRG-652). Follow-up UI for DRG-659 APIs and DRG-761 lists."*) and has no UI spec, lapse-policy notes, terminal-state notes, or open questions.

---

## 2. Three Explicit Backend & Domain Dependencies

Even after a hold-lift PR is merged by `drg`, three concrete backend/domain dependencies on `origin/main` (`ed6ecdbd`) govern what the report APIs actually compute and whether live data exists:

### Dependency 1 — `effectiveEnd` / Lapse Owner Decision
- **What `main` does today:**
  - `AssetHistoryProjection.fold()` (`backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/AssetHistoryProjection.java:311-372`) filters facts solely by `row.getEffectiveDate().isAfter(asOf)` (line 323) and toggles `installed = true` on `CREATED`/`AMENDED` and `installed = false` on `SUPERSEDED`/`TERMINATED` (lines 329–355). It **never checks `effectiveEnd < asOf`**.
  - `AssetReportService.upcomingRenewals()` (`AssetReportService.java:56-62`) queries `history.installedBase(accountId, window.end())` and filters `endsInWindow(asset, window)` (`effectiveEnd` in `[windowStart, windowEnd]`). Because `fold()` does not drop assets with `effectiveEnd < asOf`, assets ending between `windowStart` and `windowEnd - 1` still appear when queried at `window.end()`. Any future lapse filter added to `fold()` without updating `upcomingRenewals()` would silently drop those renewals.
  - `AssetReportService.churn()` (`AssetReportService.java:72-156`) and `AssetChurnKind` (`TERMINATED`, `DOWNSIZED`) do **not** model non-renewal / term-end lapse. Neither `AssetStatus` (`ACTIVE`, `AMENDED`, `SUPERSEDED`, `TERMINATED`) nor `AssetHistoryKind` (`CREATED`, `AMENDED`, `SUPERSEDED`, `TERMINATED`) has an `EXPIRED` or `LAPSED` state, and no lapse scheduler exists.
- **Owner decision required:** Does an asset whose `effectiveEnd < asOf` automatically lapse out of the installed base (and/or count as churn), or does it remain installed until explicitly terminated/renewed? UI copy, badges, window semantics, and churn totals cannot assume lapse semantics until `drg` decides.

### Dependency 2 — Replay Wiring into the `asset_history` Projection (and Active Write Holds)
- **What `main` does today:**
  - Both report endpoints (`AssetReportService.java:57,76`) read exclusively from `AssetHistoryProjection` -> `AssetHistoryStore` (`JpaAssetHistoryStore` -> `asset_history` table, `V1_14__create_asset_history.sql`).
  - `OrderedAssetHistoryReplay` (`OrderedAssetHistoryReplay.java:88-111`, [`docs/runbooks/ordered-asset-history-replay.md`](../../runbooks/ordered-asset-history-replay.md) lines 31–32) captures verified manifests into the **separate table** `asset_history_replay_events` (`V1_24`), explicitly documenting *"This service writes the journal only. The installed-base projection stays under the existing CAM write path."* It does **not** populate `asset_history`, and has no controller, CLI, or Kafka consumer entry point in `src/main/`.
  - Every `src/main/` writer into `AssetHistoryProjection.apply(...)` is currently disabled or held:
    1. `EffectiveAssetActivation.applyNext()` (`EffectiveAssetActivation.java:144`): `EffectiveAssetActivationJob` is off by default (`@ConditionalOnProperty(name = "opencrm.assets.effective-activation.enabled", havingValue = "true")`, `EffectiveAssetActivationJob.java:11`) and has no trusted producer calling `stage()` (`docs/runbooks/effective-asset-activation.md:18-21`).
    2. `AssetChangeApply.onUnitApplied()` (`AssetChangeApply.java:105,124,155,165`): held behind `AssetLifecycleReadiness.isReady() == false` (`AssetLifecycleReadiness.java:12-14`, `docs/runbooks/s4-asset-lifecycle-hold.md`).
    3. Legacy `AssetActivation.onActivated()` (`AssetActivation.java:20`): held off (`opencrm.assets.legacy-acceptance-write-enabled=false`) and never calls `history.apply(...)`.
    4. `AssetLifecycleDueRunner` (`AssetLifecycleDueRunner.java:66-68`): gated by `AssetLifecycleReadiness.isReady() == false`, with zero `AssetLifecycleChangeApplier` implementations registered (`AssetLifecycleChangeApplier.java:4-6`).
    5. Zero `@KafkaListener` exists in CAM to consume `AssetCreated`, `AssetAmended`, `AssetTerminated`, or `AssetSuperseded` into `asset_history`.
  - Additionally, `DRG-657` (installed-base migration) and `DRG-660` (lineage backfill) remain S7 NO-GO.
- **Impact:** Until replay wiring into `asset_history` and the upstream asset write/S4 holds are resolved, live environments have no ongoing feed populating `asset_history`, so report screens will return empty against live data.

### Dependency 3 — Terminal-State Integrity in the Read Folds (`AssetHistoryProjection` & `AssetReportService`)
- **What `main` does today:**
  - Write/replay validators treat `TERMINATED` and `SUPERSEDED` as terminal: `AssetLifecycleTransitions.allowed()` (`AssetLifecycleTransitions.java:13-18`) refuses all transitions out of `SUPERSEDED` and `TERMINATED`, and `OrderedAssetHistoryReplay.requireValidEffectiveOrder()` (`OrderedAssetHistoryReplay.java:224-237`) rejects any `ASSET_AMENDED` effective after closure.
  - **Read folds do not enforce that invariant:**
    1. `AssetHistoryProjection.fold()` (`AssetHistoryProjection.java:328-356`): if an `AMENDED` entry sorts after `TERMINATED` or `SUPERSEDED` (by `effectiveDate`, `appliedOrder`), `case AMENDED` sets `installed = true; status = AssetStatus.AMENDED;` — **resurrecting a closed asset** into `installedBase()` and `upcomingRenewals()`.
    2. `AssetReportService.churnForAsset()` (`AssetReportService.java:136-153`): checks `supersededBefore(...)` and `supersedeExplainsExit(...)` for `SUPERSEDED`, **but has no equivalent guard for `TERMINATED`** — a post-termination downsize `AMENDED` still emits a `DOWNSIZED` churn event, and a duplicate `TERMINATED` event double-counts `quantityChurned` and `eventCount`.
- **Impact:** Until read folds enforce terminal-state finality, out-of-order or un-reconciled `asset_history` rows can produce resurrected renewals or inflated churn figures.

---

## 3. Linear DRG-905 Deliverable & AC Mapping Against `origin/main` (`ed6ecdbd`)

| DRG-905 Deliverable / AC | Current `main` State (Verified) | Readiness Verdict | Blocking Dependencies |
| --- | --- | --- | --- |
| **AC-1:** A user with `InstalledBase.Read` can open upcoming-renewals and churn reports for an account and window, with empty and unauthorized states. | Backend endpoints exist (`AssetReportController.java:35,46`), enforce `InstalledBase.Read` + delegated `scp` (`403`) and tenant `tid` (`#1077`), and are routed by `gateway-service` (`/api/v1/assets/**`, `application.yml:272,682`). **Frontend:** `ContractService` has no report methods; `contracts.routes.ts` / `app.routes.ts` / `nav-items.ts` have no report routes, screens, or nav entries. | **Held / Not Started** | **Gate 0** (asset-reports FE hold). Account-level isolation (`DRG-906 AC2` / `drg` Q11) is also still open — any `InstalledBase.Read` user can query any same-tenant `accountId`. |
| **AC-2:** Figures reconcile with the asset OHS / report APIs already on `main` (`AssetReportController`). | Backend reconciliation test passes in unit CI (`AssetReportReconciliationTest` 1/0/0, `AssetReportServiceTest` 12/0/0, `AssetReportControllerTest` 3/0/0). Frontend shared models (`UpcomingRenewalsReport`, `AssetChurnReport`, `AssetChurnEventDto`, `InstalledAssetDto`) already exist and pass field-parity tests (`backend-fe-contract-parity.spec.ts:100-185`). | **Contract Ready; Semantics & Live Data Blocked** | **Gate 0** for UI wiring; **Dep 1** (lapse semantics), **Dep 2** (replay wiring into `asset_history`), **Dep 3** (terminal-state read-fold integrity) for live-data reconciliation beyond synthetic fixtures. |
| **AC-3:** No new write path; this is a read UI. | Both backend endpoints are `@Transactional(readOnly = true)` `GET` routes; `ContractService` is read-only. | **Ready (policy-compliant once Gate 0 lifts)** | **Gate 0** (hold currently blocks even read-only report screens). |
| **AC-4:** Component and a11y checks. DoD v0.4 + asset-management AM-xx. | No report components or specs exist. Existing `@frontend/cpq-contracts` specs (`asset-list` 7, `contract-detail` 10, `contract-list` 8, `contract.service` 5, `contracts.routes` 1) and `@frontend/shared-ui` (`DataTableComponent`, `PageHeaderComponent`, `EmptyStateComponent`, `StatusBadgeComponent`) establish the Vitest + a11y pattern. | **Not Started** | **Gate 0** (no scaffolding until hold lift). |

---

## 4. Verified API & Frontend Inventory on `origin/main` (`ed6ecdbd`)

### 4.1 Backend Report & OHS APIs (`contract-asset-management`)

| Endpoint | Controller / Service | Query Parameters & Validation | Response Payload (`ApiResponse<T>`) | Auth & Scope |
| --- | --- | --- | --- | --- |
| `GET /api/v1/assets/reports/upcoming-renewals` | `AssetReportController.upcomingRenewals` (`:35-44`) -> `AssetReportService.upcomingRenewals` (`:51-70`) | `accountId` (required, non-blank); **either** `windowStart` + `windowEnd` (inclusive, `start <= end`) **or** `withinDays` (`>= 0`, optional `asOf`, defaults to UTC today). Invalid/mixed bounds -> `400 VALIDATION_REJECTED`. | `UpcomingRenewalsReport`: `{ accountId, windowStart, windowEnd, assetCount, assets: InstalledAssetDto[] }` (sorted by `effectiveEnd`, then `assetId`) | `401` unauth (`jwt`); `403` missing `InstalledBase.Read` or non-delegated JWT (and anonymous in `dev`); tenant-scoped by JWT `tid` (`#1077`), empty on cross-tenant or invalid `tid`. |
| `GET /api/v1/assets/reports/churn` | `AssetReportController.churn` (`:46-53`) -> `AssetReportService.churn` (`:72-92`) | `accountId` (required, non-blank), `periodStart` (required `ISO.DATE`), `periodEnd` (required `ISO.DATE`, `start <= end`). Invalid bounds -> `400 VALIDATION_REJECTED`. | `AssetChurnReport`: `{ accountId, periodStart, periodEnd, assetCount, eventCount, quantityChurned, events: AssetChurnEventDto[] }` (sorted by `effectiveDate`, then `assetId`; `churnKind` in `TERMINATED \| DOWNSIZED`; supersedes excluded) | Same auth & tenant-scoping contract. |
| `GET /api/v1/assets?accountId&asOf` *(OHS companion)* | `AssetQueryController.installedBase` (`:42-48`) -> `AssetHistoryProjection.installedBase` (`:208-228`) | `accountId`, `asOf` (`ISO.DATE`) | `InstalledAssetDto[]` | Same auth & tenant-scoping contract. |

- **Gateway routing:** `backend/services/gateway-service/src/main/resources/application.yml:272,682` already maps `/api/v1/assets/**` to `contract-asset-management` with the gateway `RequestRateLimiter`.

### 4.2 Frontend Models, Services & Components (Verified Directly on `main`)

| Layer | File on `origin/main` (`ed6ecdbd`) | Verified Current State | Exact Gaps for DRG-905 |
| --- | --- | --- | --- |
| **Shared models** | `frontend/libs/shared/models/src/lib/asset.model.ts` (re-exported by `src/index.ts:18`) | Already defines `AssetStatus`, `ASSET_STATUSES`, `ASSET_STATUS_LABELS`, `AssetChurnKind` (`'TERMINATED' \| 'DOWNSIZED'`), `ASSET_CHURN_KINDS`, `isAssetChurnKind`, `AssetDto`, `InstalledAssetDto`, `AssetHistoryEntryDto`, `UpcomingRenewalsReport`, `AssetChurnEventDto`, `AssetChurnReport`. | Missing `ASSET_CHURN_KIND_LABELS` display map (unlike `ASSET_STATUS_LABELS`). No `LAPSED`/`EXPIRED` type (blocked by Dep 1). |
| **Parity tests** | `frontend/libs/shared/models/src/lib/backend-fe-contract-parity.spec.ts:73-185` | Actively asserts field parity against Java sources for `AssetDto`, `InstalledAssetDto`, `AssetHistoryEntryDto`, `UpcomingRenewalsReport`, `AssetChurnReport`, `AssetChurnEventDto`, and `ASSET_CHURN_KINDS`. | Parity already passing for current backend shapes; will need updating if Dep 1 adds new fields/enum values. |
| **HTTP envelope** | `frontend/apps/crm/src/app/app.config.ts:38-45`, `api-response.model.ts:1-15` | `apiResponseInterceptor` unwraps `ApiResponse<T>` so services receive `T` directly; `403` passes through as `HttpErrorResponse` handled by `isAccessDenied(err)` (`contract.service.ts:7-9`). | Ready for report GET calls; `400 VALIDATION_REJECTED` error surfacing should reuse `ApiError` / `HttpErrorResponse` cleanly. |
| **Data service** | `frontend/libs/cpq/contracts/src/lib/contract.service.ts` (`:16-37`) | Only calls `GET /api/v1/subscriptions`, `GET /api/v1/subscriptions/{id}`, and `GET /api/v1/assets` (`?subscriptionId=`). | Zero client methods for `/api/v1/assets/reports/upcoming-renewals`, `/api/v1/assets/reports/churn`, or `/api/v1/assets?accountId&asOf`. |
| **Routes & nav** | `contracts.routes.ts`, `app.routes.ts:51-59`, `nav-items.ts:83-97` | Only `/contracts`, `/contracts/:id`, and `/assets` exist. `frontend/libs/crm/organizations/` has zero links to assets, contracts, or reports. | Zero report routes, nav links, or account-context entry points (held by Gate 0). |
| **Existing screens** | `asset-list.component.ts` (7 specs), `contract-list.component.ts` (8 specs), `contract-detail.component.ts` (10 specs) | `ContractListComponent` uses `@frontend/shared-ui` `DataTableComponent`; `AssetListComponent` and `ContractDetailComponent` use raw HTML `<table>` and render a dedicated `403` `InstalledBase.Read` notice. | Report screens should follow `@frontend/shared-ui` `DataTableComponent` (`data-testid="data-table"`) + `PageHeaderComponent` + `EmptyStateComponent` + `StatusBadgeComponent`, and clarify how users supply CAM's external-customer `accountId` (since CAM `accountId` is `SubscriptionDto.customerId` / `AssetDto.accountId`, not numeric CRM org ID). |

---

## 5. Proposed Work Split (Independent vs. Blocked)

### Prerequisite Gate (Owner Decision — `drg`)
- **Gate 0 (Hold-Lift PR):** `drg` merges a narrow update to `AGENT.md:4` and `docs/forge/42-contracts-ui-hold-exception.md:21` explicitly lifting the hold for **read-only** asset report screens (upcoming renewals and churn), while keeping lifecycle actions, amendments, history/supersede views (unless separately lifted), and all browser writes held. Confirm entry-point UX (route under `/assets/reports` vs tabs on `/assets`, and `accountId` selector/input expectations).

### Workstream 1 — Independent UI Delivery (Unblocked Immediately After Gate 0)
Can be implemented and verified in CI against the existing `AssetReportController` HTTP contracts and mocked/unit fixtures **without waiting** for live production data:
1. **Model & HTTP client layer:** Add `ASSET_CHURN_KIND_LABELS` in `asset.model.ts` (if needed) and read-only report methods (`upcomingRenewals(...)` supporting both `windowStart`/`windowEnd` and `withinDays`/`asOf`, plus `churn(accountId, periodStart, periodEnd)`) with Vitest `HttpTestingController` specs.
2. **Read-only report screens & states:** Build upcoming-renewals and asset-churn report views using `@frontend/shared-ui` (`DataTableComponent`, `PageHeaderComponent`, `EmptyStateComponent`, `StatusBadgeComponent`):
   - Explicit `loading`, `denied` (`403` -> `"Viewing asset reports requires the InstalledBase.Read role."` matching `AssetListComponent`), `validation` (`400 VALIDATION_REJECTED`), `failed` (retry), and `empty` states.
   - Surface report summary metrics returned verbatim by the API (`assetCount`, `windowStart`/`windowEnd` for renewals; `assetCount`, `eventCount`, `quantityChurned`, `periodStart`/`periodEnd`, and explicit note that supersedes are excluded for churn).
   - Zero write controls.
3. **Verification:** Vitest component specs, keyboard/ARIA a11y checks (`role="status"`, `role="alert"`, labelled form controls, table semantics), and reconciliation fixture tests verifying rendered totals match `AssetReportController` JSON payloads (`AssetReportReconciliationTest` parity).

### Workstream 2 — Blocked Work (Must Wait on Backend & Owner Decisions)
Must **not** be implemented, assumed, or mocked around in UI copy until their upstream owners resolve them:
1. **Blocked by Dependency 1 (`effectiveEnd` / lapse owner decision):** Any `EXPIRED`/`LAPSED` asset status or churn category, any KPI claiming term-end non-renewals are excluded from active base or counted as churn, or any window-boundary adjustment in `upcomingRenewals()`.
2. **Blocked by Dependency 2 (Replay wiring into `asset_history` + write/S4 hold lifts; `DRG-657`/`DRG-660` S7 NO-GO):** Live-environment Playwright UAT verifying real activated/amended/terminated/superseded report rows without synthetic seeding, and any claim of production report completeness.
3. **Blocked by Dependency 3 (Terminal-state integrity in read folds):** Backend fix in `AssetHistoryProjection.fold()` (prevent post-closure `AMENDED` resurrection) and `AssetReportService.churnForAsset()` (prevent post-termination downsize or duplicate termination counting) before relying on production report figures.
4. **Separate security rollout gate (`DRG-906 AC2` / `drg` Q11):** Account-level authorization isolation on `/api/v1/assets/reports/*` remains an open owner decision outside `DRG-905`; UI must not imply per-account access enforcement while `InstalledBase.Read` remains tenant-wide.

---

## 6. Explicit Non-Actions in This Assessment
- No frontend component, service, route, sidebar, or test scaffolding was created.
- No backend code, migration, hold flag, or `AGENT.md` / `42-contracts-ui-hold-exception.md` hold was modified.
- `DRG-926` Draft PR `#1101` is complete and untouched; Linear and Notion issue statuses are unchanged.
