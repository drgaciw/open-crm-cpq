# ADR-001: Primary LLM — Claude Sonnet 4.6 (replacing Azure OpenAI GPT-4)

**Status:** Superseded by ADR-002 (GLM-5 via Zhipu AI)
**Date:** 2026-03-15
**Superseded by:** ADR-002 (cost — GLM-5 is 3–5x cheaper)
**Deciders:** DRGAMTD (Enterprise Architect / Platform Owner)

---

## Context

The open-crm-cpq platform requires a primary LLM for all 37 CPQ AI agents covering tool function calling, multi-turn agent workflows, structured output generation, and RAG-augmented recommendations. The original architecture selected Azure OpenAI (GPT-4) as the primary provider with Claude used selectively for "agent workflows."

This decision supersedes that split-provider model. The rationale for re-evaluation:

- **Operational complexity**: Maintaining two LLM providers (Azure OpenAI primary + Claude secondary) doubles configuration surface, credential management, retry/fallback logic, and cost monitoring scope for a 1-FTE team.
- **Capability alignment**: The 37 CPQ agents rely heavily on structured tool calling, long-context retention across multi-step approval flows, and nuanced instruction-following for pricing guardrail enforcement. Claude Sonnet 4.6 demonstrates superior performance on these task types compared to GPT-4o equivalents, particularly on instruction adherence in constrained autonomy tiers.
- **Toolchain coherence**: The project's primary development toolchain is already Claude-native (Claude Code, Cowork, Claude Agent SDK). Aligning the runtime LLM with the development LLM reduces context-switching and enables direct testing of agent prompts in the same model that will execute them in production.
- **Spring AI 1.0 upgrade** (see pending Spring AI upgrade task): Spring AI 1.0 ships a first-class `spring-ai-anthropic-spring-boot-starter` with full `@Tool` annotation support, `ChatClient.Builder` streaming, and pgvector advisor integration — identical API surface to the Azure OpenAI starter.
- **Cost predictability**: Anthropic direct API pricing for Claude Sonnet 4.6 ($3.00/$15.00 per million tokens with 90% cache-read discount) is well-understood and directly visible in the installed cost monitor. Azure OpenAI pricing varies by region, deployment SKU, and PTU commitment — harder to track at the per-agent level.

---

## Decision

**Replace Azure OpenAI GPT-4 with Claude Sonnet 4.6 (`claude-sonnet-4-6`) as the primary LLM for all 37 CPQ agents.**

Azure OpenAI is removed from the primary provider role. Claude Sonnet 4.6 handles all agent invocations via the Anthropic API through `spring-ai-anthropic-spring-boot-starter`.

The fallback provider (for circuit-breaker scenarios) remains an open decision — GPT-4o via direct OpenAI API is the natural candidate but is not implemented in this ADR.

---

## Options Considered

### Option A: Claude Sonnet 4.6 via Anthropic API (Selected)

| Dimension | Assessment |
|-----------|------------|
| Instruction following | High — best-in-class for constrained autonomy patterns |
| Tool calling accuracy | High — `@Tool` annotation support in Spring AI 1.0 |
| Long context (200K) | High — handles full quote workflow chains in single context |
| Prompt caching | High — 90% cache-read discount reduces per-agent cost significantly |
| Spring AI integration | High — first-class `spring-ai-anthropic-spring-boot-starter` |
| Operational complexity | Low — single provider, single credential, single pricing model |
| Cost (with caching) | ~$0.30/M cached reads — lowest effective cost for repeated agent calls |
| Dev/prod parity | High — same model used in Claude Code development sessions |

**Pros:** Single provider simplicity; superior instruction adherence; native prompt caching economics; direct Anthropic API (no Azure intermediary latency/quota); 200K context handles full CPQ quote histories.

**Cons:** No Azure AD integration for credential management; requires Anthropic API key secret management separate from Azure Key Vault; no PTU (provisioned throughput) option if high-volume SLA becomes required.

### Option B: Azure OpenAI GPT-4o (Original Architecture)

| Dimension | Assessment |
|-----------|------------|
| Instruction following | Medium — adequate for standard tool calling |
| Tool calling accuracy | Medium — function calling API is mature |
| Long context (128K) | Medium — sufficient for most agent chains |
| Prompt caching | None — Azure OpenAI does not support prompt caching |
| Spring AI integration | High — `spring-ai-azure-openai-spring-boot-starter` |
| Operational complexity | Medium — Azure credential stack (AD, Key Vault, deployment names) |
| Cost | Higher effective cost — no caching discount; PTU required for SLA |
| Dev/prod parity | Low — development uses Claude Code; production uses GPT-4o |

**Pros:** Azure-native credential management via Managed Identity; co-located with AKS infrastructure; single Azure invoice for all cloud spend.

**Cons:** No prompt caching (critical cost driver at 37-agent scale); lower instruction-following fidelity for constrained autonomy tiers; splits dev and prod LLM surface; requires Azure OpenAI deployment provisioning per region.

### Option C: Dual Provider (Original Split Model — Azure Primary / Claude Secondary)

| Dimension | Assessment |
|-----------|------------|
| Flexibility | High — different models for different task types |
| Operational complexity | High — two credential stacks, two pricing models, two fallback paths |
| Cost monitoring | High complexity — two providers in cost monitor |
| Team overhead (1-FTE) | Unacceptable — doubles configuration and debugging surface |

**Rejected.** The operational overhead is disproportionate for a 1-FTE team, and the flexibility benefit does not outweigh the complexity cost at this stage.

---

## Trade-off Analysis

The decisive trade-off is **prompt caching economics vs Azure-native credential management**. At 37-agent scale running background stewards continuously, prompt caching is not a nice-to-have — it is the primary cost control mechanism. The installed cost monitor (`clawdbot-cost-monitor`) already shows caching reduces effective costs by 5–10x on long conversation chains. Azure OpenAI has no equivalent.

Azure-native credential management (Managed Identity, Key Vault) is a real advantage for enterprise deployments, but at current project phase (Phase 4, CPQ engine, pre-production), the simplicity of a single Anthropic API key outweighs the credential automation benefit.

Dev/prod parity is the secondary trade-off. Using the same model in development (Claude Code + Cowork) and production agents eliminates the "it worked in dev but behaves differently in prod" failure mode common in LLM-powered systems.

---

## Consequences

**What becomes easier:**
- Single Spring AI starter, single `application.yml` configuration block
- Prompt caching automatically applied to all repeated agent invocations (system prompts, tool definitions, workspace context)
- Cost monitor (`clawdbot-cost-monitor`) already has Sonnet 4.6 pricing — no additional configuration needed
- Agent prompts developed and tested in Claude Code/Cowork match production behaviour exactly
- Spring AI 1.0 `@Tool` annotations, `ChatClient.Builder`, and pgvector `VectorStoreAdvisor` all work identically with the Anthropic starter

**What becomes harder:**
- Azure Managed Identity for LLM credentials is no longer applicable — Anthropic API key requires explicit secret management (Azure Key Vault secret + K8s secret store CSI driver recommended for AKS deployment)
- No PTU option if throughput SLA requirements emerge at enterprise scale — revisit if concurrent agent invocations exceed Anthropic rate limits

**What we will need to revisit:**
- Fallback LLM provider strategy (GPT-4o direct OpenAI as circuit-breaker candidate) — defer to Phase 5 AI infrastructure sprint
- Azure AI Search integration for RAG — Azure AI Search can still be used as the vector store independent of the LLM provider choice; `spring-ai-azure-store` is a separate dependency from the LLM starter

---

## Implementation

### 1. `backend/services/ai-service/pom.xml`

Replace:
```xml
<spring-ai.version>0.8.1</spring-ai.version>
...
<artifactId>spring-ai-azure-openai-spring-boot-starter</artifactId>
```

With:
```xml
<spring-ai.version>1.0.0</spring-ai.version>
...
<artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
```

### 2. `application.yml` (ai-service)

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-6
          max-tokens: 8096
          temperature: 0.1   # Low temperature for deterministic pricing/approval decisions
```

### 3. All agent `ChatClient` beans

Update model string from `"claude-sonnet-4-20250514"` or `"gpt-4"` to `"claude-sonnet-4-6"`.

### 4. Cost monitor

`cost-calculator.js` — add entry:
```javascript
'claude-sonnet-4-6': { input: 3.00, output: 15.00, cacheWrite: 3.75, cacheRead: 0.30 }
```

---

## Action Items

- [x] ADR-001 written and accepted
- [ ] Upgrade `spring-ai.version` to `1.0.0` in `ai-service/pom.xml`
- [ ] Replace `spring-ai-azure-openai-spring-boot-starter` with `spring-ai-anthropic-spring-boot-starter`
- [ ] Add `application.yml` with Anthropic config to `ai-service/src/main/resources/`
- [ ] Update all `ChatClient` bean model strings to `claude-sonnet-4-6`
- [ ] Add `ANTHROPIC_API_KEY` to `backend/.env.example` and AKS secret store
- [ ] Update `MODERNIZATION_PLAN.md` AI stack references
- [ ] Update `agent-prd.md` technology stack table
- [ ] Update `AGENT.md` ADR table
- [ ] Add `claude-sonnet-4-6` pricing to `cost-calculator.js`
- [ ] Remove Azure OpenAI deployment config from `MODERNIZATION_PLAN.md` Phase 5 checklist
- [ ] Add fallback provider decision to ADR backlog (ADR-002 candidate)
