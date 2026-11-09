---
name: spring-ai-orchestration
description: >-
  Federation hub for Open CRM/CPQ AI — routes across all cpq-agent-* functional
  agents, deal-desk workflows, gateway tools, Redis triggers, and
  cpq-lifecycle-tracer. Use when implementing agents, sequencing multi-agent
  flows, or choosing which agent handles a CPQ task.
disable-model-invocation: true
---

# Spring AI Orchestration (Open CRM/CPQ)

Pair with **`spring-ai-orchestration-specialist`** — federation hub for all functional agents.

## Quick triggers

- Which cpq-agent-* should handle this task?
- Implement / debug a functional agent or deal-desk workflow
- Wire gateway tools for pricing, quotes, or configuration
- Quote accepted → deal-desk not firing (ai-service side)
- OPENCRM_AI_AGENTS_ENABLED / orchestration 503

## Three routing lanes

| Lane | Use | Target |
|------|-----|--------|
| Workflow | Multi-step deal desk | `POST .../orchestrations/deal-desk/invoke` → 037 |
| Functional agent | Single agent logic | `POST .../agents/cpq-agent-NNN/invoke` |
| Cross-service | CRM/quote/events | **`cpq-lifecycle-tracer`** subagent |

## Reference files (read before deep work)

| File | Contents |
|------|----------|
| [agent-registry.md](agent-registry.md) | All 37 PRD agents + extended A–F agents + deal-desk cluster |
| [playbooks.md](playbooks.md) | Quote creation, renewal, amendment, deal-desk sequences |
| `agents/HANDOFFS.md` | Full handoff matrix and mermaid flow |
| `agent-prd.md` §3–§4 | Agent index and orchestration flows |

## Enable agents locally

```bash
export OPENCRM_AI_AGENTS_ENABLED=true
export AZURE_OPENAI_API_KEY=...
export AZURE_OPENAI_ENDPOINT=...
export OPENCRM_GATEWAY_BASE_URL=http://localhost:8080
```

## HTTP invoke paths

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/ai/agents` | List all registered CpqAgent beans |
| `POST /api/v1/ai/agents/{agentId}/invoke` | Single functional agent |
| `POST /api/v1/ai/agents/{agentId}/stream` | SSE stream |
| `GET /api/v1/ai/orchestrations` | List workflows |
| `POST /api/v1/ai/orchestrations/{workflowId}/invoke` | Workflow (e.g. `deal-desk`) |

Port **8090** (gateway: `/api/v1/ai/**`).

## Critical agent IDs

| Role | ID |
|------|-----|
| Entry point | cpq-agent-001 |
| Margin guardrail | cpq-agent-011 |
| Primary orchestrator | cpq-agent-037 |
| Deal desk cluster | cpq-agent-01, 02, 03 |
| Approval chain | cpq-agent-024 → 025 → 037 |

## Key source locations

| Area | Path |
|------|------|
| Agent router | `ai/router/AgentRouter.java` |
| Workflow orchestrator | `ai/orchestration/DefaultWorkflowOrchestrator.java` |
| Event listener | `ai/listener/QuoteAcceptedListener.java` |
| Domain agent base | `ai/agent/AbstractCpqAgent.java` |
| Gateway tools | `ai/registry/CpqAgentTools.java` |

## Targeted tests

```bash
mvn -f backend/pom.xml -pl services/ai-service test -Dtest=CpqAgentToolsTest
mvn -pl backend/services/ai-service test -Dtest=AgentRouterFeatureFlagTest
mvn -pl backend/services/ai-service test -Dtest=*AgentTest
```

## Integrated Cursor agents

| Agent | When to delegate |
|-------|------------------|
| `cpq-lifecycle-tracer` | CRM deal WON, QuoteAcceptedEvent, LeadToDealLifecycleTest, non-ai-service lifecycle |
| `spring-ai-orchestration-specialist` | ai-service + functional agent routing (this hub) |

## GitNexus requirement

1. `gitnexus_impact({ target: "SymbolName", direction: "upstream" })` before edits
2. Warn on HIGH/CRITICAL risk
3. `gitnexus_detect_changes()` before commit
