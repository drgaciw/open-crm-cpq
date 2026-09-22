# Open CRM/CPQ — Architecture (reverse-engineered)

**This file is how agents decide what to build vs hold — and how engineers understand the system as it actually is.**

Reverse-engineered from the working tree on **2026-09-19** (all claims verified against `backend/pom.xml`, service `application.yml` files, gateway `SecurityConfig`, `backend/scripts/init-db.sql`, `docker-compose.yml`, `frontend/package.json`, `docs/ci-contract.yaml`, and service source). Steering rules (landed vs target, holds) are preserved and remain normative. It is **not** the 16-month [MODERNIZATION_PLAN.md](../MODERNIZATION_PLAN.md) (target brochure). [AGENT.md](../AGENT.md) phase tables are **target-state** role maps — do not treat them as this week's backlog.

**Steer from:** this page → [docs/adr/README.md](adr/README.md) → [CPQ_API_CONTRACT_MATRIX.md](backlog/CPQ_API_CONTRACT_MATRIX.md) → the latest **authorized** forge wave plan.
**Tip (survey):** `origin/main` @ `c92d5a9` (Wave W COMPLETE `60debf8` / W4 `0b6d3ad`). Dual-read **true**. ADR-006 **Accepted**. ADR-005 **Proposed**.

---

## 1. System overview

**Open CRM/CPQ** is an enterprise CRM and Configure-Price-Quote platform modernized from a Frappe-based Python/Vue 3 application (`cpq_beta/`, read-only reference) into a cloud-native microservices system:

- **11 Spring Boot 4.0.6 / Java 25 services** in a Maven reactor, behind a single **Spring Cloud Gateway** edge.
- **Angular 22 Nx workspace** SPA (`frontend/apps/crm`) that talks **only** to the gateway via `frontend/proxy.conf.json` (`/api` → `:8080`).
- **PostgreSQL 16** (single database `opencrm`, schema-per-service) + **Redis 7** (cache, rate limiting).
- **Kafka** for async integration (document jobs, catalog lifecycle, quote-accepted) with a **transactional outbox** in `libs/common`.
- **Rules**: Drools 10.2 DRL on pricing-service; shared `ProductRuleEvaluationCore` on configuration-engine; OpenL Tablets present **behind a port** but not required for forge.
- **AI**: `ai-service` AgentRouter; runtime is **Azure OpenAI `gpt-4o`**. [ADR-014](adr/ADR-014-jev-openrouter-model-selection.md) accepts Jev-driven OpenRouter model selection as the next direction; implementation is behind a disabled flag; live rollout is pending and ADR-002 is superseded.

Agents using this repo must:

1. Implement against **landed** contracts and code.
2. Treat **target** as destination, not permission to implement.
3. Refuse **holds** unless a new wave plan explicitly un-holds them.

**Do not:** invent `/api/v1/activities/**`; flip `approvals.legacy-local-read-enabled`; enable NgRx; treat GLM-5 as live runtime; Accept ADR-005; ship subscriptions FE; delete product `POST /validate`; put CSRF on domain services.

---

## 2. Monorepo layout

| Path | Role |
|------|------|
| `backend/pom.xml` | Maven reactor — Spring Boot **4.0.6** parent, Java **25**, Spring Cloud **2025.1.1** |
| `backend/libs/{common,security,rules-engine}` | Shared Maven modules (published language — [ADR-011](adr/ADR-011-shared-maven-libs-published-language.md)) |
| `backend/services/*` | 11 microservices (see §4) |
| `backend/docker-compose.yml` | Local Postgres 16, Redis 7 + crm/pricebook/pricing/product/quote/gateway images |
| `backend/scripts/init-db.sql` | Schemas `crm, cpq, pricing, product, quote, communication, ai` |
| `frontend/` | Nx 23.2 workspace — Angular **22.1.6**, TypeScript **6.0.3**, Vitest 4, Tailwind 4, Playwright e2e |
| `frontend/apps/crm` | SPA shell (`app.routes.ts` all `loadChildren`, `shell.component.ts`, `sidebar.component.ts`) |
| `frontend/libs/crm/*`, `frontend/libs/cpq/*`, `frontend/libs/shared/*` | Feature libraries (see §11) |
| `frontend/proxy.conf.json` | Dev proxy `/api` → gateway `:8080` (**committed; do not retarget**) |
| `infra/jenkins/` | Jenkins controller/agent compose + `ai-assist` advisory LLM stages |
| `scripts/ci/` | CI stage scripts; SSOT entrypoint `run-core.sh` |
| `docs/ci-contract.yaml` | Machine-readable Jenkins/GHA stage contract |
| `agents/` | 66 CPQ agent definitions (PRDs for the AI agent fleet) |
| `jev-worker/` | Cloudflare Worker (wrangler) side artifact — JEV local UAT helper |
| `cpq_beta/` | Legacy Frappe source — **read-only reference** |

---

## 3. Runtime topology

Browser (`localhost:4200`) → **gateway `:8080`** (edge CSRF + routes) → domain services. The gateway does **not** embed domain validation (route + rewrite only).

```text
Angular CRM :4200  ──(/api proxy)──▶  gateway-service :8080   Cookie CSRF + Origin allow-list
                                         │
                                         ├─ crm-service :8081              schema crm
                                         ├─ pricing-service :8082          schema pricing
                                         ├─ product-service :8083          schema product
                                         ├─ quote-service :8084            schema quote
                                         ├─ approval-routing :8085
                                         ├─ document-generation :8086      Kafka jobs; FE quote PDFs via quote-service
                                         ├─ contract-asset :8087           SubscriptionDto API; no FE
                                         ├─ configuration-engine :8088     sessions + internal evaluate
                                         ├─ pricebook-service :8089        schema pricing (shared schema name in compose)
                                         └─ ai-service ⚠ yml port 8086     AgentRouter (Azure gpt-4o) — collides with document-generation, see §16
PostgreSQL 16 :5432 (opencrm, schema-per-service)      Redis 7 :6379      Kafka :9092 (external/Azure locally excluded)
```

- `docker-compose.yml` builds **only** postgres, redis, crm, pricebook, pricing, product, quote, gateway (+ optional pgadmin/redis-commander under `tools` profile). configuration-engine, approval-routing, document-generation, contract-asset, ai-service are run locally via `mvnw spring-boot:run` (CPQ services may be down during CRM-only UAT — list APIs empty/failing is expected).
- `communication-service` has gateway routes (`/api/v1/emails/**`, `/calls/**`, `/templates/**` → `lb://communication-service`) but is **not in the Maven reactor** — requests hit the gateway `forward:/fallback/communication` CircuitBreaker fallback.
- Every gateway route has a **Resilience4j CircuitBreaker** with a `/fallback/*` forward (10-call sliding window, 50% failure threshold, 5s open wait).
- Gateway default filters: Redis **RequestRateLimiter** (50 rps replenish / 100 burst, `clientKeyResolver`) + CORS dedupe; CORS allow-list = `${CORS_ALLOWED_ORIGINS:${FRONTEND_URL:http://localhost:4200}}` — **no `*` wildcards**.
- `/api/v1/cpq/...` **aliases** rewrite to `/api/v1/...` per segment (verified in `application.yml` routes + `CpqGatewayRoutesTest`). Canonical path registry: [CPQ_API_CONTRACT_MATRIX.md](backlog/CPQ_API_CONTRACT_MATRIX.md).

### Who owns `/api/v1/*` (do not mix)

| Path prefix | Owner | FE (landed) |
|-------------|--------|-------------|
| `/api/v1/leads`, `/deals`, `/contacts`, `/organizations` | crm-service | `libs/crm/*` |
| `/api/v1/pricing/**`, `/pricing-customers/**`, `/rule-sets/**`, `/rules/**` | **pricing-service** | simulator, pricing-customers, rule-sets |
| `/api/v1/price-books/**` | **pricebook-service** | price-books |
| `/api/v1/products/**`, `/product-bundles/**`, `/product-options/**`, `/product-attributes/**`, `/configuration/**` (rules + **retained** validate) | **product-service** | catalog, product-bundles |
| `POST /api/v1/configuration/validate` | **gateway rewrite** → CE `POST /internal/v1/configurations/evaluate` | agents; product path **retained** |
| `/api/v1/configurations/**` | **configuration-engine** (sessions, beta) | none |
| `/api/v1/quotes/**` including `/quotes/approvals/**` | **quote-service** (BFF approvals) | quotes, approvals inbox |
| `/api/v1/approvals/**` | **approval-routing** (canonical; **not** FE) | FE must **not** call this |
| `/api/v1/documents/**` | document-generation | FE quote docs use **quote-service** documents API |
| `/api/v1/subscriptions/**` | contract-asset | **no FE** |
| `/api/v1/erp/**` | gateway stub → `forward:/fallback/erp/unavailable` (S2A-7 reservation) | — |
| `/api/v1/activities/**` | **does not exist** | do not invent |

**Disambiguate:** `/api/v1/rules` (pricing DRL) ≠ `/api/v1/configuration/rules` (product config rules). `/api/v1/quotes/approvals` ≠ `/api/v1/approvals`. The **validate route must stay ordered before** `configuration/**` (SCG first-match would keep agents on the product `List<String>` path).

---

## 4. Backend services

| Service | Port | Schema | Owns |
|---------|------|--------|------|
| gateway-service | 8080 | — | Edge: routes, CPQ alias rewrites, CSRF, rate limit, circuit breakers, JWT (prod), fallbacks |
| crm-service | 8081 | crm | Leads, Deals, Contacts, Organizations; quote-accepted consumer side |
| pricing-service | 8082 | pricing | Execute/simulate pricing, rule-sets, rules (DRL), pricing-customers |
| product-service | 8083 | product | Catalog, options, attributes, bundles, configuration **rules CRUD** + retained inline validate |
| quote-service | 8084 | quote | Quotes, lines, documents API, **approvals BFF** (single-writer → approval-routing; dual-read legacy local reads) |
| approval-routing | 8085 | approval | Canonical approvals and complete quote history |
| document-generation | 8086 | document | Kafka document job consumer + REST |
| contract-asset-management | 8087 | contract | Subscriptions — DTO-only, `activate` → `ErpAdapter.syncToErp` |
| configuration-engine | 8088 | configuration | Configuration sessions; `POST /internal/v1/configurations/evaluate`; `ProductRuleEvaluationCore` |
| pricebook-service | 8089 | pricing | Price books + entries (published = immutable) |
| ai-service | 8090 | ai | Azure baseline; Jev/OpenRouter selection and execution opt-in; agents disabled by default |

Per-service package convention: `com.opencrm.{service}/{config,controller,service,repository,entity,dto,mapper,event,exception,validator}`. Services run with **virtual threads enabled** (`spring.threads.virtual.enabled: true` — gateway, quote, ai verified).

---

## 5. Cross-cutting backend conventions (landed, enforced by `libs/common`)

| Convention | Evidence |
|------------|----------|
| All entities extend `BaseEntity` — Long ID + UUID, `created_at/updated_at/created_by/updated_by`, `@Version` optimistic locking, `is_deleted` **soft delete** (`softDelete()`) | `backend/libs/common/.../BaseEntity.java` |
| All REST responses wrapped in `ApiResponse<T>` (`success/message/errorCode/traceId/data/errors/pagination/timestamp`) | `backend/libs/common/.../ApiResponse.java` |
| **Entities never at the API boundary** — MapStruct DTOs only ([ADR-006](adr/ADR-006-entities-never-at-api-boundary.md), Accepted) | verified: no `ApiResponse<{Subscription,ConfigurationSession,ApprovalRequest,DocumentGenerationJob}>` (non-Dto) hits in controllers |
| Value objects `Money`, `Percentage` in shared libs | `backend/libs/common/.../valueobject/` |
| Errors via `BusinessException(code)` / `ResourceNotFoundException` | `backend/libs/common/.../exception/` |
| Flyway for all schema changes (`V{version}__{description}.sql`), `ddl-auto: validate` | every service `application.yml` |
| Security context via `UserContext` / `SecurityUtils` (`libs/security`) | `backend/libs/security/` |
| Domain services: `csrf.disable()` + `SessionCreationPolicy.STATELESS` ([ADR-004](adr/ADR-004-csrf-edge-boundary.md)) | `crm-service/config/SecurityConfig.java` etc. |
| Azure Boot-4 workarounds: compatibility verifier off; Azure token/AAD-B2C/Event-Hubs-Kafka autoconfig **excluded** (system property + per-service `spring.autoconfigure.exclude`) | `backend/pom.xml`, quote-service yml |

---

## 6. Data architecture

- **One PostgreSQL database `opencrm`, isolated schemas** (`crm, cpq, pricing, product, quote, communication, ai, configuration, approval, document, contract`). Pricing-service owns all migrations for the shared pricing/pricebook schema; pricebook's Flyway remains disabled. Existing standalone databases require explicit compatibility overrides; see [database alignment](database-schema-alignment.md).
- Extensions: `uuid-ossp`, `pg_trgm`; **pgvector commented out** until needed.
- App role `opencrm_app` with schema-scoped grants (bootstrap only).
- Quote soft-delete is enforced with domain guards: `QUOTE_ACCEPTED_NO_DELETE` on accepted quotes (`QuoteService.java:119`), `softDelete()` on lines.
- Price books: once published (`effectiveDate` present and `<=` today) → `PRICE_BOOK_PUBLISHED_IMMUTABLE`; list-row delete still **R2 HELD**.
- Hibernate `ddl-auto: validate` everywhere — schema drift fails fast; all changes go through Flyway.

---

## 7. Event architecture

`libs/common` ships a **published language** for async integration (reverse-engineered as [ADR-010](adr/ADR-010-kafka-outbox-events.md)):

- `KafkaTopics` — canonical topic names; `EventContractRegistry` — contract registry.
- **Transactional outbox**: `OutboxEvent` / `OutboxEventRepository` / `OutboxEventPublisher` / `OutboxRelayPublisher` — write event row in the same TX as the aggregate, relay publishes to Kafka.
- Versioned event DTOs under `event/dto/v1` (`PriceCalculatedEventV1`, `QuoteAcceptedEventV1`, `DiscountApprovalRequiredEventV1`, `ProductRetiredEventV1`, `SubscriptionRenewalEligibleEventV1`) with contract tests (`PublishedEventContractTest`, `PublishedLanguageEventV1Test`).
- Domain events present: `ConfigurationCommittedEvent`, `ContractExecutedEvent`, `ProductCatalogLifecycleEvent`, `ProductRetiredEvent` (+ CE `ProductRetiredEventConsumer`), `QuoteAcceptedCrmMessage` (CRM sync).
- **document-generation** consumes Kafka (`group-id: document-generation`, `auto-offset-reset: earliest`) for document jobs; quote-service also produces (String serializer). Kafka S2B-9 (event-driven rule invalidation) and agent Kafka #241 remain **held**.

---

## 8. Rules engine

- `backend/libs/rules-engine` exposes a **port abstraction**: `RuleEnginePort` / `HybridRuleEnginePort` / `RulesEngine`, `RulesEngineType`, shared fact model (`QuoteFact`, `ProductFact`, `CustomerFact`, `PricingWaterfallFact`, `QuoteLineItemFact`), priority + conflict-resolution, compile cache (`RulesCacheManager`).
- **Drools 10.2** implementation landed (`DroolsRuleCompiler`, `DroolsRulesEngine`, `DroolsSessionFactory`, KieFileSystem compiler) with compatibility-evidence tests (`Drools844CompatibilityEvidenceTest`, `PricingDrlCorpusCompileTest`). pricing-service executes rule-sets/rules as DRL.
- **OpenL Tablets** (`OpenLRulesEngine`, dep `5.27.6`) exists behind the same port but is **not required** for forge; full SpEL/OpenL rollout **held**.
- Configuration evaluation: `configuration-engine` `ProductRuleEvaluationCore` — hard REQUIRES/EXCLUDES/VALIDATES, soft RECOMMENDS, DEFAULT expand; full S2B-8 `ConstraintSolver` remains follow-on. Ownership split per [backlog ADR](backlog/ADR-configuration-api-reconciliation.md): product-service owns rule **persistence/CRUD**; CE owns **evaluation**; gateway owns the **agent-stable URL**.

---

## 9. AI

- `ai-service` = **AgentRouter** (`/api/v1/ai/**`, `/api/v1/copilot/**` through the gateway). Agents disabled by default (`OPENCRM_AI_AGENTS_ENABLED:false`); Redis state store; Postgres `ai` schema audit logging.
- **Runtime: Azure OpenAI `gpt-4o`** (`spring.ai.azure.openai`, `AZURE_OPENAI_*`, `deployment-name: gpt-4o`). Fixed-model ADR-001 and ADR-002 are historical. [ADR-014](adr/ADR-014-jev-openrouter-model-selection.md) defines Jev-driven selection using OpenRouter models/prices; [requirements](backlog/REQ-jev-openrouter-model-selection.md) retain live rollout gates.
- CI LLM path is separate ([ADR-003](adr/ADR-003-ci-llm-integration.md)): `LLM_ENDPOINT`-driven advisory stages in Jenkins only — **never** point `ai-assist` at `ai-service:8086`.
- `agents/` holds the CPQ agent PRD fleet (66 files — 28 numbered `cpq-agent-*` PRDs plus supporting docs; the ADR-001/002 fleet target is 37 agents); AI parse of validate results (H2) and quote preflight via `ProductServiceClient` are landed consumers.

---

## 10. Security

[ADR-004](adr/ADR-004-csrf-edge-boundary.md) **Accepted** — CSRF **gateway-only**.

| Layer | Landed (verified 2026-09-19) |
|-------|--------|
| Gateway | `CookieServerCsrfTokenRepository.withHttpOnlyFalse` + `ServerCsrfTokenRequestAttributeHandler` (`SecurityConfig.java`, dev + prod chains) + `CsrfProtectionFilter` Origin/Referer allow-list; `security.csrf.allowed-origins` = `CORS_ALLOWED_ORIGINS`/`FRONTEND_URL` (default `http://localhost:4200`). **No** `*`/`*.azurewebsites.net` |
| Gateway auth | dev profile: `permitAll` (CSRF still on); default profile: JWT `oauth2ResourceServer`, `/actuator|api-docs|swagger-ui` public, rest `authenticated()` |
| Domain services | `csrf.disable()` + `STATELESS` (Bearer/mesh) — verified in crm-service; **do not** merge Jules #402/#404 Cookie CSRF on microservices |
| Identity | Azure AD B2C config present, **`enabled: false` by default** (`AZURE_AD_B2C_ENABLED:false` in compose); AAD-B2C autoconfig excluded on Boot 4 |
| `SECURITY_OPEN` | Must not be a production-reachable bypass |
| GitNexus | `CsrfProtectionFilter` / `SecurityConfig` often **UNKNOWN** — confirm with `rg` before edits |

---

## 11. Frontend

| Path | Role |
|------|------|
| `frontend/apps/crm` | Shell: `app.routes.ts` (all `loadChildren`), `sidebar.component.ts`, `shell.component.ts` |
| `frontend/libs/crm/{dashboard,organizations,contacts,leads,deals}` | CRM lists/details |
| `frontend/libs/cpq/{catalog,quotes,approvals,price-books,pricing-customers,rule-sets,product-bundles,pricing-simulator,pricing-data-access,pricing-ui}` | CPQ |
| `frontend/libs/shared/{ui,models,data-access,utils}` | EmptyState, ConfirmDialog, PageHeader, DataTable; DTOs |

**Stack (verified):** Angular **22.1.6** + Material/CDK/Aria, Nx **23.2.1**, TypeScript **6.0.3**, Vite 8 + **Vitest 4** (Analog `setupTestBed`), Tailwind **4.1**, Playwright **1.55** e2e (`e2e:smoke|jev|chaos`).

**State:** **signals + HttpClient services** — [ADR-007](adr/ADR-007-frontend-signals-no-state-library.md). **NgRx held** (no `@ngrx/*` in `frontend/package.json`). Standalone components, OnPush, strict TS, reactive forms, Tailwind utilities.

**Shell governance:** at most **one** new portal per wave may own `app.routes.ts` + `sidebar.component.ts` + tsconfig path + `test:cpq`. Polish waves prefer **zero** new routes. Sidebar (landed): CRM dashboard/orgs/contacts/leads/deals; CPQ catalog, quotes, approvals, price-books, pricing-customers, rule-sets, bundles, pricing simulator. **No** subscriptions, standalone configuration-rules, or Activities.

Vitest: `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`.

---

## 12. CI/CD

[ADR-003](adr/ADR-003-ci-llm-integration.md) **Accepted**. SSOT: [docs/ci-contract.yaml](ci-contract.yaml) → `scripts/ci/run-core.sh`.

- **Jenkins primary** on PR/push (multibranch, status context `jenkins/opencrm-cpq-ci`); GHA `.github/workflows/ci.yml` is **`workflow_dispatch` manual fallback only**.
- Toolchain: Temurin **25** (Maven), Node **22.22.3**, Jenkins WAR `2.492.3-lts-jdk21`; service runtime image `eclipse-temurin:25-jre-alpine`.
- Stages: Toolchain (runs blocking non-AI `verify-drift.py`) → Shared Libraries → Parallel fast checks (Backend Unit `-Punit-tests` Docker-free, CPQ Beta Python, Frontend) → Backend Integration (needs Docker/Testcontainers) → optional **AI Assist** (Jenkins-only, `AI_CI_ENABLED=true`, advisory unless `AI_CI_BLOCKING=true`) → Security Audit (non-blocking).
- Drift prevention rules live in `ci-contract.yaml` (change stage scripts + run-core arms together; no GHA push/PR triggers without cutover doc update; no ai-assist in GHA without ADR amendment).

**ITs:** Testcontainers (Postgres 16, Redis 7). If login `id` lacks `docker` but `getent group docker` includes the user, wrap Maven:

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25
newgrp docker <<'EOF'
cd backend && ./mvnw -B -pl services/<module> -am test
EOF
```

Do not "fix" CI by inventing runners (#238 **HOLD**).

---

## 13. Testing strategy

| Layer | Tooling |
|-------|---------|
| Java unit | Maven Surefire, `unit-tests` profile (`excludedGroups=integration`, JaCoCo skipped) — Docker-free |
| Java integration | `integration-tests` profile, Testcontainers Postgres/Redis; Docker Engine API `api.version=1.44` pinned for Docker 29+ |
| Frontend unit | Vitest 4 + `@analogjs/vitest-angular`, `TestBed` + `vi.fn()` (not Jasmine spies), jsdom |
| E2E / UAT | Playwright scripts (`e2e/uat-crm-smoke.mjs`, `e2e/uat-jev-local.mjs`, `e2e/chaos-crm.mjs`); browser automation preferred for UAT |
| Contracts | `PublishedEventContractTest`, `CpqGatewayRoutesTest` (CPQ alias rewrites), H7 dual-read flag-off unit proof, `Drools844CompatibilityEvidenceTest` |

---

## 14. CPQ contracts (runtime)

| Rule | Landed |
|------|--------|
| Config validate | Gateway → CE evaluate + `ConfigurationValidationResult` (`libs/common`). Product `POST /api/v1/configuration/validate` **retained** (I3 flat O(rules), `List<String>`). Do not delete. Do not put domain validation in gateway |
| Dual-read | quote-service `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — [ADR-005 Proposed](adr/ADR-005-approvals-legacy-dual-read.md); `ApprovalService` branches on it (7 sites). Flip is **HIGH**. Hold **J–W** |
| Quote DELETE | Soft-delete. ACCEPTED → `QUOTE_ACCEPTED_NO_DELETE` (verified `QuoteService.java:119`) |
| Price books | Published → `PRICE_BOOK_PUBLISHED_IMMUTABLE`. List-row delete **R2 HELD** |
| Approvals FE | Quote BFF only ([ADR-013](adr/ADR-013-quote-bff-approvals-single-writer.md)); canonical writes single-writer to approval-routing |
| Subscriptions | DTO API on `main`; activate still `ErpAdapter.syncToErp`; **no FE** |

---

## 15. Landed vs target

Copy this table into planning. **Do not present the Target column as current.**

| Dimension | Landed (forge, 2026-09) | Target / held |
|-----------|-------------------------|---------------|
| Backend | Spring Boot **4.0.6** / Java **25** reactor — 11 services (§4) | Azure AKS; remaining identity & comms. **Do not** downgrade Boot |
| Frontend | Angular **22** Nx `apps/crm` + libs. **Signals + HttpClient** ([ADR-007](adr/ADR-007-frontend-signals-no-state-library.md)) | NgRx 19+ **held**; customer-portal / admin-console **not** in tree |
| Database | PostgreSQL 16 + Redis 7 (compose); **one DB `opencrm`, schemas per service** ([ADR-008](adr/ADR-008-postgres-schema-per-service.md)) | Same + production Azure. pgvector commented until needed |
| Infrastructure | Docker Compose local; **Jenkins primary CI**; GHA manual fallback — [ADR-003](adr/ADR-003-ci-llm-integration.md) | Azure AKS / Terraform / Helm |
| AI | `ai-service` AgentRouter; **runtime Azure OpenAI `gpt-4o`** | [ADR-014](adr/ADR-014-jev-openrouter-model-selection.md) Jev selects qualifying OpenRouter models at lowest total cost; **implemented behind a disabled flag; live rollout pending** |
| Rules | Drools DRL on pricing; CE `ProductRuleEvaluationCore`; port abstraction ([ADR-012](adr/ADR-012-rules-engine-port-abstraction.md)); OpenL **not** required | OpenL Tablets + full Drools / full SpEL **held** |
| Events | Kafka + outbox + `v1` published language ([ADR-010](adr/ADR-010-kafka-outbox-events.md)); document-generation consumer | Kafka S2B-9 rule invalidation, agent Kafka #241 **held** |
| Identity | Gateway JWT config present; Azure AD B2C **`enabled: false` by default** | Full OAuth2/OIDC identity service |
| Search | CRM/quote list + `PageInfo` over gateway | Elasticsearch 8 **not in tree** |
| Comms | Gateway stubs `/api/v1/emails/**` etc. → `communication-service` (**not** in Maven reactor) | Twilio / SendGrid / WhatsApp hub. **#430 on `main` — do not undo** |
| Activities | **No** schema, controller, or FE | Do **not** invent `/api/v1/activities/**` |

**Forge status:** Waves **A–W COMPLETE** on `main`; hygiene N′ COMPLETE; Wave **T skipped** (do not name T1/T2). Not early stage.

---

## 16. Reverse-engineering findings (2026-09-19) — drift & risks

Observations from code that plans should account for (none authorize change by themselves):

1. **AI port collision resolved** — AI uses `8090`; document generation retains `8086`. Gateway default/dev AI routes have explicit HTTP targets. AI is an opt-in Compose profile.
2. **Database defaults aligned** — configuration, approval, document and contract services use isolated schemas in `opencrm`. Existing standalone databases need explicit legacy overrides until a separately reviewed migration; [runbook](database-schema-alignment.md).
3. **Compose coverage gap** — configuration-engine, approval-routing, document-generation and contract-asset still use separate local launch procedures; Azure autoconfig exclusions remain necessary.
4. **communication-service route remains `lb://`** — with no registry it falls through to the CircuitBreaker fallback; ERP remains an explicit stub (`forward:/fallback/erp/unavailable`).
5. **Azure-on-Boot-4 shims are load-bearing** — root Surefire system properties + per-service `spring.autoconfigure.exclude` (token credential, compatibility verifier, AAD B2C, Event Hubs Kafka OAuth2). Removing them breaks Boot 4 startup; keep them until spring-cloud-azure 7.x.
6. **Existing ADR statuses re-verified against code** — see [adr/README.md](adr/README.md) verification column (ADR-001…006 + backlog ADR all match their stated status).

---

## 17. Holds (do not implement unless a wave plan un-holds)

| Hold | Why |
|------|-----|
| Activities API / `/api/v1/activities/**` | No schema/controller |
| CI #238 | Runners |
| Agent Kafka #241 | Stack B2 / S2A-2 |
| Dual-read YAML/env flip | **HIGH**; ADR-005 Proposed |
| NgRx | Signals + HTTP ([ADR-007](adr/ADR-007-frontend-signals-no-state-library.md)) |
| Subscriptions / contract-asset FE | ERP on activate |
| Full SpEL / OpenL | Partial CE/Drools only |
| Kafka S2B-9 | Event bus |
| Gateway domain validation | Route + adapt only ([ADR-009](adr/ADR-009-gateway-edge-only.md)) |
| Delete product-service validate | Dual-run retained |
| WhatsApp #430 undo | On `main` |
| **R2** price-book list-row delete | N stretch; still held |
| Named Wave T / T1 / T2 | Skipped |
| Jev/OpenRouter selection as runtime | ADR-014 Accepted; implementation behind a disabled flag; live rollout pending. ADR-002 GLM-5 direction superseded |
| Enabling any Jev advisory flag outside local/offline | [ADR-015](adr/ADR-015-jev-advisory-decision-support.md) un-holds advisory + shadow **code** only (`enabled:false`, no-op transport). Turning a flag on beyond local/offline needs the ADR-015 shadow → canary gates; `names` duplicate mode needs data-processing sign-off |
| Accept ADR-005 | Flip not authorized |

Open GitHub issues: **#238**, **#241** only. Linear/Notion Open CRM: **none** (ignore cmano-clone / Aegis).

---

## 18. How agents use GitNexus

Index lives on **open-crm-cpq** (this workspace). Disambiguate repos when more than one is indexed.

```bash
node .gitnexus/run.cjs impact "SymbolName" --direction upstream --repo . --file path/to/File.java
```

| Rule | |
|------|--|
| Before symbol edits | `impact` upstream; warn HIGH/CRITICAL |
| **UNKNOWN ≠ unused** | HTTP controllers, Spring filters, property access. Confirm with `rg` |
| CLI stale | Untracked `.worktrees/` is often **false stale**. Compare indexed commit vs HEAD |
| Skip analyze | If index is many commits behind and analyze is slow; plan from `rg` + files + `query` |
| Rename | Do not find-and-replace symbols; use graph-aware rename |
| Before commit | `detect-changes` on the **worktree that has the edits** |

---

## 19. Forge waves (short) and ADR catalog

| Wave | Role |
|------|------|
| A–M | Foundation through quote header / price-book entries / clone lines — **COMPLETE** |
| N | Price-book header, pricing-customers portal, quote soft-delete — **COMPLETE** (+ N′ H1+H2) |
| O | Rule-sets portal, catalog options/attrs, subscription DTO — **COMPLETE** |
| P | Bundles portal (shell), rule impact, catalog config-rules — **COMPLETE** |
| Q, S, U, V | FE UX / clean-build polish — **COMPLETE** |
| **T** | **Skipped** — do not name T1/T2 |
| W | CE / routing / document-job DTOs; **Accept ADR-006** — **COMPLETE** (`60debf8` / W4 `0b6d3ad`) |

Plans: [23](forge/23-wave-n-architecture.md)–[35](forge/35-wave-w-architecture.md). [07-wave-backlog.md](forge/07-wave-backlog.md) is **historical**. Implementers **must not** edit [forge/00-orchestrator-status.md](forge/00-orchestrator-status.md).

**Full ADR catalog with statuses:** [adr/README.md](adr/README.md). Summary:

| ADR | Status | Path |
|-----|--------|------|
| ADR-001 LLM Claude Sonnet 4.6 | **Superseded** by ADR-002 | [adr/ADR-001-primary-llm-claude-sonnet-4-6.md](adr/ADR-001-primary-llm-claude-sonnet-4-6.md) |
| ADR-002 GLM-5 | **Superseded** by ADR-014; never implemented | [adr/ADR-002-primary-llm-glm-5.md](adr/ADR-002-primary-llm-glm-5.md) |
| ADR-014 Jev/OpenRouter selection | **Accepted** decision; implementation behind a disabled flag (default Azure gpt-4o) | [adr/ADR-014-jev-openrouter-model-selection.md](adr/ADR-014-jev-openrouter-model-selection.md) |
| ADR-015 Jev advisory decision support | **Accepted** decision; advisory + shadow in domain services, flag-off + no-op transport — not runtime behavior | [adr/ADR-015-jev-advisory-decision-support.md](adr/ADR-015-jev-advisory-decision-support.md) |
| ADR-003 CI / Jenkins | Accepted | [adr/ADR-003-ci-llm-integration.md](adr/ADR-003-ci-llm-integration.md) |
| ADR-004 CSRF edge | Accepted | [adr/ADR-004-csrf-edge-boundary.md](adr/ADR-004-csrf-edge-boundary.md) |
| ADR-005 dual-read | **Proposed** — do not flip | [adr/ADR-005-approvals-legacy-dual-read.md](adr/ADR-005-approvals-legacy-dual-read.md) |
| ADR-006 entities | **Accepted** — no subscriptions FE | [adr/ADR-006-entities-never-at-api-boundary.md](adr/ADR-006-entities-never-at-api-boundary.md) |
| Config API | Accepted; product validate retained | [backlog/ADR-configuration-api-reconciliation.md](backlog/ADR-configuration-api-reconciliation.md) |
| ADR-007…013 | **Accepted (reverse-engineered)** — documented from code 2026-09-19 | [adr/README.md](adr/README.md) |

---

## 20. File map (landed)

| Path | Role |
|------|------|
| `backend/pom.xml` | Reactor, Boot **4.0.6**, Java **25** |
| `backend/docker-compose.yml` | Postgres 16, Redis 7, service ports |
| `backend/scripts/init-db.sql` | Schemas `crm`, `cpq`, `pricing`, `product`, `quote`, `communication`, `ai` |
| `backend/libs/common/` | `BaseEntity`, `ApiResponse`, `Money`/`Percentage`, events + outbox, `ConfigurationValidationResult` |
| `backend/libs/security/` | `UserContext`, `SecurityUtils` |
| `backend/libs/rules-engine/` | Rule engine port, Drools impl, shared facts |
| `backend/services/gateway-service/` | Edge CSRF + routes + CPQ aliases + circuit breakers |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | Agent-contract paths |
| `docs/ci-contract.yaml` | Jenkins/GHA stage SSOT |
| `frontend/proxy.conf.json` | `/api` → gateway `:8080` (committed; keep) |
| `cpq_beta/` | Legacy Frappe — read-only reference |
