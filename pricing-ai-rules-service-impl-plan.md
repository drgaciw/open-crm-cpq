# Pricing AI Rules Service - Merge Implementation Plan

> **Source Repository:** https://github.com/drgaciw/pricing-ai-rules-service.git
> **Target Project:** open-crm-cpq
> **Date:** 2026-02-22
> **Recommendation:** **STRONG YES** - Merge via Selective Feature Port (CPQ Alignment Score: 9/10)

---

## 1. Context & Problem Statement

The open-crm-cpq project has a well-architected pricing-service microservice with a **critical unimplemented section**: the Drools rule execution at `PricingExecutionService.java:159-161` is a TODO stub that logs but never actually executes pricing rules. The ai-service also contains many stubbed agents (current review found **38 agent classes** with **37** `TODO: Implement actual LLM call via ChatClient` markers). The rules-engine shared library (`DroolsRulesEngine`) can execute rules when given `RuleDefinition` objects, but the pricing-service never calls it.

The `pricing-ai-rules-service` provides a **working** Drools pricing engine with real DRL rules, AI-powered rule creation via Sequential Thinking + Context7, rule optimization, reinforcement learning experiments, and comprehensive financial metrics - all of which directly fill the gaps in open-crm-cpq.

---

## 2. CPQ Requirement Alignment Analysis

### 2.1 Gap Analysis

| Gap in open-crm-cpq | What pricing-ai-rules-service provides | CPQ Impact |
|---|---|---|
| `PricingExecutionService:160` TODO - no rule execution | Working KieSession creation, fact insertion, rule firing with metrics | **CRITICAL** - Without this, CPQ cannot price quotes |
| DRL assets exist, but are not executed by pricing flow | 240-line `PricingRules.drl` with 9 rules covering volume discounts, TCV tiers, churn adjustments, enterprise bundles | **HIGH** - Source rules may add coverage, but target already has DRL files and seeded DRL content |
| 37 AI agent TODO stubs across 38 agent classes (`// TODO: Implement actual LLM call`) | AI orchestration: Sequential Thinking for rule creation, Context7 for docs, Puppeteer for testing | **HIGH** - AI-powered CPQ is a key differentiator |
| No rule optimization capability | AI-driven rule optimization with performance + logic analysis and in-memory caching | **MEDIUM** - Improves rule quality over time |
| No rule experimentation/A/B testing | Reinforcement learning with experiment/variant framework and multi-armed bandit algorithms | **MEDIUM** - Enables data-driven pricing improvements |
| Financial metrics exist but are not rule-execution-linked and lack historical rule metrics logging | Complete ARR/TCV/ACV/CLV calculation via Drools rules + dedicated FinancialMetricsService | **MEDIUM** - Improves observability and metric traceability |

### 2.2 Technology Stack Compatibility

| Dimension | open-crm-cpq (Target) | pricing-ai-rules-service (Source) | Compatibility |
|---|---|---|---|
| Java | 21 | 21 | Identical |
| Spring Boot | 3.2.2 | 3.5.9 | Compatible (source patterns work on 3.2.x) |
| Drools | 8.44.0.Final (backend BOM / rules-engine lib) | 10.1.0 | **Compatibility spike required** (8.x vs 10.x APIs/behavior) |
| Spring AI | 0.8.1 (Azure OpenAI starter, ChatClient assumptions) | 1.0.0 | **Migration decision required** (ChatClient vs ChatModel, provider impact) |
| Database | PostgreSQL 16 | PostgreSQL | Identical |
| Migrations | Flyway | Liquibase | Convert migrations to Flyway |
| Cache | Redis 7 | Redis | Identical |
| Build | Maven | Maven | Identical |

### 2.3 Recommendation

**STRONG YES (9/10)** - The pricing-ai-rules-service was clearly built for this class of CPQ system. Its features map directly to the modernization plan's Phase 4 (CPQ Engine) and Phase 5 (AI Agents):

- **Configure:** Rule-based product configuration (DRL rules handle bundles, tiers, enterprise pricing)
- **Price:** Dynamic pricing with volume discounts, churn risk adjustments, TCV-based multipliers, minimum commitments
- **Quote:** Financial metrics (ARR/TCV/ACV/CLV) essential for SaaS/enterprise quoting

The 1-point deduction reflects integration effort required for entity model reconciliation, existing DRL rule reconciliation, and AI stack/provider differences.

---

## 3. Merge Strategy: Selective Feature Port

Rather than deploying as a separate microservice (which would duplicate entities and add network latency) or doing a full absorb (which risks breaking existing code), we recommend **selectively porting specific high-value features** into the existing architecture.

### 3.1 What We Port vs. What We Keep

| Decision | Rationale |
|---|---|
| **Port:** Drools execution logic | Fills the critical TODO in PricingExecutionService |
| **Port:** Selected source DRL rules (after reconciliation) | Adds high-value pricing patterns without breaking existing target rules |
| **Port:** AI orchestration workflow | Implements targeted agent stubs first, then expand |
| **Port:** Rule optimization + RL experiments | New high-value capabilities |
| **Port:** Financial metrics enhancements | Completes the metrics story |
| **Keep:** Target's entity models | Richer (soft-delete, audit fields, BaseEntity pattern) |
| **Keep:** Target's RulesEngine interface | Clean abstraction already in place |
| **Keep:** Target's Flyway migrations | Don't switch migration tools |
| **Keep:** Target's microservice boundaries | Don't introduce a redundant service |

---

## 4. Implementation Plan (Phase 0 + 6 Delivery Phases)

### Phase 0: Compatibility & Integration Decision Spike

**Priority:** CRITICAL - Reduces rework across Phases 1-6  
**Estimated Effort:** 1 day
**Status:** COMPLETED (2026-02-22)

#### What to do

Validate the major compatibility assumptions before implementation:
- Drools **8.44.0.Final** (target) vs **10.1.0** (source)
- Spring AI **0.8.1 Azure OpenAI + ChatClient** (target) vs **1.0.0 + ChatModel** (source)
- Existing target DRL catalog vs source `PricingRules.drl`

#### Steps

1. **Run a Drools compatibility spike** using the target `rules-engine` with one source rule copied verbatim and one adapted rule
2. **Decide AI integration path:**
   - **Path A (recommended first):** stay on Spring AI 0.8.1 + Azure OpenAI starter and implement orchestration against existing `ChatClient` assumptions
   - **Path B (optional later):** upgrade ai-service to Spring AI 1.0.0 and migrate to `ChatModel`
3. **Inventory current target rule assets** (resource `.drl` files + `V2__seed_default_rules.sql`) and decide whether source rules are additive, replacements, or references only
4. **Document outcomes** in this plan before starting Phase 1

#### Phase 0 Outcomes (Implemented)

1. **Drools compatibility decision: Stay on target Drools `8.44.0.Final` for Phase 1**
   - Added evidence test: `backend/libs/rules-engine/src/test/java/com/opencrm/rules/drools/Drools844CompatibilityEvidenceTest.java`
   - Verified in main workspace with:
     - `backend\\mvnw.cmd -pl libs\\rules-engine -Dtest=Drools844CompatibilityEvidenceTest test`
     - Result: `BUILD SUCCESS` (2 tests, 0 failures)
   - Evidence doc: `docs/phase0/phase0-drools-compatibility.md`

2. **AI integration path decision: Choose Path A for Phase 4A; defer Path B to Phase 4B**
   - Phase 4A: keep target `ai-service` stack (Spring AI `0.8.1` + Azure OpenAI starter + current `ChatClient` assumptions)
   - Phase 4B: optional Spring AI `1.0.0+` migration and selective `ChatModel` pattern adoption after routing integration is proven
   - Evidence doc: `docs/phase0/phase0-ai-stack-decision.md`

3. **Rule catalog reconciliation decision: Treat target catalog as baseline, port selected source rules only**
   - Confirmed Phase 0 target baseline includes **8 DRL resource files** and **8 seeded active rules** in `V2__seed_default_rules.sql`
   - Mapped source `PricingRules.drl` rules into `overlap` / `additive` / `defer`
   - Deferred high-complexity `Enterprise Subscription Bundle Pricing` pending fact-model support
   - Evidence doc: `docs/phase0/phase0-rule-catalog-reconciliation.md`

4. **Reusable workflow skill created (process repeatability)**
   - Skill artifact: `agents/skills/phase0-compatibility-spike/SKILL.md`
   - Purpose: repeatable Phase 0 rule-catalog reconciliation spike in isolated worktrees

#### Files

| Action | File |
|---|---|
| **Review** | `backend/pom.xml` (Drools version) |
| **Review** | `backend/libs/rules-engine/pom.xml` |
| **Review** | `backend/services/ai-service/pom.xml` |
| **Review** | `backend/services/ai-service/src/main/resources/application.yml` |
| **Review** | `backend/services/pricing-service/src/main/resources/rules/` |
| **Review** | `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql` |
| **Reference** | `pricing-ai-rules-service/price-drools-service/pom.xml` |
| **Reference** | `pricing-ai-rules-service/price-drools-service/src/main/resources/rules/PricingRules.drl` |
| **Created (evidence)** | `backend/libs/rules-engine/src/test/java/com/opencrm/rules/drools/Drools844CompatibilityEvidenceTest.java` |
| **Created (evidence)** | `docs/phase0/phase0-drools-compatibility.md` |
| **Created (evidence)** | `docs/phase0/phase0-ai-stack-decision.md` |
| **Created (evidence)** | `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| **Created (skill)** | `agents/skills/phase0-compatibility-spike/SKILL.md` |

### Phase 1: Wire Drools Execution into PricingExecutionService

**Priority:** CRITICAL - This makes the pricing engine functional
**Estimated Effort:** 3-4 days

#### What to do

Replace lines 159-161 in `PricingExecutionService.java` (the TODO) with actual Drools rule execution using the existing `RulesEngine` interface from `libs/rules-engine`.

#### Steps

1. **Inject `RulesEngine`** into `PricingExecutionService` (it's already a Spring bean via `DroolsRulesEngine`)
2. **Build `RuleExecutionContext`** at line 159:
   - Create `CustomerFact` from `Customer` entity (map externalId, type, segment, region, industry, churnScore)
   - Create `QuoteFact` from `PricingContext` (map quoteExternalId, subscriptionType, durationInMonths, currency)
   - Create `ProductFact` per line item (map productExternalId, productType, listPrice)
   - Create `PricingWaterfallFact` initialized with listPrice totals
   - Set globals: `FinancialMetricLogger` instance
   - Define a **metadata key contract** for fields not currently modeled as typed properties (e.g., usage, deal-type, margin inputs)
3. **Convert `Rule` entities to `RuleDefinition` objects:**
   ```java
   List<RuleDefinition> ruleDefinitions = activeRules.stream()
       .map(rule -> RuleDefinition.builder()
           .id(rule.getId().toString())
           .name(rule.getName())
           .content(rule.getContent())
           .priority(rule.getPriority())
           .build())
       .toList();
   ```
4. **Execute rules:** `RuleExecutionResult result = rulesEngine.execute(context, ruleDefinitions);`
5. **Extract modified facts** from `result.getModifiedFacts()` and map back onto `PricingLineResult` objects
6. **Port `FinancialMetricLogger`** pattern from source's `DroolsServiceImpl.java:303-308`
7. **Map rule outputs back** into:
   - `PricingLineResult` (line-level discounts/final prices/explanation codes)
   - `PricingWaterfall`
   - `PricingResult.approvalState` / `approvalTier` (where set by rules via metadata/explanations)

#### Files

| Action | File |
|---|---|
| **Modify** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionService.java` |
| **Review** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingContext.java` |
| **Review** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingLineRequest.java` |
| **Review** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingLineResult.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/engine/RulesEngine.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/drools/DroolsRulesEngine.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/engine/RuleExecutionContext.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/engine/RuleDefinition.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/CustomerFact.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/QuoteFact.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/ProductFact.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/PricingWaterfallFact.java` |
| **Reference** | `temp-pricing-ai-rules/.../service/drools/DroolsServiceImpl.java` (execution pattern, FinancialMetricLogger) |

---

### Phase 2: Rule Catalog Reconciliation & Optional Source Rule Port

**Priority:** HIGH - Prevents rule conflicts/regressions when enabling execution
**Estimated Effort:** 1-2 days

#### What to do

Reconcile the source `PricingRules.drl` with the target's **existing** DRL resources and seeded DRL rule content before importing anything. Port only the source rules that add clear value and fit the target fact model.

#### Steps

1. **Inventory existing target rule assets**:
   - `backend/services/pricing-service/src/main/resources/rules/*.drl`
   - seeded rule content in `V2__seed_default_rules.sql`
2. **Create a rule mapping table** (source rule → target equivalent / additive / skip / defer)
3. **Decide coexistence strategy**:
   - Keep target DRL package convention (e.g., `package com.opencrm.pricing.rules`)
   - Decide whether source rules live as separate DRL resources, DB-seeded `Rule` rows, or both
4. **Adapt only selected source rules** to target fact model:
   - Prefer target fact metadata helpers (`getString`, `getInt`, `getDecimal`, `getBoolean`) to avoid large typed model churn
   - Explicitly document metadata keys required by each imported rule
   - Defer or redesign the source enterprise bundle rule if typed bundle object graphs are not available in target facts
5. **Enrich `QuoteFact` / `QuoteLineItemFact` only when justified**
   - Add typed fields only for high-frequency, stable attributes
   - Use metadata for experimental or source-specific fields
6. **Regression compile test all active DRL** (existing + newly ported) before rollout

#### Files

| Action | File |
|---|---|
| **Review/Modify** | `backend/services/pricing-service/src/main/resources/rules/` |
| **Review/Modify** | `backend/services/pricing-service/src/main/resources/db/migration/V2__seed_default_rules.sql` |
| **Modify (optional)** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/QuoteFact.java` |
| **Modify (optional)** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/model/QuoteLineItemFact.java` |
| **Reference** | `temp-pricing-ai-rules/.../resources/rules/PricingRules.drl` |

---

### Phase 3: Implement Drools Rule Validation in RuleService

**Priority:** HIGH - Prevents invalid rules from being deployed
**Estimated Effort:** 1 day

#### What to do

Fill the TODO at `RuleService.java:~148` with DRL syntax validation using the Drools compiler.

#### Steps

1. **Inject `DroolsRuleCompiler`** (from rules-engine lib) or use KieServices directly
2. **Implement validation method:**
   - Compile rule content via `KieFileSystem` + `KieBuilder` (or enhance `DroolsRuleCompiler` to expose diagnostics)
   - Collect errors with line/column information
   - Return structured validation result
3. **Decide API behavior** for `POST /api/v1/rules/{id}/validate`:
   - Keep pass/fail (`ApiResponse<Void>`) and throw `BusinessException` on invalid DRL, or
   - Return a structured DTO with errors/warnings
4. **Wire into RuleService** so rules are validated before save/update and explicit validate endpoint

#### Port from source

The `validateRule()` and `buildValidationResult()` methods from `DroolsServiceImpl.java:507-553` provide the exact pattern needed:
- Write rule content to a temp `KieFileSystem` path
- Build with `KieBuilder.buildAll()`
- Check `Results.hasMessages(Message.Level.ERROR)`
- Extract error details (text, line, column, severity)

#### Files

| Action | File |
|---|---|
| **Modify** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/rule/RuleService.java` |
| **Modify (if structured response)** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/rule/RuleController.java` |
| **Reuse** | `backend/libs/rules-engine/src/main/java/com/opencrm/rules/drools/DroolsRuleCompiler.java` |
| **Reference** | `temp-pricing-ai-rules/.../service/drools/DroolsServiceImpl.java:507-553` |

---

### Phase 4: AI Rule Orchestration (Target-Native First, Upgrade Optional)

**Priority:** HIGH - Enables AI-powered rule creation
**Estimated Effort:** 3-5 days (4A) + optional 1-2 days (4B upgrade/migration)

#### What to do

Port the AI rule creation workflow into the ai-service, implementing targeted agent stubs first and integrating with the existing agent router contract. Treat Spring AI/provider upgrades as a separate optional track.

#### Steps

1. **Phase 4A (recommended): Build target-native orchestration on current ai-service stack**
   - Keep Spring AI `0.8.1` + Azure OpenAI starter initially
   - Implement orchestration behind the existing agent model (`CpqAgent`) and/or a dedicated workflow controller

2. **Create `PricingRuleOrchestrationService`** in ai-service, adapting the 8-step workflow:
    1. Requirements Analysis (Sequential Thinking via Spring AI ChatClient)
    2. Documentation Pattern Matching (Context7 library docs lookup)
    3. Rule Drafting (ChatClient generates DRL from analysis)
   4. Rule Enhancement (enrich with documentation examples)
   5. Test Case Generation (ChatClient generates test scenarios)
   6. Rule Testing (validate via DroolsRuleCompiler)
   7. Rule Optimization (analyze for performance/logic improvements)
   8. Final Validation (compile and verify)

3. **Create `SequentialThinkingService`** - adapt source's sequential reasoning pattern to the target stack:
   - If staying on Spring AI 0.8.1, use `ChatClient`
   - If upgrading to Spring AI 1.0.0 later, migrate to `ChatModel` (source pattern)
   - `analyzeBusinessRequirement()` → break requirement into conditions, actions, edge cases
   - `generateDroolsRule()` → generate DRL from analysis
   - `generateTestCases()` → create test scenarios
   - `performSequentialReasoning()` → multi-step reasoning with revision capability

4. **Port prompt templates** from source's `prompts.yml` → ai-service resources, with prompt versioning and fallback behavior

5. **Implement agent stubs first (targeted):**
   - `DealDeskWorkflowOrchestrationAgent` (`cpq-agent-037`) → wire to orchestration service
   - `PriceBookManagementAgent` (cpq-agent-035) → wire to rule creation for price book rules

6. **Expose orchestration via API**
   - **Preferred first:** integrate with existing `AgentRouter` (`POST /api/v1/ai/agents/{agentId}/invoke`)
   - **Optional dedicated endpoint:** mirror source style (`/api/v1/ai/workflow/create-rule`) instead of introducing a third incompatible path

7. **Phase 4B (optional, separate PR): Spring AI/provider migration**
   - Evaluate Spring AI `1.0.0` upgrade
   - Decide whether to keep Azure OpenAI or switch to OpenAI starter
   - Refactor ChatClient-based code to `ChatModel` only after Phase 4A is stable

#### Files

| Action | File |
|---|---|
| **Create** | `backend/services/ai-service/src/main/java/com/opencrm/ai/orchestration/PricingRuleOrchestrationService.java` |
| **Create** | `backend/services/ai-service/src/main/java/com/opencrm/ai/sequential/SequentialThinkingService.java` |
| **Create** | `backend/services/ai-service/src/main/java/com/opencrm/ai/sequential/RuleAnalysisResult.java` |
| **Create** | `backend/services/ai-service/src/main/java/com/opencrm/ai/sequential/ThinkingStep.java` |
| **Create** | `backend/services/ai-service/src/main/java/com/opencrm/ai/sequential/TestCase.java` |
| **Create** | `backend/services/ai-service/src/main/resources/prompts.yml` |
| **Modify** | `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/governance/DealDeskWorkflowOrchestrationAgent.java` |
| **Modify** | `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/governance/PriceBookManagementAgent.java` |
| **Review/Modify** | `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java` |
| **Review** | `backend/services/ai-service/src/main/java/com/opencrm/ai/config/AgentConfiguration.java` |
| **Review** | `backend/services/ai-service/src/main/resources/application.yml` |
| **Reference** | `temp-pricing-ai-rules/.../ai/orchestration/AIOrchestrationService.java` |
| **Reference** | `temp-pricing-ai-rules/.../ai/sequential/SequentialThinkingService.java` |
| **Reference** | `temp-pricing-ai-rules/.../ai/prompt/PromptService.java` |
| **Reference** | `temp-pricing-ai-rules/.../api/controller/ai/AIOrchestrationController.java` |
| **Reference** | `temp-pricing-ai-rules/.../resources/prompts.yml` |

---

### Phase 5: Port Rule Optimization & Reinforcement Learning

**Priority:** MEDIUM - Advanced pricing intelligence
**Estimated Effort:** 2-3 days

#### What to do

Add AI-driven rule optimization and A/B testing via reinforcement learning experiments.

#### Steps

1. **Port `RuleOptimizationService`:**
   - Analyze rules for performance improvements (condition reordering, constraint optimization)
   - Logic optimization (conflict resolution, simplification)
   - In-memory caching of optimized rule content
   - Integration with pricing execution (optional pre-execution optimization flag)

2. **Port Experiment/Variant entities:**
   - `Experiment`: id, name, description, ruleName, status (DRAFT/RUNNING/PAUSED/COMPLETED/FAILED), explorationStrategy, optimizationGoals, winningVariant, conclusion
   - `Variant`: experimentId, ruleId, name, parameterValues (JSON), metrics, type (CONTROL/TREATMENT)
   - Adapt to target's `BaseEntity` pattern (includes createdAt, updatedAt, deleted, version)

3. **Port `ReinforcementLearningService`:**
   - Multi-armed bandit algorithm for rule variant selection
   - Experiment lifecycle management
   - Variant performance metric tracking
   - Virtual thread support for concurrent evaluation

4. **Create Flyway migrations:**
    - `V3__add_rule_optimization_tables.sql`
    - `V4__add_experiment_tables.sql`

5. **Add REST endpoints for experiment management**
   - Align auth/authorization with target controller patterns
   - Define consumer contracts (who starts/updates/applies experiments)
   - Port source `RuleExperimentController` shapes selectively, not blindly

#### Files

| Action | File |
|---|---|
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/optimization/RuleOptimizationService.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/experiment/Experiment.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/experiment/Variant.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/experiment/ExperimentService.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/experiment/ExperimentRepository.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/experiment/ReinforcementLearningService.java` |
| **Create** | Flyway migrations in `backend/services/pricing-service/src/main/resources/db/migration/` |
| **Reference** | `temp-pricing-ai-rules/.../ai/engine/AIRuleOptimizationService.java` |
| **Reference** | `temp-pricing-ai-rules/.../ai/rl/ReinforcementLearningService.java` |
| **Reference** | `temp-pricing-ai-rules/.../ai/rl/model/Experiment.java` |
| **Reference** | `temp-pricing-ai-rules/.../ai/rl/model/Variant.java` |

---

### Phase 6: Rule Execution Metrics Logging & Performance Monitoring (CLV Already Present)

**Priority:** MEDIUM - Completes rule observability and auditability
**Estimated Effort:** 1-2 days

#### What to do

Add rule execution performance monitoring and financial metric history logging. Note: target already computes and stores `CLV` in `FinancialMetrics`; Phase 6 should focus on rule-linked telemetry and metric persistence.

#### Steps

1. **Review current CLV behavior** in `MetricsCalculator` (currently proxy-based) and improve only if business-approved
2. **Create `FinancialMetricsLog` entity** for metric history tracking (Flyway migration)
3. **Create `DroolsMetricsInterceptor`** for rule execution performance tracking:
   - Execution time per rule
   - Rules fired count
   - Cache hit rates
   - Memory usage estimates
4. **Add Prometheus metrics** for pricing execution SLA monitoring
5. **Correlate metrics with pricing audit logs** (quote/rule set identifiers) where possible

#### Files

| Action | File |
|---|---|
| **Modify (optional)** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/MetricsCalculator.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/metrics/FinancialMetricsLog.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/metrics/FinancialMetricsLogRepository.java` |
| **Create** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/metrics/DroolsMetricsInterceptor.java` |
| **Create** | Flyway migration `V5__add_financial_metrics_log.sql` |
| **Review** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/FinancialMetrics.java` |
| **Review** | `backend/services/pricing-service/src/main/java/com/opencrm/pricing/waterfall/PricingResult.java` |
| **Reference** | `temp-pricing-ai-rules/.../service/metrics/FinancialMetricsService.java` |
| **Reference** | `temp-pricing-ai-rules/.../service/drools/DroolsMetricsInterceptor.java` |

---

## 5. Dependency Changes

### pricing-service `pom.xml`

```xml
<!-- No immediate dependency change required for Phase 1-3 -->
<!-- pricing-service already depends on rules-engine and actuator/metrics -->

<!-- OPTIONAL (Phase 0 decision): align Drools version only after compatibility spike -->
<!-- backend currently uses Drools 8.44.0.Final via parent/rules-engine -->
```

**Notes**
- `rules-engine` is **already present** in `pricing-service`.
- Prometheus/Micrometer support is **already present** in `pricing-service`.
- Drools version changes should be treated as a **separate compatibility decision**, not bundled into Phase 1.

### ai-service `pom.xml`

```xml
<!-- OPTION A (recommended first): keep existing Spring AI 0.8.1 + Azure OpenAI starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
</dependency>

<!-- OPTION B (optional later): migrate to Spring AI 1.0.0 + ChatModel APIs -->
<!-- Requires explicit provider decision (Azure vs OpenAI) and code migration -->
```

**No Spring Boot version upgrade required** - but Spring AI/provider migration is a separate architectural decision.

---

## 6. Entity Model Reconciliation

| Source Entity | Target Mapping | Notes |
|---|---|---|
| `Quote` (pricing fact) | `QuoteFact` in rules-engine lib | Prefer metadata-backed mapping first; add typed fields only for high-value stable attributes |
| `Customer` | `CustomerFact` in rules-engine lib | Already compatible - same fields |
| `QuoteLineItem` | `QuoteLineItemFact` in rules-engine lib | Source enterprise-bundle rule needs object graph not currently present; likely metadata redesign or defer |
| `Rule` / `RuleSet` | Keep target's entities | Target has richer model: soft-delete, audit, conditionGroup, actionSet |
| `RuleExecutionMetrics` | New entity in pricing-service | Port schema, adapt to BaseEntity |
| `Experiment` / `Variant` | New entities in pricing-service | Port schema, adapt to BaseEntity |
| `FinancialMetricsLog` | New entity in pricing-service | Port schema, adapt to BaseEntity |
| `RuleDependency` | Enhance target's Rule entity | Add dependency tracking |

---

## 7. Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Spring AI stack/provider gap (0.8.1 Azure ChatClient vs source 1.0.0 ChatModel) | **HIGH** | Phase 0 decision spike; implement target-native first, migrate later if needed |
| Drools 8.44.0.Final (target) vs 10.1.0 (source) compatibility | **HIGH** | Phase 0 compatibility spike; do not upgrade blindly |
| Existing target DRL/seeded rule regressions when enabling execution | **HIGH** | Reconcile catalog first; compile/regression test all active DRL before rollout |
| Entity model mismatch (source rules expect richer object graph) | MEDIUM | Use metadata contract first; defer/redesign bundle-heavy rules |
| Flyway vs Liquibase | LOW | Write equivalent Flyway scripts (straightforward DDL) |
| Spring Boot version difference (3.2 vs 3.5) | LOW | Source patterns mostly use stable APIs; validate per component |

---

## 8. Verification Plan

### Per-Phase Verification

| Phase | Test |
|---|---|
| Phase 0 | Compatibility spike: compile/adapt at least one source DRL in target rules-engine; record Drools + Spring AI decisions |
| Phase 1 | Unit test: mock RulesEngine, verify PricingExecutionService calls execute() with correct facts |
| Phase 2 | Integration test: compile **all target active DRL** (existing resources + any imported source rules), verify no regressions |
| Phase 3 | Unit/integration test: submit invalid DRL → verify pass/fail behavior or structured validation errors (depending on API decision) |
| Phase 4 | Integration test: invoke via `AgentRouter` and/or workflow endpoint → verify AI returns DRL content (with missing-credential/fallback case covered) |
| Phase 5 | Unit test: create experiment → add variants → verify winning variant selection |
| Phase 6 | Unit test: verify metrics interceptor records execution time/rules fired; verify `FinancialMetricsLog` persistence |

### End-to-End Verification

1. **Start Docker Compose** (`docker-compose up` - PostgreSQL, Redis)
2. **Run Flyway migrations** (automatic on service startup)
3. **Seed test data:**
    - Create Customer (ENTERPRISE, churnScore=65)
    - Create Products (SaaS seat-based, $100/month)
    - Create PriceBook with entries
    - Confirm existing seeded RuleSet/Rules from `V2__seed_default_rules.sql` compile and execute
    - Add imported source rules only after reconciliation decision (if applicable)
4. **Execute pricing:**
   ```bash
   POST /api/v1/pricing/execute
   {
     "customerExternalId": "CUST-001",
     "quoteExternalId": "Q-001",
     "lineItems": [{ "productExternalId": "PROD-001", "quantity": 100 }],
     "region": "US",
     "durationInMonths": 12,
     "subscriptionType": "ANNUAL"
   }
   ```
5. **Verify response:**
    - Line results have non-zero discountRate (volume discount applied)
    - Financial metrics include ARR, TCV, ACV (CLV if customer data present)
    - Pricing waterfall has steps from rule execution
    - Audit log persisted with input/output snapshots
    - Existing seeded rules continue to work (no regressions from imported rules)

6. **Full test suite:** `mvn clean test` across all modules
7. **Build verification:** `mvn clean package -DskipTests` succeeds

---

## 9. Source Repository Structural Reference

```
pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/
├── ai/
│   ├── context/          # Context7 library integration
│   ├── engine/           # AI rule optimization (AIRuleOptimizationService)
│   ├── mcp/              # MCP client implementations (adapt to Spring AI ChatClient)
│   ├── metrics/          # AI performance metrics
│   ├── orchestration/    # AIOrchestrationService (8-step workflow) ← KEY PORT
│   ├── performance/      # Rule performance analysis
│   ├── prompt/           # Prompt templates
│   ├── puppeteer/        # Web testing (adapt to Drools compiler validation)
│   ├── rl/               # Reinforcement learning (experiments, variants) ← PORT
│   └── sequential/       # Sequential thinking (rule analysis) ← PORT
├── api/
│   ├── controller/       # REST endpoints (reference for new endpoints)
│   ├── dto/              # DTOs (reference for request/response shapes)
│   └── mapper/           # MapStruct mappers
├── config/               # Spring config (reference for optimization flags)
├── domain/
│   ├── model/            # JPA entities (reference for new entities)
│   └── repository/       # Spring Data repos
├── service/
│   ├── drools/           # DroolsServiceImpl ← KEY PORT (execution + validation)
│   ├── metrics/          # FinancialMetricsService ← PORT
│   ├── optimization/     # RuleOptimizationService ← PORT
│   └── pricing/          # PricingServiceImpl (reference for batch pricing pattern)
└── PriceDroolsServiceApplication.java
```

---

## 10. Implementation Priority Order

If time is constrained, implement in this order (each phase is independently valuable):

1. **Phase 0 + Phase 1** (MUST HAVE) - Confirms compatibility assumptions and makes the pricing engine actually execute rules.
2. **Phase 2 (minimal reconciliation)** (MUST HAVE) - Prevents rule conflicts/regressions when execution is enabled.
3. **Phase 3** (SHOULD HAVE) - Prevents invalid rules from being deployed.
4. **Phase 6** (SHOULD HAVE) - Adds rule execution observability and metrics logging.
5. **Phase 4A** (NICE TO HAVE) - AI-powered rule creation on current target AI stack.
6. **Phase 5** (NICE TO HAVE) - Rule experimentation and optimization.
7. **Phase 4B** (OPTIONAL) - Spring AI/provider migration after Phase 4A is stable.

Phase 0 + Phase 1 + minimal Phase 2 deliver the core pricing-engine activation value of this merge.
