# ADR-002: Primary LLM — GLM-5 via Zhipu AI (supersedes ADR-001)

**Status:** Superseded by [ADR-014](ADR-014-jev-openrouter-model-selection.md) on 2026-09-19; GLM-5 implementation never landed
**Date:** 2026-03-15
**Supersedes:** ADR-001 (Claude Sonnet 4.6)
**Deciders:** DRGAMTD (Enterprise Architect / Platform Owner)

> Historical decision retained for provenance. The fixed GLM-5 choice, dependency instructions, price comparisons, and unchecked action items below are obsolete and must not be implemented from this record. Current direction: Jev selects qualifying OpenRouter models at the lowest total cost under ADR-014. See the [implementation requirements](../backlog/REQ-jev-openrouter-model-selection.md). Azure OpenAI `gpt-4o` remains the checked-in runtime pending implementation.

### Runtime vs decision (verified 2026-08-31)

| Surface | ADR-002 says | Tree says |
|---------|--------------|-----------|
| Primary model | GLM-5 via Zhipu (`glm-5`) | Azure OpenAI **gpt-4o** |
| `ai-service` YAML | `spring.ai.zhipuai` / `ZHIPUAI_API_KEY` | `spring.ai.azure.openai` / `AZURE_OPENAI_*` / `deployment-name: gpt-4o` |
| Starter / BOM | `spring-ai-starter-model-zhipuai` 1.1.0 | `spring-ai-azure-openai-spring-boot-starter`, `<spring-ai.version>0.8.1</spring-ai.version>` |
| `AGENT.md` | (action item: update) | Spring AI 1.1.7 + **Azure OpenAI** + Claude Agent SDK; ADR table “Azure OpenAI primary, Claude for agent workflows” |

**Do not treat this ADR as describing production runtime.** Action items below remain **unchecked**. Changing YAML/pom is a separate implementation PR (not this docs review). CI LLM path is **ADR-003** (`LLM_ENDPOINT` for Jenkins ai-assist) — do not conflate with `ai-service`.

---

## Context

ADR-001 selected Claude Sonnet 4.6 as the primary LLM. Cost is the driving force for this supersession. Claude Sonnet 4.6 is priced at $3.00/M input and $15.00/M output tokens. With 37 CPQ agents running background stewards continuously, the projected monthly API spend at scale is significant.

GLM-5 (launched February 11, 2026) offers $1.00/M input and $3.20/M output — approximately 3x cheaper on input and 4.7x cheaper on output — at 744B parameters with a 200K context window comparable to Claude's.

Spring AI 1.1 GA (November 2025) ships a native `spring-ai-starter-model-zhipuai` starter, providing the same `ChatClient` / `@Tool` annotation API surface as the Anthropic starter with no code changes required beyond configuration.

---

## ⚠️ Data Residency Flag

Zhipu AI (Z.ai) is a Beijing-based company. API calls route through Zhipu AI's infrastructure in China. For a CPQ platform processing customer pricing data, discount approvals, contract values, and margin data, the following must be confirmed before production deployment:

- Customer data processing agreements (DPA) with Zhipu AI
- Regulatory compliance for any applicable jurisdictions (GDPR, CCPA, SOC 2)
- Customer contractual obligations regarding data residency

This ADR records the decision at the architecture level. Compliance sign-off is a prerequisite for production go-live with this provider.

---

## Decision

**Replace Claude Sonnet 4.6 (ADR-001) with GLM-5 (`glm-5`) via Zhipu AI API as the primary LLM for all 37 CPQ agents.**

Spring AI is simultaneously upgraded from `1.0.0` to `1.1.0` to use the updated starter naming convention and access GLM-5 model support.

---

## Options Considered

### Option A: GLM-5 via Zhipu AI (Selected)

| Dimension | Assessment |
|-----------|------------|
| Input cost | $1.00/M tokens — 3x cheaper than Sonnet 4.6 |
| Output cost | $3.20/M tokens — 4.7x cheaper than Sonnet 4.6 |
| Prompt caching | Not supported — cost advantage is on raw pricing |
| Context window | 200K tokens — matches Claude |
| Parameters | 744B MoE (40B active) — frontier-class |
| Spring AI support | Native `spring-ai-starter-model-zhipuai` in 1.1.0 |
| Data residency | ⚠️ China-based — requires compliance review |
| Instruction following | Strong for agentic tasks; less established for CPQ-specific guardrails |

**Pros:** Significantly lower API cost; frontier-class 744B parameter model; native Spring AI 1.1 support; 200K context; OpenAI-compatible API.

**Cons:** No prompt caching; data routes through Zhipu AI infrastructure in China; GLM-5 not yet explicitly listed in Spring AI's model enum (specify as string `"glm-5"`); less Western enterprise production track record than Claude.

### Option B: Claude Sonnet 4.6 (ADR-001 — superseded)

| Dimension | Assessment |
|-----------|------------|
| Input cost | $3.00/M — 3x more expensive |
| Output cost | $15.00/M — 4.7x more expensive |
| Prompt caching | 90% cache-read discount ($0.30/M) — partially offsets raw price gap |
| Data residency | Anthropic (US-based) — straightforward compliance |

**Rejected on cost grounds.** Prompt caching partially narrows the gap but does not close it at sustained background-steward workloads.

---

## Trade-off Analysis

The raw price difference is decisive: 3–5x cheaper per token. The prompt caching advantage of Claude (90% cache-read discount) partially offsets this for highly repetitive agent calls, but GLM-5's base pricing still wins at scale for the mix of advisory, automated, and orchestrator agents in the 37-agent fleet.

The material risk is data residency. This is recorded but not a blocker for development and pre-production phases.

---

## Consequences

**What becomes easier:**
- Monthly LLM spend reduced approximately 3–5x at equivalent call volume
- Spring AI 1.1 upgrade also brings improved `@Tool` annotation support, better streaming, and additional advisor components

**What becomes harder:**
- No prompt caching — long agent conversation chains pay full input price every turn
- GLM-5 model string must be passed as a literal string `"glm-5"` until Spring AI's ZhiPu model enum is updated
- Data residency compliance review required before production

**What we will need to revisit:**
- ADR-003 candidate: Fallback LLM provider (Claude Sonnet 4.6 as circuit-breaker remains the natural choice)
- Data residency sign-off (prerequisite for production)
- Re-evaluate if Zhipu AI introduces prompt caching — would further strengthen cost position

---

## Implementation

### 1. `backend/services/ai-service/pom.xml`

```xml
<!-- Spring AI version -->
<spring-ai.version>1.1.0</spring-ai.version>

<!-- Starter -->
<artifactId>spring-ai-starter-model-zhipuai</artifactId>
```

### 2. `application.yml`

```yaml
spring:
  ai:
    zhipuai:
      api-key: ${ZHIPUAI_API_KEY}
      chat:
        options:
          model: glm-5
          temperature: 0.1
          max-tokens: 8096
```

### 3. Cost monitor `cost-calculator.js`

```javascript
'glm-5': { input: 1.00, output: 3.20, cacheWrite: 0, cacheRead: 0 }
```

---

## Action Items

- [x] ADR-002 written and accepted
- [ ] Upgrade `spring-ai.version` to `1.1.0` in `ai-service/pom.xml`
- [ ] Replace `spring-ai-anthropic-spring-boot-starter` with `spring-ai-starter-model-zhipuai`
- [ ] Update `application.yml` with Zhipu AI config
- [ ] Add `ZHIPUAI_API_KEY` to `backend/.env.example` and AKS secret store
- [ ] Add `glm-5` pricing to `cost-calculator.js`
- [ ] Update `MODERNIZATION_PLAN.md`, `AGENT.md`, `agent-prd.md`
- [ ] **Compliance:** Data residency review with Zhipu AI DPA before production go-live
- [ ] ADR-003: Fallback LLM provider (Claude Sonnet 4.6 circuit-breaker candidate)
