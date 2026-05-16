# Graphite CPQ Microservice Backlog

This backlog translates the CPQ microservice documentation into Graphite-friendly stacks. Each stack is a dependent PR chain that should stay green at every step. Independent stack lanes can run in parallel in separate git worktrees or subagent workspaces.

## Execution Model

- Use `main` as trunk and keep PRs small, following `docs/GRAPHITE_STACKED_PRS.md`.
- Prefer one Graphite stack per epic. Do not place unrelated CRM, CPQ, frontend, and AI work in one long chain.
- Run Stack 0 first to restore fast feedback. Run Stack 1 after Stack 0 so integration-test work starts from a clean unit-test baseline.
- Run Stack 2 lanes in parallel after `S1A-4` and `S1B-4` establish backend verification. Run Stack 3 in the sibling `pricing-ai-rules-service` repo in parallel with Stack 2, except where it depends on pricing/rules contracts.
- Use one worktree per stack lane, for example `stacks/s0-crm-test`, `stacks/s2-pricing`, and `../pricing-ai-rules-service-stacks/s3-rules-core`.

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
| S0B-1 | `frontend/libs/shared/data-access` | Fix the `shared-data-access` component/spec failure by aligning standalone component metadata and Vitest Angular TestBed setup. | None | `frontend/libs/shared/data-access/README.md`; `AGENT.md` |
| S0B-2 | `frontend/libs/shared/data-access` | Add focused tests for exported shared data-access primitives that are documented as library features: API response interceptor, error interceptor, loading service, notification service, and base CRUD service. | S0B-1 | `frontend/libs/shared/data-access/README.md`; `frontend/libs/shared/data-access/USAGE_EXAMPLE.md` |
| S0B-3 | `frontend` | Add `shared-data-access` to the documented frontend unit-test run set so CI catches regressions in the library. | S0B-2 | `docs/CRM_TESTING_STRATEGY.md`; `MODERNIZATION_PLAN.md` |

Parallelization: S0A and S0B can run in parallel in separate worktrees because they touch backend CRM tests and frontend shared library tests independently.

## Stack 1: Test Infrastructure

Goal: make backend integration tests predictable and move toward green `mvn verify` across backend modules.

### Stack 1A: Testcontainers and Docker Readiness

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S1A-1 | `backend/services/crm-service` | Tag Docker-dependent integration tests consistently, including `*IntegrationTest` and `LeadToDealLifecycleTest`, without removing them from full verification. | S0A-3, S0B-3 | `docs/CRM_TESTING_STRATEGY.md`; `backend/README.md` |
| S1A-2 | `backend` | Add Maven profile(s) for unit-only PR verification and full Docker-backed integration verification. | S1A-1 | `docs/CRM_TESTING_STRATEGY.md`; `MODERNIZATION_PLAN.md` |
| S1A-3 | `backend` | Add Windows Docker Desktop Testcontainers guidance and default environment checks to backend test documentation. | S1A-2 | `backend/README.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |
| S1A-4 | `.github/workflows` | Split CI into fast unit checks and Docker-backed integration checks while preserving full `mvn verify` on the integration job. | S1A-2 | `docs/CRM_TESTING_STRATEGY.md`; `docs/CRM_CUTOVER_CHECKLIST.md`; `docs/GRAPHITE_STACKED_PRS.md` |

### Stack 1B: Backend Module Verification Sweep

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S1B-1 | `backend/libs/common`, `backend/libs/security`, `backend/libs/rules-engine` | Establish shared-library verification as the bottom PR so service stacks can depend on stable common code. | S0A-3, S0B-3 | `docs/GRAPHITE_STACKED_PRS.md`; `backend/CPQ_IMPLEMENTATION.md` |
| S1B-2 | `backend/services/*` | Inventory all backend module test modes and identify which modules require Docker, Redis, or only mocks. | S1B-1 | `backend/README.md`; `docs/CRM_TESTING_STRATEGY.md` |
| S1B-3 | `backend` | Add or normalize test profile properties for PostgreSQL, Redis, Flyway/Liquibase, and service schemas so module tests do not depend on developer-local config. | S1B-2 | `backend/README.md`; `MODERNIZATION_PLAN.md` |
| S1B-4 | `backend` | Add a top-of-stack verification PR that proves backend modules can run under the chosen unit/integration split. | S1B-3 | `AGENT.md`; `docs/GRAPHITE_STACKED_PRS.md` |

Parallelization: S1A and S1B can start after Stack 0. S1A owns tagging/profile semantics; S1B owns inventory and shared module verification. Merge S1A before CI workflow changes in S1B if both touch workflow files.

## Stack 2: Feature Implementation

Goal: implement CPQ microservice work from `AGENT.md`, `MODERNIZATION_PLAN.md`, and the service `DDD_SPEC.md` files in reviewable Graphite lanes.

### Stack 2A: Shared Contracts and Published Language

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2A-1 | `backend/libs/common` | Define versioned published-language DTOs or schema placeholders for `QuoteAccepted`, `PriceCalculated`, `DiscountApprovalRequired`, `ProductRetired`, and `SubscriptionRenewalEligible`. | S1A-4, S1B-4 | `backend/services/quote-service/DDD_SPEC.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2A-2 | `backend/libs/common` | Normalize `KafkaTopics` naming and add missing target events such as `ConfigurationCommitted`, `BundleStructureChanged`, and `RuleSetPublished` as non-breaking constants. | S2A-1 | `backend/services/product-service/DDD_SPEC.md`; `backend/services/configuration-engine/DDD_SPEC.md` |
| S2A-3 | `backend/libs/rules-engine` | Document and stabilize shared pricing facts: `QuoteFact`, `QuoteLineItemFact`, `CustomerFact`, `ProductFact`, and `PricingWaterfallFact`. | S2A-1 | `backend/services/pricing-service/DDD_SPEC.md`; `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| S2A-4 | `backend/libs/common` | Add contract tests or serialization tests for the published-language DTOs before service consumers adopt them. | S2A-1 | `AGENT.md`; `docs/GRAPHITE_STACKED_PRS.md` |

### Stack 2B: Product and Configuration Boundary

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2B-1 | `backend/services/product-service` | Implement or harden catalog lifecycle events for product publish/update/retire and bundle structure changes. | S2A-1 | `backend/services/product-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2B-2 | `backend/services/product-service` | Validate bundle option-group min/max constraints and attribute value rules at write time. | S2B-1 | `backend/services/product-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2B-3 | `backend/services/configuration-engine` | Add `ConfigurationCommitted` contract handling and committed configuration snapshots. | S2A-2, S2B-1 | `backend/services/configuration-engine/DDD_SPEC.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2B-4 | `backend/services/configuration-engine` | Introduce `ConfigurationModel` compile/cache invalidation behavior driven by product and bundle events. | S2B-3 | `backend/services/configuration-engine/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2B-5 | `backend/services/configuration-engine` | Add tests for validation, selection, and recommendation-rule boundaries so AI recommendations cannot mark invalid configurations as valid. | S2B-4 | `backend/services/configuration-engine/DDD_SPEC.md`; `../pricing-ai-rules-service/PRD.md` |

### Stack 2C: Pricing, Pricebook, and Approval

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2C-1 | `backend/services/pricebook-service` | Decide and encode whether `PriceBookEntry` is an entity under `PriceBook`; add effective-period and immutable-publication tests. | S2A-1 | `backend/services/pricebook-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2C-2 | `backend/services/pricing-service` | Introduce a `PriceCalculation` audit concept or harden `PricingAuditLog` to persist input snapshots, rule set version, and pricebook version. | S2A-3, S2C-1 | `backend/services/pricing-service/DDD_SPEC.md`; `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| S2C-3 | `backend/services/pricing-service` | Add derived metrics for ARR, TCV, and ACV before porting quote-level additive pricing policies. | S2C-2 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2C-4 | `backend/services/pricing-service` | Add TCV pricing tiers and ACV minimum commitment rules with explicit write-order and conflict tests. | S2C-3 | `docs/phase0/phase0-rule-catalog-reconciliation.md`; `MODERNIZATION_PLAN.md` |
| S2C-5 | `backend/services/pricing-service` | Publish `PriceCalculated` and `DiscountApprovalRequired` with explanation codes and deterministic snapshots. | S2A-1, S2C-2 | `backend/services/pricing-service/DDD_SPEC.md`; `backend/services/approval-routing/DDD_SPEC.md` |
| S2C-6 | `backend/services/approval-routing` | Reconcile `ApprovalRequest` ownership with `quote-service` and add policy-evaluation snapshot persistence. | S2C-5 | `backend/services/approval-routing/DDD_SPEC.md`; `backend/services/quote-service/DDD_SPEC.md` |
| S2C-7 | `backend/services/approval-routing` | Implement separation-of-duty, multi-level approval, SLA, and escalation test cases behind deterministic policy evaluation. | S2C-6 | `backend/services/approval-routing/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |

### Stack 2F: Hybrid Rules Management and OpenL

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2F-1 | `backend/libs/rules-engine` | Define the rule engine port that separates business-user OpenL tables from programmatic Drools rules while preserving one deterministic execution trace. | S2A-3 | `MODERNIZATION_PLAN.md`; `AGENT.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2F-2 | `backend/services/pricing-service` | Add OpenL pricing/discount table import and validation scaffolding for business-user maintained pricing rules. | S2F-1, S2C-2 | `MODERNIZATION_PLAN.md`; `backend/services/pricing-service/DDD_SPEC.md` |
| S2F-3 | `backend/services/pricing-service` | Add precedence tests proving OpenL pricing/discount outputs and Drools complex-rule outputs reconcile without double-discounting. | S2F-2, S2C-4 | `MODERNIZATION_PLAN.md`; `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| S2F-4 | `frontend/libs/crm/cpq` | Add an admin-facing rule management backlog stub for OpenL table upload/validation status, gated behind backend contracts. | S2F-2, S2E-4 | `MODERNIZATION_PLAN.md`; `AGENT.md` |

### Stack 2D: Quote, Documents, Contract, and CRM Fan-Out

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2D-1 | `backend/services/quote-service` | Define `QuoteAccepted.v1` payload from quote, line, customer, configuration, pricing, and terms snapshots. | S2A-1, S2C-5 | `backend/services/quote-service/DDD_SPEC.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2D-2 | `backend/services/quote-service` | Harden Quote aggregate invariants: line containment, total reconciliation, immutable accepted state, and version monotonicity. | S2D-1 | `backend/services/quote-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2D-3 | `backend/services/document-generation` | Add idempotent `QuoteAccepted` consumer behavior for accepted quote/proposal document jobs. | S2D-1 | `backend/services/document-generation/DDD_SPEC.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2D-4 | `backend/services/contract-asset-management` | Create subscription activation from `QuoteAccepted` with idempotency and correlation IDs. | S2D-1 | `backend/services/contract-asset-management/DDD_SPEC.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2D-5 | `backend/services/contract-asset-management` | Model renewal eligibility and emit `SubscriptionRenewalEligible` from installed-base state, not last quote history. | S2D-4 | `backend/services/contract-asset-management/DDD_SPEC.md`; `C:/Users/dgorn/cpq-crm/cpq-ddd-session.md` |
| S2D-6 | `backend/services/crm-service` | Define and test deal/opportunity update semantics from `QuoteAccepted` without moving accepted commercial ownership into CRM. | S2D-1 | `backend/services/crm-service/DDD_SPEC.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |

### Stack 2E: Gateway and Frontend CPQ Access

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S2E-1 | `backend/services/gateway-service` | Add gateway routes for CPQ services while keeping commands delegated to aggregate-owning services. | S1A-4, S1B-4 | `backend/services/gateway-service/DDD_SPEC.md`; `MODERNIZATION_PLAN.md` |
| S2E-2 | `frontend/libs/shared/data-access` | Add typed clients for CPQ published APIs after backend route contracts are available. | S2E-1 | `AGENT.md`; `MODERNIZATION_PLAN.md` |
| S2E-3 | `frontend/libs/crm/*` | Add dashboard and lead lifecycle UI test coverage for gateway-backed CRM flows before broader CPQ UI work. | S0B-3, S2E-1 | `MODERNIZATION_PLAN.md`; `docs/CRM_CUTOVER_CHECKLIST.md` |
| S2E-4 | `frontend/libs/crm/cpq` | Scaffold CPQ feature library boundaries for catalog/configuration/pricing/quote flows. | S2E-2, S2D-2 | `MODERNIZATION_PLAN.md`; `AGENT.md` |

Parallelization: S2B, S2C, S2D, and S2F can run in separate worktrees after S2A shared contracts. S2E can start once gateway route conventions are clear, but CPQ UI should wait for the corresponding backend API contracts.

## Stack 3: AI Integration and Pricing Rules Service

Goal: harden `pricing-ai-rules-service` and connect it to Open CRM AI/pricing boundaries without mixing deterministic pricing with advisory AI outputs.

### Stack 3A: Price Drools Stability and Compatibility

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3A-1 | `pricing-ai-rules-service/price-drools-service` | Fix remaining test compilation failures, starting with `RulePerformanceAnalysisServiceTest`. | None | `../pricing-ai-rules-service/BUILD_SUMMARY.md`; `../pricing-ai-rules-service/price-drools-service/COMPILE_ERRORS.md` |
| S3A-2 | `pricing-ai-rules-service/price-drools-service` | Disable Spring Cloud Config requirement for tests or add test config import defaults. | S3A-1 | `../pricing-ai-rules-service/BUILD_SUMMARY.md`; `../pricing-ai-rules-service/AGENTS.md` |
| S3A-3 | `pricing-ai-rules-service/price-drools-service` | Replace deprecated `@MockBean`/`@SpyBean` test usage with Spring Boot 4-compatible test annotations where applicable. | S3A-1 | `../pricing-ai-rules-service/compilation-errors-inventory.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3A-4 | `pricing-ai-rules-service/price-drools-service` | Verify Drools 10.1 compatibility with Jakarta/Spring Boot 4 test slices before enabling broader upgrade work. | S3A-3 | `../pricing-ai-rules-service/dependency-compatibility-report.md`; `../pricing-ai-rules-service/AGENTS.md` |

### Stack 3B: Rule Engine Domain Core

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3B-1 | `price-drools-service` | Implement or verify `AuditService` entity, repository, and service referenced by the agentic development plan. | S3A-1 | `../pricing-ai-rules-service/AGENTIC_DEVELOPMENT_PLAN.md`; `../pricing-ai-rules-service/TECH_SPEC.md` |
| S3B-2 | `price-drools-service` | Align `FinancialMetrics` model and API endpoints with ARR, TCV, ACV, CLV, churn score, discount rate, and price multiplier requirements. | S3B-1 | `../pricing-ai-rules-service/TECH_SPEC.md`; `../pricing-ai-rules-service/AGENTIC_DEVELOPMENT_PLAN.md` |
| S3B-3 | `price-drools-service` | Implement deterministic rule evaluation order with priority, impact class, rule ID tie-breakers, and dependency graph validation. | S3B-2 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3B-4 | `price-drools-service` | Add cycle and conflicting-mutation diagnostics for rule publication and runtime evaluation. | S3B-3 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3B-5 | `price-drools-service` | Add rule simulation harness for quote/config/pricing facts and approval outputs. | S3B-4 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/AI_SPEC.md` |

### Stack 3C: Backend Simplification and Controller Hygiene

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3C-1 | `price-drools-service` | Search and remove `NumericalParameterValue` duplication by migrating to `NumericParameterValue`. | S3A-1 | `../pricing-ai-rules-service/backend-simplification-tasks.md` |
| S3C-2 | `price-drools-service` | Create `BaseMCPClient` abstraction and keep Context7, Puppeteer, and Sequential Thinking clients behind it. | S3A-1 | `../pricing-ai-rules-service/backend-simplification-tasks.md`; `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md` |
| S3C-3 | `price-drools-service` | Refactor rule/pricing/optimization/AI controllers to rely on `GlobalExceptionHandler` instead of local broad try/catch blocks. | S3A-1 | `../pricing-ai-rules-service/backend-simplification-tasks.md`; `../pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/exception/README.md` |
| S3C-4 | `price-drools-service` | Add null-safety utility or targeted null-handling simplification for repetitive service/controller guards. | S3C-3 | `../pricing-ai-rules-service/backend-simplification-tasks.md` |

### Stack 3D: AI and MCP Re-Enablement

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3D-1 | `price-drools-service` | Preserve AI feature flags and add smoke tests for disabled-feature responses while Spring AI compatibility is constrained. | S3A-2 | `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md`; `../pricing-ai-rules-service/dependency-compatibility-report.md` |
| S3D-2 | `price-drools-service` | Re-enable Sequential Thinking, Context7, and Puppeteer clients behind flags once dependency compatibility is proven. | S3D-1, S3C-2 | `../pricing-ai-rules-service/AI_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md`; `../pricing-ai-rules-service/price-drools-service/SPRING-AI-MIGRATION.md` |
| S3D-3 | `price-drools-service` | Add MCP tool tests for `generateRule`, `optimizeRule`, `analyzeRule`, `validateRule`, and `generateTestCases`. | S3D-2 | `../pricing-ai-rules-service/price-drools-service/MCP_SERVER_GUIDE.md`; `../pricing-ai-rules-service/AI_SPEC.md` |
| S3D-4 | `price-drools-service` | Add policy-safe rule promotion workflow from AI-generated draft to validated ruleset to published production ruleset. | S3B-5, S3D-3 | `../pricing-ai-rules-service/PRD.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |
| S3D-5 | `backend/services/ai-service` | Add `RulesOptimizationAdapter` contract from Open CRM `ai-service` to `price-drools-service` without allowing AI to mutate pricing directly. | S2A-1, S3D-4 | `backend/services/ai-service/DDD_SPEC.md`; `../pricing-ai-rules-service/price-drools-service/DDD_SPEC.md` |

### Stack 3E: Open CRM AI Service Pilot

| PR | Service/Module | Actionable Todo | Stack Dependency | Reference Doc |
|---|---|---|---|---|
| S3E-1 | `backend/services/ai-service` | Implement a thin internal LLM adapter for the current `CpqAgent` and `AgentRouter` seam without upgrading the AI framework in the same PR. | S1B-4 | `docs/phase0/phase0-ai-stack-decision.md`; `backend/services/ai-service/DDD_SPEC.md` |
| S3E-2 | `backend/services/ai-service` | Convert pilot agents from simulated output to model-backed output using specific agent specs selected in the PR, starting with pricing agents such as `customer-segment-discount`, `volume-discount`, and `minimum-margin-enforcement`. | S3E-1 | `docs/phase0/phase0-ai-stack-decision.md`; `agents/customer-segment-discount.md`; `agents/volume-discount.md`; `agents/minimum-margin-enforcement.md` |
| S3E-3 | `backend/services/ai-service` | Add audit logging and snapshot references for agent invocations before expanding domain agent batches. | S3E-2 | `backend/services/ai-service/DDD_SPEC.md`; `docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md` |
| S3E-4 | `backend/services/ai-service` | Batch remaining agents by domain package, one Graphite PR per package group with matching tests. | S3E-3 | `docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md`; `AGENT.md` |

Parallelization: S3A and S3C can start immediately in the pricing rules repo if they use separate worktrees. S3B should wait for S3A-1. S3D waits for compatibility and controller/client cleanup. S3E can run in `open-crm-cpq` after Stack 1, but its rules-adapter PR waits for S3D-4 and Stack 2 shared contracts.

## Orchestrated Subagent Assignment

- `S0A`: one debugging agent focused only on `LeadServiceTest` and `LeadService`.
- `S0B`: one frontend test agent focused only on `frontend/libs/shared/data-access`.
- `S1A`: one backend test-infrastructure agent for Testcontainers tagging/profiles.
- `S1B`: one CI/build agent for module inventory and workflow split.
- `S2A`: one shared-contract agent; all Stack 2 service lanes depend on its output.
- `S2B`, `S2C`, `S2D`, `S2E`, `S2F`: five independent implementation agents/worktrees after S2A lands.
- `S3A`, `S3B`, `S3C`, `S3D`: pricing rules repo agents/worktrees, with S3C controller cleanup parallel to S3A stability.
- `S3E`: Open CRM AI-service agent, separate from pricing rules repo agents.

## Backlog Acceptance Criteria

- Every PR in a stack is independently reviewable and keeps its scoped verification green.
- Bottom PRs establish shared contracts or test stability before service behavior depends on them.
- No PR mixes deterministic pricing decisions with advisory AI behavior.
- No service doc ownership rule is violated: Quote owns quote lifecycle, Pricing owns final price, Approval owns approval policy, Subscription owns installed base, AI owns recommendations/audit, and Drools owns executable rule evaluation artifacts.
- Every PR description includes stack position, dependency, verification performed, and reference docs used.
