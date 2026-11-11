# Open CRM/CPQ - AI Agent Development Guide

**Steering (read first):** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/adr/README.md](docs/adr/README.md) · forge waves [23](docs/forge/23-wave-n-architecture.md)–[35](docs/forge/35-wave-w-architecture.md).  
**Do not** treat `MODERNIZATION_PLAN.md` or the phase tables below as “what to build this week.” They are **target-state** roadmaps. Forge **holds** (do not implement unless a wave plan says so): CI #238, agent Kafka #241, dual-read YAML flip, NgRx, contract-asset FE, full SpEL, Kafka S2B-9, gateway domain validation, product-validate deletion, WhatsApp #430 undo, R2 price-book list-row delete, Jev/OpenRouter model **selection** as runtime (ADR-014). Narrow exception: [ADR-015](docs/adr/ADR-015-jev-advisory-decision-support.md) authorizes advisory + shadow Jev **code** in domain services, flag-off with a no-op transport — it never gates a decision, and enabling a flag beyond local/offline is itself held behind the ADR-015 gates.

## Project Overview

**Open CRM/CPQ** is an enterprise CRM and Configure-Price-Quote platform modernized from a Frappe-based Python/Vue 3 application into a cloud-native microservices architecture.

| Dimension | Legacy (Frappe) | Landed (forge, 2026-08) | Target (not all landed) |
|-----------|-----------------|-------------------------|-------------------------|
| Backend | Frappe Framework (Python) | Spring Boot 4.1.2 parent / Java 25 microservices (crm, gateway, product, pricing, pricebook, quote, configuration-engine, approval-routing, document-generation, contract-asset, ai-service) | Azure AKS / remaining identity & comms |
| Frontend | Vue 3 / Frappe UI | Angular **22** Nx CRM app + CPQ libs (catalog, quotes, approvals, price-books, pricing-customers, rule-sets, product-bundles, simulator); **signals + HttpClient**, **not** NgRx | NgRx 19+ (**held**); customer-portal / admin-console apps |
| Database | MariaDB 10.6 | PostgreSQL 16 + pgvector (compose); schemas per service | Same + production Azure |
| Infrastructure | Docker / GitHub Actions | Docker Compose local; **Jenkins primary CI** (GHA `workflow_dispatch` fallback) — [ADR-003](docs/adr/ADR-003-ci-llm-integration.md) | Azure AKS / Terraform / Helm |
| AI | None | `ai-service` AgentRouter; **runtime** Azure OpenAI `gpt-4o` (`application.yml`) | [ADR-014](docs/adr/ADR-014-jev-openrouter-model-selection.md): Jev selects qualifying OpenRouter models at lowest total cost; **implemented behind a disabled flag; live rollout pending**; supersedes GLM-5 ADR-002 |
| Rules | None | Drools DRL on pricing-service; CE `ProductRuleEvaluationCore`; OpenL **not** required for forge | OpenL Tablets + full Drools |

**Current implementation status (2026-09-13):** **Not early stage.** Forge waves **A–V COMPLETE** on `main`; hygiene N′ COMPLETE; Wave T **skipped**; **Wave W** this closeout (W1∥W2∥W3 BE DTO wraps — configuration-session / approval-routing / document-job; W4 Accept ADR-006 — [35-wave-w-architecture.md](docs/forge/35-wave-w-architecture.md)). Controllers, services, repositories, Flyway, Vitest, and Maven tests **exist**. Angular CRM scaffold **exists** (`frontend/apps/crm`). Dual-read default remains `${APPROVALS_LEGACY_LOCAL_READ:true}` (**HIGH** to flip; hold **J–W**). Activities note and task API exists under `/api/v1/activities` (DRG-520 slice; email, meeting, and mailbox ingestion are still open). Do not add a mailbox route. N residual **R1** landed in Q2; **R2** price-book list-row delete still **HELD**; no N′′.

---

## Agent-to-Phase Mapping

Use the following agents for each modernization phase. Launch the recommended agent using Claude Code's Task tool.

### Phase 1: Foundation (Infrastructure + Backend Skeleton + Frontend Scaffold)

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Azure infrastructure (AKS, PostgreSQL, Redis) | `cloud-architect` | `deployment-engineer` |
| Terraform IaC modules | `cloud-architect` | - |
| CI/CD pipelines (GitHub Actions) | `deployment-engineer` | - |
| Spring Boot service scaffolding | `backend-architect` | - |
| Identity Service (OAuth2/OIDC) | `backend-architect` | `cloud-architect` |
| API Gateway configuration | `backend-architect` | - |
| Nx workspace + Angular scaffold | `frontend-developer` | `typescript-pro` |
| Shared UI component library | `ui-designer` | `frontend-developer` |
| Tailwind + design tokens setup | `ui-designer` | - |
| Developer experience (DX) setup | `dx-optimizer` | - |

### Phase 2: Core CRM Services

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Contact/Lead/Deal REST APIs | `backend-architect` | `full-stack-developer` |
| JPA repositories + query optimization | `database-optimizer` | `backend-architect` |
| PostgreSQL schema + Flyway migrations | `database-optimizer` | `postgresql-pglite-pro` |
| Search Service (Elasticsearch) | `backend-architect` | - |
| CRM Portal pages (Angular) | `frontend-developer` | `react-pro` (patterns) |
| NgRx state management | `typescript-pro` | `frontend-developer` |
| API integration + data-access libs | `full-stack-developer` | `typescript-pro` |
| Unit + integration tests | `backend-architect` | `error-detective` |

### Phase 3: Communication Hub

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Twilio SDK integration | `backend-architect` | `full-stack-developer` |
| Email service (SendGrid/Graph API) | `backend-architect` | - |
| WhatsApp Business API | `backend-architect` | - |
| WebSocket notifications | `full-stack-developer` | `frontend-developer` |
| Unified inbox UI | `frontend-developer` | `ux-designer` |
| Communication templates engine | `backend-architect` | `prompt-engineer` |

### Phase 4: CPQ Engine

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Product Catalog Service | `backend-architect` | `database-optimizer` |
| Configuration Engine + rules | `backend-architect` | - |
| OpenL Tablets pricing rules | `backend-architect` | `data-engineer` |
| Drools complex business rules | `backend-architect` | - |
| Quote builder + PDF generation | `full-stack-developer` | `frontend-developer` |
| Approval workflow engine | `backend-architect` | - |
| CPQ data model optimization | `database-optimizer` | - |

### Phase 5: AI Agents

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Spring AI + Azure OpenAI setup | `ai-engineer` | `backend-architect` |
| RAG pipeline (pgvector + AI Search) | `ai-engineer` | `data-engineer` |
| Sales Copilot agent design | `ai-engineer` | `prompt-engineer` |
| Analytics Agent | `ai-engineer` | `data-scientist` |
| Customer Chatbot | `ai-engineer` | `prompt-engineer` |
| Agent prompt engineering | `prompt-engineer` | `ai-engineer` |
| Copilot sidebar UI | `frontend-developer` | `ux-designer` |

### Phase 6: Advanced Features

| Task | Primary Agent | Supporting Agent |
|------|--------------|-----------------|
| Customer Portal frontend | `frontend-developer` | `ux-designer` |
| Admin Console frontend | `frontend-developer` | `ui-designer` |
| Load testing + optimization | `performance-engineer` | `database-optimizer` |
| Security audit | `backend-architect` | `error-detective` |
| Data migration from Frappe | `data-engineer` | `python-pro` |

### Cross-Cutting (Any Phase)

| Task | Agent |
|------|-------|
| Bug investigation | `error-detective` or `issue-debugger` |
| Production incidents | `incident-responder` or `devops-incident-responder` |
| Infrastructure troubleshooting | `devops-troubleshooter` |
| Code quality reviews | `code-simplifier` |
| Legacy Frappe code analysis | `legacy-modernizer` |
| GraphQL API layer (if adopted) | `graphql-architect` |

---

## Development Conventions

### Java / Spring Boot

```
Package structure per service:
  com.opencrm.{service}/
    config/         # @Configuration classes
    controller/     # @RestController (thin, delegates to service)
    service/        # Business logic (@Service)
    repository/     # Spring Data JPA (@Repository)
    entity/         # JPA entities
    dto/            # Request/Response DTOs
    mapper/         # MapStruct mappers
    event/          # Domain events (Kafka producers/consumers)
    exception/      # Service-specific exceptions
    validator/      # Custom validators
```

**Patterns to follow:**
- All entities extend `BaseEntity` (auditing, soft-delete, optimistic locking)
- Use `ApiResponse<T>` wrapper for all REST responses
- Use `@Builder` pattern on entities and DTOs
- Lazy fetch for all `@ManyToOne` / `@OneToMany` associations
- Database indexes on frequently queried columns (see existing entities)
- `SecurityUtils.getCurrentUser()` for authentication context
- Flyway for all schema migrations (`V{version}__{description}.sql`)
- MapStruct for entity-to-DTO conversions (never expose entities in APIs)
- `@Transactional` on service methods, not controllers
- Pagination via Spring Data `Pageable` + `ApiResponse.PageInfo`

**Naming:**
- REST endpoints: `/api/v1/{resource}` (plural nouns, kebab-case)
- Database tables: `snake_case` (plural)
- Java classes: `PascalCase`
- Java fields: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Enums: `SCREAMING_SNAKE_CASE` values

### Angular / TypeScript

```
Nx workspace structure:
  apps/
    crm-portal/       # Main CRM application
    customer-portal/  # Customer self-service
    admin-console/    # System administration
  libs/
    shared/ui/        # Shared UI components
    shared/data-access/  # API clients, state
    shared/utils/     # Common utilities
    shared/models/    # TypeScript interfaces
    crm/{feature}/    # Feature libraries
```

**Landed FE layout:** `frontend/apps/crm` (not `crm-portal` yet); `frontend/libs/cpq/{catalog,quotes,approvals,price-books,pricing-customers,pricing-data-access,pricing-simulator,pricing-ui}`; `frontend/libs/shared/{ui,models,data-access}`.

**Patterns to follow (landed):**
- Standalone components (no NgModules)
- OnPush change detection by default
- Smart (container) + dumb (presentational) component pattern
- **Signals + HttpClient services** (Deal-style) for CPQ — **do not introduce NgRx** until an Accepted ADR (forge **holds** NgRx)
- Strict TypeScript (`strict: true`, no `any`)
- Reactive forms over template-driven forms
- `async` pipe over manual subscriptions
- Tailwind utility classes, no custom CSS unless necessary

### Git & Commits

- Branch naming: `{type}/{ticket-id}-{short-description}` (e.g., `feat/CRM-42-lead-scoring`)
- Commit messages: Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`)
- PRs require: passing CI, code review, no security warnings
- **PR stacking (default):** Agents stack independent, small PRs two to three deep by default (each PR builds on the previous one's branch; each is reviewable alone), instead of opening every story as a separate PR off `main` at once.
  - Wave plans group stories into stacks of 2–3 at intake.
  - Stop stacking (start a new stack off `main`, or serialize) when a PR touches a shared file — e.g. `EventContractRegistry`, `KafkaTopics`, `EventContractRegistryTest`, a service's Flyway migration sequence, gateway `proxy.conf.json` / CSRF / routes, CI workflows, Jenkins, `docs/ci-contract.yaml` — or a service another lane needs.
  - CPQ Decision checks every stack for shared-file boundaries **before dispatch**, not after; a flagged stack is split or serialized before launch.
  - Stacks merge bottom-up. Each PR still needs the required checks (**Backend Unit**, **Frontend**, **Frontend npm audit**) green on an up-to-date branch.

---

## Recommended MCP Servers

Enable these MCP servers in your Claude Code environment for maximum productivity:

| MCP Server | Purpose |
|-----------|---------|
| `sequential-thinking` | Complex architectural decisions, multi-step problem solving |
| `context7` | Up-to-date library documentation (Spring Boot, Angular, etc.) |
| `e2b` | Run code snippets in sandbox (SQL validation, script testing) |
| `playwright` | Browser automation for E2E testing Angular UI |
| `Vercel` | If deploying customer portal to Vercel |

---

## Quality Gates

Before marking any task as complete, verify:

- [ ] Code compiles without warnings (`./gradlew build` or `nx build`)
- [ ] All existing tests pass (`./gradlew test` or `nx test`)
- [ ] New code has test coverage (unit tests at minimum)
- [ ] No new security vulnerabilities introduced
- [ ] API changes documented in OpenAPI spec
- [ ] Database changes have Flyway migration scripts
- [ ] DTOs used at API boundaries (entities never leaked to controllers)
- [ ] Error handling follows `BusinessException` / `ResourceNotFoundException` patterns
- [ ] Logging follows structured JSON format with correlation IDs

---

## Key architecture decisions (landed vs target)

**Canonical ADRs:** [docs/adr/README.md](docs/adr/README.md). Do not invent ADRs in this table.

| Topic | Landed (steer forge here) | Target / held (do not implement unless a wave plan says so) |
|-------|---------------------------|--------------------------------------------------------------|
| CSRF | Gateway Cookie CSRF only; domain `csrf.disable()` + STATELESS — [ADR-004](docs/adr/ADR-004-csrf-edge-boundary.md) | — |
| Config validate | Gateway → CE evaluate + `ConfigurationValidationResult`; product `POST /validate` **retained** — [backlog ADR](docs/backlog/ADR-configuration-api-reconciliation.md) | Full S2B-8 ConstraintSolver; Kafka S2B-9 **held** |
| Dual-read approvals | `${APPROVALS_LEGACY_LOCAL_READ:true}` — [ADR-005 Proposed](docs/adr/ADR-005-approvals-legacy-dual-read.md); J5 gate; hold **J–W** | Flip to `false` is **HIGH** — not authorized |
| API entities | Subscription + configuration-session + approval-routing + document-job APIs are DTO-only (O3 + W1–W3 on `main`) — [ADR-006 Accepted](docs/adr/ADR-006-entities-never-at-api-boundary.md) | Subscriptions **FE** held; do **not** re-expose JPA entities at `/api/v1/**` |
| Frontend state | Signals + service HTTP | NgRx 19+ **held** |
| AI | Runtime **Azure OpenAI gpt-4o** in `ai-service` | [ADR-014](docs/adr/ADR-014-jev-openrouter-model-selection.md) Jev/OpenRouter selection — [rollout requirements](docs/backlog/REQ-jev-openrouter-model-selection.md); ADR-002 superseded |
| CI | Jenkins primary; GHA manual — [ADR-003](docs/adr/ADR-003-ci-llm-integration.md) | — |
| DB | Shared PostgreSQL, separate schemas | — |
| Search | CRM/quote list+PageInfo over gateway; **not** Elasticsearch in tree | Elasticsearch 8 (target) |
| Activities | Note and task API `/api/v1/activities` (DRG-520 slice) | Email, meeting, mailbox ingestion, and the Angular client are still open |
| GraphQL | **Not** in tree | Optional later |
| PDF | Quote-service Thymeleaf/OpenHTML documents API | — |

---

## File reference (landed)

| File | Purpose |
|------|---------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | **Index** — adr + forge waves + matrix + CI |
| [docs/adr/README.md](docs/adr/README.md) | ADR catalog |
| `MODERNIZATION_PLAN.md` | **Target** 16-month plan — not current forge SSOT |
| `backend/pom.xml` | Maven reactor (Spring Boot parent **4.1.2**, Java 25) |
| `backend/docker-compose.yml` | Local PostgreSQL 16, Redis 7 |
| `backend/libs/common/` | BaseEntity, ApiResponse, BusinessException |
| `backend/libs/security/` | UserContext, SecurityUtils |
| `backend/libs/rules-engine/` | Shared rule facts / engine bits |
| `backend/services/crm-service/` | Lead, Deal, Contact, Organization **APIs** |
| `backend/services/gateway-service/` | Spring Cloud Gateway + CSRF edge |
| `backend/services/product-service/` | Catalog, options, attributes, bundles, configuration rules |
| `backend/services/pricing-service/` | Execute/simulate, rule-sets, rules, pricing-customers |
| `backend/services/pricebook-service/` | Price books + entries |
| `backend/services/quote-service/` | Quotes, lines, documents, BFF approvals |
| `backend/services/configuration-engine/` | Sessions + evaluate (`/api/v1/configurations/evaluate` for users via the gateway, `/internal/v1/configurations/evaluate` for services) |
| `backend/services/approval-routing/` | Canonical approvals |
| `backend/services/document-generation/` | Kafka document jobs |
| `backend/services/contract-asset-management/` | Subscriptions (`SubscriptionDto` + paginated list; **no FE**) |
| `backend/services/ai-service/` | AgentRouter (runtime Azure OpenAI) |
| `frontend/apps/crm/` | Angular CRM + CPQ routes |
| `frontend/libs/cpq/` | Feature libs listed above |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | Agent-contract paths |
| `docs/ci-contract.yaml` | Jenkins/GHA stage SSOT |
| `cpq_beta/` | Legacy Frappe source |
