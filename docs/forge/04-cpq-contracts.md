# Forge Plan: CPQ API Contract Matrix vs Implementation

**Track:** `04-cpq-contracts` (forge scout: cpq-api)  
**Mode:** Plan / discovery only (no application source edits in this wave)  
**Date:** 2026-08-30  
**Sources:** `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`, `docs/backlog/ADR-configuration-api-reconciliation.md`, controllers under `backend/services/{product,pricing,quote,pricebook,configuration-engine,approval-routing,gateway}-service`

---

## 1. Track name + scope

| Field | Value |
|-------|--------|
| **Track name** | CPQ API contract matrix vs implementation |
| **In scope** | Agent-contract / stable routes for configuration, pricing, rules/DRL, quotes + BFF approvals, product catalog integrity, ERP stubs; gateway route predicates; pricebook surface (implemented but matrix-absent); test/contract gaps; highest-value next PRs |
| **Services** | `product-service` (8083), `pricing-service` (8082), `quote-service` (8084), `pricebook-service` (8089), `configuration-engine` (8088), `approval-routing` (8085), `gateway-service` (8080) |
| **Out of scope (this track)** | CRM cutover UI, FE CPQ scaffold (track 05), Jenkins/GHA stage graph (track 06), full `erp-adapter-service` greenfield, Drools business-rule content rewrites |

**Agent-contract consumers (matrix Appendix A):** cpq-agent-011 (margin), 015 (compatibility), 016 (validation); related: 002 (option config), 004/price-book agents (path drift).

---

## 2. Current state (what exists)

### 2.1 Contract documentation

- **`docs/backlog/CPQ_API_CONTRACT_MATRIX.md`** — published registry (S2A-7): **69** rows across configuration, pricing, rules, quote/approval, product/CRM integrity, ERP stubs. Last verified against controllers **2026-05-18**.
- **`docs/backlog/ADR-configuration-api-reconciliation.md`** (S2A-8) — ownership split decided; **implementation PR S2B-11 not landed**.
- Gateway §9 predicates largely match `gateway-service/src/main/resources/application.yml` (including `/api/v1/cpq/**` rewrite aliases).

### 2.2 Controllers present (path ↔ owner)

| Area | Owner | Evidence |
|------|--------|----------|
| Rule CRUD + **agent validate** | product-service | `ConfigurationRuleController` → `/api/v1/configuration` (`POST /validate` returns `ApiResponse<List<String>>`) |
| Session config APIs | configuration-engine | `ConfigurationSessionController` → `/api/v1/configurations/**` (beta) |
| Pricing execute / simulate / audit | pricing-service | `PricingExecutionController` → `/api/v1/pricing/**` |
| Pricing customers | pricing-service | `CustomerController` → `/api/v1/pricing-customers/**` |
| DRL rules + `POST .../rules/{id}/validate` | pricing-service | `RuleController` / `RuleSetController` |
| Quotes + line items + BFF approvals | quote-service | `QuoteController`, `QuoteLineItemController`, `ApprovalController` under `/api/v1/quotes/**` |
| Canonical approvals | approval-routing | `ApprovalController` → `/api/v1/approvals/**` (matrix: **internal**) |
| Product / bundles / attributes | product-service | Controllers under `/api/v1/products/**`, `product-bundles/**`, `product-attributes/**` |
| Price books | pricebook-service | `PriceBookController` → `/api/v1/price-books/**` + `InternalPriceBookController` → `/internal/v1/price-books/resolve-batch` |
| ERP agent paths | gateway stub | Route id `erp-stub` → `forward:/fallback/erp/unavailable` → `FallbackController.erpNotImplemented()` → **501** |

### 2.3 Gateway

- Agent-critical prefixes routed: configuration, configurations, pricing, rules, quotes, approvals, products, CRM, erp-stub (`CpqGatewayRoutes.AGENT_CONTRACT_ROUTE_IDS`).
- Price books routed (`pricebook-service`, `cpq-pricebook-service`) but **not listed in the contract matrix**.
- Gateway unit tests assert YAML contains route ids / path strings (`CpqGatewayRoutesTest`) — **not** live HTTP contract tests.

### 2.4 Partial cross-service wiring

- `quote-service` → `PricingServiceClient.executePricing` used from `QuoteService` (calculate path).
- `quote-service` → `ProductServiceClient.validateConfiguration` **exists** (`POST /api/v1/configuration/validate`) but **no callers** in quote domain services (preflight S2D-3a not wired).
- `pricing-service` emits `DISCOUNT_APPROVAL_REQUIRED`; `approval-routing` consumes via `DiscountApprovalEventConsumer` (event path ≠ quote BFF path).
- `pricing-service` resolves list prices via `PriceBookResolutionClient` → pricebook internal API.

---

## 3. Gaps with evidence (paths / commands)

### G1 — Agent validate unstable vs ADR (S2B-11 missing) — **CRITICAL**

| Expected (ADR / agents) | Actual |
|-------------------------|--------|
| `POST /api/v1/configuration/validate` agent-contract; structured `{ valid, violations[] }` | product-service inline; body `List<Long>`; response `ApiResponse<List<String>>` |
| Gateway owns external contract; evaluates via configuration-engine | Gateway **pass-through** to product-service only (`Path=/api/v1/configuration/**`) |
| configuration-engine owns evaluation | `ConstraintSolver` is **hardcoded placeholder** (color / option_a / premium_feature) |

**Evidence:**

```bash
rg -n 'validate|List<String>' backend/services/product-service/src/main/java/com/opencrm/product/configrule/ConfigurationRuleController.java
# POST /validate → ApiResponse<List<String>>

sed -n '24,50p' backend/services/configuration-engine/src/main/java/com/opencrm/configuration/constraint/ConstraintSolver.java
# placeholder constraints; comment: "would be loaded from configuration rules"

rg -n 'internal/v1/configurations/evaluate|ConfigurationValidate' backend/services --glob '*.java'
# no matches — no stateless evaluator endpoint
```

`ConfigRuleType` includes RECOMMENDS / DEFAULT / VALIDATES, but `ConfigurationRuleService.validateConfiguration` only evaluates **REQUIRES** and **EXCLUDES**.

### G2 — Quote BFF does not delegate to approval-routing (S2C-12 incomplete) — **HIGH**

Matrix claims: `POST /api/v1/quotes/{quoteId}/approvals` “delegates to approval-routing (S2C-12)”.

**Actual:** `quote-service` `ApprovalService` persists local `ApprovalRequest` + flips quote status; **zero** references to approval-routing / `:8085` / `/api/v1/approvals` under quote-service.

```bash
rg -n 'approval-routing|8085|/api/v1/approvals' backend/services/quote-service --glob '*.java'
# (empty)

# Parallel systems:
# - quote BFF: backend/services/quote-service/.../approval/ApprovalService.java
# - Kafka: pricing → approval-routing DiscountApprovalEventConsumer
```

Agents using BFF pending/approve see a **different store** than approval-routing’s canonical queue → unstable agent-contract for cpq-agent-011.

### G3 — Price books implemented but absent from matrix + agent path drift — **HIGH**

```bash
rg -n 'price-book|pricebook' docs/backlog/CPQ_API_CONTRACT_MATRIX.md
# NOT IN MATRIX

# Canonical impl:
# PriceBookController @RequestMapping("/api/v1/price-books")  (port 8089)
# Gateway: Path=/api/v1/price-books/**

# Agent skill drift:
rg -n '/api/v1/(pricing/)?price-books' agents/ --glob '*.md'
# agents/currency-fx-management.md → /api/v1/price-books  (correct)
# agents/skills/price-book-management/SKILL.md → /api/v1/pricing/price-books  (WRONG — would hit pricing-service)
# agents/skills/list-price-application/SKILL.md → /api/v1/pricing/price-books  (WRONG)
```

### G4 — ERP agent-contract routes are stubs only — **EXPECTED / BLOCKING for FULL_PREFLIGHT**

```bash
rg -n 'erp-stub|erpNotImplemented' backend/services/gateway-service
find backend/services -iname '*Erp*Controller*'   # none for public ERP API
# GET /api/v1/erp/costs|inventory → 501 NOT_IMPLEMENTED
```

Agents 011/016 must treat 501 as adapter unavailable (matrix §8). No typed fail-safe contract test beyond YAML presence.

### G5 — Test gaps on agent-contract surfaces — **HIGH**

| Service | `*Test.java` count | Controller HTTP tests |
|---------|-------------------:|------------------------|
| product-service | 6 | **0** (no `*Controller*Test`) |
| pricing-service | 4 | **0** |
| quote-service | 2 | **0** (event factory only) |
| pricebook-service | 2 | **0** (service/unit only) |
| configuration-engine | 3 | **0** |
| approval-routing | 1 | **0** |
| gateway-service | 1 | YAML string asserts only |

```bash
# Reproduce controller-test absence:
find backend/services/{product,pricing,quote,pricebook,configuration-engine,approval-routing}-service \
  -name '*Controller.java' | while read f; do basename "$f"; done
# vs
find backend/services/.../src/test -name '*Controller*Test*.java'   # empty for those services

rg -l 'MockMvc|WebTestClient' backend/services/{product,pricing,quote,pricebook,configuration-engine,approval-routing}-service/src/test
# (empty)
```

No S2C-1b **golden fixtures** for `POST /api/v1/pricing/execute` (only unit `PricingExecutionServiceTest` with mocked pricebook client). No S2B-11 contract tests for validate response shape.

### G6 — Gateway test depth / ERP forward path fragility — **MEDIUM**

- `CpqGatewayRoutesTest` only checks `application.yml` contains substrings.
- `erp-stub` URI is `forward:/fallback/erp/unavailable` while handler is `@RequestMapping("/fallback/erp/**")` — likely works via `/**`, but **no runtime assertion** of 501 body shape for agent fail-safes.

### G7 — Quote preflight not consuming validate (S2D-3a) — **MEDIUM**

`ProductServiceClient.validateConfiguration` is dead code from quote orchestration perspective; matrix/ADR expect quote preflight to call agent validate via client/gateway.

### G8 — Matrix freshness — **LOW–MEDIUM**

Matrix “last verified” **2026-05-18**. Since then: pricebook service + gateway aliases + more quote lifecycle endpoints (`submit`, `calculate`, documents) exist but are not fully reflected for agent vs stable tiering.

---

## 4. Blast radius / risk notes

> GitNexus MCP tools were **not available** in this scout session; blast radius below is from static callers / matrix consumers. Before implementation PRs edit symbols, run `gitnexus_impact` per workspace rules.

| Change target | Upstream / consumers | Risk |
|---------------|----------------------|------|
| `ConfigurationRuleController.validate` / response DTO | Agents 015/016/002; `ProductServiceClient`; future gateway shim; FE if any | **HIGH** — agent-contract; additive DTO preferred; never delete path without shim |
| `ConstraintSolver.validate` | `ConfigurationSessionService` (selections, validate, complete) | **MEDIUM** — session beta APIs; wrong rules → false completes |
| Quote `ApprovalService` → approval-routing client | Agents 011 BFF paths; dual DB until cutover; Kafka discount flow | **HIGH / CRITICAL** if dual-write or status drift |
| Gateway `configuration/**` route split (CRUD vs validate) | All agents via :8080; product-service direct callers | **HIGH** — predicate order / rewrite bugs |
| Price-book path canonicalization | Agents 004/032, skills, pricing resolution client, FE pricing IA | **MEDIUM** — document + alias before rename |
| ERP stub → real adapter | Margin + FULL_PREFLIGHT agents | **HIGH** when started; **do not** remove 501 without replacement |
| Pricing `execute` golden fixtures | Drools packs, pricebook resolve, approval events | **MEDIUM** — fixture churn if DRL changes |

**Warn before edit:** any PR that changes `POST /api/v1/configuration/validate` request/response or quote approval BFF semantics should be treated as **HIGH** risk and gated by contract tests + matrix/ADR update.

---

## 5. Proposed PRs (title, files, acceptance, risk)

Ordered by **value / risk** for the next implementation wave (after forge approval).

### PR-A — `test(cpq): agent-contract HTTP smoke suite for matrix Appendix A`

| | |
|--|--|
| **Why first** | Locks today’s behavior before S2B-11 / S2C-12 churn; highest leverage, lowest product risk |
| **Files** | New: `backend/services/gateway-service/src/test/.../AgentContractRouteIT.java` (or shared `backend/libs/contract-tests/`); optional `@WebMvcTest` per critical controller; fixtures under `src/test/resources/contracts/` |
| **Acceptance** | Every Appendix A path (except ERP) returns non-404 through gateway route config or service MockMvc; ERP asserts **501** + reserved message; CI job tag `contract`; matrix row IDs referenced in test names |
| **Risk** | **LOW** |

### PR-B — `docs(cpq): register price-books in contract matrix + fix agent skill path drift`

| | |
|--|--|
| **Files** | `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` (new § or §4 subsection); `agents/skills/price-book-management/SKILL.md`; `agents/skills/list-price-application/SKILL.md`; optional ADR note; `CpqGatewayRoutesTest` assert `/api/v1/price-books/**` |
| **Acceptance** | Canonical external path = `/api/v1/price-books/**` (pricebook-service); skills no longer call `/api/v1/pricing/price-books`; stability tier at least `stable` (promote selected GETs to agent-contract if agents 004/032 require) |
| **Risk** | **LOW** |

### PR-C — `feat(configuration-engine): replace ConstraintSolver placeholder with product rule evaluation (S2B-8)`

| | |
|--|--|
| **Files** | `ConstraintSolver.java`; rule projection client from product-service; `ConfigurationSessionServiceTest`; new unit tests for REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES |
| **Acceptance** | Session validate uses persisted `ConfigurationRule`s (not hardcoded color/option_a); RECOMMENDS does not fail hard; graph EXCLUDES conflicts covered by tests |
| **Risk** | **MEDIUM** — depends on rule fetch/cache design (ADR open Q3) |
| **Depends on** | Product rule CRUD stability (already present) |

### PR-D — `feat(gateway): S2B-11 configuration validate compatibility layer`

| | |
|--|--|
| **Files** | Gateway route/filter or BFF adapter for `POST /api/v1/configuration/validate`; shared DTO `{ valid, violations[] }`; optional `POST /internal/v1/configurations/evaluate` on configuration-engine; contract tests; **retain** product-service handler until parity |
| **Acceptance** | External path unchanged; response additive/structured per ADR; dual-run or shim proven by PR-A fixtures; matrix §3/§8 updated; product-service not deleted |
| **Risk** | **HIGH** — agent-contract; warn + gitnexus_impact on validate symbols |
| **Depends on** | PR-C (meaningful evaluator) + PR-A (fixtures) |

### PR-E — `feat(quote): BFF approvals delegate to approval-routing (S2C-12)`

| | |
|--|--|
| **Files** | `quote-service/.../approval/ApprovalService.java` + new client; approval-routing API; migration/dual-read plan; tests for pending/approve/reject parity |
| **Acceptance** | Agent paths under `/api/v1/quotes/**/approvals*` unchanged; canonical state in approval-routing; no silent dual-source pending queues; matrix note updated |
| **Risk** | **HIGH** — status machine + agent 011 |
| **Depends on** | Clear ownership ADR already in matrix; ideally PR-A covers BFF paths first |

### PR-F — `test(pricing): S2C-1b golden fixtures for POST /api/v1/pricing/execute`

| | |
|--|--|
| **Files** | `pricing-service` test resources (JSON fixtures); `PricingExecution*` IT with Testcontainers or stubbed pricebook; assert margin / approval-margin-exception signals |
| **Acceptance** | Deterministic execute outputs for N fixtures; failure if DRL/waterfall regresses; referenced from matrix §4 |
| **Risk** | **MEDIUM** |
| **Parallel with** | PR-B, PR-E (after A) |

### Deferred (not wave-1 unless unblocked)

| PR | Note |
|----|------|
| ERP adapter service | Replace stub; keep paths; large infra — separate track |
| S2D-3a quote preflight | After PR-D validate shape stable |
| Remove product-service validate | **Only** post–S2B-11 parity (ADR migration) |

---

## 6. Parallelization / dependencies

```text
PR-A (contract smoke) ─────────────────────────────┐
PR-B (matrix + skill paths) ──── parallel with A ──┤
PR-F (pricing goldens) ──────── parallel with A/B ─┤──► Wave verify
         │                                         │
         ▼                                         │
PR-C (ConstraintSolver / S2B-8)                    │
         │                                         │
         ▼                                         │
PR-D (gateway validate shim / S2B-11) ◄────────────┘
         │
         ▼
PR-E (quote BFF → approval-routing)   [can start design in parallel; merge after A]
```

| Batch | PRs | Notes |
|------:|-----|-------|
| 1 | A, B, F | Docs + tests only / pricing fixtures — safe parallel |
| 2 | C | Needs product rule API; no gateway break |
| 3 | D | Needs C + A; HIGH risk review |
| 4 | E | Needs A; coordinate with Kafka discount consumer to avoid double approvals |

**Blocked on:** human approval of forge wave (`docs/forge/07-wave-backlog.md`); decision on ADR open questions (stateless evaluate vs ephemeral session; shared DTO location); optional GitNexus re-index if stale before symbol edits.

---

## 7. Explicit DO NOT TOUCH list

1. **Do not remove or rename** `POST /api/v1/configuration/validate` (agent-contract).
2. **Do not delete** product-service `/api/v1/configuration/*` handlers without a gateway shim + contract-test parity (ADR).
3. **Do not** point agents at `/api/v1/configurations/{id}/validate` as a substitute for the agent validate path.
4. **Do not** remove gateway `erp-stub` / 501 behavior until `erp-adapter-service` serves the same paths.
5. **Do not** change quote BFF external paths (`/api/v1/quotes/**/approvals*`) when wiring approval-routing — adapt internally only.
6. **Do not** conflate DRL `/api/v1/rules/**` (pricing-service) with configuration `/api/v1/configuration/rules/**` (product-service).
7. **Do not** “fix” price books by mounting them under `/api/v1/pricing/price-books` without matrix ADR — prefer documenting `/api/v1/price-books` and correcting skills.
8. **Do not** embed domain validation logic in gateway filters beyond route + DTO adaptation (ADR §3).
9. **Do not** edit CRM cutover / FE CPQ UI / Jenkins stage SSOT in this track (owned by forge tracks 01–02–05–06).
10. **Do not** mass-rewrite Drools `.drl` packs without PR-F golden fixtures.
11. **Do not** commit application changes from this scout — plan file only until wave approval.

---

## 8. Discovery commands (re-runnable)

```bash
# Matrix vs ERP stub
rg -n 'erp-stub|NOT_IMPLEMENTED' backend/services/gateway-service

# Validate ownership
rg -n '@RequestMapping\("/api/v1/configuration' -A2 backend/services/product-service
rg -n '@RequestMapping\("/api/v1/configurations' -A2 backend/services/configuration-engine

# Approval dual stack
rg -n 'class ApprovalService' backend/services/quote-service backend/services/approval-routing

# Price-book path drift
rg -n '/api/v1/(pricing/)?price-books' agents docs/backlog -g '*.md'

# Controller test gap
find backend/services/{product,pricing,quote,pricebook,configuration-engine,approval-routing}-service \
  -name '*Controller.java' | wc -l
find backend/services/{product,pricing,quote,pricebook,configuration-engine,approval-routing}-service \
  -path '*/test/**' -name '*Controller*Test*.java' | wc -l
```

---

## 9. Scout summary (for orchestrator merge)

| Finding | Severity | Next PR |
|---------|----------|---------|
| S2B-11 validate shim + structured body missing; ConstraintSolver placeholder | CRITICAL | C → D |
| Quote BFF approvals not delegated (S2C-12 gap) | HIGH | E |
| Zero MockMvc/contract coverage on CPQ agent controllers | HIGH | A |
| Price books live but matrix-absent; skill path `/pricing/price-books` wrong | HIGH | B |
| ERP paths 501 stub only | Expected / blocking preflight | Defer adapter |
| Pricing execute lacks golden fixtures (S2C-1b) | MEDIUM | F |

**Recommended wave-1 start after approval:** **PR-A + PR-B** in parallel, then **PR-C → PR-D**.
