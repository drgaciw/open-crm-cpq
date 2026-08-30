# CPQ API Contract Matrix

**Backlog PR:** S2A-7  
**Status:** Published contract registry (documentation only; gateway route stubs tracked in same PR stack)  
**Last verified against controllers:** 2026-08-30 (`ConfigurationRuleController`, `ConfigurationSessionController`, `RuleController`, `PricingExecutionController`, `CustomerController`, `PriceBookController`, quote/approval controllers)

---

## 1. Purpose and how to use

This matrix is the **canonical registry of agent-critical REST routes** for Open CRM CPQ. It satisfies S2A-7 acceptance criteria:

1. Every route referenced by `agents/validation-rules.md` (cpq-agent-016), `agents/compatibility-rules.md` (cpq-agent-015), `agents/minimum-margin-enforcement.md` (cpq-agent-011), and price-book consumers (cpq-agent-004 / 032 / 035) appears below with **owning service** and **stability tier**.
2. **Gateway** must expose these paths without embedding domain logic (`gateway-service` routes in `application.yml`; stubs in S2A-7).
3. PR descriptions for configuration, pricing, and approval work should link here instead of duplicating path lists.

**How to use:**

| Audience | Action |
|----------|--------|
| Agent / AI implementers | Call only `agent-contract` or `stable` paths unless an ADR explicitly promotes a `beta` path. |
| Service owners | Changing `agent-contract` rows requires ADR + matrix update + gateway contract test (S2B-11, S2C-1b). |
| Gateway / platform | Route predicates must preserve **external path**; delegate to owner in §3–§7. |
| Reviewers | Cross-check PR diffs against this file and `GRAPHITE_CPQ_MICROSERVICE_BACKLOG.md` Post-Review Amendment **S2A-7**. |

**Related work:** S2A-8 (configuration API ADR), S2B-11 (compatibility layer), S2C-12 (approval ownership), S2E-2 (typed frontend clients).

**Source documents:** `cpq-pricing-prd.md` §10.1, agent PRDs above, controller source under `backend/services/*/src/main/java`.

---

## 2. Stability tier definitions

| Tier | Meaning | Agent usage |
|------|---------|-------------|
| **stable** | Implemented, gateway-routed, intended for production UI and integrations. Breaking changes require version bump or deprecation window. | Preferred for all runtime agent flows. |
| **agent-contract** | Explicitly named in agent PRDs as the stable **external** contract; gateway must keep this path even if implementation moves (see §8). | **Required** for autonomous agents; do not substitute alternate paths without ADR. |
| **beta** | Implemented but boundary still moving (session model, approval split, response shape). | Use only when PRD allows; expect ADR follow-up. |
| **internal** | Service-to-service or superseded BFF; agents should not call directly when an `agent-contract` alias exists. | Orchestration layers only (quote-service → pricing-service, etc.). |

**Port map (local MVP):** crm-service `8081`, pricing-service `8082`, product-service `8083`, quote-service `8084`, approval-routing `8085`, configuration-engine `8088`, pricebook-service `8089`.

---

## 3. Configuration APIs

Configuration **rule CRUD** is owned by **product-service** (`ConfigurationRuleController` → `/api/v1/configuration`). **Session-scoped** validation lives on **configuration-engine** (`ConfigurationSessionController` → `/api/v1/configurations`). Agent PRDs standardize on **`POST /api/v1/configuration/validate`**. **Wave F / S2B-11 (hybrid):** gateway routes that POST to configuration-engine `POST /internal/v1/configurations/evaluate` (thin REQUIRES/EXCLUDES) and returns structured `{ valid, violations[] }` (`ConfigurationValidationResult` in `libs/common`). product-service `POST /validate` **retained** for direct/parity until post-parity retirement.

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| POST | `/api/v1/configuration/rules` | product-service (8083) | stable | cpq-agent-015 | `ConfigurationRuleController.create`; semi-autonomous rule creation after human approval. |
| GET | `/api/v1/configuration/rules` | product-service (8083) | stable | cpq-agent-015 | Paginated list; constraint graph / coverage analysis. |
| GET | `/api/v1/configuration/rules/{id}` | product-service (8083) | stable | cpq-agent-015 | Single rule fetch (implied by create/verify flow; not in PRD text but required for admin UI). |
| GET | `/api/v1/configuration/rules/product/{productId}` | product-service (8083) | stable | cpq-agent-015, cpq-agent-016 | Product-scoped rule set for validation and status checks. |
| DELETE | `/api/v1/configuration/rules/{id}` | product-service (8083) | stable | cpq-agent-015 | Rule retirement; requires impact assessment per agent PRD. |
| POST | `/api/v1/configuration/validate` | **gateway** → configuration-engine (8088) evaluate; product-service (8083) path **retained** | **agent-contract** | cpq-agent-015, cpq-agent-016 | **Canonical agent path.** Body `List<Long>` product IDs; gateway response `ApiResponse<ConfigurationValidationResult>` (`valid` + `violations[{ruleId,ruleType,description,remediation}]`). Internal: `POST /internal/v1/configurations/evaluate`. Thin REQUIRES/EXCLUDES only (full S2B-8 follow-on). |
| POST | `/api/v1/configurations` | configuration-engine (8088) | beta | — | Session create (`productId`, `userId` query params); not in three agent PRDs; related to S2B-7. |
| GET | `/api/v1/configurations/{id}` | configuration-engine (8088) | beta | — | Session read. |
| GET | `/api/v1/configurations` | configuration-engine (8088) | beta | — | List by `userId` + `status`. |
| POST | `/api/v1/configurations/{id}/selections` | configuration-engine (8088) | beta | — | Option selection mutations. |
| DELETE | `/api/v1/configurations/{id}/selections/{optionKey}` | configuration-engine (8088) | beta | — | Remove selection. |
| POST | `/api/v1/configurations/{id}/validate` | configuration-engine (8088) | beta | — | **Session-scoped** validate; different contract from agent path (§8). |
| POST | `/api/v1/configurations/{id}/complete` | configuration-engine (8088) | beta | — | Commit valid session. |
| POST | `/api/v1/configurations/{id}/cancel` | configuration-engine (8088) | beta | — | Cancel session. |

**Section row count:** 12

---

## 4. Pricing APIs

Deterministic pricing execution and customer context for margin and validation agents.

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| POST | `/api/v1/pricing/execute` | pricing-service (8082) | **agent-contract** | cpq-agent-011 | `PricingExecutionController`; waterfall + audit + `PriceCalculated`; runs `approval-margin-exception.drl` (salience 930). S2C-1b goldens: `fixtures/pricing/execute/*` + `fixtures/pricing/drl/*` (`PricingExecuteGoldenFixtureTest`, `PricingDrulesGoldenFixtureTest`, `@Tag("unit")`). |
| POST | `/api/v1/pricing/simulate` | pricing-service (8082) | stable | — | No persistence; referenced by other agents, not the three PRDs. |
| POST | `/api/v1/pricing/simulate/bulk` | pricing-service (8082) | beta | — | Implemented in `PricingExecutionController`; PRD §10.2 previously "not started". |
| GET | `/api/v1/pricing/audit/{quoteExternalId}` | pricing-service (8082) | beta | — | Audit trail query; margin audit / compliance cross-ref. |
| POST | `/api/v1/pricing-customers` | pricing-service (8082) | stable | — | Admin/catalog; not in three agent PRDs. |
| GET | `/api/v1/pricing-customers` | pricing-service (8082) | stable | — | List/filter by segment. |
| GET | `/api/v1/pricing-customers/{id}` | pricing-service (8082) | **agent-contract** | cpq-agent-011, cpq-agent-016 | Segment, churn, margin policy context (`CustomerController`). |
| PUT | `/api/v1/pricing-customers/{id}` | pricing-service (8082) | stable | — | Update pricing customer. |
| DELETE | `/api/v1/pricing-customers/{id}` | pricing-service (8082) | stable | — | Soft delete. |

**Section row count:** 9

---

## 4b. Price book APIs

Owned by **pricebook-service** (`PriceBookController` → `/api/v1/price-books`). Canonical **external** path is `/api/v1/price-books/**` (gateway → pricebook-service on 8089). Do **not** call `/api/v1/pricing/price-books` — that prefix routes to pricing-service and is not implemented.

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| GET | `/api/v1/price-books` | pricebook-service (8089) | **agent-contract** | cpq-agent-004, cpq-agent-032, cpq-agent-035 | List/filter by `region` (and currency via client filter); regional / default book resolution. |
| GET | `/api/v1/price-books/{id}` | pricebook-service (8089) | **agent-contract** | cpq-agent-004, cpq-agent-035 | Single book fetch after resolution. |
| POST | `/api/v1/price-books` | pricebook-service (8089) | stable | cpq-agent-035 | Create book; advisory agent — human confirmation required. |
| PUT | `/api/v1/price-books/{id}` | pricebook-service (8089) | stable | cpq-agent-035 | Update book metadata. |
| DELETE | `/api/v1/price-books/{id}` | pricebook-service (8089) | stable | cpq-agent-035 | Soft delete. |
| GET | `/api/v1/price-books/{id}/entries` | pricebook-service (8089) | **agent-contract** | cpq-agent-004, cpq-agent-032, cpq-agent-035 | List prices / floor prices for products on a quote. |
| GET | `/api/v1/price-books/{id}/entries/{entryId}` | pricebook-service (8089) | stable | cpq-agent-035 | Single entry fetch. |
| POST | `/api/v1/price-books/{id}/entries` | pricebook-service (8089) | stable | cpq-agent-035 | Create entry; human-gated for agent 035. |
| PUT | `/api/v1/price-books/{id}/entries/{entryId}` | pricebook-service (8089) | stable | cpq-agent-035 | Update entry. |
| DELETE | `/api/v1/price-books/{id}/entries/{entryId}` | pricebook-service (8089) | stable | cpq-agent-035 | Soft delete entry. |
| POST | `/internal/v1/price-books/resolve-batch` | pricebook-service (8089) | internal | — | `InternalPriceBookController`; pricing-service list-price resolution only. |

**Section row count:** 11

---

## 5. Rules / DRL APIs

Pricing **Drools** rules (distinct from product **configuration** rules in §3).

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| POST | `/api/v1/rules` | pricing-service (8082) | stable | — | Create rule in rule set (`RuleController`). |
| GET | `/api/v1/rules/by-rule-set/{ruleSetId}` | pricing-service (8082) | stable | — | List rules for a set. |
| GET | `/api/v1/rules/{id}` | pricing-service (8082) | stable | — | Get rule by ID. |
| PUT | `/api/v1/rules/{id}` | pricing-service (8082) | stable | — | Update rule. |
| DELETE | `/api/v1/rules/{id}` | pricing-service (8082) | stable | — | Soft delete. |
| POST | `/api/v1/rules/{id}/validate` | pricing-service (8082) | **agent-contract** | cpq-agent-016 | DRL compile/syntax check via `RuleService.validate`; blocks activation of malformed rules. |
| GET | `/api/v1/rules/{id}/impact` | pricing-service (8082) | beta | — | `RuleController.impact`; PRD §10.2 planned—**now implemented**. |
| POST | `/api/v1/rule-sets` | pricing-service (8082) | stable | — | Rule set lifecycle (`RuleSetController`). |
| GET | `/api/v1/rule-sets` | pricing-service (8082) | stable | — | List rule sets. |
| GET | `/api/v1/rule-sets/{id}` | pricing-service (8082) | stable | — | Get rule set. |
| PUT | `/api/v1/rule-sets/{id}` | pricing-service (8082) | stable | — | Update rule set. |
| DELETE | `/api/v1/rule-sets/{id}` | pricing-service (8082) | stable | — | Soft delete. |
| POST | `/api/v1/rule-sets/{id}/activate` | pricing-service (8082) | stable | — | DRAFT → ACTIVE. |
| POST | `/api/v1/rule-sets/{id}/deprecate` | pricing-service (8082) | stable | — | ACTIVE → DEPRECATED. |

**Section row count:** 14

---

## 6. Quote and approval APIs

Quote lifecycle and **BFF approval** paths used by agents (`quote-service`). Canonical workflow state on **approval-routing** (`/api/v1/approvals/*`) is **internal** to agents per S2C-12.

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| GET | `/api/v1/quotes` | quote-service (8084) | **agent-contract** | cpq-agent-015, cpq-agent-016 | Filters: `status`, `dealType`, `customerExternalId`; impact assessment & completeness. |
| GET | `/api/v1/quotes/{id}` | quote-service (8084) | **agent-contract** | cpq-agent-011 | Quote status for margin gate (DRAFT → PENDING_APPROVAL block). |
| GET | `/api/v1/quotes/{quoteId}/line-items` | quote-service (8084) | **agent-contract** | cpq-agent-011, cpq-agent-015, cpq-agent-016 | Line-level margin, impact analysis, completeness. |
| POST | `/api/v1/quotes/{quoteId}/approvals` | quote-service (8084) | **agent-contract** | cpq-agent-011 | BFF submit; tiers DEAL_DESK / CFO / below-cost exception; delegates to approval-routing (S2C-12). |
| GET | `/api/v1/quotes/{quoteId}/approvals` | quote-service (8084) | stable | cpq-agent-011 | Approval history for a quote. |
| GET | `/api/v1/quotes/approvals/pending` | quote-service (8084) | **agent-contract** | cpq-agent-011 | Pending queue; monitor unblock after approval. |
| POST | `/api/v1/quotes/approvals/{approvalId}/approve` | quote-service (8084) | stable | — | BFF approve action. |
| POST | `/api/v1/quotes/approvals/{approvalId}/reject` | quote-service (8084) | stable | — | BFF reject action. |
| POST | `/api/v1/quotes` | quote-service (8084) | stable | — | Create quote (supporting workflow). |
| PUT | `/api/v1/quotes/{id}` | quote-service (8084) | stable | — | Update DRAFT/REJECTED. |
| POST | `/api/v1/quotes/{quoteId}/line-items` | quote-service (8084) | stable | — | Add line item. |
| PUT | `/api/v1/quotes/{quoteId}/line-items/{lineItemId}` | quote-service (8084) | stable | — | Update line item. |
| DELETE | `/api/v1/quotes/{quoteId}/line-items/{lineItemId}` | quote-service (8084) | stable | — | Remove line item. |
| GET | `/api/v1/approvals/{id}` | approval-routing (8085) | internal | — | Canonical approval record; agents use quote BFF. |
| GET | `/api/v1/approvals/pending` | approval-routing (8085) | internal | — | Duplicate path shape vs quote BFF; prefer quote path for agents. |
| GET | `/api/v1/approvals/pending/tier/{tier}` | approval-routing (8085) | internal | — | Tier-filtered queue (DEAL_DESK, CFO). |
| POST | `/api/v1/approvals/{id}/approve` | approval-routing (8085) | internal | — | Canonical approve. |
| POST | `/api/v1/approvals/{id}/reject` | approval-routing (8085) | internal | — | Canonical reject. |

**Section row count:** 18

---

## 7. Cross-service integrity APIs

Routes used by cpq-agent-016 (and related catalog checks) outside configuration/pricing/quote cores. Product catalog rows support validation completeness (status, attributes, bundles).

### 7a. Product catalog (product-service)

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| GET | `/api/v1/products/{id}` | product-service (8083) | **agent-contract** | cpq-agent-015, cpq-agent-016 | Lifecycle status (ACTIVE, DRAFT, DEPRECATED, END_OF_LIFE). |
| GET | `/api/v1/products` | product-service (8083) | stable | — | List/filter catalog. |
| GET | `/api/v1/product-bundles/product/{productId}` | product-service (8083) | **agent-contract** | cpq-agent-016 | Bundle component status checks. |
| GET | `/api/v1/product-attributes` | product-service (8083) | **agent-contract** | cpq-agent-016 | Attribute definitions for data-quality validation. |
| GET | `/api/v1/product-attributes/products/{productId}/values` | product-service (8083) | **agent-contract** | cpq-agent-016 | Required/type/range validation. |
| POST | `/api/v1/product-attributes/products/{productId}/values` | product-service (8083) | stable | — | Set attribute values (configuration workflow). |
| GET | `/api/v1/product-bundles/{id}` | product-service (8083) | stable | — | Single bundle fetch. |
| POST | `/api/v1/products` | product-service (8083) | stable | — | Create product. |
| GET | `/api/v1/products/search` | product-service (8083) | stable | — | Search by name/SKU. |

**Subsection row count:** 9

### 7b. CRM (crm-service)

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| GET | `/api/v1/contacts` | crm-service (8081) | **agent-contract** | cpq-agent-016 | Cross-service integrity: customer/contact existence. |
| GET | `/api/v1/contacts/{id}` | crm-service (8081) | stable | — | Single contact (implied for referential checks). |
| GET | `/api/v1/deals/{id}` | crm-service (8081) | **agent-contract** | cpq-agent-016 | Deal validity and pipeline stage. |
| GET | `/api/v1/deals` | crm-service (8081) | stable | — | List deals. |

**Subsection row count:** 4

### 7c. ERP adapter (gateway stub — erp-adapter-service not implemented)

Agent PRDs reference ERP paths; **no `erp` backend controllers** exist under `backend/services`. As of **S2A-7** (#287), **gateway-service** registers route id `erp-stub` for `/api/v1/erp/**` → `FallbackController.erpNotImplemented()` (**501 NOT_IMPLEMENTED**). Planned owner: **erp-adapter-service** (future).

| Method | Path | Owning service | Stability | Consumer agents | Notes |
|--------|------|----------------|-----------|-----------------|-------|
| GET | `/api/v1/erp/costs/{productExternalId}` | **gateway stub** (planned erp-adapter) | agent-contract | cpq-agent-011, cpq-agent-016 | COGS / landed cost for margin and integrity. **501** until adapter lands. |
| GET | `/api/v1/erp/costs/bulk` | **gateway stub** (planned erp-adapter) | agent-contract | cpq-agent-011 | Bulk cost for multi-line quotes (canonical verb per agent PRDs). **501** until adapter lands. |
| GET | `/api/v1/erp/inventory/{productExternalId}` | **gateway stub** (planned erp-adapter) | agent-contract | cpq-agent-016 | Availability / FULL_PREFLIGHT. **501** until adapter lands. |

**Subsection row count:** 3

**§7 total row count:** 16 (9 + 4 + 3)

---

## 8. Known path tensions and reconciliation

| Tension | Current state | Agent contract | Reconciliation |
|---------|---------------|----------------|----------------|
| **Stateless vs session validate** | `POST /api/v1/configuration/validate` on **product-service** accepts product ID list. `POST /api/v1/configurations/{id}/validate` on **configuration-engine** validates an in-memory session. | Agents **must** use `/api/v1/configuration/validate` only. | **S2A-8 ADR:** rule CRUD stays on product-service; session validate stays on configuration-engine; gateway preserves agent path (may delegate in S2B-11). |
| **Validate response shape** | product-service returns `List<String>` messages; agent PRDs expect structured `{ valid, violations[] }`. | agent-contract path unchanged; body may evolve additively. | S2B-11 contract tests; no breaking removal of product-service handler without gateway shim. |
| **Quote vs approval-routing approvals** | Duplicate pending/approve paths under `/api/v1/quotes/...` and `/api/v1/approvals/...`. | Agents use **quote-service BFF** paths only. | S2C-12: pricing emits events; approval-routing owns state machine; quote-service orchestrates. |
| **ERP cost paths** | Gateway **501 stub** at `/api/v1/erp/**` (S2A-7); no erp-adapter yet. | agent-contract paths reserved. | Introduce erp-adapter-service; until then agents treat **501** as adapter unavailable per PRD fail-safes. |
| **Price-book path drift** | Skills historically documented `/api/v1/pricing/price-books`. Impl + gateway use `/api/v1/price-books` on **pricebook-service**. | Agents **must** use `/api/v1/price-books/**` only. | Corrected in agent skills (B0-6 / forge PR-B). Do not add a pricing-service mount without ADR. |

**ADR pointer:** [ADR-configuration-api-reconciliation.md](./ADR-configuration-api-reconciliation.md) (S2A-8).

---

## 9. Gateway routing expectations

External clients and agents should call **only through the gateway** (default port `8080`). Predicates from `gateway-service/src/main/resources/application.yml`:

| Gateway predicate | Upstream service | Agent-critical prefixes |
|-------------------|------------------|-------------------------|
| `/api/v1/configuration/**` | product-service | Rules CRUD + **`POST .../validate`** (agent-contract) |
| `/api/v1/configurations/**` | configuration-engine | Session APIs (beta); not agent-contract |
| `/api/v1/pricing/**`, `/api/v1/pricing-customers/**`, `/api/v1/rule-sets/**`, `/api/v1/rules/**` | pricing-service | execute, simulate, rules validate, pricing-customers |
| `/api/v1/price-books/**` | pricebook-service | list/get books + entries (**agent-contract** GETs); CRUD stable |
| `/api/v1/cpq/price-books/**` | pricebook-service (rewrite → `/api/v1/price-books/**`) | Gateway alias only; agents prefer non-`/cpq` path |
| `/api/v1/quotes/**` | quote-service | quotes, line-items, quote-scoped approvals |
| `/api/v1/approvals/**` | approval-routing | internal (orchestration only) |
| `/api/v1/products/**`, `/api/v1/product-bundles/**`, `/api/v1/product-options/**`, `/api/v1/product-attributes/**` | product-service | catalog integrity |
| `/api/v1/leads/**`, `/api/v1/deals/**`, `/api/v1/contacts/**`, `/api/v1/organizations/**` | crm-service | deal/contact integrity |
| `/api/v1/erp/**` | **gateway-service** (`erp-stub` → 501 fallback) | Agent-contract paths return **501** until erp-adapter-service exists (S2A-7) |

**S2A-7 gateway stub (implemented in #287):** Route id `erp-stub` forwards `/api/v1/erp/**` to `FallbackController.erpNotImplemented()` so agent paths get **501 NOT_IMPLEMENTED** instead of **404** before erp-adapter lands.

**Internal clients (reference):**

- `ProductServiceClient` → `GET /api/v1/products/{id}`, `POST /api/v1/configuration/validate`
- `PricingServiceClient` → `POST /api/v1/pricing/execute`, `POST /api/v1/pricing/simulate`
- `PriceBookResolutionClient` → `POST /internal/v1/price-books/resolve-batch` (pricing → pricebook; not agent-facing)

---

## Appendix A. Agent-contract path index (quick lookup)

| Path | Agents |
|------|--------|
| `POST /api/v1/configuration/validate` | 015, 016 |
| `POST /api/v1/configuration/rules` (+ GET list/product, DELETE) | 015 |
| `POST /api/v1/rules/{id}/validate` | 016 |
| `POST /api/v1/pricing/execute` | 011 |
| `GET /api/v1/pricing-customers/{id}` | 011, 016 |
| `GET /api/v1/price-books`, `GET .../{id}`, `GET .../{id}/entries` | 004, 032, 035 |
| `GET /api/v1/quotes`, `GET .../line-items` | 011, 015, 016 |
| `GET /api/v1/quotes/{id}` | 011 |
| `POST /api/v1/quotes/{quoteId}/approvals`, `GET /api/v1/quotes/approvals/pending` | 011 |
| `GET /api/v1/products/{id}`, bundles, attributes | 015, 016 |
| `GET /api/v1/contacts`, `GET /api/v1/deals/{id}` | 016 |
| `GET /api/v1/erp/costs/*`, `GET .../costs/bulk`, `GET .../inventory/*` | 011, 016 — **gateway stub (501)** |

---

## Appendix B. Row counts by section

| Section | Rows |
|---------|------|
| §3 Configuration APIs | 12 |
| §4 Pricing APIs | 9 |
| §4b Price book APIs | 11 |
| §5 Rules / DRL APIs | 14 |
| §6 Quote & approval APIs | 18 |
| §7 Cross-service integrity | 16 |
| **Total matrix rows** | **80** |

**Routes not assignable to an implemented backend service:** 0 (§7c ERP rows route through gateway stub; erp-adapter-service is future work).
