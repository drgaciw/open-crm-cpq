# Forge Track 02 — Angular CRM Frontend Hardening

**Agent:** `crm-fe` (forge scout)  
**Mode:** plan / discovery only (no application source edits)  
**Date:** 2026-08-30  
**Related:** [CRM_API_PARITY.md](../CRM_API_PARITY.md), [CRM_TESTING_STRATEGY.md](../CRM_TESTING_STRATEGY.md), [CRM_CUTOVER_CHECKLIST.md](../CRM_CUTOVER_CHECKLIST.md), [00-orchestrator-status.md](./00-orchestrator-status.md)

---

## 1. Track name + scope

| Field | Value |
|-------|--------|
| **Track** | `02-crm-frontend` — Angular CRM frontend hardening |
| **In scope** | `frontend/apps/crm`, `frontend/libs/crm/**` (leads, deals, contacts, organizations, dashboard), `frontend/libs/shared/{models,data-access,ui,utils}`, proxy/env alignment for CRM HTTP, Vitest coverage for CRM feature libs |
| **Out of scope** | Backend controller/service changes (except documenting FE contract mismatches for track `01-crm-api-parity`), CPQ frontend (`05-fe-cpq-ui`), gateway CI (`06-gateway-ci`), Frappe/`cpq_beta`, auth/OIDC UI |
| **Success** | Lead/deal lifecycle actions work end-to-end in UI; search/list/org-contacts unwrap correctly after `ApiResponse` interceptor; real unit tests for services + critical components; shared UI reused where it reduces duplication; proxy targets gateway for local cutover smoke |

---

## 2. Current state (what exists)

### App shell

- Nx app `frontend/apps/crm` with lazy routes: dashboard, organizations, contacts, leads, deals (`app.routes.ts`).
- HTTP stack: `loadingInterceptor` → `apiResponseInterceptor` → `errorInterceptor` (`app.config.ts`).
- Dev proxy: `frontend/proxy.conf.json` forwards `/api` → **`http://localhost:8081`** (crm-service direct), not gateway **8080**.

### Feature libraries (`frontend/libs/crm`)

| Lib | Routes / surfaces | Data service | Lifecycle / special APIs |
|-----|-------------------|--------------|---------------------------|
| `crm-leads` | list, detail, form | `LeadService` | Service methods: `markContacted`, `qualify`, `convertToDeal`, `markLost`, `getStatusCounts`. **UI wired on detail only.** |
| `crm-deals` | list, detail, form, kanban | `DealService` | `advanceStage` / `markWon` / `markLost` on list + detail. `loadPipelineSummary` exists; dashboard/kanban mostly use list or raw HTTP. |
| `crm-contacts` | list, detail, form | `ContactService` | CRUD + search only |
| `crm-organizations` | list, detail, form | `OrganizationService` | CRUD + search + `loadOrganizationContacts` (detail) |
| `crm-dashboard` | single dashboard | Direct `HttpClient` + `LeadService.getStatusCounts` | Pipeline KPIs via `GET /deals/pipeline` |

### Shared libraries

- **`shared-models`:** `LeadDto` / `DealDto` / `ContactDto` / `OrganizationDto`, requests, `PipelineSummaryDto`, `ApiResponse` / `SpringPageResponse`, stage/status color maps. Includes `UNQUALIFIED` and `LeadPriority` types.
- **`shared-data-access`:** `apiResponseInterceptor` (unwrap + list+`pagination` → page shape), `errorInterceptor` + `NotificationService` (MatSnackBar), `loadingInterceptor`, **`BaseCrudService` (unused by CRM services)**.
- **`shared-ui`:** `EmptyState`, `PageHeader`, `SearchInput`, `StatusBadge`, `ConfirmDialog`, `DataTable`, pipes — **not imported by any `libs/crm/**` feature** (grep: zero `@frontend/shared-ui` usage under CRM).
- **`shared-utils`:** date helpers, form validators, query-params helper (partially used).

### Parity doc vs code (service layer)

`docs/CRM_API_PARITY.md` correctly lists FE **service** callers for nearly all CRM endpoints. Gap is primarily **UI surface** and **response-shape handling**, not missing HTTP methods on services.

### Tests (inventory)

```text
# CRM feature libs: 5 scaffold-only specs (crm-*-*.spec.ts "should create")
# No lead.service / deal.service / *-detail / *-list / *-form / kanban / dashboard.component specs

# Shared: BaseCrudService, NotificationService, LoadingService, ConfirmDialog, DataTable, pipes, utils
# Commands (after npm ci in frontend/):
cd frontend && npm ci --legacy-peer-deps
npx nx run-many --target=test --projects="crm-deals,crm-leads,crm-contacts,crm-organizations,crm-dashboard,shared-models,shared-data-access,shared-ui,shared-utils" --parallel=3
```

**Evidence:** `frontend/node_modules` absent at discovery time → Nx test run could not execute (`Could not find Nx modules`). Coverage assessment is from file inventory + static review.

---

## 3. Gaps with evidence (paths / commands)

### A. Critical data-access / contract bugs

#### A1. Lead convert: FE treats `LeadDto` as `DealDto` (HIGH)

| | |
|--|--|
| **API** | `POST /api/v1/leads/{id}/convert` returns `ApiResponse<LeadDto>` with `convertedDealId` (`LeadController.convert`, `LeadService.convert`) |
| **FE** | `LeadService.convertToDeal` typed as `Observable<DealDto>`; detail navigates to `/deals/${deal.id}` |
| **Paths** | `frontend/libs/crm/leads/src/lib/lead.service.ts` (L101–103); `lead-detail.component.ts` (L261–274) |
| **Parity doc error** | `CRM_API_PARITY.md` row claims Shared models `DealDto` for convert — incorrect vs backend |
| **Symptom** | After convert, router likely opens `/deals/{leadId}` (wrong id) unless coincidence |

```bash
# Backend contract
rg -n "ApiResponse<LeadDto> convert|convertedDealId" backend/services/crm-service/src/main/java/com/opencrm/crm/lead
# FE caller
rg -n "convertToDeal|convertedDealId" frontend/libs/crm/leads
```

#### A2. Search unwrap broken for all four CRM resources (HIGH)

Interceptor only rebuilds page shape when `pagination` is present **and** `data` is an **array**:

```47:56:frontend/libs/shared/data-access/src/lib/api-response.interceptor.ts
          if (apiResponse.pagination && Array.isArray(apiResponse.data)) {
            const p = apiResponse.pagination;
            unwrapped = {
              content: apiResponse.data,
              ...
            };
          }
```

| Resource | Backend `search` payload | After interceptor | Service `search()` | Result |
|----------|--------------------------|-------------------|--------------------|--------|
| Lead / Deal | `ApiResponse.success(Page<T>, pageInfo)` — **data is Page object** | Page left as-is (not array) | `this.leads.set(leads)` expecting `T[]` | Table/autocomplete get non-array |
| Contact / Org | `ApiResponse.success(List, pageInfo)` | Wrapped to `{ content, number, ... }` | `this.contacts.set(contacts)` expecting `T[]` | Sets page object, not `content` |
| Org contacts | List + pageInfo | Same wrap | `organizationContacts.set(rows)` as `ContactDto[]` | Detail contact list broken |

**Paths:**  
`lead.service.ts` L79–90, `deal.service.ts` L80–91, `contact.service.ts` L75–87, `organization.service.ts` L105–117 & L123–139  
Backend: `LeadController.search` / `DealController.search` vs `ContactController.search` / `OrganizationController.search` / `getContacts`

```bash
rg -n "ApiResponse\.success\(" backend/services/crm-service/src/main/java/com/opencrm/crm/{lead,deal,contact,organization}/*Controller.java
rg -n "search\(|loadOrganizationContacts" frontend/libs/crm --glob '*service.ts'
```

**Knock-on:** Lead/Deal/Contact forms use `*.search()` for autocomplete (`lead-form.component.ts` `searchContacts` / `searchOrgs`) — association pickers fail when search returns non-arrays.

#### A3. Dual list response handling is fragile (MEDIUM)

List endpoints return **List + PageInfo**; interceptor synthesizes `SpringPageResponse`. Services use `p.content ?? p`. Works today if interceptor always wraps, but:

- No shared helper; copy-pasted across four services + dashboard.
- `BaseCrudService.list/search` assume native Spring `Page` in body and always read `.content` — **incompatible** with current interceptor + CRM controllers; explains why CRM services do not extend it.

#### A4. Proxy vs cutover gateway (MEDIUM)

| Doc / checklist | Proxy |
|-----------------|-------|
| Cutover: smoke via **4200 → 8080 gateway** | `proxy.conf.json` → **8081** |

Not a runtime bug for “crm-service only” local work, but fails the documented cutover path and hides gateway routing issues.

---

### B. Missing / thin UI actions vs API (qualify / convert / pipeline)

| Capability | API + service | UI today | Gap |
|------------|---------------|----------|-----|
| Mark contacted / Qualify / Convert / Lose | Yes (`LeadService`) | **Detail only** (`lead-detail.component.ts` action bar) | List row menu: Edit/Delete only — no lifecycle actions (`lead-list.component.ts` L115–122) |
| Advance / Win / Lose deal | Yes | List menu + detail | OK for actions; lose uses `prompt()`, lead uses `LoseReasonDialog` — inconsistent UX |
| Pipeline summary | `DealService.loadPipelineSummary` + dashboard HTTP | Dashboard bars use pipeline; **kanban ignores summary** and loads `list(0,200)` | Kanban not true pipeline API; no drag-and-drop → no stage change without opening detail/list menu |
| Lead priority filter | API `priority` on list | `LeadService.list` has no `priority` param; form has no priority field | Documented optional gap in parity doc; still FE-incomplete |
| Contact `status` / Org `type` list filters | API supports | Services omit params; lists have no filter tabs | Same class of gap as priority |
| `UNQUALIFIED` status | In `LeadStatus` model + `StatusBadge` colors | Lead tabs: All/New/Contacted/Qualified/Converted/Lost — **no Unqualified** | Orphans API status |

**Evidence commands:**

```bash
rg -n "onQualify|onConvert|markContacted|advanceStage|loadPipelineSummary" frontend/libs/crm
rg -n "priority|UNQUALIFIED|status\?:" frontend/libs/crm --glob '*.ts'
rg -n "EmptyState|PageHeader|SearchInput|StatusBadge|ConfirmDialog" frontend/libs/crm
```

---

### C. Test coverage gaps

| Area | Current | Needed |
|------|---------|--------|
| CRM services | None | HttpTestingController specs for list unwrap, search unwrap, convert→`convertedDealId`, lifecycle POSTs |
| Lead/deal detail actions | None | Component tests: qualify/convert navigation, lose dialog, advance/win |
| Dashboard | Scaffold `crm-dashboard.spec.ts` only | forkJoin + KPI computation / empty pipeline |
| Kanban | None | Column grouping from `deals()` signal |
| Interceptor | No dedicated spec under data-access | Unit-test list+pagination wrap vs Page-in-data vs bare entity |
| Scaffold specs | `crm-leads`, `crm-deals`, … | Keep or replace; they do not cover production components |

CI already intends CRM + shared-models tests (`CRM_TESTING_STRATEGY.md`); failing/absent meaningful coverage will greenwash.

---

### D. UX / consistency gaps

1. **Native `confirm` / `prompt`** on deals vs Material dialogs on leads; shared `ConfirmDialog` unused.
2. **Duplicated empty states / page headers / search fields** instead of `shared-ui`.
3. **Custom chip/badge CSS** per feature instead of `StatusBadgeComponent` (which already knows `UNQUALIFIED`).
4. **Silent `error: () => {}`** on deal actions (list/detail) — snackbar may still fire via interceptor, but no local recovery/loading affordance like lead `actionLoading`.
5. **Dashboard** bypasses `DealService` / `LeadService.list` — harder to mock and duplicates unwrap (`deals.content ?? deals`).
6. **No success toasts** on convert/win/qualify (only error path via interceptor).
7. **Kanban** is read-only board (click-through); pipeline management still requires list menus or detail.

---

### E. Docs drift to fix alongside FE (coordinate with `01-crm-api-parity`)

- Convert shared-models column: `DealDto` → `LeadDto` (+ navigate via `convertedDealId`).
- Call out search payload asymmetry (Page vs List) as FE bug until backend unified **or** FE normalizer handles both.
- Optional query params remain FE-extendable, not deprecate-candidates.

---

## 4. Blast radius / risk notes

GitNexus MCP was **not available** in this scout session. Implementers **must** run `gitnexus_impact` upstream on symbols before editing, per workspace rules. Static blast radius:

| Change target | Direct callers / consumers | Risk |
|---------------|----------------------------|------|
| `apiResponseInterceptor` unwrap rules | All CRM HTTP via app interceptors; dashboard; any future CPQ FE using same interceptor | **HIGH** — wrong change breaks every list |
| Shared page-unwrap helper used by 4 services | All list/search/org-contacts UIs + form autocomplete | **HIGH** if helper wrong; **MEDIUM** if additive |
| `LeadService.convertToDeal` return type + detail navigation | Lead detail only today; any future list actions | **MEDIUM–HIGH** (wrong navigation post-convert) |
| `LeadService` / `DealService` / `ContactService` / `OrganizationService` `search` | Lists + lead/deal/contact/org forms | **HIGH** (broken search UX) |
| Proxy target 8081→8080 | Local `nx serve crm` only | **LOW** if gateway up; **MEDIUM** if gateway misconfigured |
| Adopting `shared-ui` in lists | Visual churn across 4 list pages | **LOW–MEDIUM** (CSS/regression) |
| Kanban drag → `advanceStage` | Deal stage machine; may skip stages vs API “advance one step” | **MEDIUM** — confirm API semantics before drag multi-hop |
| Extending list filters (priority/status/type) | Backend already supports; FE query only | **LOW** |

**Do not** mass-migrate CRM services onto `BaseCrudService` in the same PR as unwrap fixes — BaseCrud assumes different response shapes; treat as a later refactor after a normalizer exists.

---

## 5. Proposed PRs

### PR-FE-1 — Fix search / org-contacts unwrap + convert contract (P0)

| | |
|--|--|
| **Title** | `fix(crm-fe): normalize paginated search responses and lead convert navigation` |
| **Files** | `frontend/libs/shared/data-access/src/lib/` (helper or interceptor hardening + specs); `lead.service.ts`, `deal.service.ts`, `contact.service.ts`, `organization.service.ts`; `lead-detail.component.ts`; optionally `docs/CRM_API_PARITY.md` convert row |
| **Acceptance** | (1) After search, list signals are `T[]` and pagination updated for all four resources. (2) Org detail contacts render names/links. (3) Convert navigates to `/deals/{convertedDealId}` and types as `LeadDto`. (4) Vitest covers interceptor/helper + service search/convert. (5) Autocomplete on lead form still returns contact/org rows. |
| **Risk** | **HIGH** — touches shared interceptor path; gate with interceptor unit tests first |

### PR-FE-2 — Lead list lifecycle actions + UNQUALIFIED / priority UX (P1)

| | |
|--|--|
| **Title** | `feat(crm-fe): expose lead qualify/convert actions on list and priority/unqualified filters` |
| **Files** | `lead-list.component.ts`, `lead.service.ts` (`priority` query), `lead-form.component.ts` (priority control), status tabs for `UNQUALIFIED`; reuse `LoseReasonDialog` / `ConfirmDialog` |
| **Acceptance** | From list menu (status-gated): Contacted / Qualify / Convert / Lose call same service methods as detail; Unqualified tab works; create/edit can set priority; list can filter by priority (API param). |
| **Risk** | **MEDIUM** — depends on PR-FE-1 convert fix |

### PR-FE-3 — Deal pipeline UX hardening (P1)

| | |
|--|--|
| **Title** | `feat(crm-fe): align deal lose dialog, kanban stage actions, and pipeline summary usage` |
| **Files** | `deal-list.component.ts`, `deal-detail.component.ts`, `deal-kanban.component.ts`, `deal.service.ts`; shared lose dialog (extract from leads or shared-ui); dashboard optionally switch to `DealService` |
| **Acceptance** | Lose uses Material dialog (parity with leads); kanban offers Advance/Won/Lost (or drag with single-step advance confirmed); optional header totals from `loadPipelineSummary`; empty error handlers replaced with loading/disable patterns. |
| **Risk** | **MEDIUM** — stage machine UX; avoid inventing multi-stage jump if API only advances one step |

### PR-FE-4 — Filter parity for contacts status + organizations type (P2)

| | |
|--|--|
| **Title** | `feat(crm-fe): add contact status and organization type list filters` |
| **Files** | `contact.service.ts`, `contact-list.component.ts`, `organization.service.ts`, `organization-list.component.ts`, models if enums incomplete |
| **Acceptance** | Optional query params forwarded; tabs/chips filter lists; empty states correct when filtered empty. |
| **Risk** | **LOW** |

### PR-FE-5 — Adopt shared-ui on CRM lists (P2)

| | |
|--|--|
| **Title** | `refactor(crm-fe): use shared-ui page header, search, empty state, status badge, confirm dialog` |
| **Files** | Four `*-list` components, detail deletes, `frontend/libs/shared/ui/**` only if small API tweaks needed |
| **Acceptance** | No behavior change; visual consistency; ConfirmDialog replaces native confirm for delete; StatusBadge used for lead/deal chips where colors match. |
| **Risk** | **LOW–MEDIUM** visual regression — screenshot/manual smoke |

### PR-FE-6 — Meaningful Vitest coverage for CRM libs (P1, can stack with FE-1)

| | |
|--|--|
| **Title** | `test(crm-fe): service and lifecycle component coverage for leads/deals/dashboard` |
| **Files** | New `*.spec.ts` beside services and detail/list/dashboard; keep scaffold specs or delete if redundant |
| **Acceptance** | `nx run-many --target=test --projects="crm-leads,crm-deals,crm-contacts,crm-organizations,crm-dashboard,shared-data-access"` green after `npm ci`; covers unwrap, convert navigation id, advance/win/lose POST URLs, dashboard KPI reduce. |
| **Risk** | **LOW** |

### PR-FE-7 — Proxy / local gateway alignment (P2)

| | |
|--|--|
| **Title** | `chore(crm-fe): point Angular proxy at gateway :8080 with documented 8081 override` |
| **Files** | `frontend/proxy.conf.json`, `frontend/README.md` (short CRM section), maybe `docs/CRM_GATEWAY_ENV.md` cross-link |
| **Acceptance** | Default proxy `8080`; README documents `8081` for direct debugging; cutover smoke path matches checklist. |
| **Risk** | **LOW** locally if gateway not running — document both |

---

## 6. Parallelization / dependencies

```text
                    ┌─────────────────┐
                    │ PR-FE-1 unwrap  │  ← start immediately (blocks real E2E)
                    │ + convert fix   │
                    └────────┬────────┘
           ┌─────────────────┼─────────────────┐
           ▼                 ▼                 ▼
    ┌────────────┐   ┌────────────┐   ┌────────────┐
    │ PR-FE-2    │   │ PR-FE-3    │   │ PR-FE-6    │
    │ lead UX    │   │ deal/pipe  │   │ tests      │
    └─────┬──────┘   └─────┬──────┘   └────────────┘
          │                │
          ▼                ▼
    ┌────────────┐   ┌────────────┐
    │ PR-FE-4    │   │ PR-FE-5    │  ← after UX stabilizes
    │ filters    │   │ shared-ui  │
    └────────────┘   └────────────┘

    PR-FE-7 (proxy) — independent anytime; before cutover smoke

Cross-track:
  01-crm-api-parity — update convert/search rows when FE-1 lands (or land doc fix in FE-1)
  03-quote-deal-e2e — needs working deals UI + gateway; not blocked by lead list menus
  05-fe-cpq-ui — should copy unwrap helper + service patterns from FE-1, not BaseCrud as-is
  06-gateway-ci — FE-7 assumes gateway routes exist
```

**Suggested wave 1 implementation order:** FE-1 → FE-6 (same stack or immediate follow) → FE-2 ∥ FE-3 → FE-7 → FE-4 ∥ FE-5.

---

## 7. Explicit DO NOT TOUCH list

| Path / area | Reason |
|-------------|--------|
| `cpq_beta/**` | Legacy Frappe; out of CRM FE track |
| `backend/services/{cpq,communication,ai,gateway}-service/**` | Other forge tracks |
| `backend/services/crm-service/**` business logic | FE track documents mismatches; backend shape unification belongs to `01-crm-api-parity` unless FE-only fix is insufficient |
| `frontend/libs/shared/data-access/.../base-crud.service.ts` mass adoption | Wrong response assumptions until normalizer exists |
| Auth / Azure AD B2C / route guards | Not scaffolded; do not invent in this track |
| NgRx store introduction | Current signal services are intentional; no wholesale state rewrite |
| Playwright / e2e harness greenfield | Strategy says manual gateway smoke; optional later, not wave-1 FE hardening |
| `docs/ci-contract.yaml`, Jenkinsfile, `.github/workflows/**` | Track `06-gateway-ci` |
| Renaming public Angular selectors / route paths (`/leads`, `/deals`, …) | Breaks bookmarks and cutover scripts |
| Frappe UI / Vue under any path | Not part of Nx CRM portal |

---

## 8. Verification checklist (for implementers)

```bash
cd frontend && npm ci --legacy-peer-deps
npx nx serve crm
# Gateway up on :8080 (preferred) or temporarily crm-service :8081 per README

# Manual smoke
# 1. Search leads/deals/contacts/orgs — tables populate
# 2. Lead NEW → Contacted → Qualify → Convert → lands on correct deal id
# 3. Deal Advance / Won / Lost
# 4. Dashboard pipeline bars + KPIs
# 5. Org detail contacts list
# 6. Kanban columns match stages

npx nx run-many --target=test --projects="crm-leads,crm-deals,crm-contacts,crm-organizations,crm-dashboard,shared-data-access,shared-models" --parallel=3
npx nx build crm
```

Before commit (workspace rule): `gitnexus_detect_changes()` once GitNexus is available; before symbol edits: `gitnexus_impact({target, direction:"upstream"})`.

---

## 9. Scout summary

The CRM Angular portal already has full **service-level** HTTP coverage for CRM controllers, and lead/deal **detail** (plus deal **list**) expose the important lifecycle verbs. Hardening priority is not “add missing API clients” — it is (1) **fix broken page/search unwrap and convert navigation**, (2) **surface lead actions on the list and finish filter/priority UX**, (3) **pipeline/kanban/dialog consistency**, (4) **replace scaffold tests with real coverage**, (5) **adopt shared-ui and gateway proxy** for cutover readiness.
