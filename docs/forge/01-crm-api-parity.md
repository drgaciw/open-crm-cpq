# Forge plan: CRM API parity & cutover readiness

**Track:** `01-crm-api-parity` (forge scout `crm-api`)  
**Mode:** Plan / discovery only (no application source edits in this wave)  
**Owner pane:** Herdr `01-crm-api-parity`  
**Related docs:** [CRM_API_PARITY.md](../CRM_API_PARITY.md), [CRM_CUTOVER_CHECKLIST.md](../CRM_CUTOVER_CHECKLIST.md), [CRM_TESTING_STRATEGY.md](../CRM_TESTING_STRATEGY.md), [CRM_GATEWAY_ENV.md](../CRM_GATEWAY_ENV.md)  
**Sibling tracks:** `02-crm-frontend`, `03-quote-deal-e2e`, `06-gateway-ci`

---

## 1. Track name + scope

| Item | Value |
|------|--------|
| **Track** | CRM API parity and cutover readiness |
| **In scope** | `crm-service` REST surface vs Angular CRM libs; gateway CRM predicates; activities API decision; OpenAPI / contract hygiene; test holes that block cutover confidence |
| **Out of scope** | Frappe big-bang migration; Activity Service greenfield; quote→deal Redis E2E implementation (track `03`); FE UX polish beyond parity blockers (track `02`); Jenkins/GHA CI graph edits (track `06`) |
| **Services / packages** | `backend/services/crm-service`, `backend/services/gateway-service` (CRM routes only), `frontend/libs/crm/**`, `frontend/libs/shared/models/**`, `frontend/libs/shared/data-access` (`apiResponseInterceptor`) |

**Success criteria for later implementation wave:** parity matrix stays accurate; no FE-without-API bugs; search/list response shapes consistent; activities deliberately deferred with gateway freeze; OpenAPI usable as FE SoT; cutover checklist pre-cutover items for API/gateway checkable.

---

## 2. Current state (what exists)

### Backend (`crm-service`)

Four controllers under `com.opencrm.crm.*` with full CRUD + domain actions:

| Resource | Base path | Controllers / key files |
|----------|-----------|-------------------------|
| Leads | `/api/v1/leads` | `LeadController`, `LeadService`, Flyway `V1` + `V2__add_lead_qualified_at.sql` |
| Deals | `/api/v1/deals` | `DealController`, pipeline summary, stage machine |
| Contacts | `/api/v1/contacts` | `ContactController` |
| Organizations | `/api/v1/organizations` | `OrganizationController` + `GET /{id}/contacts` |

**Inventory count:** 35 HTTP routes documented in `docs/CRM_API_PARITY.md` match controller `@*Mapping` annotations (verified via grep on `backend/services/crm-service/src/main/java`).

**Cross-cutting already present:**

- `ApiResponse<T>` envelope + `PageInfo` on list/search
- Soft-delete / auditing via shared `BaseEntity` patterns
- Redis `QuoteAcceptedCrmMessage` listener (`event/*`) — cutover-adjacent, owned with track `03`
- Springdoc: `springdoc.api-docs.path=/api-docs`, Swagger UI `/swagger-ui.html`, `OpenApiConfig` group `crm` → `/api/v1/**`
- Per-method `@Operation` / `@Tag` on controllers
- Unit + `@Tag("integration")` Testcontainers suites (`BaseIntegrationTest`, `*ControllerIntegrationTest`, `LeadToDealLifecycleTest`)

### Gateway

```yaml
# backend/services/gateway-service/src/main/resources/application.yml (crm-service route)
Path=/api/v1/leads/**,/api/v1/deals/**,/api/v1/contacts/**,/api/v1/organizations/**
uri: ${CRM_SERVICE_URI:http://localhost:8081}
```

Same predicate duplicated under a second spring profile block (~line 312). **No** `/api/v1/activities/**`. Documented in `CRM_GATEWAY_ENV.md` / cutover checklist.

### Frontend

| Lib | Service | Routes used |
|-----|---------|-------------|
| `crm-leads` | `LeadService` | CRUD, search, contact/qualify/convert/lose, stats |
| `crm-deals` | `DealService` | CRUD, search, advance/win/lose, pipeline |
| `crm-contacts` | `ContactService` | CRUD, search |
| `crm-organizations` | `OrganizationService` | CRUD, search, org contacts |
| `crm-dashboard` | direct `HttpClient` | `/deals/pipeline`, `/deals`, `/leads` |
| `shared-models` | DTO interfaces | Lead/Deal/Contact/Organization + `ApiResponse` |
| `shared-data-access` | `apiResponseInterceptor` | Unwraps envelope; rebuilds Spring-like page when `pagination` + array `data` |

**Parity matrix status (route presence):** every documented API row has an FE caller — no FE-without-API holes for the 35 routes. Optional **query filters** and **response-shape** issues remain (below).

### Activities

- Spring: **no** `Activity*` entity/controller/migration in `crm-service`
- Gateway: **no** activities predicate (correct freeze)
- Legacy: `cpq_beta/crm/api/activities.py` (`get_activities` for CRM Deal / CRM Lead)
- Explicit comments: `CrmServiceApplication.java`, cutover checklist Phase 2

---

## 3. Gaps with evidence (paths / commands)

### G1 — Search response shape inconsistency (HIGH — FE runtime risk)

| Controller | Search return | Evidence |
|------------|---------------|----------|
| `LeadController.search` | `ApiResponse.success(**leads** /* Page */, pageInfo)` | `LeadController.java` ~L113; IT expects `$.data.content` |
| `DealController.search` | same Page-in-`data` | `DealController.java` ~L111 |
| `ContactController.search` | `ApiResponse.success(**contacts.getContent()**, pageInfo)` | `ContactController.java` ~L107 |
| `OrganizationController.search` | List-in-`data` + PageInfo | `OrganizationController.java` ~L108 |

FE services type search as `T[]` and assign directly to signals:

```text
frontend/libs/crm/leads/src/lib/lead.service.ts   search() → this.leads.set(leads)
frontend/libs/crm/deals/src/lib/deal.service.ts   search() → this.deals.set(deals)
frontend/libs/crm/contacts/.../contact.service.ts search() → this.contacts.set(contacts)
frontend/libs/crm/organizations/...               search() → this.organizations.set(orgs)
```

`apiResponseInterceptor` only rebuilds a page object when `data` is an **array**. Lead/Deal search already put a Spring `Page` in `data`, so the UI receives a page object, not `LeadDto[]`. Contact/Org search pass an array + `pagination`, so the interceptor **wraps** into `{ content, number, size, ... }` — again not a bare array.

**Reproduce:**

```bash
# After stack up: search via gateway and inspect JSON shape
curl -s 'http://localhost:8080/api/v1/leads/search?q=test' | jq '.data|type,.pagination'
curl -s 'http://localhost:8080/api/v1/contacts/search?q=test' | jq '.data|type,.pagination'
```

**Interpretation:** Documented “route parity” is green; **payload parity for search is not**. Fix either backend (normalize all search to `List` + `PageInfo` like list endpoints) or FE (always read `.content` / shared page helper). Prefer **backend normalize** so OpenAPI + all clients share one list contract.

### G2 — Optional list filters: API ⊇ FE (MEDIUM — product gap, not cutover blocker)

| API query param | Backend | FE service | UI |
|-----------------|---------|------------|-----|
| `priority` on `GET /leads` | `LeadController.list` | `LeadService.list` only passes `status` | Lead list tabs use status only |
| `status` on `GET /contacts` | `ContactController.list` | `ContactService.list` — no status | No contact status tabs |
| `type` on `GET /organizations` | `OrganizationController.list` | `OrganizationService.list` — no type | No type filter |

Evidence already noted in `docs/CRM_API_PARITY.md` “Optional query params”. Unit coverage exists for filters (`LeadServiceTest` priority/status; `ContactServiceTest` status; `OrganizationServiceTest` type); **controller ITs** cover lead status filter only — not lead priority / contact status / org type.

### G3 — DTO drift: `qualifiedAt` missing on FE `LeadDto` (MEDIUM)

| Layer | Field |
|-------|--------|
| Java `LeadDto` | `Instant qualifiedAt` (`LeadDto.java` L40) |
| Flyway | `V2__add_lead_qualified_at.sql` |
| FE `LeadDto` | **absent** (`frontend/libs/shared/models/src/lib/lead.model.ts`) |
| IT | `LeadControllerIntegrationTest` / lifecycle asserts `$.data.qualifiedAt` |

Not a crash (extra JSON fields ignored), but breaks “OpenAPI / models as SoT” and any UI that should show qualify timestamp.

### G4 — Org contacts FE typing vs page unwrap (MEDIUM)

`OrganizationService.loadOrganizationContacts` types `ContactDto[]` and `set(rows)` directly, while API returns List + `PageInfo` → interceptor produces Spring-like page. Same class of bug as G1 for org detail contacts.

Evidence: `organization.service.ts` L123–139; `OrganizationController.getContacts` L111–129.

### G5 — Activities API decision (DECISION — do not implement in this wave)

| Question | Answer |
|----------|--------|
| Needed for Spring cutover? | **No** — checklist forbids phantom gateway route |
| Legacy capability? | Yes — `cpq_beta/crm/api/activities.py` |
| Target architecture? | Separate Activity Service / Phase 2 vertical (`MODERNIZATION_PLAN.md`, cutover Phase 2) |
| **Decision** | **Defer.** Keep gateway without `/api/v1/activities/**`. Track a Phase-2 spike (read-only timeline) under a later forge wave — not PRs in this track’s implementation slice unless PM expands scope. |

### G6 — OpenAPI / contract hygiene (MEDIUM)

| Gap | Evidence |
|-----|----------|
| Minimal `OpenApiConfig` (group only; no `OpenAPI` Info bean title/version/contact) | `OpenApiConfig.java` |
| No committed exported OpenAPI artifact for FE/codegen/CI drift check | No `openapi.yaml` under `crm-service` / `docs/` |
| Gateway does not proxy `/api-docs` or `/swagger-ui/**` to crm-service | `application.yml` CRM predicates are resource paths only |
| Controllers annotated, but search return types disagree (`Page` vs `List`) → generated schema will advertise inconsistency | G1 |

**Commands:**

```bash
# Direct crm-service (8081)
curl -s http://localhost:8081/api-docs | jq '.paths | keys'
# Via gateway today — expect miss unless path added
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api-docs
```

### G7 — Test holes (MEDIUM–HIGH for cutover confidence)

**Backend integration gaps**

| Hole | Evidence |
|------|----------|
| Lead HTTP update / delete not covered in `LeadControllerIntegrationTest` | No `updateLead` / `deleteLead` / PUT|DELETE cases in that class (service unit tests cover soft-delete) |
| No IT for `GET /leads?priority=` | Only unit `should list leads with priority filter` |
| No IT for contact list `status` / org list `type` | Service tests only |
| Search ITs assert JSON but do not lock List-vs-Page contract across all four resources | Lead search asserts `$.data.content`; contact/org differ |

**Frontend test holes**

| Hole | Evidence |
|------|----------|
| CRM lib `*.spec.ts` are create-smoke only | e.g. `crm-leads.spec.ts` — `it('should create')` |
| No `HttpTestingController` specs for `LeadService` / `DealService` / search unwrap | `rg LeadService frontend/libs --glob '*.spec.ts'` → no service specs |
| No Playwright CRM smoke | `CRM_TESTING_STRATEGY.md` states manual gateway smoke |

**CI alignment (informational):** strategy doc describes `unit-tests` / `integration-tests` profiles and GHA split — verify with track `06`, do not change CI graphs here.

### G8 — Parity doc / stale comments (LOW)

- `api-response.model.ts` comments claim Lead/Deal return `ApiResponse<Page<T>>` while list endpoints actually return `List` + `PageInfo` (interceptor synthesizes page). Misleading for implementers.
- `CRM_API_PARITY.md` does not yet document G1 search shape split or `qualifiedAt` drift.

### G9 — Missing endpoints relative to Frappe (OUT OF SCOPE inventory)

Not required for Spring cutover; list for Phase 2 backlog only:

- Activities / notes / tasks timeline (`activities.py`)
- Broader Frappe CRM DocType APIs beyond Lead/Deal/Contact/Organization

**Do not** add stub routes.

---

## 4. Blast radius / risk notes

> Discovery-only: GitNexus impact was not required for edits (no symbol edits). Before implementing the PRs below, run `gitnexus_impact` on each modified controller/service method and warn on HIGH/CRITICAL.

| Change area | Upstream / consumers | Risk |
|-------------|----------------------|------|
| Normalize search `data` to `List` + `PageInfo` | All four FE `*.search()` methods; any Postman/scripts expecting `$.data.content` on lead/deal search; OpenAPI schemas | **HIGH** if shipped without FE + IT updates in same PR |
| FE-only search unwrap fix | CRM list UIs only | **MEDIUM** — leaves OpenAPI inconsistent if backend unchanged |
| Add `qualifiedAt` to FE models | Lead detail / future UI | **LOW** |
| Expose list filters in FE | List components, query params | **LOW–MEDIUM** |
| Gateway `/api-docs` proxy | Security (`SecurityConfig` already permits swagger on crm-service); gateway surface | **MEDIUM** — avoid exposing swagger in prod without auth gate |
| Activities route | Entire cutover checklist | **CRITICAL** if added without schema — **do not** |
| Quote Redis path | Deal WON | Owned by track `03` — avoid dual edits |

**Cutover readiness (API slice):** Route/gateway freeze is largely ready; **search payload bug (G1/G4)** should be fixed before declaring staging CRUD/search smoke green.

---

## 5. Proposed PRs

### PR-1 — Normalize CRM search (and org-contacts) list contract

| Field | Content |
|-------|---------|
| **Title** | `fix(crm): align search responses to List + PageInfo like list endpoints` |
| **Files** | `LeadController.java`, `DealController.java` (change `success(page, pageInfo)` → `success(page.getContent(), pageInfo)`); optionally keep Contact/Org as-is; update `LeadControllerIntegrationTest` / `DealControllerIntegrationTest` assertions if any assume double-wrapped shapes; FE `lead.service.ts`, `deal.service.ts`, `contact.service.ts`, `organization.service.ts` to consume interceptor page (`content`) consistently; refresh `docs/CRM_API_PARITY.md` |
| **Acceptance** | All four `GET .../search` return `data: T[]` + `pagination`; FE search populates table signals with arrays; org contacts list renders; ITs green under `-Pintegration-tests`; OpenAPI shows array schemas for search |
| **Risk** | **HIGH** — coordinate BE+FE in one PR or stacked BE-then-FE with temporary FE tolerance for both shapes |

### PR-2 — FE model + parity doc drift (`qualifiedAt`, comments)

| Field | Content |
|-------|---------|
| **Title** | `docs(crm): sync LeadDto.qualifiedAt and parity matrix notes` |
| **Files** | `frontend/libs/shared/models/src/lib/lead.model.ts`; `api-response.model.ts` comments; `docs/CRM_API_PARITY.md` |
| **Acceptance** | FE `LeadDto` includes `qualifiedAt?: string`; parity doc lists G1 resolution status and optional filters; model comments match List+PageInfo reality |
| **Risk** | **LOW** |

### PR-3 — FE optional filters (API already supports)

| Field | Content |
|-------|---------|
| **Title** | `feat(crm-fe): pass lead priority, contact status, org type list filters` |
| **Files** | `lead.service.ts` + lead-list UI; `contact.service.ts` + contact-list; `organization.service.ts` + organization-list; shared models if needed |
| **Acceptance** | Query params appear in network tab; empty/invalid filter behavior unchanged; unit/component tests for param wiring |
| **Risk** | **LOW**; can parallelize with track `02-crm-frontend` |

### PR-4 — CRM OpenAPI SoT hardening

| Field | Content |
|-------|---------|
| **Title** | `chore(crm): OpenAPI Info bean + export artifact + optional gateway docs route (dev only)` |
| **Files** | `OpenApiConfig.java` (add `OpenAPI` bean); script or CI step to dump `/api-docs` → `docs/openapi/crm-service.v1.json` (or under `crm-service/api-docs/`); optional gateway route for `/api-docs/**` + `/swagger-ui/**` **dev profile only**; security review note in `CRM_GATEWAY_ENV.md` |
| **Acceptance** | Exported spec lists all 35 paths; search schemas match PR-1; FE can diff models against export; prod gateway does not anonymously expose swagger unless explicitly approved |
| **Risk** | **MEDIUM** (gateway exposure) |

### PR-5 — Test hole closure (cutover confidence)

| Field | Content |
|-------|---------|
| **Title** | `test(crm): lead update/delete ITs, filter ITs, FE service HttpClient specs` |
| **Files** | `LeadControllerIntegrationTest` (PUT/DELETE); filter ITs for priority/status/type; FE `*.service.spec.ts` with `HttpTestingController` covering list/search unwrap; optional thin Playwright or document reliance on `docs/scripts/staging-gateway-smoke.ps1` |
| **Acceptance** | `mvn -pl services/crm-service -am -Pintegration-tests verify`; `nx run-many --target=test --projects=crm-leads,crm-deals,crm-contacts,crm-organizations,shared-models`; Jacoco still ≥ 0.80 |
| **Risk** | **LOW–MEDIUM** (CI time / Docker) |

### PR-6 — Activities decision record only (no code)

| Field | Content |
|-------|---------|
| **Title** | `docs(crm): record activities API deferral for cutover` |
| **Files** | `docs/CRM_CUTOVER_CHECKLIST.md` (checkbox clarification); short ADR or section in `docs/CRM_API_PARITY.md` “Activities: deferred”; link to `cpq_beta/crm/api/activities.py` |
| **Acceptance** | Written decision: no gateway route until controller + schema exist; Phase 2 spike ticket referenced |
| **Risk** | **NONE** (docs) |

---

## 6. Parallelization / dependencies

```text
PR-6 (docs decision) ─────────────────────────────► independent (start immediately)
PR-2 (model/doc drift) ────────────────────────────► independent; soft-depends on PR-1 notes
PR-1 (search contract) ──► blocks reliable search UX + OpenAPI accuracy
         │
         ├─► PR-4 (OpenAPI export)  [after PR-1 schemas stable]
         └─► PR-5 (tests)           [after PR-1; can draft FE specs in parallel]
PR-3 (FE filters) ─────────────────────────────────► parallel with track 02; after or beside PR-2
```

| Dependency | Notes |
|------------|--------|
| Track `02-crm-frontend` | Owns richer filter UX / dashboard; coordinate PR-3 |
| Track `03-quote-deal-e2e` | Redis quote accept → deal WON; do not merge Redis listener changes here |
| Track `06-gateway-ci` | Gateway swagger proxy + CI profile verification |
| Cutover checklist | Staging smoke after PR-1 + gateway URI freeze |

**Suggested implementation order:** PR-6 → PR-1 → PR-2 → PR-5 → PR-4 → PR-3.

---

## 7. Explicit DO NOT TOUCH list

| Do not touch | Why |
|--------------|-----|
| `cpq_beta/**` | Legacy Frappe; Phase 2 only |
| Gateway predicate adding `/api/v1/activities/**` | Phantom route forbidden until real API |
| New Activity entity / communication-service timeline | Separate modernization phase |
| `QuoteAccepted*` Redis/Kafka contracts & processors | Track `03` |
| `docs/ci-contract.yaml`, Jenkinsfile, GHA stage graph | Track `06` / CI SSOT skills |
| CPQ services (pricing, quote, product, approval, …) | Tracks `04` / `05` |
| Auth / Azure AD B2C resource-server hardening beyond CRM docs exposure note | Separate security wave |
| Database drop/restore or production cutover execution | Checklist ops, not this plan |
| Renames of public DTO property names without `gitnexus_rename` + FE sync | Breaks clients |
| Broad “fix FE to expect Page vs List inconsistently” without normalizing backend | Encodes G1 permanently |

---

## 8. Discovery evidence appendix (commands run)

```bash
# Controllers / mappings
rg '@(Get|Post|Put|Delete)Mapping' backend/services/crm-service/src/main/java

# Gateway CRM routes
rg 'leads|deals|contacts|organizations|activities' \
  backend/services/gateway-service/src/main/resources/application.yml

# FE API usage
rg '/api/v1/' frontend/libs/crm --glob '*.ts'

# Activities references
rg 'activities' docs/CRM_*.md backend/services/crm-service -g '*.{md,java,yml}'

# Search success() shapes
rg 'ApiResponse.success' backend/services/crm-service/src/main/java -g '*Controller.java'

# FE smoke-only specs
rg 'should create' frontend/libs/crm --glob '*.spec.ts'
```

**Docs read:** `docs/CRM_API_PARITY.md`, `docs/CRM_CUTOVER_CHECKLIST.md`, `docs/CRM_TESTING_STRATEGY.md`, `docs/CRM_GATEWAY_ENV.md`, `docs/forge/00-orchestrator-status.md`.

---

## 9. Cutover checklist mapping (API track)

| Checklist item | Status after discovery |
|----------------|------------------------|
| Gateway CRM paths only (no activities) | **Met** — keep frozen (PR-6) |
| Parity review API↔FE | **Partial** — routes OK; search payload + filters + `qualifiedAt` open (PR-1/2/3) |
| CRUD smoke via gateway | Blocked on G1 for **search**; CRUD likely OK |
| OpenAPI as SoT | **Weak** — PR-4 |
| Automated tests | Backend strong with holes; FE weak — PR-5 |

---

*End of plan. Implementation requires orchestrator approval (`docs/forge/07-wave-backlog.md` gate).*
