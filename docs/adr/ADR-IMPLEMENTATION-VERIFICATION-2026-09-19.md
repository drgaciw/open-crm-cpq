# ADR implementation verification — GitNexus + Jev (2026-09-19)

> **Historical snapshot — policy update 2026-09-19:** This evaluation predates [ADR-014](ADR-014-jev-openrouter-model-selection.md), which supersedes ADR-002's fixed GLM-5 selection with Jev-driven OpenRouter model selection. The original verdicts below are preserved as historical evidence; they do not verify ADR-014, whose implementation is [in backlog](../backlog/REQ-jev-openrouter-model-selection.md). Azure OpenAI `gpt-4o` remains the configured runtime. No new Jev evaluation was run for this documentation update.

**Question:** are the ADRs in [docs/adr/](README.md) actually implemented in the project?

**Current interpretation:** The historical verdicts below are not a completeness score. ADR-001/002 are superseded decisions; ADR-005 remains a held operational cutover. Subsequent source review found four standalone database defaults, shared pricing/pricebook migration ownership, missing completed canonical approval history, and a legacy local-write compatibility exception. The implementation and its validation are tracked in [ADR gap implementation](../adr-gap-implementation.md). ADR-014 requires separate implementation and rollout evidence; the historical Jev scores do not establish it.

---

## Method

Two independent instruments, combined per ADR:

1. **GitNexus graph pass** (repo `open-crm-cpq`, index @ `baf4a277`, 1 commit behind HEAD): `context()` on pivot symbols, `cypher()` over the knowledge graph (callers, imports, module inventories, zero-row checks), cross-checked with direct file reads. Empty graph results were never treated as "unused" — each was confirmed by complementary file evidence.
2. **Jev structured evaluation** (live, model `typesafe/jev-1.13` via the OpenRouter decisions endpoint, reusing the repo's sanctioned client `frontend/e2e/lib/decide.mjs`): each ADR's claim + cited evidence was submitted as a `state`, with two typed questions per ADR — a `choice` verdict (`implemented` / `partial` / `absent`, with validated full probability distribution and confidence) and a `noul` support probability ("the cited evidence establishes implementation"). Answers were **raw readouts** — no confidence gating — because this is judgement, not browser action; nothing was acted on from the model's output. Three batched calls (5/4/4 questions), evaluated 2026-09-19T23:23Z.

Jev only ever saw evidence text; GitNexus + files remain the ground truth. No secrets were embedded in the harness or this report.

---

## Results

| ADR | Decision | GitNexus / file evidence (abridged) | Jev verdict (confidence) | Combined conclusion |
|-----|----------|--------------------------------------|--------------------------|---------------------|
| 001 | LLM — Claude Sonnet 4.6 | Cypher for Zhipu/Anthropic in `ai-service`: **0 rows** | `implemented` (0.84; P=0.90/0.06/0.04) | ✅ Supersession holds — no Anthropic starter in tree |
| 002 | LLM — GLM-5 via Zhipu | `AgentRouter` class w/ `agentsEnabled` flag + feature-flag tests; runtime yml = `spring.ai.azure.openai`, `deployment-name: gpt-4o` | **`partial`** (0.72; **P(partial)=0.81**) | ⚠️ **Partial by design** — decision Accepted, GLM-5 not landed; AgentRouter + Azure runtime is exactly what ADR-002 documents |
| 003 | CI — Jenkins primary, GHA manual | `ci-contract.yaml`: `primary: jenkins`, GHA `push_branches: []`, SSOT `run-core.sh`, `ai_assist.platform: jenkins_only` | `implemented` (0.94) | ✅ Implemented |
| 004 | CSRF edge-only | `CsrfProtectionFilter` + tests `constructorRejectsAzureWildcardPattern` / `rejectsUnlistedAzureSubdomainOrigin`; gateway `CookieServerCsrfTokenRepository`; crm `csrf.disable()`+STATELESS (SecurityConfig:34-35,44-45) | `implemented` (0.94) | ✅ Implemented |
| 005 | Approvals dual-read default `true` | `ApprovalService.legacyLocalReadEnabled` + `approveLegacyLocal`/`rejectLegacyLocal`; yml `${APPROVALS_LEGACY_LOCAL_READ:true}`; `ApprovalServiceSingleWriterTest.getPendingWithLegacyFlagOffDoesNotReadLocal` | `implemented` but **confidence 0.47** (P=0.64/0.34/0.02) — lowest of the set | ✅ Mechanism implemented; ⚠️ the *decision* is deliberately still **Proposed** (flip held) — Jev's low confidence correctly senses that distinction |
| 006 | Entities never at API boundary | `SubscriptionControllerTest.assertNoEntityLeakFields` + `containsForbiddenField`; controller = list/getById/getByCustomer/getByStatus/activate/cancel/suspend/renew; grep: 0 raw-entity `ApiResponse<>` wraps | `implemented` (**1.00**) | ✅ Implemented (with contract tests enforcing it) |
| 007 | FE signals + HttpClient, no NgRx | `frontend/package.json` has **no `@ngrx/*`**; `shell.component.ts:104` `signal(true)`; `app.routes.ts` all `loadChildren` | `implemented` (**1.00**) | ✅ Implemented |
| 008 | One DB, schema-per-service | `init-db.sql` schemas crm/cpq/pricing/product/quote/communication/ai; compose `currentSchema=` per service; Flyway per service; `ddl-auto: validate` | `implemented` (0.99) | ✅ Implemented (document-generation `cpq_document` default noted as drift, ARCHITECTURE.md §16) |
| 009 | Gateway edge-only | Gateway main = **exactly 7 classes** (CpqGatewayRoutes, CsrfProtectionFilter, FallbackController, GatewayApplication, OpenApiConfig, RateLimitingConfig, SecurityConfig) — zero domain logic; CPQ alias rewrites + validate→CE rewrite + circuit breakers in yml | `implemented` (**1.00**) | ✅ Implemented |
| 010 | Kafka outbox + versioned events | `OutboxEventPublisher.publish` called from **7 production sites across 5 services** (approval-routing reject; CE publishConfigurationCommitted; contract-asset RenewalScheduler; pricing publishDiscountApprovalRequired; product bundle/retire/updated; quote accept); `event/dto/v1/*` + contract tests; document-generation Kafka consumer | `implemented` (0.99) | ✅ Implemented |
| 011 | Shared Maven libs as published language | **155** service files import `libs/common`; reactor = 3 libs + 11 services, no service-to-service deps; CI `shared-libraries` first blocking stage | `implemented` (**1.00**) | ✅ Implemented |
| 012 | Rules engine port; Drools landed, OpenL dormant | `HybridRuleEnginePort` **implements** `RuleEnginePort` (ENGINE_ORDER + engines, port test); `OpenLRulesEngine` = **0 incoming CALLS** (dormant, as documented); CE `ProductRuleEvaluationCore` at `configuration-engine/evaluate/`; Drools 10.2 + evidence tests | `implemented` (0.91) | ✅ Implemented (OpenL dormancy is the documented state, not a gap) |
| 013 | Quote BFF approvals, single-writer | `ApprovalService.approvalRoutingClient` + `toDtoFromRouting`; canonical `ApprovalController` in approval-routing (DTO-only); `ApprovalServiceSingleWriterTest`; gateway route split quotes/approvals | `implemented` (0.98) | ✅ Implemented |

**Jev aggregate (min-choice-confidence rule from `decide.mjs`):** 0.47 — dominated by ADR-005; excluding the two status-by-design cases (002, 005), aggregate rises to **0.84**. Mean support probability across all 13: **0.77** (ADR-002's 0.12 is the deliberate outlier).

---

## Interpretation notes

- **ADR-002 (Jev: `partial`, P(partial)=0.81)** is the cleanest signal in the run: the model separated "AgentRouter + Azure runtime implemented" from "GLM-5 not implemented" without being told which parts were intentional. This matches the ADR's own status line: *Accepted (decision); runtime not landed*.
- **ADR-005 (confidence 0.47)** is not a failure — the claim spans an implemented mechanism (flag, legacy paths, flag-off unit proof) and a deliberately unaccepted decision (flip held, HIGH gate). Verdict recorded as: mechanism ✅, decision ⏸ Proposed.
- Zero-row graph evidence (Zhipu/Anthropic classes; `OpenLRulesEngine` callers) was paired with file-level confirmation before use, per the "UNKNOWN/empty ≠ unused" rule.
- **Discrepancies found during the pass** (documented, not fixed): none new beyond those already recorded in [ARCHITECTURE.md §16](../ARCHITECTURE.md) (ai-service `:8086` port collision; document-generation `cpq_document` default).

## Provenance

- Graph index: `open-crm-cpq` @ `baf4a277` (indexed 2026-09-19T19:53Z; 1957 files / 16946 nodes / 32248 edges).
- Jev run: transport `openrouter`, model `typesafe/jev-1.13`, evaluated 2026-09-19T23:23:44Z; harness reuses `frontend/e2e/lib/decide.mjs` (`ask()` with strict distribution validation); raw answers, `minConfidence` disabled; harness executed from the session temp dir, not committed.
- Evidence collected by: architecture reverse-engineering pass of 2026-09-19 ([docs/ARCHITECTURE.md](../ARCHITECTURE.md), [adr/README.md](README.md) verification table).
