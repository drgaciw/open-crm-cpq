# Open CRM/CPQ - AI Agent Development Guide

## Project Overview

**Open CRM/CPQ** is a greenfield enterprise CRM and Configure-Price-Quote platform being modernized from a Frappe-based Python/Vue 3 application into a cloud-native microservices architecture.

| Dimension | Current State | Target State |
|-----------|--------------|--------------|
| Backend | Frappe Framework (Python) | Spring Boot 3.5.9 / Spring Cloud 2025.0.0 / Java 21 |
| Frontend | Vue 3 / Frappe UI | Angular ~21.2 / Nx 22.7 / Vite 8 / Tailwind |
| Database | MariaDB 10.6 | PostgreSQL 16 + pgvector |
| Infrastructure | Docker / GitHub Actions | Azure AKS / Terraform / Helm |
| AI | None | Spring AI 1.1.7 + Azure OpenAI + Claude Agent SDK |
| Rules Engine | None | OpenL Tablets + Drools 10.2.0 |

**Current Implementation Status**: Early stage. Core JPA entities exist for CRM (Lead, Deal, Contact, Organization) with shared libraries (BaseEntity, ApiResponse, SecurityUtils). No controllers, services, repositories, or tests yet. Docker Compose for local dev (PostgreSQL 16, Redis 7). No frontend scaffolding started.

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

**Patterns to follow:**
- Standalone components (no NgModules)
- OnPush change detection by default
- Smart (container) + dumb (presentational) component pattern
- NgRx for global state, Component Store for local state
- Strict TypeScript (`strict: true`, no `any`)
- Reactive forms over template-driven forms
- `async` pipe over manual subscriptions
- Tailwind utility classes, no custom CSS unless necessary

### Git & Commits

- Branch naming: `{type}/{ticket-id}-{short-description}` (e.g., `feat/CRM-42-lead-scoring`)
- Commit messages: Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`)
- PRs require: passing CI, code review, no security warnings

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

## Key Architecture Decisions (ADRs)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Database per service vs shared | Shared DB, separate schemas | Simpler operations for team of 12-15; schema isolation provides enough boundary |
| Rules engine | OpenL Tablets + Drools hybrid | OpenL for business-user Excel rules; Drools for complex programmatic rules |
| Event bus | Kafka / Azure Service Bus | Async decoupling between services; event sourcing for Activity Service |
| Search | Elasticsearch 8 | Full-text search, fuzzy matching, entity indexing across services |
| AI orchestration | Spring AI + Claude Agent SDK | Multi-LLM strategy with Azure OpenAI primary, Claude for agent workflows |
| Frontend state | NgRx 19+ | Predictable state, dev tools, effects for side effects |
| API style | REST (primary) + GraphQL (optional) | REST for CRUD; GraphQL considered for complex aggregation queries |
| Authentication | Azure AD B2C + OAuth2/OIDC | Enterprise SSO, customer identity, JWT-based resource server |
| Caching | Redis 7 (cache-aside pattern) | Session management, API response caching, rate limiting |
| PDF generation | OpenHTML + Thymeleaf | Template-based quote/proposal PDFs, no external service dependency |

---

## File Reference

| File | Purpose |
|------|---------|
| `MODERNIZATION_PLAN.md` | Comprehensive architecture and roadmap document |
| `backend/pom.xml` | Root Maven build with Spring Boot 3.5.9, Spring Cloud 2025.0.0, Java 21 |
| `backend/settings.gradle.kts` | Multi-module project: 5 services + 2 shared libs |
| `backend/docker-compose.yml` | Local dev: PostgreSQL 16, Redis 7, pgAdmin, Redis Commander |
| `backend/scripts/init-db.sql` | DB init: 4 schemas (crm, cpq, communication, ai) + pgvector |
| `backend/libs/common/` | BaseEntity, ApiResponse, BusinessException |
| `backend/libs/security/` | UserContext, SecurityUtils (JWT extraction) |
| `backend/services/crm-service/` | Lead, Deal, Contact, Organization entities |
| `backend/services/gateway-service/` | Spring Cloud Gateway (bootstrap only) |
| `backend/services/cpq-service/` | CPQ service (build config only, no entities yet) |
| `backend/services/communication-service/` | Communication service (build config only) |
| `backend/services/ai-service/` | AI service (build config only) |
| `cpq_beta/` | Existing Frappe CRM (152MB) - migration source |
