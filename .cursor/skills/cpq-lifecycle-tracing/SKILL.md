---
name: cpq-lifecycle-tracing
description: >-
  Trace and validate Open CRM/CPQ revenue lifecycle flows across CRM, quote,
  pricing, configuration, gateway, and AI services. Use when quote acceptance
  fails to close deals, LeadToDealLifecycleTest fails, cross-service events
  break, or Phase 6 integration hardening is needed.
disable-model-invocation: true
---

# CPQ Lifecycle Tracing

Pair with the **`cpq-lifecycle-tracer`** subagent for isolated deep dives.

## Quick triggers

- Quote accepted but deal not WON
- Lead conversion or stage advancement regression
- Redis / QuoteAccepted event processing errors
- Gateway 502/504 on `/api/v1/quotes` or `/api/v1/crm`
- AI deal-desk workflow not firing on `quote:accepted`

## Canonical documents

- `cpq-pricing-prd.md` — §7 CPQ lifecycle, §7.2 stage-to-API mapping
- `agent-prd.md` — §4 agent orchestration flows
- `agents/deal-desk-workflow-orchestration.md` — cpq-agent-037 workflow

## Targeted verification commands

```bash
# CRM crown-jewel lifecycle
mvn -f backend/pom.xml -pl services/crm-service test -Dtest=LeadToDealLifecycleTest

# Published event contract (QuoteAccepted v1)
mvn -f backend/pom.xml -pl libs/common test -Dtest=PublishedEventContractTest

# CRM integration slice
mvn -pl backend/services/crm-service test -Dtest="*IntegrationTest"
```

Adjust `-pl` paths if running from `backend/` directory.

## Symbols to inspect by symptom

| Symptom | Start here |
|---------|------------|
| Deal not WON after accept | `QuoteAcceptedEventListener`, `QuoteAcceptedDealProcessor` |
| Duplicate deal updates | `QuoteAcceptedIdempotency` |
| AI not orchestrating | **`spring-ai-orchestration-specialist`** (ai-service); `QuoteAcceptedListener`, `DefaultWorkflowOrchestrator` |
| Quote stuck in state | quote-service transition guards / approval workflow |
| Pricing mismatch | `PricingExecutionService`, Drools rule files |
| Route not found | `gateway-service/.../application.yml` |

## Integrated Cursor agents

| Agent | Scope |
|-------|-------|
| `cpq-lifecycle-tracer` | Cross-service revenue path (this skill) |
| `spring-ai-orchestration-specialist` | ai-service agents, deal-desk workflow, cpq-agent-* routing |

## GitNexus requirement

Before modifying any lifecycle symbol:

1. `gitnexus_impact({ target: "SymbolName", direction: "upstream" })`
2. Warn on HIGH/CRITICAL risk
3. After changes: `gitnexus_detect_changes()`
