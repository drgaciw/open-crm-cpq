# CRM API ↔ frontend parity

**Scope:** `com.opencrm.crm` controllers (`LeadController`, `DealController`, `ContactController`, `OrganizationController`) vs `frontend/libs/crm/**` HTTP usage and `frontend/libs/shared/models/**` types.

**Gap rules (from cutover plan):**

| Situation | Interpretation |
|-----------|----------------|
| API route with **no** FE caller | Candidate to **deprecate**, mark **internal-only** (e.g. Postman/Swagger), or **add** to UI later. |
| FE call with **no** matching API row | **Bug** or **wrong base URL** / gateway route. |

**Gateway:** Angular apps should use the **gateway** (default dev: `http://localhost:8080`); `crm-service` direct port **8081** is for debugging only.

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

---

## Parity matrix (API → FE service / types)

| HTTP | Path | FE caller | Shared models |
|------|------|-----------|---------------|
| POST | `/api/v1/leads` | `LeadService.create` | `LeadCreateRequest` → `LeadDto` |
| GET | `/api/v1/leads` | `LeadService.list` | `LeadDto`, `SpringPageResponse` |
| GET | `/api/v1/leads/{id}` | `LeadService.getById` | `LeadDto` |
| PUT | `/api/v1/leads/{id}` | `LeadService.update` | `LeadUpdateRequest` → `LeadDto` |
| DELETE | `/api/v1/leads/{id}` | `LeadService.delete` | — |
| GET | `/api/v1/leads/search` | `LeadService.search` | `LeadDto[]` + `PageInfo` (FE reads interceptor `SpringPageResponse.content`) |
| POST | `/api/v1/leads/{id}/contact` | `LeadService.markContacted` | `LeadDto` |
| POST | `/api/v1/leads/{id}/qualify` | `LeadService.qualify` | `LeadDto` |
| POST | `/api/v1/leads/{id}/convert` | `LeadService.convertToDeal` | `LeadDto` (navigate via `convertedDealId`) |
| POST | `/api/v1/leads/{id}/lose` | `LeadService.markLost` | `LeadDto` |
| GET | `/api/v1/leads/stats` | `LeadService.getStatusCounts`, dashboard KPI **`totalLeadCount`** | `Partial<Record<LeadStatus, number>>` |
| POST | `/api/v1/deals` | `DealService.create` | `DealCreateRequest` → `DealDto` |
| GET | `/api/v1/deals` | `DealService.list`, `DashboardComponent` (preview) | `DealDto`, `SpringPageResponse` |
| GET | `/api/v1/deals/{id}` | `DealService.getById` | `DealDto` |
| PUT | `/api/v1/deals/{id}` | `DealService.update` | `DealUpdateRequest` → `DealDto` |
| DELETE | `/api/v1/deals/{id}` | `DealService.delete` | — |
| GET | `/api/v1/deals/search` | `DealService.search` | `DealDto[]` + `PageInfo` (FE reads interceptor `SpringPageResponse.content`) |
| POST | `/api/v1/deals/{id}/advance` | `DealService.advanceStage` | `DealDto` |
| POST | `/api/v1/deals/{id}/win` | `DealService.markWon` | `DealDto` |
| POST | `/api/v1/deals/{id}/lose` | `DealService.markLost` | `DealDto` |
| GET | `/api/v1/deals/pipeline` | `DealService.loadPipelineSummary`, `DashboardComponent` | `PipelineSummaryDto[]` |
| POST | `/api/v1/contacts` | `ContactService.create` | `ContactCreateRequest` → `ContactDto` |
| GET | `/api/v1/contacts` | `ContactService.list` | `ContactDto`, `SpringPageResponse` |
| GET | `/api/v1/contacts/{id}` | `ContactService.getById` | `ContactDto` |
| PUT | `/api/v1/contacts/{id}` | `ContactService.update` | `ContactUpdateRequest` → `ContactDto` |
| DELETE | `/api/v1/contacts/{id}` | `ContactService.delete` | — |
| GET | `/api/v1/contacts/search` | `ContactService.search` | `ContactDto[]` + `PageInfo` (FE reads interceptor `SpringPageResponse.content`) |
| POST | `/api/v1/organizations` | `OrganizationService.create` | `OrganizationCreateRequest` → `OrganizationDto` |
| GET | `/api/v1/organizations` | `OrganizationService.list` | `OrganizationDto` / page unwrap |
| GET | `/api/v1/organizations/{id}` | `OrganizationService.getById` | `OrganizationDto` |
| PUT | `/api/v1/organizations/{id}` | `OrganizationService.update` | `OrganizationUpdateRequest` → `OrganizationDto` |
| DELETE | `/api/v1/organizations/{id}` | `OrganizationService.delete` | — |
| GET | `/api/v1/organizations/search` | `OrganizationService.search` | `OrganizationDto[]` + `PageInfo` (FE reads interceptor `SpringPageResponse.content`) |
| GET | `/api/v1/organizations/{id}/contacts` | `OrganizationService.loadOrganizationContacts`, **organization detail** UI | `ContactDto[]` + `PageInfo` (FE reads `.content`) |

### FE-only / indirect usage

- **`DashboardComponent`** (`libs/crm/dashboard`) calls `HttpClient` for `/api/v1/deals/pipeline`, `/api/v1/deals`, `/api/v1/leads` (preview rows). Same routes as `DealService` / lead list; not duplicate API surface.

### Optional query params (API ⊇ FE)

- **Leads:** `priority` on list — not exposed in `LeadService.list` (FE could extend).
- **Contacts:** `status` on list — not passed by `ContactService.list`.
- **Organizations:** `type` on list — not passed by `OrganizationService.list`.

### Response shape notes (B0-1)

- **List / search / org contacts:** all return `ApiResponse` with `data: T[]` and `pagination` (`PageInfo`). Lead/Deal search previously put a Spring `Page` in `data`; they now match Contact/Org (`getContent()` + `PageInfo`).
- **FE unwrap:** `apiResponseInterceptor` rebuilds `{ content, number, size, totalElements, totalPages }` when `data` is an array and `pagination` is present. CRM services must set table signals from `.content`, not the page object.
- **Lead convert:** `POST /leads/{id}/convert` returns `LeadDto` with `convertedDealId`; UI navigates to `/deals/{convertedDealId}` (not `lead.id`).

---

## DTO alignment

**Source of truth:** OpenAPI from `crm-service` (`/api-docs` or Swagger UI). Types live under:

- `frontend/libs/shared/models/src/lib/*.model.ts` (`LeadDto`, `DealDto`, `ContactDto`, `OrganizationDto`, requests, `PipelineSummaryDto`, pagination helpers).

**Process:** Compare JSON property names from OpenAPI with interfaces; fix **real drift** only (prefer a thin mapping layer if you must support renames without churn).

---

## Related docs

- Gateway/env and observability: [CRM_CUTOVER_CHECKLIST.md](./CRM_CUTOVER_CHECKLIST.md)
- Redis quote acceptance contract: `QuoteAcceptedCrmMessage` in `backend/libs/common/.../QuoteAcceptedCrmMessage.java`
