# Open CRM/CPQ — agent operating architecture

**This file is how agents decide what to build vs hold.** Read it before inventing APIs, flipping flags, or adding frameworks.

It is **not** the 16-month [MODERNIZATION_PLAN.md](../MODERNIZATION_PLAN.md) (target brochure). It is **not** only a forge file list. [AGENT.md](../AGENT.md) phase tables are **target-state** role maps — do not treat them as this week’s backlog.

**Steer from:** this page → [docs/adr/README.md](adr/README.md) → [CPQ_API_CONTRACT_MATRIX.md](backlog/CPQ_API_CONTRACT_MATRIX.md) → the latest **authorized** forge wave plan.  
**Tip (survey):** `origin/main` @ `c92d5a9` (Wave W COMPLETE `60debf8` / W4 `0b6d3ad`). Dual-read **true**. ADR-006 **Accepted**. ADR-005 **Proposed**.

---

## 1. Purpose

Agents (forge panes, coding assistants) must:

1. Implement against **landed** contracts and code.
2. Treat **target** as destination, not permission to implement.
3. Refuse **holds** unless a new wave plan explicitly un-holds them.

**Do not:** invent `/api/v1/activities/**`; flip `approvals.legacy-local-read-enabled`; enable NgRx; treat GLM-5 as live runtime; Accept ADR-005; ship subscriptions FE; delete product `POST /validate`; put CSRF on domain services.

---

## 2. Landed vs target

Copy this table into planning. **Do not present the Target column as current.**

| Dimension | Landed (forge, 2026-09) | Target / held |
|-----------|-------------------------|---------------|
| Backend | Spring Boot **4.0.6** parent / Java **25** reactor (`backend/pom.xml`): crm, gateway, product, pricing, pricebook, quote, configuration-engine, approval-routing, document-generation, contract-asset, ai-service | Azure AKS; remaining identity & comms. **Do not** downgrade Boot |
| Frontend | Angular **22** Nx `frontend/apps/crm` + `libs/cpq/*` + `libs/crm/*` + `libs/shared/*`. **Signals + HttpClient** | NgRx 19+ **held**; customer-portal / admin-console apps **not** in tree |
| Database | PostgreSQL 16 + Redis 7 (compose); **one DB `opencrm`, schemas per service** | Same + production Azure. pgvector extension commented until needed |
| Infrastructure | Docker Compose local; **Jenkins primary CI**; GHA `workflow_dispatch` fallback — [ADR-003](adr/ADR-003-ci-llm-integration.md) | Azure AKS / Terraform / Helm |
| AI | `ai-service` AgentRouter; **runtime Azure OpenAI `gpt-4o`** | [ADR-002](adr/ADR-002-primary-llm-glm-5.md) GLM-5 **Accepted, not landed** — **not** production |
| Rules | Drools DRL on pricing-service; CE `ProductRuleEvaluationCore`; OpenL **not** required | OpenL Tablets + full Drools / full SpEL **held** |
| Identity | Gateway JWT config present; Azure AD B2C **`enabled: false` by default** | Full OAuth2/OIDC identity service |
| Search | CRM/quote list + `PageInfo` over gateway | Elasticsearch 8 **not in tree** |
| Comms | Gateway stubs `/api/v1/emails/**` etc. → `communication-service` (**not** in Maven reactor) | Twilio / SendGrid / WhatsApp hub. **#430 on `main` — do not undo** |
| Activities | **No** schema, controller, or FE | Do **not** invent `/api/v1/activities/**` |

**Forge status:** Waves **A–W COMPLETE** on `main`; hygiene N′ COMPLETE; Wave **T skipped** (do not name T1/T2). Not early stage.

---

## 3. Runtime topology

Browser (`localhost:4200`) → **gateway `:8080`** (edge CSRF + routes) → domain services. Gateway **does not** embed domain validation (route + rewrite only).

```text
Angular CRM :4200
       │
       ▼
gateway-service :8080     Cookie CSRF + Origin allow-list
       │
       ├─ crm-service :8081              schema crm
       ├─ pricing-service :8082          schema pricing
       ├─ product-service :8083          schema product
       ├─ quote-service :8084            schema quote
       ├─ approval-routing :8085
       ├─ document-generation :8086      (Kafka jobs; FE quote PDFs go through quote-service)
       ├─ contract-asset :8087           SubscriptionDto API; **no FE**
       ├─ configuration-engine :8088     sessions + internal evaluate
       ├─ pricebook-service :8089        schema pricing (shared schema name in compose)
       └─ ai-service                     AgentRouter (Azure gpt-4o)
PostgreSQL 16 :5432  (opencrm)     Redis 7 :6379
```

Canonical path registry: [CPQ_API_CONTRACT_MATRIX.md](backlog/CPQ_API_CONTRACT_MATRIX.md). `/api/v1/cpq/...` aliases rewrite to `/api/v1/...` (`CpqGatewayRoutesTest`).

### Who owns `/api/v1/*` (do not mix)

| Path prefix | Owner | FE (landed) |
|-------------|--------|-------------|
| `/api/v1/leads`, `/deals`, `/contacts`, `/organizations` | crm-service | `libs/crm/*` |
| `/api/v1/pricing/**`, `/pricing-customers/**`, `/rule-sets/**`, `/rules/**` | **pricing-service** | simulator, pricing-customers, rule-sets |
| `/api/v1/price-books/**` | **pricebook-service** | price-books |
| `/api/v1/products/**`, `/product-bundles/**`, `/product-options/**`, `/product-attributes/**`, `/configuration/**` (rules + **retained** validate) | **product-service** | catalog, product-bundles |
| `POST /api/v1/configuration/validate` | **gateway rewrite** → CE `POST /internal/v1/configurations/evaluate` | agents; product path **retained** |
| `/api/v1/configurations/**` | **configuration-engine** (sessions, **beta**) | none |
| `/api/v1/quotes/**` including `/quotes/approvals/**` | **quote-service** (BFF approvals) | quotes, approvals inbox |
| `/api/v1/approvals/**` | **approval-routing** (canonical; **not** FE) | FE must **not** call this |
| `/api/v1/documents/**` | document-generation | FE quote docs use **quote-service** documents API |
| `/api/v1/subscriptions/**` | contract-asset | **no FE** |
| `/api/v1/activities/**` | **does not exist** | do not invent |

**Disambiguate:** `/api/v1/rules` (pricing DRL) ≠ `/api/v1/configuration/rules` (product config rules). `/api/v1/quotes/approvals` ≠ `/api/v1/approvals`.

---

## 4. Security

[ADR-004](adr/ADR-004-csrf-edge-boundary.md) **Accepted** — CSRF **gateway-only**.

| Layer | Landed |
|-------|--------|
| Gateway | Cookie CSRF (`CookieServerCsrfTokenRepository.withHttpOnlyFalse`) + `CsrfProtectionFilter`. `security.csrf.allowed-origins` = explicit list (`CORS_ALLOWED_ORIGINS` / `FRONTEND_URL`, default `http://localhost:4200`). **No** `*` / `*.azurewebsites.net` |
| Domain services | `csrf.disable()` + `STATELESS` (Bearer/mesh). **Do not** merge Jules #402/#404 Cookie CSRF on microservices |
| `SECURITY_OPEN` | Must not be a production-reachable bypass |
| GitNexus | `CsrfProtectionFilter` / `SecurityConfig` often **UNKNOWN** — confirm with `rg` |

---

## 5. API boundary

[ADR-006](adr/ADR-006-entities-never-at-api-boundary.md) **Accepted** (W4 after O3 + W1–W3).

- Controllers return **DTOs** via MapStruct, wrapped in `ApiResponse<T>` (or documented envelopes such as product validate `List<String>`).
- **Never** `ApiResponse<Entity>` at `/api/v1/**`.
- Landed DTO wraps include: quotes/products/price-books/customers, `SubscriptionDto`, `ConfigurationSessionDto`, routing `ApprovalRequestDto`, `DocumentGenerationJobDto`.
- **Does not authorize** subscriptions FE, ERP adapter rewrite, or deleting product validate.

Quote BFF already used `ApprovalRequestDto`; FE inbox calls `/api/v1/quotes/approvals/**` only.

---

## 6. Frontend

| Path | Role |
|------|------|
| `frontend/apps/crm` | Shell: `app.routes.ts` (all `loadChildren`), `sidebar.component.ts`, `shell.component.ts` |
| `frontend/libs/crm/{dashboard,organizations,contacts,leads,deals}` | CRM lists/details |
| `frontend/libs/cpq/{catalog,quotes,approvals,price-books,pricing-customers,rule-sets,product-bundles,pricing-simulator,pricing-data-access,pricing-ui}` | CPQ |
| `frontend/libs/shared/{ui,models,data-access}` | EmptyState, ConfirmDialog, PageHeader, DataTable; DTOs |

**State:** signals + HttpClient services. **NgRx held.**

**Shell:** at most **one** new portal per wave may own `app.routes.ts` + `sidebar.component.ts` + tsconfig path + `test:cpq`. Polish waves prefer **zero** new routes. P1 already owns Bundles; Q4 owns chrome (sidenav/skip-link).

**Sidebar (landed):** CRM dashboard/orgs/contacts/leads/deals; CPQ catalog, quotes, approvals, price-books, pricing-customers, rule-sets, bundles, pricing simulator. **No** subscriptions, standalone configuration-rules, or Activities.

Vitest: `cd frontend && NX_DAEMON=false npx nx test <proj> --skip-nx-cache`.

---

## 7. CPQ contracts (runtime)

| Rule | Landed |
|------|--------|
| Config validate | Gateway → CE evaluate + `ConfigurationValidationResult`. Product `POST /api/v1/configuration/validate` **retained**. Do not delete. Do not put domain validation in gateway |
| Dual-read | `quote-service` `approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}` — [ADR-005 Proposed](adr/ADR-005-approvals-legacy-dual-read.md). Flip is **HIGH**. Hold **J–W** |
| Quote DELETE | Soft-delete. **ACCEPTED** → `QUOTE_ACCEPTED_NO_DELETE` |
| Price books | Published (`effectiveDate` present and `<=` today) → `PRICE_BOOK_PUBLISHED_IMMUTABLE`. List-row delete **R2 HELD** |
| Approvals FE | Quote BFF only |
| Subscriptions | DTO API on `main`; activate still `ErpAdapter.syncToErp`; **no FE** |

---

## 8. CI

[ADR-003](adr/ADR-003-ci-llm-integration.md) **Accepted**. SSOT: [docs/ci-contract.yaml](ci-contract.yaml) → `scripts/ci/run-core.sh`. Jenkins on PR/push; GHA **manual** fallback only.

**ITs:** Testcontainers (Postgres 16, Redis 7). If login `id` lacks `docker` but `getent group docker` includes the user, wrap Maven:

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25
newgrp docker <<'EOF'
cd backend && ./mvnw -B -pl services/<module> -am test
EOF
```

Do not “fix” CI by inventing runners (#238 **HOLD**).

---

## 9. Holds (do not implement unless a wave plan un-holds)

| Hold | Why |
|------|-----|
| Activities API / `/api/v1/activities/**` | No schema/controller |
| CI #238 | Runners |
| Agent Kafka #241 | Stack B2 / S2A-2 |
| Dual-read YAML/env flip | **HIGH**; ADR-005 Proposed |
| NgRx | Signals + HTTP |
| Subscriptions / contract-asset FE | ERP on activate |
| Full SpEL / OpenL | Partial CE/Drools only |
| Kafka S2B-9 | Event bus |
| Gateway domain validation | Route + adapt only |
| Delete product-service validate | Dual-run retained |
| WhatsApp #430 undo | On `main` |
| **R2** price-book list-row delete | N stretch; still held |
| Named Wave T / T1 / T2 | Skipped |
| GLM-5 as runtime | ADR-002 Accepted, **not** landed |
| Accept ADR-005 | Flip not authorized |

Open GitHub issues: **#238**, **#241** only. Linear/Notion Open CRM: **none** (ignore cmano-clone / Aegis).

---

## 10. How agents use GitNexus

Index lives on **open-crm-cpq** at `/home/username01/Projects/open-crm-cpq` (two indexed repos — **always** disambiguate).

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "SymbolName" --direction upstream \
  --repo /home/username01/Projects/open-crm-cpq --file path/to/File.java
```

| Rule | |
|------|--|
| Always | `-r /home/username01/Projects/open-crm-cpq` and `--file` for overload/`delete` |
| Before symbol edits | `impact` upstream; warn HIGH/CRITICAL |
| **UNKNOWN ≠ unused** | HTTP controllers, Spring filters, property access. Confirm with `rg` |
| CLI stale | Untracked `.worktrees/` is often **false stale**. Compare indexed commit vs HEAD |
| Skip analyze | If index is many commits behind and analyze is slow; plan from `rg` + files + `query` |
| Rename | Do not find-and-replace symbols; use graph-aware rename |
| Before commit | `detect-changes` on the **worktree that has the edits** (primary index ≠ architect index) |

Stats at last orchestrator index (~`04909db` vs later HEAD — **stale**): ~1867 files, 21885 symbols, 45223 edges, 777 clusters, 559 processes. Query examples: gateway `CsrfProtectionFilter` + `security.csrf.allowed-origins`; CE `ProductRetiredEventConsumer`; `CpqGatewayRoutesTest` CPQ path rewrites.

---

## 11. Forge waves (short) and ADRs

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

| ADR | Status | Path |
|-----|--------|------|
| ADR-002 GLM-5 | Accepted; **runtime not landed** | [adr/ADR-002-primary-llm-glm-5.md](adr/ADR-002-primary-llm-glm-5.md) |
| ADR-003 CI | Accepted | [adr/ADR-003-ci-llm-integration.md](adr/ADR-003-ci-llm-integration.md) |
| ADR-004 CSRF edge | Accepted | [adr/ADR-004-csrf-edge-boundary.md](adr/ADR-004-csrf-edge-boundary.md) |
| ADR-005 dual-read | **Proposed** — do not flip | [adr/ADR-005-approvals-legacy-dual-read.md](adr/ADR-005-approvals-legacy-dual-read.md) |
| ADR-006 entities | **Accepted** — no subscriptions FE | [adr/ADR-006-entities-never-at-api-boundary.md](adr/ADR-006-entities-never-at-api-boundary.md) |
| Config API | Accepted; product validate retained | [backlog/ADR-configuration-api-reconciliation.md](backlog/ADR-configuration-api-reconciliation.md) |

---

## 12. File map (landed)

| Path | Role |
|------|------|
| `backend/pom.xml` | Reactor, Boot **4.0.6**, Java **25** |
| `backend/docker-compose.yml` | Postgres 16, Redis 7, service ports |
| `backend/scripts/init-db.sql` | Schemas `crm`, `cpq`, `pricing`, `product`, `quote`, `communication`, `ai` |
| `backend/libs/common/` | `BaseEntity`, `ApiResponse`, exceptions |
| `backend/services/gateway-service/` | Edge CSRF + routes |
| `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` | Agent-contract paths |
| `docs/ci-contract.yaml` | Jenkins/GHA stage SSOT |
| `cpq_beta/` | Legacy Frappe — read-only reference |
