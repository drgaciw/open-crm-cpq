# Phase 0 AI Stack / Provider Decision (Evidence Memo)

## Decision Summary

Recommendation:
- Phase 4A: choose **Path A** (stay on target `ai-service` stack assumptions: Spring AI `0.8.1` + Azure OpenAI starter + `ChatClient`-oriented integration contract) to unblock agent integration on the existing `AgentRouter`/`CpqAgent` scaffold.
- Phase 4B: choose **Path B** (planned upgrade to Spring AI `1.0.0+` and adopt `ChatModel` patterns selectively) after Phase 4A proves end-to-end agent routing and PRD-to-agent wiring.

Why:
- The target module is explicitly built/configured for Spring AI `0.8.1` and Azure OpenAI (`backend/services/ai-service/pom.xml:17`, `backend/services/ai-service/pom.xml:52`, `backend/services/ai-service/src/main/resources/application.yml:25`, `backend/services/ai-service/src/main/resources/application.yml:26`).
- The target codebase still contains stubbed agents with `ChatClient` TODOs, so the fastest Phase 4A evidence path is implementing against the existing contract rather than upgrading the AI API surface first (`backend/services/ai-service/src/main/java/com/opencrm/ai/config/AgentConfiguration.java:7`, `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/pricing/CustomerSegmentDiscountAgent.java:30`).
- The source repo patterns are useful, but they are on Spring AI `1.0.0` and are `ChatModel`-centric, which is a version/API migration, not just a copy/paste integration (`pricing-ai-rules-service/price-drools-service/pom.xml:23`, `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:22`, `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:45`).

## Path Definitions

- **Path A (Target-first compatibility)**: Implement Phase 4A AI integration on the current target `ai-service` stack (Spring AI `0.8.1`, Azure OpenAI starter, `ChatClient` assumptions), preserving `AgentRouter` and current `CpqAgent` contracts.
- **Path B (Source-pattern alignment now)**: Upgrade target `ai-service` to Spring AI `1.0.0+` and port source repo `ChatModel` patterns (custom model beans, retries/fallbacks, prompt/structured-output approach) before Phase 4A agent integration.

## Target vs Source Evidence

### Target `ai-service` (Open CRM/CPQ worktree)

- Spring AI version pinned to `0.8.1` and Azure starter dependency:
  - `backend/services/ai-service/pom.xml:17`
  - `backend/services/ai-service/pom.xml:52`
  - `backend/services/ai-service/pom.xml:77`
- Azure OpenAI configuration is already modeled in `application.yml` (`spring.ai.azure.openai...`):
  - `backend/services/ai-service/src/main/resources/application.yml:25`
  - `backend/services/ai-service/src/main/resources/application.yml:26`
  - `backend/services/ai-service/src/main/resources/application.yml:27`
  - `backend/services/ai-service/src/main/resources/application.yml:31`
- Target config assumes Spring AI Azure autoconfig will provide a `ChatClient` bean:
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/config/AgentConfiguration.java:7`
- Agent implementations are scaffolded and not yet connected to an LLM; example `cpq-agent-008` still returns simulated output and has `ChatClient` TODO:
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/pricing/CustomerSegmentDiscountAgent.java:10`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/pricing/CustomerSegmentDiscountAgent.java:30`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/pricing/CustomerSegmentDiscountAgent.java:35`

### Source repo (pricing AI reference)

- Source repo pins Spring AI `1.0.0` and uses `spring-ai-openai` (not Azure starter):
  - `pricing-ai-rules-service/price-drools-service/pom.xml:23`
  - `pricing-ai-rules-service/price-drools-service/pom.xml:99`
  - `pricing-ai-rules-service/price-drools-service/pom.xml:270`
- Source config explicitly states Spring AI `1.0.0` replaced `ChatClient` with `ChatModel` and builds specialized `ChatModel` beans:
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:22`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:50`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:72`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:94`
- Source runtime usage is `ChatModel` + `Prompt` + structured output/retry patterns (good design input, but version-specific):
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:45`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:48`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:49`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:155`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:223`
- Source also includes a useful `ChatModel` fallback pattern via `@ConditionalOnMissingBean`:
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/AppConfig.java:27`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/AppConfig.java:28`
  - `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/AppConfig.java:29`
- Source uses generic OpenAI properties (`spring.ai.openai.*`), while target is Azure-specific (`spring.ai.azure.openai.*`):
  - `pricing-ai-rules-service/price-drools-service/src/main/resources/application.yml:38`
  - `pricing-ai-rules-service/price-drools-service/src/main/resources/application.yml:39`
  - `pricing-ai-rules-service/price-drools-service/src/main/resources/application.yml:40`
  - `backend/services/ai-service/src/main/resources/application.yml:26`
  - `backend/services/ai-service/src/main/resources/application.yml:27`
  - `backend/services/ai-service/src/main/resources/application.yml:28`

## Path A vs Path B Evaluation

### Path A (Phase 4A recommended)

Pros:
- Lowest delivery risk: aligns with current target dependencies/config and avoids a simultaneous framework upgrade.
- Matches current architecture docs that still describe Spring AI `ChatClient` agent orchestration (`agent-prd.md:43`).
- Preserves the existing agent routing API surface already defined in code and PRD (`backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:16`, `agent-prd.md:118`, `agent-prd.md:119`, `agent-prd.md:120`).
- Lets us validate the real integration seam first: PRD/spec -> `CpqAgent` bean -> `AgentRouter` endpoint -> provider call.

Cons:
- Accepts short-term technical debt (eventual upgrade from Spring AI `0.8.1` to `1.0.0+`).
- Source repo `ChatModel` examples cannot be used directly without translation.

### Path B (Phase 4B recommended, not Phase 4A)

Pros:
- Aligns with the modernization direction that calls out Spring AI `1.0+` (`MODERNIZATION_PLAN.md:158`).
- Enables reuse of stronger source patterns: multi-bean model profiles, retry, fallback, structured output (`pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/SpringAiConfig.java:50`, `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/config/AppConfig.java:27`, `pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/ai/sequential/SequentialThinkingService.java:49`).

Cons:
- Higher scope/risk for Phase 4A because it combines: Spring AI upgrade, provider wiring changes, agent implementation work, and potential API refactors (`ChatClient` -> `ChatModel`).
- Target docs and current stubs are `ChatClient`-worded, so a full Path B move requires touching design docs/assumptions as well as code (`backend/services/ai-service/src/main/java/com/opencrm/ai/config/AgentConfiguration.java:7`, `agent-prd.md:43`).
- Source repo is OpenAI-generic in config and code; target is Azure OpenAI-specific, so provider wiring is not a direct transplant.

## Integration Points to Use in Phase 4A / 4B

### 1) `AgentRouter` (runtime dispatch + API seam)

- `AgentRouter` is the stable ingress for agent discovery/invocation/streaming and already maps to the PRD route contract:
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:16`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:27`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:39`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:46`
  - `agent-prd.md:118`
  - `agent-prd.md:119`
  - `agent-prd.md:120`

### 2) `CpqAgent` contract (adapter seam for `ChatClient`/`ChatModel`)

- `CpqAgent` defines the per-agent execution contract (`invoke` + `stream`) and is the right place to isolate model-provider API differences behind internal helper classes:
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/CpqAgent.java:7`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/CpqAgent.java:27`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/CpqAgent.java:32`
- `AgentRequest`/`AgentResponse` are generic enough to support both Path A and Path B without changing router endpoints:
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/AgentRequest.java:6`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/AgentRequest.java:7`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/AgentResponse.java:5`
  - `backend/services/ai-service/src/main/java/com/opencrm/ai/agent/AgentResponse.java:6`

### 3) `agents/*.md` specs (behavior/input-output truth source)

- The agent PRD index is the source of truth for agent IDs, routing expectations, and per-agent specs (`agent-prd.md:43`, `agent-prd.md:49`, `agent-prd.md:118`, `agent-prd.md:119`, `agent-prd.md:120`).
- Each `agents/*.md` file provides actionable integration details (dependencies, inputs, outputs, workflow sequencing). Example for `cpq-agent-008`:
  - `agents/cpq-agent-008.md:7`
  - `agents/cpq-agent-008.md:12`
  - `agents/cpq-agent-008.md:20`
  - `agents/cpq-agent-008.md:49`
  - `agents/cpq-agent-008.md:85`

## Recommendation for Phase 4A / 4B

### Phase 4A (implement now)

Choose **Path A**:
- Keep Spring AI `0.8.1` + Azure OpenAI starter in target `ai-service`.
- Implement a thin internal LLM adapter for the existing `CpqAgent` stubs (using current `ChatClient` assumptions) so `AgentRouter` routes can return real model-backed output for a small pilot set of agents.
- Use `agents/*.md` as the implementation spec and keep agent IDs aligned with `@Service("cpq-agent-###")`.

Success evidence for Phase 4A:
- `GET /api/v1/ai/agents` lists implemented agents from bean registry (`backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:27`).
- `POST /invoke` and `/stream` return non-simulated outputs for pilot agents (`backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:39`, `backend/services/ai-service/src/main/java/com/opencrm/ai/router/AgentRouter.java:46`).

### Phase 4B (planned upgrade/hardening)

Choose **Path B**:
- Upgrade to Spring AI `1.0.0+` after Phase 4A routing/provider integration is stable.
- Port source patterns selectively:
  - specialized model beans (temperature/behavior profiles),
  - retry template,
  - fallback `ChatModel`,
  - prompt/structured-output patterns where needed.
- Reconcile provider configuration for Azure OpenAI while adopting `ChatModel` APIs.

## Final Call

Use **Path A for Phase 4A** and schedule **Path B for Phase 4B**. The local evidence shows the target code is structurally ready for routing and agent registration today, but not yet ready for a low-risk Spring AI API migration at the same time.




