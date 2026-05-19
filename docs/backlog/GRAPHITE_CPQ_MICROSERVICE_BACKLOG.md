# Graphite CPQ Microservice Backlog

This backlog translates the CPQ microservice documentation into Graphite-friendly stacks. Each stack is a dependent PR chain that should stay green at every step. Independent stack lanes can run in parallel in separate git worktrees or subagent workspaces.

## Execution Model

- Use `main` as trunk and keep PRs small, following `docs/GRAPHITE_STACKED_PRS.md`.
- Prefer one Graphite stack per epic. Do not place unrelated CRM, CPQ, frontend, and AI work in one long chain.
- Run Stack 0 first to restore fast feedback. Run Stack 1 after Stack 0 so integration-test work starts from a clean unit-test baseline.
- Run Stack 2 lanes in parallel after `S1A-5` and `S1B-6` establish backend verification. Run Stack 3 in the sibling `pricing-ai-rules-service` repo in parallel with Stack 2, except where it depends on pricing/rules contracts.
- Use one worktree per stack lane, for example `stacks/s0-crm-test`, `stacks/s2-pricing`, and `../pricing-ai-rules-service-stacks/s3-rules-core`.
- Stack 2 lanes may **start** in parallel after `S2A-6`, but **merge order** must follow cross-lane dependencies: `S2B-11` and `S2C-1b` before `S2D-3a`; `S2D-2` before `S2E-4`; `S2E-4` before `S2F-4`; `S2C-12` before `S2D-3`.
- Graphite playbook: author amendment slices bottom-up per lane (`graphite-stack-author` → `gt create --insert` when inserting mid-stack, e.g. `S2C-1b` after `S2C-1`); ship with `graphite-submit-sync` only after lane-local `mvn verify` / scoped Nx tests pass.

## Stack 0: Immediate Stability

Goal: restore the two known red tests before broad refactors or infrastructure work.

### Stack 0A: CRM Unit Test Baseline

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S0A-1 | `backend/services/crm-service` | Fix `LeadServiceTest$GetStatusCounts` by aligning the mock setup with `LeadService.getStatusCounts()` and `LeadRepository.countGroupByStatus()`, or by changing the service to intentionally call `countByStatusAndDeletedFalse` for all statuses. | None | `docs/CRM_TESTING_STRATEGY.md`; `backend/services/crm-service/DDD_SPEC.md` |
| S0A-2 | `backend/services/crm-service` | Add an explicit assertion for statuses omitted from grouped repository results so dashboard counts are deterministic for zero-count statuses. | S0A-1 | `MODERNIZATION_PLAN.md`; `docs/CRM_TESTING_STRATEGY.md` |
| S0A-3 | `backend/services/crm-service` | Verify CRM unit tests remain Docker-free and document the focused command in the PR body. | S0A-2 | `docs/CRM_TESTING_STRATEGY.md`; `backend/README.md` |

### Stack 0B: Frontend Shared Data Access Test Baseline

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S0B-1 | `frontend/libs/shared/data-access` | Fix the missing `shared-data-access.css` stylesheet reference or remove the unused scaffold component/spec if the library is intended to be service-only. | None | `frontend/libs/shared/data-access/README.md`; `frontend/libs/shared/data-access/project.json` |
| S0B-2 | `frontend/libs/shared/data-access` | Convert `notification.service.spec.ts` from Jasmine globals to Vitest-compatible `vi.fn`, `expect.objectContaining`, and typed mocks. | S0B-1 | `frontend/libs/shared/data-access/vite.config.mts`; `frontend/libs/shared/data-access/tsconfig.spec.json` |
| S0B-3 | `frontend/libs/shared/data-access` | Align standalone component metadata and Angular TestBed setup so the `shared-data-access` unit suite compiles under Nx/Vitest. | S0B-2 | `frontend/libs/shared/data-access/README.md`; `AGENT.md` |
| S0B-4 | `frontend/libs/shared/data-access` | Add focused tests for exported shared data-access primitives that are documented as library features: API response interceptor, error interceptor, loading service, notification service, and base CRUD service. | S0B-3 | `frontend/libs/shared/data-access/README.md`; `frontend/libs/shared/data-access/USAGE_EXAMPLE.md` |
| S0B-5 | `frontend` | Add `shared-data-access` to the documented frontend unit-test run set so CI catches regressions in the library. | S0B-4 | `docs/CRM_TESTING_STRATEGY.md`; `MODERNIZATION_PLAN.md` |

Parallelization: S0A and S0B can run in parallel in separate worktrees because they touch backend CRM tests and frontend shared library tests independently.

## Stack 1: Test Infrastructure

Goal: make backend integration tests predictable and move toward green `mvn verify` across backend modules.

### Stack 1A: Testcontainers and Docker Readiness

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S1A-1 | `backend/services/crm-service` | Pick one Testcontainers wiring pattern: explicit `@Container` plus normal PostgreSQL driver, or pure `jdbc:tc:` mode; remove the mixed setup. | S0A-3, S0B-5 | `docs/CRM_TESTING_STRATEGY.md`; `backend/README.md` |
| S1A-2 | `backend/services/crm-service` | Tag Docker-dependent integration tests consistently, including `*IntegrationTest` and `LeadToDealLifecycleTest`, without removing them from full verification. | S1A-1 | `docs/CRM_TESTING_STRATEGY.md`; `backend/README.md` |
| S1A-3 | `backend` | Add Maven profile(s) for unit-only PR verification and full Docker-backed integration verification. | S1A-2 | `docs/CRM_TESTING_STRATEGY.md`; `MODERNIZATION_PLAN.md` |
| S1A-4 | `backend` | Add Windows PowerShell and Docker Desktop Testcontainers guidance plus default environment checks to backend test documentation. | S1A-3 | `backend/README.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |
| S1A-5 | `.github/workflows` | Split CI into fast unit checks and Docker-backed integration checks while preserving full `mvn verify` on the integration job. | S1A-3 | `docs/CRM_TESTING_STRATEGY.md`; `docs/CRM_CUTOVER_CHECKLIST.md`; `docs/GRAPHITE_STACKED_PRS.md` |

### Stack 1B: Backend Module Verification Sweep

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S1B-1 | `backend/libs/common`, `backend/libs/security`, `backend/libs/rules-engine` | Establish shared-library verification as the bottom PR so service stacks can depend on stable common code. | S0A-3, S0B-5 | `docs/GRAPHITE_STACKED_PRS.md`; `backend/CPQ_IMPLEMENTATION.md` |
| S1B-2 | `backend/services/*` | Inventory all backend module test modes and identify which modules require Docker, Redis, or only mocks. | S1B-1 | `backend/README.md`; `docs/CRM_TESTING_STRATEGY.md` |
| S1B-3 | `backend` | Add or normalize test profile properties for PostgreSQL, Redis, Flyway/Liquibase, and service schemas so module tests do not depend on developer-local config. | S1B-2 | `backend/README.md`; `MODERNIZATION_PLAN.md` |
| S1B-4 | `frontend` | Align `nx.json` `defaultBase` with Graphite trunk `main` before relying on affected-based checks. | S0B-5 | `docs/GRAPHITE_STACKED_PRS.md`; `docs/CRM_TESTING_STRATEGY.md` |
| S1B-5 | `backend` | Normalize documented Maven commands with `-am` where internal modules are required, and state the expected per-PR verification command. | S1B-3 | `backend/README.md`; `docs/GRAPHITE_STACKED_PRS.md` |
| S1B-6 | `backend` | Add a top-of-stack verification PR that proves backend modules can run under the chosen unit/integration split. | S1B-5 | `AGENT.md`; `docs/GRAPHITE_STACKED_PRS.md` |

Parallelization: S1A and S1B can start after Stack 0. S1A owns Testcontainers wiring/profile semantics; S1B owns module inventory, Nx base alignment, and verification documentation. Merge S1A before CI workflow changes if both touch workflow files.

## Stack 2: Feature Implementation

Goal: implement CPQ microservice work from `AGENT.md`, `MODERNIZATION_PLAN.md`, and the service `DDD_SPEC.md` files in reviewable Graphite lanes.

### Stack 2A: Shared Contracts and Published Language

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2A-1 | `backend/libs/common` | Standardize shared CPQ value objects: `Money`, `Percentage`, `EffectivePeriod`, correlation IDs, and idempotency IDs. | S1A-5, S1B-6 | `backend/CPQ_IMPLEMENTATION.md`; `AGENT.md` |
| S2A-2 | `backend/libs/common` | Define versioned published-language DTOs for `ProductRetired`, `ConfigurationCommitted`, `PriceCalculated`, `DiscountApprovalRequired`, `QuoteAccepted`, `ApprovalCompleted`, `DocumentRendered`, and `SubscriptionRenewalEligible`. | S2A-1 | `backend/services/quote-service/DDD_SPEC.md`; `backend/services/pricing-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2A-3 | `backend/libs/common` | Harden transactional outbox model/publisher interfaces and normalize `KafkaTopics` constants for the CPQ event set. | S2A-2 | `backend/CPQ_IMPLEMENTATION.md`; `docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md` |
| S2A-4 | `backend/libs/rules-engine` | Add batch DRL compile tests for all pricing-service DRLs plus conflict/order regression tests. | S2A-1 | `docs/phase0/phase0-drools-compatibility.md`; `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| S2A-5 | `backend/libs/rules-engine` | Introduce derived metric facts for ARR, TCV, and ACV without enabling new price writers yet. | S2A-4 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2A-6 | `backend/libs/common` | Add contract or serialization tests for published-language DTOs before service consumers adopt them. | S2A-2 | `AGENT.md`; `docs/GRAPHITE_STACKED_PRS.md` |
| S2A-7 | `docs/backlog`, `backend/services/gateway-service` | Publish `CPQ_API_CONTRACT_MATRIX.md`: canonical agent-critical routes (`/api/v1/configuration/*`, `/api/v1/pricing/*`, `/api/v1/rules/*`, `/api/v1/quotes/*`), owning service, and stability tier; add gateway route stubs that preserve legacy paths. | S2A-6 | `cpq-pricing-prd.md`; `agents/validation-rules.md`; `agents/compatibility-rules.md`; `agents/minimum-margin-enforcement.md` |
| S2A-8 | `docs/backlog`, `backend/services/product-service`, `backend/services/configuration-engine` | Record configuration API reconciliation ADR: rule CRUD remains on `product-service` (`ConfigurationRuleController`); session-scoped validation remains on `configuration-engine` (`/api/v1/configurations/{id}/validate`); gateway keeps agent-stable `POST /api/v1/configuration/validate`. | S2A-7 | `backend/services/configuration-engine/DDD_SPEC.md`; `backend/services/product-service/DDD_SPEC.md`; `agents/compatibility-rules.md` |

### Stack 2B: Product and Configuration Boundary

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2B-1 | `backend/services/product-service` | Implement product lifecycle commands and events: publish, update, retire, active-bundle guardrails, and active-quote guardrails. | S2A-2 | `backend/services/product-service/DDD_SPEC.md`; `agents/product-sku-management.md` |
| S2B-2 | `backend/services/product-service` | Add SKU validation, duplicate-search APIs, product type/category metadata checks, and bulk import validation. | S2B-1 | `agents/product-sku-management.md`; `MODERNIZATION_PLAN.md` |
| S2B-3 | `backend/services/product-service` | Implement attribute definitions, product attribute values, option groups, options, and option price adjustments. | S2B-2 | `agents/attribute-feature-catalog.md`; `agents/option-feature-configuration.md` |
| S2B-4 | `backend/services/product-service` | Implement bundle aggregate APIs with min/max, required/optional items, no circular nesting, and bundle change events. | S2B-3 | `agents/bundle-assembly.md`; `backend/services/product-service/DDD_SPEC.md` |
| S2B-5 | `backend/services/pricebook-service` | Implement `PriceBook` and `PriceBookEntry` with effective dating, publication lifecycle, immutable published versions, and resolution API. | S2A-1 | `backend/services/pricebook-service/DDD_SPEC.md`; `agents/price-book-management.md` |
| S2B-6 | `backend/services/pricebook-service` | Add coverage/gap queries for active products by region, segment, currency, and effective period. | S2B-5 | `agents/price-book-management.md`; `MODERNIZATION_PLAN.md` |
| S2B-7 | `backend/services/configuration-engine` | Implement `ConfigurationSession`, option selection, attribute assignment, validation result, and committed immutable snapshot. | S2B-4 | `backend/services/configuration-engine/DDD_SPEC.md`; `agents/option-feature-configuration.md` |
| S2B-8 | `backend/services/configuration-engine` | Implement compatibility rule validation for `REQUIRES`, `EXCLUDES`, `RECOMMENDS`, `DEFAULT`, and `VALIDATES`, including graph conflict checks. | S2B-7 | `agents/compatibility-rules.md`; `agents/validation-rules.md` |
| S2B-9 | `backend/services/configuration-engine` | Consume `ProductRetired` and bundle changes to invalidate active sessions and compiled configuration models. | S2A-3, S2B-8 | `backend/CPQ_IMPLEMENTATION.md`; `backend/services/configuration-engine/DDD_SPEC.md` |
| S2B-10 | `backend/services/configuration-engine` | Add tests for validation, selection, and recommendation-rule boundaries so AI recommendations cannot mark invalid configurations as valid. | S2B-9 | `backend/services/configuration-engine/DDD_SPEC.md`; `../pricing-ai-rules-service/PRD.md` |
| S2B-11 | `backend/services/product-service`, `backend/services/gateway-service`, `backend/services/configuration-engine` | Implement configuration API compatibility layer per S2A-8 ADR: harden `POST/GET/DELETE /api/v1/configuration/rules*` on product-service; implement gateway-backed `POST /api/v1/configuration/validate` that delegates to configuration-engine session validation or a stateless compatibility evaluator; add contract tests proving agent paths remain stable. | S2A-8, S2B-8, S2E-1 | `agents/compatibility-rules.md`; `agents/validation-rules.md`; `cpq-pricing-prd.md` |

### Stack 2C: Pricing, Pricebook, and Approval

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2C-1 | `backend/services/pricing-service` | Define deterministic `PricingSnapshot`, `PriceCalculation`, canonical waterfall stages, audit log, and `PriceCalculated` event. | S2A-2, S2A-5 | `backend/services/pricing-service/DDD_SPEC.md`; `../pricing-ai-rules-service/PRD.md` |
| S2C-1b | `backend/services/pricing-service`, `backend/libs/rules-engine` | Expose agent-critical deterministic REST contracts: `POST /api/v1/pricing/execute` (waterfall + audit + `PriceCalculated`), `POST /api/v1/pricing/simulate` (no persistence), and wire `POST /api/v1/rules/{id}/validate` (`RuleController` → `RuleService.validate` → rules-engine compile check); add golden fixtures aligned with S2A-4 DRL tests. | S2C-1, S2A-4 | `cpq-pricing-prd.md`; `agents/minimum-margin-enforcement.md`; `agents/volume-discount.md`; `agents/validation-rules.md` |
| S2C-2 | `backend/services/pricing-service` | Add pricebook resolution adapter/projection and list-price/floor-price initialization. | S2B-5, S2C-1b | `agents/list-price-application.md`; `backend/services/pricebook-service/DDD_SPEC.md` |
| S2C-3 | `backend/services/pricing-service` | Implement pricing method mapping for tiered/block, graduated, percent-of-total, cost-plus placeholder, subscription, usage, and custom DRL validation. | S2C-2 | `agents/pricing-method.md`; `../pricing-ai-rules-service/PRD.md` |
| S2C-4 | `backend/services/pricing-service` | Implement seeded Phase 0 rule execution: named-seat volume, usage overage, perpetual maintenance, core pack, migration credit, proration, renewal uplift, and margin exception. | S2A-4, S2C-3 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `docs/phase0/phase0-drools-compatibility.md` |
| S2C-5 | `backend/services/pricing-service` | Add derived metrics for ARR, TCV, and ACV before porting quote-level additive pricing policies. | S2C-4 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2C-6 | `backend/services/pricing-service` | Add TCV pricing tiers and ACV minimum commitment rules with explicit write-order and conflict tests. | S2C-5 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `MODERNIZATION_PLAN.md` |
| S2C-7 | `backend/services/pricing-service` | Implement discount stacking policy and caps for volume, segment, promotional, and deal-specific discounts. | S2C-4 | `agents/volume-discount.md`; `agents/customer-segment-discount.md`; `agents/promotional-campaign.md`; `agents/deal-specific-discount.md` |
| S2C-8 | `backend/services/pricing-service` | Implement margin/cost integration port with cached ERP-cost fallback and margin audit fields. | S2C-3 | `agents/markup-cost-integration.md`; `agents/minimum-margin-enforcement.md` |
| S2C-9 | `backend/services/pricing-service` | Publish `DiscountApprovalRequired` and `MarginThresholdViolated` when thresholds trigger; do not decide approval. | S2C-8 | `backend/services/pricing-service/DDD_SPEC.md`; `backend/services/approval-routing/DDD_SPEC.md` |
| S2C-10 | `backend/services/pricing-service` | Extend `POST /api/v1/pricing/simulate` (from S2C-1b) with scenario packs for what-if pricing, renewal, tier, bundle, FX, migration, and margin cases. | S2C-1b, S2C-4 | `agents/renewal-opportunity-detection.md`; `agents/usage-based-pricing.md`; `agents/bundle-assembly.md` |
| S2C-11 | `backend/services/pricing-service` | Add FX conversion port and currency-normalized pricing audit metadata. | S2C-2 | `agents/currency-fx-management.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2C-12 | `backend/services/approval-routing`, `backend/services/quote-service` | Reconcile approval ownership per canonical model: **pricing** publishes `DiscountApprovalRequired` / `MarginThresholdViolated`; **approval-routing** owns policy evaluation, workflow state, and decisions (`/api/v1/approvals/*`); **quote-service** owns quote lifecycle orchestration and exposes BFF endpoints (`POST /api/v1/quotes/{quoteId}/approvals`, `GET /api/v1/quotes/approvals/pending`) that delegate to approval-routing; deprecate duplicate in-process approval logic in quote-service; persist policy-evaluation snapshots. | S2C-9 | `backend/services/approval-routing/DDD_SPEC.md`; `backend/services/quote-service/DDD_SPEC.md`; `agents/multilevel-approval-routing.md`; `agents/minimum-margin-enforcement.md` |
| S2C-13 | `backend/services/approval-routing` | Implement approval policy, steps, decisions, SLA/escalation, separation-of-duty, and deterministic policy tests; consume pricing threshold events and map tiers (DEAL_DESK, CFO, below-cost exception) without recomputing price. | S2C-12 | `backend/services/approval-routing/DDD_SPEC.md`; `agents/multilevel-approval-routing.md` |
| S2C-14 | `backend/services/pricing-service`, `backend/services/approval-routing` | Add deterministic margin-enforcement integration tests: `POST /api/v1/pricing/execute` with `approval-margin-exception.drl` emits `MarginThresholdViolated`; approval-routing creates the correct tier workflow; no AI or LLM on the path. | S2C-1b, S2C-9, S2C-13 | `agents/minimum-margin-enforcement.md`; `docs/phase0/phase0-drools-compatibility.md` |

### Stack 2F: Hybrid Rules Management and OpenL

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2F-1 | `backend/libs/rules-engine` | Define the rule engine port that separates business-user OpenL tables from programmatic Drools rules while preserving one deterministic execution trace. | S2A-5 | `MODERNIZATION_PLAN.md`; `AGENT.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2F-2 | `backend/services/pricing-service` | Add OpenL pricing/discount table import and validation scaffolding for business-user maintained pricing rules. | S2F-1, S2C-3 | `MODERNIZATION_PLAN.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2F-3 | `backend/services/pricing-service` | Add precedence tests proving OpenL pricing/discount outputs and Drools complex-rule outputs reconcile without double-discounting. | S2F-2, S2C-7 | `MODERNIZATION_PLAN.md`; `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| S2F-4 | `frontend/libs/crm/cpq` | Add an admin-facing rule management backlog stub for OpenL table upload/validation status, gated behind backend contracts. | S2F-2, S2E-4 | `MODERNIZATION_PLAN.md`; `AGENT.md` |

### Stack 2D: Quote, Documents, Contract, and CRM Fan-Out

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2D-1 | `backend/services/quote-service` | Implement Quote aggregate with line items inside the aggregate, totals reconciliation, status history, and valid-until rules. | S2A-1 | `backend/services/quote-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2D-2 | `backend/services/quote-service` | Persist product, configuration, pricing, customer, and terms snapshots on quote lines. | S2B-10, S2C-4, S2D-1 | `backend/services/quote-service/DDD_SPEC.md`; `backend/CPQ_IMPLEMENTATION.md` |
| S2D-3a | `backend/services/quote-service` | Implement validation-rules preflight orchestrator for `FULL_PREFLIGHT` scope: product status checks, configuration validate via `ProductServiceClient`, quote completeness, cross-service referential integrity (customer/deal/pricing-customer), and DRL validate handoff; classify ERROR vs WARNING; no AI overrides of deterministic failures. | S2B-11, S2C-1b, S2D-2 | `agents/validation-rules.md`; `agents/compatibility-rules.md`; `cpq-pricing-prd.md` |
| S2D-3 | `backend/services/quote-service` | Add quote submission orchestration: invoke S2D-3a preflight, pricing freshness via `PricingServiceClient`, tax readiness, and approval handoff via quote BFF → approval-routing; block submit on hard validation errors. | S2C-14, S2D-3a | `agents/validation-rules.md`; `agents/deal-desk-workflow-orchestration.md` |
| S2D-4 | `backend/services/quote-service` | Implement quote versioning, revision, amendment model, and accepted quote immutability. | S2D-1 | `backend/services/quote-service/DDD_SPEC.md`; `agents/term-coterming.md` |
| S2D-5 | `backend/services/quote-service` | Implement tax calculation state/ACL placeholder and block submit on unmapped or provisional tax where required. | S2D-3 | `agents/tax-calculation-integration.md`; `backend/services/quote-service/DDD_SPEC.md` |
| S2D-6 | `backend/services/quote-service` | Publish versioned `QuoteAccepted.v1` with required snapshots and correlation IDs. | S2D-3, S2D-4 | `backend/services/quote-service/DDD_SPEC.md`; `backend/CPQ_IMPLEMENTATION.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2D-7 | `backend/services/document-generation` | Implement idempotent `DocumentGenerationJob`, template version resolution, render lifecycle, retry states, and failure states. | S2D-6 | `backend/services/document-generation/DDD_SPEC.md`; `agents/quote-document-generation.md` |
| S2D-8 | `backend/services/document-generation` | Consume `QuoteAccepted`, render quote/proposal PDF asynchronously, and publish `DocumentRendered`. | S2D-7 | `backend/CPQ_IMPLEMENTATION.md`; `backend/services/document-generation/DDD_SPEC.md` |
| S2D-9 | `backend/services/contract-asset-management` | Consume `QuoteAccepted`, create subscription/contract snapshot, and enforce idempotent activation workflow. | S2D-6 | `backend/services/contract-asset-management/DDD_SPEC.md`; `agents/contract-lifecycle-management.md` |
| S2D-10 | `backend/services/contract-asset-management` | Add renewal eligibility scheduler and publish `SubscriptionRenewalEligible`. | S2D-9 | `agents/renewal-opportunity-detection.md`; `backend/CPQ_IMPLEMENTATION.md` |
| S2D-11 | `backend/services/contract-asset-management` | Add amendment, co-term, and proration model hooks where pricing owns calculation and contract owns state. | S2D-9, S2C-4 | `agents/term-coterming.md`; `agents/upgrade-downgrade-path.md` |
| S2D-12 | `backend/services/crm-service` | Consume `QuoteAccepted` idempotently to update deal/opportunity state without moving accepted commercial ownership into CRM. | S2D-6 | `backend/services/crm-service/DDD_SPEC.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |
| S2D-13 | `backend/services/crm-service` | Add deal search fields needed by CPQ analytics: competitor, deal type, stage duration, and win/loss outcome. | S2D-12 | `agents/win-probability-analysis.md`; `agents/competitive-pricing-intelligence.md` |

### Stack 2E: Gateway and Frontend CPQ Access

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2E-1 | `backend/services/gateway-service` | Add gateway routes for product, configuration, pricing, quote, approval, document status, and contract status; keep domain logic out of the gateway. | S1A-5, S1B-6 | `backend/services/gateway-service/DDD_SPEC.md`; `AGENT.md` |
| S2E-2 | `frontend/libs/shared/data-access` | Add typed clients for CPQ published APIs after backend route contracts are available. | S2E-1, S2A-7 | `AGENT.md`; `MODERNIZATION_PLAN.md`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` |
| S2E-3 | `frontend/libs/crm/*` | Add dashboard and lead lifecycle UI test coverage for gateway-backed CRM flows before broader CPQ UI work. | S0B-5, S2E-1 | `MODERNIZATION_PLAN.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |
| S2E-4 | `frontend/libs/crm/cpq` | Scaffold CPQ feature library boundaries for catalog, configuration, pricing, quote, and rules admin flows. | S2E-2, S2D-2 | `MODERNIZATION_PLAN.md`; `AGENT.md` |

Parallelization: S2B product/pricebook work can run in parallel after S2A shared contracts. S2C pricing is mostly linear because each price stage depends on prior pricing state. S2D quote and consumer services split into parallel document, contract, CRM, and gateway lanes after `QuoteAccepted.v1`. **Merge gates:** land `S2A-7`/`S2A-8` before `S2B-11`; land `S2C-1b` before `S2D-3a`; land `S2C-12`/`S2C-14` before `S2D-3`; land `S2D-2` before `S2E-4`; land `S2E-4` before `S2F-4`.

## Stack 3: AI Integration and Pricing Rules Service

Goal: harden `pricing-ai-rules-service` and connect it to Open CRM AI/pricing boundaries without mixing deterministic pricing with advisory AI outputs.

### Stack 3A: Price Drools Stability and Compatibility

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3A-1 | `pricing-ai-rules-service/price-drools-service` | Reconcile actual platform state: Spring Boot 3.5 vs 4, Spring AI disabled vs enabled, endpoint prefix drift, and current migration baseline. | None | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3A-2 | `pricing-ai-rules-service/price-drools-service` | Fix remaining test compilation and startup failures, starting with `RulePerformanceAnalysisServiceTest` and Spring Cloud Config test startup. | S3A-1 | `../pricing-ai-rules-service/BUILD_SUMMARY.md`; `../pricing-ai-rules-service/price-drools-service/COMPILE_ERRORS.md` |
| S3A-3 | `pricing-ai-rules-service/price-drools-service` | Freeze pricing, rules, optimization, and AI endpoint contracts, including `/api/v1/...` vs `/v1/...` prefix alignment. | S3A-1 | `../pricing-ai-rules-service/price-drools-service/README.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3A-4 | `pricing-ai-rules-service/price-drools-service` | Build golden fixtures for single pricing, batch pricing, financial metrics, validation failures, and auth failures. | S3A-3 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/python-port.plan.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3A-5 | `pricing-ai-rules-service/price-drools-service` | Replace deprecated `@MockBean`/`@SpyBean` test usage with Spring Boot 4-compatible test annotations where applicable. | S3A-2 | `../pricing-ai-rules-service/compilation-errors-inventory.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3A-6 | `pricing-ai-rules-service/price-drools-service` | Verify Drools 10.1 compatibility with Jakarta/Spring Boot 4 using rule compile and KieSession smoke tests. | S3A-5 | `../pricing-ai-rules-service/dependency-compatibility-report.md`; `../pricing-ai-rules-service/AGENTS.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |

### Stack 3B: Rule Engine Domain Core

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3B-1 | `price-drools-service` | Implement or verify `AuditService` entity, repository, and service referenced by the agentic development plan. | S3A-2 | `../pricing-ai-rules-service/AGENTIC_DEVELOPMENT_PLAN.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-2 | `price-drools-service` | Align `FinancialMetrics` model and API endpoints with ARR, TCV, ACV, CLV, churn, discount rate, multiplier, and minimum commitment semantics. | S3A-4 | `../pricing-ai-rules-service/TECH_SPEC.md`; `../pricing-ai-rules-service/AGENTIC_DEVELOPMENT_PLAN.md` |
| S3B-3 | `price-drools-service` | Implement or verify canonical waterfall fields: list, special, regular, customer, partner, net unit, and net total. | S3A-4 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-4 | `price-drools-service` | Split pricing method parity into stackable PRs for `List`, `Cost`, `Block`, `Percent of Total`, `Subscription`, and `Usage`. | S3B-3 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-5 | `price-drools-service` | Implement deterministic `PriceRule`, `PriceCondition`, `PriceAction`, evaluation events, evaluation order, and tie breakers. | S3B-3 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3B-6 | `price-drools-service` | Add rule dependency graph validation for cycles, ambiguous precedence, and conflicting same-field mutations before activation. | S3B-5 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3B-7 | `price-drools-service` | Enforce immutable published ruleset versions, validation, CRUD, cache clearing, and rule test execution contracts. | S3B-6 | `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-8 | `price-drools-service` | Key rule/result caches by immutable ruleset version plus fact hash to avoid stale pricing behavior. | S3B-7 | `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-9 | `price-drools-service` | Add rule simulation harness for quote/config/pricing facts and approval outputs. | S3B-7 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/AI_SPEC.md` |
| S3B-10 | `price-drools-service` | Verify rule execution, validation, cache hit, pricing latency, and financial metrics are exposed with p95/p99 targets. | S3B-9 | `../pricing-ai-rules-service/TECH_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/README.md` |
| S3B-11 | `price-drools-service` | Verify public pricing endpoint RBAC and admin-only rules/optimization/AI endpoint RBAC. | S3A-3 | `../pricing-ai-rules-service/price-drools-service/README.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |

### Stack 3C: Backend Simplification and Controller Hygiene

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3C-1 | `price-drools-service` | Reconcile stale simplification docs with source state before acting, especially completed `BaseMCPClient` work. | S3A-1 | `../pricing-ai-rules-service/backend-simplification-tasks.md`; `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3C-2 | `price-drools-service` | Search and remove `NumericalParameterValue` duplication by migrating to `NumericParameterValue` if still present. | S3C-1 | `../pricing-ai-rules-service/backend-simplification-tasks.md` |
| S3C-3 | `price-drools-service` | Modernize existing Context7, Puppeteer, and Sequential Thinking MCP clients behind `BaseMCPClient` with typed errors, timeouts, retries, and circuit breakers. | S3C-1 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md`; `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3C-4 | `price-drools-service` | Refactor rule/pricing/optimization/AI controllers to rely on `GlobalExceptionHandler` instead of local broad try/catch blocks. | S3A-2 | `../pricing-ai-rules-service/backend-simplification-tasks.md`; `../pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/exception/README.md` |
| S3C-5 | `price-drools-service` | Add null-safety utility or targeted null-handling simplification for repetitive service/controller guards. | S3C-4 | `../pricing-ai-rules-service/backend-simplification-tasks.md` |

### Stack 3D: AI and MCP Re-Enablement

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3D-1 | `price-drools-service` | Preserve AI feature flags and add smoke tests for disabled-feature responses while Spring AI compatibility is constrained. | S3A-2 | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3D-2 | `price-drools-service` | Upgrade or confirm Spring AI 2.x compatibility, then enable `features.ai.enabled` only in a controlled test profile. | S3A-1, S3D-1 | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3D-3 | `price-drools-service` | Add or verify `@ConditionalOnProperty` coverage for remaining AI classes listed as additional migration-guide candidates. | S3D-2 | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md` |
| S3D-4 | `price-drools-service` | Remove disabled AI test annotations gradually and replace remaining `@MockBean` usage with `@MockitoBean`. | S3D-2, S3A-5 | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md`; `../pricing-ai-rules-service/compilation-errors-inventory.md` |
| S3D-5 | `price-drools-service` | Replace remaining legacy `OpenAiChatClient` usages with modern `ChatClient` or `ChatModel` patterns in performance and parameter extraction services. | S3D-2 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md`; `../pricing-ai-rules-service/compilation-errors-inventory.md` |
| S3D-6 | `price-drools-service` | Standardize typed structured output models across rule analysis, optimization, test generation, and performance analysis. | S3D-5 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md`; `../pricing-ai-rules-service/AI_SPEC.md` |
| S3D-7 | `price-drools-service` | Externalize prompts for rule analysis, optimization, test generation, and performance recommendations. | S3D-6 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md` |
| S3D-8 | `price-drools-service` | Harden create-rule AI workflow: analyze requirement, retrieve docs, draft rule, enhance, generate tests, run tests, optimize, and validate final rule. | S3D-7, S3B-9 | `../pricing-ai-rules-service/AI_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/README.md` |
| S3D-9 | `price-drools-service` | Add rule modification orchestration with impact analysis, dependency detection, regression test generation, and no silent activation. | S3D-8 | `../pricing-ai-rules-service/AI_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3D-10 | `price-drools-service` | Add ruleset evaluation orchestration with performance monitoring, AI recommendations, enhancement validation, and explainable outputs. | S3D-8 | `../pricing-ai-rules-service/AI_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3D-11 | `price-drools-service` | Add policy-safe human governance from AI-generated draft to validated ruleset to published production ruleset. | S3B-9, S3D-10 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3D-12 | `price-drools-service` | Add MCP tool tests for `generateRule`, `optimizeRule`, `analyzeRule`, `validateRule`, and `generateTestCases`. | S3D-3, S3C-3 | `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md`; `../pricing-ai-rules-service/AI_SPEC.md` |
| S3D-13 | `price-drools-service` | Validate MCP resources for `drools://documentation/*`, best practices, syntax reference, and reusable pricing patterns. | S3D-12 | `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3D-14 | `price-drools-service` | Add MCP auth, rate limiting, input validation, and no-stack-trace structured errors for `/api/mcp/**`. | S3D-12 | `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3D-15 | `price-drools-service` | Expose MCP tool-call, latency, resource-read, error, and health metrics. | S3D-12 | `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md`; `../pricing-ai-rules-service/price-drools-service/README.md` |
| S3D-16 | `price-drools-service` | Add safe `compileRule` and `executeTest` functions so AI can call deterministic Drools validation and test tools. | S3B-9, S3D-6 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md` |
| S3D-17 | `price-drools-service` | Add token, latency, cost, budget, and PII-redacted request/response logging for AI calls. | S3D-5 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md` |
| S3D-18 | `price-drools-service` | Add prompt injection, PII, and token-limit validation before model calls. | S3D-17 | `../pricing-ai-rules-service/ai-improvements-spring-plan.md`; `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3D-19 | `price-drools-service` | Fix reinforcement-learning experiment config/model gaps, variant generation, and parameter extraction under feature flags. | S3D-5 | `../pricing-ai-rules-service/price-drools-service/COMPILE_ERRORS.md`; `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md` |
| S3D-20 | `price-drools-service` | Replace manual AI performance-analysis JSON parsing with Jackson/ObjectMapper and add path tests for parsing logic. | S3D-6 | `../pricing-ai-rules-service/price-drools-service/cyclomatic-complexity-report-12.21.2025.md` |
| S3D-21 | `backend/services/ai-service` | Add `RulesOptimizationAdapter` contract from Open CRM `ai-service` to `price-drools-service` without allowing AI to mutate pricing directly. | S2A-2, S3D-11 | `backend/services/ai-service/DDD_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |

### Stack 3E: Open CRM AI Service Pilot

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3E-1 | `backend/services/ai-service` | Implement a thin internal LLM adapter for the current `CpqAgent` and `AgentRouter` seam without upgrading the AI framework in the same PR. | S1B-6 | `docs/phase0/phase0-ai-stack-decision.md`; `backend/services/ai-service/DDD_SPEC.md` |
| S3E-2 | `backend/services/ai-service` | Convert **orchestration-only** pilot agents from simulated output to model-backed explanations and tool routing for `customer-segment-discount` and `volume-discount`; every price outcome must come from `POST /api/v1/pricing/execute` (via `PricingServiceClient`), never from LLM output; **exclude** `minimum-margin-enforcement` (deterministic — covered by S2C-14). | S3E-1, S2C-1b | `docs/phase0/phase0-ai-stack-decision.md`; `agents/customer-segment-discount.md`; `agents/volume-discount.md` |
| S3E-2b | `backend/services/ai-service` | Add deterministic pricing-agent adapters (no LLM) for guardrail agents starting with `minimum-margin-enforcement`: invoke `POST /api/v1/pricing/execute`, interpret margin result, delegate approval creation to quote BFF → approval-routing; audit only. | S2C-14, S3E-1 | `agents/minimum-margin-enforcement.md`; `backend/services/ai-service/DDD_SPEC.md` |
| S3E-3 | `backend/services/ai-service` | Add audit logging and snapshot references for agent invocations before expanding domain agent batches. | S3E-2, S3E-2b | `backend/services/ai-service/DDD_SPEC.md`; `docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md` |
| S3E-4 | `backend/services/ai-service` | Batch remaining agents by domain package, one Graphite PR per package group with matching tests. | S3E-3 | `docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md`; `AGENT.md` |

Parallelization: S3A and S3C can start immediately in the pricing rules repo if they use separate worktrees. S3B should wait for the platform baseline and fixtures. S3D waits for compatibility and controller/client cleanup, but MCP tool/resource/security subtasks can split once the MCP server baseline is enabled. S3E can run in `open-crm-cpq` after Stack 1, but its rules-adapter PR waits for S3D-11 and Stack 2 shared contracts.

## Orchestrated Subagent Assignment

Graphite team playbook (`graphite-team-orchestrator` → `graphite-stack-author` → `graphite-stack-ship`): one worktree per lane; use `gt create --insert` when landing amendment PRs mid-stack (e.g. `S2C-1b` after `S2C-1`).

- `S0A`: one debugging agent focused only on `LeadServiceTest` and `LeadService`.
- `S0B`: one frontend test agent focused only on `frontend/libs/shared/data-access`.
- `S1A`: one backend test-infrastructure agent for Testcontainers wiring, tagging, and profiles.
- `S1B`: one CI/build agent for module inventory, Nx base alignment, and workflow split.
- `S2A`: one shared-contract agent; must land through `S2A-8` (API matrix + configuration ADR) before service lanes adopt agent paths.
- `S2B`, `S2C`, `S2D`, `S2E`, `S2F`: five implementation agents/worktrees after `S2A-8`; **parallel start, ordered merge** — see merge gates under Stack 2 parallelization.
- `S2B+S2E` coordination agent: owns `S2B-11` gateway/configuration compatibility (touches product-service, configuration-engine, gateway-service).
- `S2C+S2D` coordination agent: owns `S2C-1b`, `S2C-12`, `S2C-14`, `S2D-3a`, `S2D-3` cross-service pricing→approval→submit chain.
- `S3A`, `S3B`, `S3C`, `S3D`: pricing rules repo agents/worktrees, with S3C controller/MCP hygiene parallel to S3A stability.
- `S3E`: Open CRM AI-service agent; `S3E-2` (LLM orchestration pilots) and `S3E-2b` (deterministic guardrail adapters) must not be combined in one PR.

## Backlog Acceptance Criteria

- Every PR in a stack is independently reviewable and keeps its scoped verification green.
- Bottom PRs establish shared contracts or test stability before service behavior depends on them.
- No PR mixes deterministic pricing decisions with advisory AI behavior.
- No service doc ownership rule is violated: Quote owns quote lifecycle, Pricing owns final price, Approval owns approval policy, Subscription owns installed base, AI owns recommendations/audit, and Drools owns executable rule evaluation artifacts.
- Every PR description includes stack position, dependency, verification performed, and reference docs used.

## Amendment deliverables (post-review)

| Artifact | Backlog PR | Purpose |
|----------|------------|---------|
| [CPQ_API_CONTRACT_MATRIX.md](./CPQ_API_CONTRACT_MATRIX.md) | S2A-7 | 69-route agent-critical API registry |
| [ADR-configuration-api-reconciliation.md](./ADR-configuration-api-reconciliation.md) | S2A-8 | Rule CRUD vs session vs agent validate ownership |
| [AMENDMENT_PR_TEMPLATES.md](./AMENDMENT_PR_TEMPLATES.md) | All amendment PRs | Copy-paste Graphite PR bodies + merge gates |

## Post-Review Amendment PR Specifications

Amendments from architecture review (GitNexus: existing handlers in `ConfigurationRuleController`, `RuleController.validate`, `ApprovalService`; route map not yet indexed for agent paths). Each row adds explicit acceptance criteria for patch PRs inserted above.

| PR | Amendment | Acceptance criteria (all must pass) |
|---|---|---|
| **S2A-7** | API contract registry | (1) `CPQ_API_CONTRACT_MATRIX.md` lists every agent-critical route from `validation-rules`, `compatibility-rules`, and `minimum-margin-enforcement` with owning service. (2) Gateway exposes stable paths without domain logic. (3) Contract matrix referenced in PR template. **Verify:** `mvn -pl services/gateway-service -am test`; matrix review. |
| **S2A-8** | Configuration API ADR | (1) ADR names canonical owner for rule CRUD vs session validate vs agent validate. (2) Explicit decision: `POST /api/v1/configuration/validate` remains the agent contract path. (3) No breaking removal of product-service routes without gateway shim. **Verify:** ADR review + no compile changes required in this PR. |
| **S2B-11** | Configuration compatibility layer | (1) `POST /api/v1/configuration/validate` returns structured pass/fail compatible with agent PRDs. (2) `POST/GET/DELETE /api/v1/configuration/rules*` pass contract tests. (3) Gateway routes match S2A-7 matrix. (4) Session path `/api/v1/configurations/{id}/validate` remains available. **Verify:** `mvn -pl services/product-service,services/configuration-engine,services/gateway-service -am test`. |
| **S2C-1b** | Pricing execute + DRL validate REST | (1) `POST /api/v1/pricing/execute` returns deterministic waterfall + audit; golden tests match S2A-4 fixtures. (2) `POST /api/v1/pricing/simulate` persists nothing. (3) `POST /api/v1/rules/{id}/validate` delegates to rules-engine compile check. (4) `PricingServiceClient` integration test green. **Verify:** `mvn -pl services/pricing-service -am verify`. |
| **S2C-12** | Approval ownership reconciliation | (1) Pricing emits events only; no approval policy in pricing-service. (2) approval-routing owns `/api/v1/approvals/*` workflow state. (3) quote-service BFF delegates; duplicate `ApprovalService` decision logic removed or reduced to orchestration. (4) Agent paths `POST /api/v1/quotes/{quoteId}/approvals` and `GET /api/v1/quotes/approvals/pending` still work. **Verify:** cross-service test: pricing event → approval-routing workflow → quote BFF list pending. |
| **S2C-14** | Deterministic margin path | (1) Margin threshold breach produces `MarginThresholdViolated` event. (2) approval-routing assigns DEAL_DESK / CFO / below-cost tiers without LLM. (3) No AI-service code on the execution path. (4) Matches `approval-margin-exception.drl` salience 930 behavior. **Verify:** `mvn -pl services/pricing-service,services/approval-routing -am test` + scenario test from `minimum-margin-enforcement.md`. |
| **S2D-3a** | Validation-rules preflight | (1) `FULL_PREFLIGHT` runs product status, configuration validate, quote completeness, and cross-service integrity checks. (2) ERROR blocks; WARNING override-eligible. (3) Uses `ProductServiceClient` + `PricingServiceClient`; no inline compatibility logic in quote-service. **Verify:** `mvn -pl services/quote-service -am test` with mocked downstream clients. |
| **S3E-2** | LLM orchestration pilots (revised) | (1) Only `customer-segment-discount` and `volume-discount` use LLM. (2) All numeric outcomes sourced from `pricing/execute` responses. (3) LLM output cannot change price or approval tier. **Verify:** ai-service tests assert pricing client invoked; no direct price mutation in agent code. |
| **S3E-2b** | Deterministic guardrail adapters | (1) `minimum-margin-enforcement` adapter uses pricing/execute + approval BFF only. (2) Zero LLM calls in adapter path. (3) Audit log entries written. **Verify:** ai-service unit test with LLM bean absent/disabled. |
