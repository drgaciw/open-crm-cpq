# CRM API ↔ frontend parity

**Scope:** `com.opencrm.crm` controllers (`LeadController`, `DealController`, `ContactController`, `OrganizationController`) vs `frontend/libs/crm/**` HTTP usage and `frontend/libs/shared/models/**` types.

**Last verified:** forge Wave B5 / W1-08 (`b2-parity-docs`) — Batch 0 search contract + activities deferral encoded. No Java/FE app changes in that wave.

**Gap rules (from cutover plan):**

| Situation | Interpretation |
|-----------|----------------|
| API route with **no** FE caller | Candidate to **deprecate**, mark **internal-only** (e.g. Postman/Swagger), or **add** to UI later. |
| FE call with **no** matching API row | **Bug** or **wrong base URL** / gateway route. |

**Gateway:** Angular apps should use the **gateway** (default dev: `http://localhost:8080`); `crm-service` direct port **8081** is for debugging only. CRM predicates are only `/api/v1/leads/**`, `/api/v1/deals/**`, `/api/v1/contacts/**`, `/api/v1/organizations/**` — see [Activities (deferred)](#activities-deferred).

---

## Endpoint inventory (method + path)

Base paths: `/api/v1/leads`, `/api/v1/deals`, `/api/v1/contacts`, `/api/v1/organizations`.

| HTTP | Path | Controller method |
|------|------|---------------------|
| POST | `/api/v1/leads` | `create` |
| GET | `/api/v1/leads` | `list` (query: `status`, `priority`, pageable) |
| GET | `/api/v1/leads/{id}` | `getById` |
| PUT | `/api/v1/leads/{id}` | `update` |
| DELETE | `/api/v1/leads/{id}` | `delete` |
| GET | `/api/v1/leads/search` | `search` (`q`, pageable) |
| POST | `/api/v1/leads/{id}/contact` | `markContacted` |
| POST | `/api/v1/leads/{id}/qualify` | `qualify` |
| POST | `/api/v1/leads/{id}/convert` | `convert` |
| POST | `/api/v1/leads/{id}/lose` | `lose` |
| GET | `/api/v1/leads/stats` | `getStatusCounts` |
| POST | `/api/v1/deals` | `create` |
| GET | `/api/v1/deals` | `list` (query: `stage`, pageable) |
| GET | `/api/v1/deals/{id}` | `getById` |
| PUT | `/api/v1/deals/{id}` | `update` |
| DELETE | `/api/v1/deals/{id}` | `delete` |
| GET | `/api/v1/deals/search` | `search` (`q`, pageable) |
| POST | `/api/v1/deals/{id}/advance` | `advanceStage` |
| POST | `/api/v1/deals/{id}/win` | `win` |
| POST | `/api/v1/deals/{id}/lose` | `lose` |
| GET | `/api/v1/deals/pipeline` | `getPipelineSummary` |
| POST | `/api/v1/contacts` | `create` |
| GET | `/api/v1/contacts` | `list` (query: `status`, pageable) |
| GET | `/api/v1/contacts/{id}` | `getById` |
| PUT | `/api/v1/contacts/{id}` | `update` |
| DELETE | `/api/v1/contacts/{id}` | `delete` |
| GET | `/api/v1/contacts/search` | `search` (`q`, pageable) |
| POST | `/api/v1/organizations` | `create` |
| GET | `/api/v1/organizations` | `list` (query: `type`, pageable) |
| GET | `/api/v1/organizations/{id}` | `getById` |
| PUT | `/api/v1/organizations/{id}` | `update` |
| DELETE | `/api/v1/organizations/{id}` | `delete` |
| GET | `/api/v1/organizations/search` | `search` (`q`, pageable) |
| GET | `/api/v1/organizations/{id}/contacts` | `getContacts` |

**Count:** 35 routes. There is **no** `/api/v1/activities/**` controller or inventory row (intentional — see [Activities (deferred)](#activities-deferred)).

---

## Parity matrix (API → FE service / types)

| HTTP | Path | FE caller | Shared models |
|------|------|-----------|---------------|
| POST | `/api/v1/leads` | `LeadService.create` | `LeadCreateRequest` → `LeadDto` |
| GET | `/api/v1/leads` | `LeadService.list` | `LeadDto`, `SpringPageResponse` |
| GET | `/api/v1/leads/{id}` | `LeadService.getById` | `LeadDto` |
| PUT | `/api/v1/leads/{id}` | `LeadService.update` | `LeadUpdateRequest` → `LeadDto` |
| DELETE | `/api/v1/leads/{id}` | `LeadService.delete` | — |
| GET | `/api/v1/leads/search` | `LeadService.search` | `LeadDto[]` + `PageInfo` → FE reads `.content` |
| POST | `/api/v1/leads/{id}/contact` | `LeadService.markContacted` | `LeadDto` |
| POST | `/api/v1/leads/{id}/qualify` | `LeadService.qualify` | `LeadDto` (sets BE `qualifiedAt`) |
| POST | `/api/v1/leads/{id}/convert` | `LeadService.convertToDeal` | `LeadDto` (navigate via `convertedDealId`) |
| POST | `/api/v1/leads/{id}/lose` | `LeadService.markLost` | `LeadDto` |
| GET | `/api/v1/leads/stats` | `LeadService.getStatusCounts`, dashboard KPI **`totalLeadCount`** | `Partial<Record<LeadStatus, number>>` |
| POST | `/api/v1/deals` | `DealService.create` | `DealCreateRequest` → `DealDto` |
| GET | `/api/v1/deals` | `DealService.list`, `DashboardComponent` (preview) | `DealDto`, `SpringPageResponse` |
| GET | `/api/v1/deals/{id}` | `DealService.getById` | `DealDto` |
| PUT | `/api/v1/deals/{id}` | `DealService.update` | `DealUpdateRequest` → `DealDto` |
| DELETE | `/api/v1/deals/{id}` | `DealService.delete` | — |
| GET | `/api/v1/deals/search` | `DealService.search` | `DealDto[]` + `PageInfo` → FE reads `.content` |
| POST | `/api/v1/deals/{id}/advance` | `DealService.advanceStage` | `DealDto` |
| POST | `/api/v1/deals/{id}/win` | `DealService.markWon` | `DealDto` |
| POST | `/api/v1/deals/{id}/lose` | `DealService.markLost` | `DealDto` |
| GET | `/api/v1/deals/pipeline` | `DealService.loadPipelineSummary`, `DashboardComponent` | `PipelineSummaryDto[]` — amounts in `baseCurrency`; `unconvertedCount` per stage (ADR-032, DRG-509) |
| POST | `/api/v1/contacts` | `ContactService.create` | `ContactCreateRequest` → `ContactDto` |
| GET | `/api/v1/contacts` | `ContactService.list` | `ContactDto`, `SpringPageResponse` |
| GET | `/api/v1/contacts/{id}` | `ContactService.getById` | `ContactDto` |
| PUT | `/api/v1/contacts/{id}` | `ContactService.update` | `ContactUpdateRequest` → `ContactDto` |
| DELETE | `/api/v1/contacts/{id}` | `ContactService.delete` | — |
| GET | `/api/v1/contacts/search` | `ContactService.search` | `ContactDto[]` + `PageInfo` → FE reads `.content` |
| POST | `/api/v1/organizations` | `OrganizationService.create` | `OrganizationCreateRequest` → `OrganizationDto` |
| GET | `/api/v1/organizations` | `OrganizationService.list` | `OrganizationDto` / page unwrap |
| GET | `/api/v1/organizations/{id}` | `OrganizationService.getById` | `OrganizationDto` |
| PUT | `/api/v1/organizations/{id}` | `OrganizationService.update` | `OrganizationUpdateRequest` → `OrganizationDto` |
| DELETE | `/api/v1/organizations/{id}` | `OrganizationService.delete` | — |
| GET | `/api/v1/organizations/search` | `OrganizationService.search` | `OrganizationDto[]` + `PageInfo` → FE reads `.content` |
| GET | `/api/v1/organizations/{id}/contacts` | `OrganizationService.loadOrganizationContacts`, **organization detail** UI | `ContactDto[]` + `PageInfo` → FE reads `.content` |

### FE-only / indirect usage

- **Pipeline currency (ADR-032, DRG-509):** `totalAmount` and `weightedAmount` sum frozen base amounts in `baseCurrency` only; deals without a rate are left out and counted in `unconvertedCount`. `DealDto` adds `baseAmount`, `baseCurrency`, `baseWeightedAmount`, `fxRate`, `fxRateAsOf`, `fxSource` (omitted when null). Additive fields; no new route.
- **`DashboardComponent`** (`libs/crm/dashboard`) calls `HttpClient` for `/api/v1/deals/pipeline`, `/api/v1/deals`, `/api/v1/leads` (preview rows). Same routes as `DealService` / lead list; not duplicate API surface. Preview lists also use `content ?? rows` unwrap.

### Optional query params (API ⊇ FE)

- **Leads:** `priority` on list — not exposed in `LeadService.list` (FE could extend).
- **Contacts:** `status` on list — not passed by `ContactService.list`.
- **Organizations:** `type` on list — not passed by `OrganizationService.list`.

### Response shape notes (Batch 0 / B0-1 — current reality)

**Contract (all four resources):** list, search, and org-contacts return `ApiResponse` with:

- `data`: **`T[]`** (controller uses `page.getContent()`)
- `pagination`: **`PageInfo`** (`page`, `size`, `totalElements`, `totalPages`, …)

Lead/Deal search previously put a Spring `Page` inside `data`. Batch 0 (`feat/forge-b0-crm-search-shape`, W1-03) aligned them with Contact/Org. Controllers today:

| Endpoint family | Java return | `data` shape |
|-----------------|-------------|--------------|
| `GET …/leads`, `…/search` | `ApiResponse<List<LeadDto>>` | array + `pagination` |
| `GET …/deals`, `…/search` | `ApiResponse<List<DealDto>>` | array + `pagination` |
| `GET …/contacts`, `…/search` | `ApiResponse<List<ContactDto>>` | array + `pagination` |
| `GET …/organizations`, `…/search`, `…/{id}/contacts` | `ApiResponse<List<…>>` | array + `pagination` |

**FE unwrap:** `apiResponseInterceptor` rebuilds `{ content, number, size, totalElements, totalPages }` when `data` is an array and `pagination` is present. CRM services set table signals from `p.content ?? p` (tolerates bare array or page object). Prefer reading `.content` after the interceptor.

**Lead convert:** `POST /leads/{id}/convert` returns `LeadDto` with `convertedDealId`; UI navigates to `/deals/{convertedDealId}` (not `lead.id`). List qualify/convert actions call the same endpoints.

### DTO drift notes

| Field | Backend | FE `LeadDto` | Status |
|-------|---------|--------------|--------|
| `qualifiedAt` | `Instant` on Java `LeadDto`; set on qualify; Flyway `V2__add_lead_qualified_at.sql` | **Absent** in `frontend/libs/shared/models/.../lead.model.ts` | Documented drift — extra JSON is ignored at runtime; add to FE models when UI needs the timestamp |
| `convertedDealId` | Present | Present | Aligned; convert navigation uses it |

---

## Activities (deferred)

**Decision:** Do **not** implement an Activities API or gateway route for Spring CRM cutover.

| Layer | State |
|-------|--------|
| `crm-service` | No `Activity*` entity, controller, or Flyway table |
| Gateway | **No** `/api/v1/activities/**` predicate (freeze) — [CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md), [CRM_CUTOVER_CHECKLIST.md](./CRM_CUTOVER_CHECKLIST.md) |
| FE | No activities HTTP client under `frontend/libs/crm` |
| Legacy | `cpq_beta/crm/api/activities.py` (`get_activities` for CRM Deal / CRM Lead) — Phase 2 / Frappe inventory only |
| App comment | `CrmServiceApplication` notes activities are planned and gateway must not route until implemented |

**Cutover checklist cross-links:**

- Pre-cutover: gateway CRM paths are only leads/deals/contacts/organizations — **no phantom** `/api/v1/activities/**`.
- Phase 2: pick one vertical (e.g. read-only activities feed), design migration + real controller/schema, **then** add the gateway predicate.

**Do not:** stub routes, invent FE callers, or add gateway paths “for future use.” Treat a phantom activities route as a cutover defect.

---

## DTO alignment

**Source of truth:** OpenAPI from `crm-service` (`/api-docs` or Swagger UI). Types live under:

- `frontend/libs/shared/models/src/lib/*.model.ts` (`LeadDto`, `DealDto`, `ContactDto`, `OrganizationDto`, requests, `PipelineSummaryDto`, pagination helpers).

**Process:** Compare JSON property names from OpenAPI with interfaces; fix **real drift** only (prefer a thin mapping layer if you must support renames without churn). Known open drift: FE `LeadDto.qualifiedAt` (above).

---

## Related docs

- Gateway/env and observability: [CRM_CUTOVER_CHECKLIST.md](./CRM_CUTOVER_CHECKLIST.md), [CRM_GATEWAY_ENV.md](./CRM_GATEWAY_ENV.md)
- Forge discovery / PR plan: [docs/forge/01-crm-api-parity.md](./forge/01-crm-api-parity.md)
- Batch 0 search shape: [docs/forge/impl/b0-crm-search.md](./forge/impl/b0-crm-search.md)
- Redis quote acceptance contract: `QuoteAcceptedCrmMessage` in `backend/libs/common/.../QuoteAcceptedCrmMessage.java`
