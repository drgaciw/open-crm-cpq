# ADR-012: Rules engine behind a port — Drools DRL landed, OpenL/SpEL deferred, shared evaluation core for configuration

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — implemented across the pricing waves and CE shared-core waves G–H; recorded retroactively)*
**Date (decision embedded):** ~2026-09 (pricing waves; CE core Waves F–H) · **Documented:** 2026-09-19
**Deciders:** forge pricing/CE waves (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR — documents the landed rules architecture. Does **not** un-hold full SpEL/OpenL rollout, full S2B-8 `ConstraintSolver`, or Kafka S2B-9.

---

## Context

CPQ needs two families of rule execution: (1) **pricing** rules (discounts, waterfalls, customer tiers) over quotes/products/customers, and (2) **configuration compatibility** rules (REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES) over product options. The target-state plan named OpenL Tablets + full Drools; the phase-0 decision note (`docs/phase0/phase0-drools-compatibility.md`) and roadmap allowed starting narrower.

Verified on the tree (2026-09-19):

- `backend/libs/rules-engine` defines the abstraction: `RuleEnginePort`, `HybridRuleEnginePort`, `RulesEngine`, `RulesEngineType`, `RuleDefinition`/`RuleSetDefinition`, `RuleExecutionRequest/Result/TraceEntry`, `RulePriority`, `ConflictResolutionStrategy`, shared facts (`QuoteFact`, `QuoteLineItemFact`, `ProductFact`, `CustomerFact`, `PricingWaterfallFact`), `ExplanationCode`, `RulesCacheManager` (compile cache), and `RulesEngineAutoConfiguration` (opt-in).
- **Drools 10.2 landed**: `DroolsRuleCompiler`, `KieFileSystemCompiler`, `DroolsRulesEngine`, `DroolsSessionFactory`, `DrlResource`, with evidence tests `Drools844CompatibilityEvidenceTest` and `PricingDrlCorpusCompileTest`. pricing-service serves `/api/v1/rule-sets/**` + `/api/v1/rules/**` (DRL authoring/storage/execution).
- **OpenL Tablets** dependency present (`5.27.6`) and `OpenLRulesEngine` exists **behind the same port** — not required for forge; no OpenL content in pricing flows.
- **Configuration evaluation** (second family): configuration-engine `ProductRuleEvaluationCore` — hard REQUIRES/EXCLUDES/VALIDATES (`attr:key=value` / `attr:in:` when attributes present), soft RECOMMENDS, DEFAULT expansion; served statelessly via `POST /internal/v1/configurations/evaluate` behind the gateway shim, and on session validate. Full S2B-8 `ConstraintSolver` (graph conflict) remains follow-on. product-service retains a flat O(rules) REQUIRES/EXCLUDES inline validate for direct/parity callers (dual-run per the configuration-API reconciliation ADR).

## Decision

**All rule execution goes through the `RuleEnginePort` abstraction in `libs/rules-engine`. Drools 10.2 is the landed engine for pricing DRL. Configuration compatibility is evaluated by configuration-engine's shared `ProductRuleEvaluationCore` (the de-facto first implementation of the CE side of the port), not by Drools. OpenL Tablets is wired behind the port but dormant. New rule types extend the shared core/port — no service embeds its own evaluator.**

1. pricing-service owns DRL authoring, storage, compile cache, and execution; other services consume via HTTP (`/api/v1/pricing/**`, rule-sets/rules).
2. configuration-engine owns constraint evaluation semantics; product-service rule CRUD persists definitions but its inline validate stays transitional (dual-run, no removal date).
3. Engine selection (`RulesEngineType`) is data/config, not call-site branching; `HybridRuleEnginePort` mediates.
4. Full OpenL content, full SpEL, S2B-8 `ConstraintSolver` conflict graphs, and S2B-9 event-driven rule invalidation are **held** — implementing them requires a wave plan.
5. Compile success is a tested invariant (`PricingDrlCorpusCompileTest`) — broken DRL fails CI, not runtime.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Port + Drools for pricing + shared CE core (landed)** | Engine swappable; pricing power now; config semantics testable without a rules engine. Con: two rule "dialects" (DRL vs CE core) to keep documented. |
| B. OpenL Tablets day-1 (original plan) | Business-analyst tables are attractive but add a content pipeline the forge didn't have; port keeps the door open. **Deferred.** |
| C. Full Drools for configuration too | REQUIRES/EXCLUDES as DRL would split semantics from the session/agent path; CE core keeps one authoritative evaluator. Rejected. |
| D. Hand-rolled evaluator per service | The exact divergence the configuration-API reconciliation ADR forbids. Rejected. |

## Consequences

- **Positive:** Single pricing execution path with compile-time CI evidence; config rules evaluate identically for agents (gateway shim), UI sessions, and quote preflight; swapping/adding engines does not touch call sites.
- **Negative / residual:** OpenL dependency rides along dormant until a decision; two rule representations must stay documented (DRL for pricing, core rule rows for configuration); CE without full `ConstraintSolver` cannot do graph conflict detection yet.
- **Governance:** Full SpEL/OpenL + S2B-8 + S2B-9 remain on the holds list ([ARCHITECTURE.md §17](../ARCHITECTURE.md)).

## Verification evidence (2026-09-19)

- `backend/libs/rules-engine/src/main/java/com/opencrm/rules/**` — port, Drools impl, OpenL stub, facts, cache (file inventory in [ARCHITECTURE.md](../ARCHITECTURE.md)).
- Tests: `Drools844CompatibilityEvidenceTest`, `PricingDrlCorpusCompileTest`, `HybridRuleEnginePortTest`, `SharedPricingFactsTest`, `RulesCacheManagerTest`.
- configuration-engine `ProductRuleEvaluationCore` + `POST /internal/v1/configurations/evaluate` (CE module); product-service `ConfigurationRuleService.validateConfiguration` (flat O(rules), retained).
- `backend/pom.xml:61-62` — `drools.version 10.2.0`, `openl-tablets.version 5.27.6`.
- `docs/phase0/phase0-drools-compatibility.md`, `docs/backlog/ADR-configuration-api-reconciliation.md`.

## References

- [docs/ARCHITECTURE.md §8](../ARCHITECTURE.md) — Rules engine
- [docs/backlog/ADR-configuration-api-reconciliation.md](../backlog/ADR-configuration-api-reconciliation.md) — evaluation-vs-persistence ownership split
- [docs/adr/ADR-011-shared-maven-libs-published-language.md](ADR-011-shared-maven-libs-published-language.md) — why the port lives in `libs/rules-engine`
