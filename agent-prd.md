# CPQ AI Agent Product Requirements Document

## 1. Document Control

| Version | Date       | Author              | Description |
|---------|------------|---------------------|-------------|
| 1.0     | 2026-02-07 | Product + Platform  | Initial version: 37 agent definitions with full PRD specifications |

### 1.1 References

| Document | Location | Relevance |
|----------|----------|-----------|
| Price-Drools Service PRD | `cpq-pricing-prd.md` | Pricing domain model, API contracts, predecessor AI agents (Section 8) |
| AI Agent Development Guide | `AGENT.md` | Agent-to-phase mapping, development conventions |
| Modernization Plan | `MODERNIZATION_PLAN.md` | Architecture roadmap, AI service design |

### 1.2 Executive Summary

This document defines **37 AI agents** for the Open CRM/CPQ platform, organized into 10 functional groups covering the complete Configure-Price-Quote lifecycle. Every agent operates under a **human-in-the-loop** governance model and maintains **bidirectional integration** with ERP systems (costs, margins, inventory) and external CRM/CPQ platforms (customer data, quotes, pipeline).

The agents are built on the existing microservices architecture (CRM, Pricing, Product, Quote services) with 107 REST endpoints, 8 Drools pricing rules, and a PostgreSQL/Redis infrastructure. They extend the 4 predecessor agents defined in `cpq-pricing-prd.md` Section 8 (Rule Optimization Agent, Deal Desk Copilot, Pricing Anomaly Detector, Renewal Intelligence Agent) into a comprehensive agent ecosystem.

Each agent has a detailed individual PRD in the `agents/` directory. This parent document provides the architecture overview, agent index, inter-agent communication patterns, integration architecture, non-functional requirements, and rollout plan.

---

## 2. Agent Architecture Overview

### 2.1 Human-in-the-Loop Model

All 37 agents operate under one of three autonomy tiers. No agent may make commercial-impacting changes without human approval.

| Tier | Description | Examples | Human Interaction |
|------|-------------|----------|-------------------|
| **Advisory** | Read-only analysis; produces suggestions but never modifies data | Win-Probability Analysis, Profitability Alert, Competitive Intelligence | User reviews suggestions and decides whether to act |
| **Semi-autonomous** | Can make non-commercial changes (defaults, ordering, data enrichment) but requires approval for price/discount/state changes | Bundle Assembly, Option Configuration, Deal Desk Orchestration | User approves commercial changes; non-commercial changes auto-applied |
| **Automated with constraints** | Operates within strict guardrails; auto-applies standard rules but escalates exceptions | List Price Application, Volume Discount, Margin Enforcement | Automatic within policy; exceptions require human approval |

### 2.2 Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| Agent Orchestration | Spring AI `ChatClient` | LLM integration, tool function calling |
| Agent SDK | Anthropic Claude Agent SDK | Multi-turn agent workflows, conversation management |
| LLM Provider | Azure OpenAI (primary), Claude (agent workflows) | Natural language processing, analysis, recommendations |
| Agent State | Redis 7 | Session state, conversation context, agent coordination |
| Rules Engine | Drools 8.44.0 | Deterministic pricing rule execution |
| Tool Registry | Spring beans with `@Description` | Agent tool discovery and invocation |
| Agent Endpoints | Spring Cloud Gateway | API routing at `/api/v1/ai/agents/**` |
| Audit Storage | PostgreSQL `ai.agent_audit_log` | Immutable agent action history |
| Vector Store | PostgreSQL pgvector | Semantic search for recommendations |

### 2.3 Security Model

| Control | Implementation |
|---------|----------------|
| Authentication | Agent service accounts with scoped JWT tokens |
| Authorization | RBAC inheritance: agents operate with the invoking user's role (ADMIN, DEAL_DESK, SALES_REP, SUPPORT_REP) |
| Agent Permissions | Each agent declares `requiredRole` -- e.g., Margin Optimization requires DEAL_DESK or ADMIN |
| Data Access | Agents access only endpoints permitted for the invoking user's role |
| Audit Trail | All agent invocations logged to `ai.agent_audit_log` with: `agent_id`, `action`, `input_context`, `suggestion`, `human_decision` (ACCEPTED/REJECTED/MODIFIED), `timestamp`, `latency_ms` |
| Rate Limiting | Per-agent rate limits enforced at gateway (default: 100 req/min per user per agent) |

### 2.4 ERP Adapter Service

A new microservice `erp-adapter-service` (port 8085) normalizes ERP data from SAP, Oracle, NetSuite, or Dynamics 365 into a consistent API consumed by agents.

**Endpoints:**

| Method | Path | Description | Cache TTL |
|--------|------|-------------|-----------|
| GET | `/api/v1/erp/costs/{productExternalId}` | COGS, landed cost, component breakdown, overhead | 1 hour |
| GET | `/api/v1/erp/costs/bulk` | Batch cost lookup (up to 200 products) | 1 hour |
| GET | `/api/v1/erp/inventory/{productExternalId}` | Availability, lead times, warehouse stock | 5 minutes |
| GET | `/api/v1/erp/fx-rates/{baseCurrency}/{targetCurrency}` | Current FX rate with timestamp | 15 minutes |
| GET | `/api/v1/erp/contracts/{customerExternalId}` | Active contracts, entitlements, renewal dates | 30 minutes |
| GET | `/api/v1/erp/purchase-history/{customerExternalId}` | Historical purchases across all channels | 1 hour |
| POST | `/api/v1/erp/orders` | Create order from accepted quote | N/A |
| POST | `/api/v1/erp/margin-report` | Submit margin report for financial reconciliation | N/A |

All responses use the standard `ApiResponse<T>` wrapper. Data is cached in Redis with configurable TTL per endpoint.

### 2.5 External CRM/CPQ Integration Pattern

| Direction | Data Flow | Mechanism |
|-----------|-----------|-----------|
| **Inbound from external CRM** | Customer master data synced to `pricing.customers` | `POST /api/v1/pricing-customers` |
| **Inbound from external CRM** | Pipeline data synced to `crm.deals` | `POST /api/v1/deals` |
| **Inbound from external CRM** | Contact data synced to `crm.contacts` | `POST /api/v1/contacts` |
| **Outbound to external CRM** | Quote proposals and pricing decisions | Webhook/event push |
| **Outbound to external CRM** | Approval outcomes and contract status | Redis Pub/Sub -> connector service |
| **Outbound to ERP** | Accepted quote triggers order creation | `POST /api/v1/erp/orders` via `quote:accepted` event |

### 2.6 Agent Communication Model

Agents communicate through three patterns:

**1. Direct Invocation**: One agent calls another's skill function. Example: Bundle Assembly Agent invokes Dynamic Bundle Pricing Agent's `calculate-bundle-price` skill.

**2. Event-Driven Activation**: Agents subscribe to Redis Pub/Sub channels for domain events.

| Channel | Event | Subscribing Agents |
|---------|-------|-------------------|
| `quote:accepted` (existing) | Quote accepted by customer | cpq-agent-028, cpq-agent-030 |
| `quote:status-changed` (new) | Any quote state transition | cpq-agent-036, cpq-agent-037 |
| `product:updated` (new) | Product catalog change | cpq-agent-035 |
| `customer:churn-score-changed` (new) | Customer risk reassessment | cpq-agent-028 |
| `pricing:executed` (new) | Pricing calculation completed | cpq-agent-012, cpq-agent-036 |

**3. Orchestrator Pattern**: For complex workflows, an orchestrator agent coordinates multiple agents in sequence. The Deal Desk Workflow Orchestration Agent (cpq-agent-037) is the primary orchestrator.

### 2.7 Agent API Gateway Routing

All agent endpoints are routed via the existing gateway service at port 8080:

| Route | Target | Description |
|-------|--------|-------------|
| `/api/v1/ai/agents` | ai-service | List available agents |
| `/api/v1/ai/agents/{agentId}/invoke` | ai-service | Invoke agent synchronously |
| `/api/v1/ai/agents/{agentId}/stream` | ai-service | Invoke agent with SSE streaming |
| `/api/v1/ai/agents/{agentId}/status` | ai-service | Check agent invocation status |
| `/api/v1/ai/agents/{agentId}/history` | ai-service | Agent interaction history |

---

## 3. Agent Index

Each agent has a detailed individual PRD in the `agents/` directory. The table below provides a summary of all 37 agents with links to their individual PRD files.

### Group 1: Product Configuration (Quote Creation)

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-001 | Guided Product Selection Agent | [agents/cpq-agent-001.md](agents/cpq-agent-001.md) | Advisory | Step-by-step product search and recommendation during quote creation |
| cpq-agent-002 | Option & Feature Configuration Agent | [agents/cpq-agent-002.md](agents/cpq-agent-002.md) | Semi-autonomous | Selecting configurable options, attributes, and features with rule validation |
| cpq-agent-003 | Bundle Assembly Agent | [agents/cpq-agent-003.md](agents/cpq-agent-003.md) | Semi-autonomous | Creating and modifying product bundles with constraint enforcement |

### Group 2: Pricing - Base

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-004 | List Price Application Agent | [agents/cpq-agent-004.md](agents/cpq-agent-004.md) | Automated | Applying base list prices from regional/default price books |
| cpq-agent-005 | Pricing Method Agent | [agents/cpq-agent-005.md](agents/cpq-agent-005.md) | Automated | Applying block, percent-of-total, cost-plus, and custom pricing methods |
| cpq-agent-006 | Markup & Cost Integration Agent | [agents/cpq-agent-006.md](agents/cpq-agent-006.md) | Semi-autonomous | Real-time ERP cost pull and markup/margin calculations |

### Group 3: Pricing - Discounts

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-007 | Volume Discount Agent | [agents/cpq-agent-007.md](agents/cpq-agent-007.md) | Automated | Tiered/volume-based discount schedules via Drools rules |
| cpq-agent-008 | Customer Segment Discount Agent | [agents/cpq-agent-008.md](agents/cpq-agent-008.md) | Automated | Segment-specific (industry, size, region) pricing adjustments |
| cpq-agent-009 | Promotional Campaign Agent | [agents/cpq-agent-009.md](agents/cpq-agent-009.md) | Semi-autonomous | Time-limited promotions and campaign-based pricing |
| cpq-agent-010 | Deal-Specific Discount Agent | [agents/cpq-agent-010.md](agents/cpq-agent-010.md) | Advisory | Manual/negotiated one-off discount suggestions |

### Group 4: Pricing - Guardrails

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-011 | Minimum Margin Enforcement Agent | [agents/cpq-agent-011.md](agents/cpq-agent-011.md) | Automated | Hard margin floor checks using ERP costs and Drools rules |
| cpq-agent-012 | Profitability Alert Agent | [agents/cpq-agent-012.md](agents/cpq-agent-012.md) | Advisory | Proactive warnings on low-margin configurations |

### Group 5: Product Catalog Management

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-013 | Product SKU Management Agent | [agents/cpq-agent-013.md](agents/cpq-agent-013.md) | Advisory | Creating/maintaining individual SKUs and master data |
| cpq-agent-014 | Attribute & Feature Catalog Agent | [agents/cpq-agent-014.md](agents/cpq-agent-014.md) | Advisory | Managing configurable attributes and option sets |
| cpq-agent-015 | Compatibility Rules Agent | [agents/cpq-agent-015.md](agents/cpq-agent-015.md) | Semi-autonomous | Enforcing technical/product compatibility constraints |
| cpq-agent-016 | Validation Rules Agent | [agents/cpq-agent-016.md](agents/cpq-agent-016.md) | Automated | Error checking and validation during configuration |

### Group 6: Intelligent Selling

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-017 | Recommendation & Upsell Agent | [agents/cpq-agent-017.md](agents/cpq-agent-017.md) | Advisory | Cross-sell and upsell suggestions based on customer and deal context |
| cpq-agent-018 | Dynamic Bundle Pricing Agent | [agents/cpq-agent-018.md](agents/cpq-agent-018.md) | Semi-autonomous | Pricing adjustments when bundles are configured |

### Group 7: Subscription & Licensing

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-019 | SaaS Tier Pricing Agent | [agents/cpq-agent-019.md](agents/cpq-agent-019.md) | Automated | Tier-based subscription pricing and feature gating |
| cpq-agent-020 | Term & Co-Terming Agent | [agents/cpq-agent-020.md](agents/cpq-agent-020.md) | Semi-autonomous | Subscription term alignment and proration |
| cpq-agent-021 | Usage-Based Pricing Agent | [agents/cpq-agent-021.md](agents/cpq-agent-021.md) | Automated | Metered/usage pricing and overage calculations |
| cpq-agent-022 | Perpetual License Agent | [agents/cpq-agent-022.md](agents/cpq-agent-022.md) | Automated | On-prem one-time license pricing and core pack rounding |
| cpq-agent-023 | Maintenance & Support Fee Agent | [agents/cpq-agent-023.md](agents/cpq-agent-023.md) | Semi-autonomous | On-prem maintenance renewals and support tier pricing |

### Group 8: Governance & Approvals

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-024 | Multi-Level Approval Routing Agent | [agents/cpq-agent-024.md](agents/cpq-agent-024.md) | Automated | Routing quotes for approval based on amount, discount, or margin |
| cpq-agent-025 | Exception Handling Agent | [agents/cpq-agent-025.md](agents/cpq-agent-025.md) | Advisory | Managing approval exceptions and escalations |

### Group 9: Analytics & Intelligence

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-026 | Win-Probability Analysis Agent | [agents/cpq-agent-026.md](agents/cpq-agent-026.md) | Advisory | Predicting deal win rates based on pricing and configuration |
| cpq-agent-027 | Margin Optimization Agent | [agents/cpq-agent-027.md](agents/cpq-agent-027.md) | Advisory | Suggesting price adjustments for better margins |
| cpq-agent-028 | Renewal Opportunity Detection Agent | [agents/cpq-agent-028.md](agents/cpq-agent-028.md) | Advisory | Identifying upcoming renewals and assessing risk |
| cpq-agent-029 | Upgrade/Downgrade Path Agent | [agents/cpq-agent-029.md](agents/cpq-agent-029.md) | Advisory | Suggesting subscription changes at renewal |

### Group 10: Integration & Operations

| Agent ID | Name | PRD File | Autonomy | Purpose |
|----------|------|----------|----------|---------|
| cpq-agent-030 | Contract Lifecycle Management Agent | [agents/cpq-agent-030.md](agents/cpq-agent-030.md) | Semi-autonomous | Managing contract lifecycle from acceptance through renewal |
| cpq-agent-031 | Quote Document Generation Agent | [agents/cpq-agent-031.md](agents/cpq-agent-031.md) | Semi-autonomous | Generating and customizing quote proposal PDFs |
| cpq-agent-032 | Currency & FX Management Agent | [agents/cpq-agent-032.md](agents/cpq-agent-032.md) | Automated | Multi-currency pricing and FX rate management |
| cpq-agent-033 | Tax Calculation Integration Agent | [agents/cpq-agent-033.md](agents/cpq-agent-033.md) | Automated | Tax rate application via external tax services |
| cpq-agent-034 | Competitive Pricing Intelligence Agent | [agents/cpq-agent-034.md](agents/cpq-agent-034.md) | Advisory | Competitive pricing analysis and positioning |
| cpq-agent-035 | Price Book Management Agent | [agents/cpq-agent-035.md](agents/cpq-agent-035.md) | Advisory | Managing regional/segment price books and entries |
| cpq-agent-036 | Audit & Compliance Reporting Agent | [agents/cpq-agent-036.md](agents/cpq-agent-036.md) | Automated | Compliance reports from pricing audit logs |
| cpq-agent-037 | Deal Desk Workflow Orchestration Agent | [agents/cpq-agent-037.md](agents/cpq-agent-037.md) | Semi-autonomous | End-to-end deal desk workflow coordination |

---

## 4. Inter-Agent Communication Patterns

### 4.1 Quote Creation Orchestration Flow

```
User starts quote
  -> cpq-agent-001 (Guided Product Selection)
    -> cpq-agent-002 (Option & Feature Configuration)
      -> cpq-agent-015 (Compatibility Rules - validate)
      -> cpq-agent-016 (Validation Rules - validate)
    -> cpq-agent-003 (Bundle Assembly) [if bundle]
      -> cpq-agent-018 (Dynamic Bundle Pricing)
    -> cpq-agent-004 (List Price Application)
      -> cpq-agent-005 (Pricing Method)
      -> cpq-agent-006 (Markup & Cost Integration)
    -> cpq-agent-007 (Volume Discount)
    -> cpq-agent-008 (Customer Segment Discount)
    -> cpq-agent-009 (Promotional Campaign) [if active]
    -> cpq-agent-010 (Deal-Specific Discount) [if requested]
    -> cpq-agent-011 (Minimum Margin Enforcement)
    -> cpq-agent-012 (Profitability Alert)
    -> cpq-agent-032 (Currency & FX) [if non-base currency]
    -> cpq-agent-033 (Tax Calculation)
    -> cpq-agent-017 (Recommendation & Upsell) [advisory sidebar]
    -> cpq-agent-026 (Win-Probability) [advisory sidebar]
  -> cpq-agent-024 (Multi-Level Approval Routing) [on submit]
    -> cpq-agent-025 (Exception Handling) [if needed]
    -> cpq-agent-037 (Deal Desk Orchestration)
  -> cpq-agent-031 (Quote Document Generation) [on approval]
  -> cpq-agent-030 (Contract Lifecycle) [on acceptance]
```

### 4.2 Renewal Flow

```
cpq-agent-028 (Renewal Opportunity Detection) [scheduled scan]
  -> cpq-agent-029 (Upgrade/Downgrade Path)
  -> cpq-agent-019/022/023 (Tier/License/Maintenance pricing)
  -> cpq-agent-020 (Term & Co-Terming)
  -> Re-enter quote creation flow at cpq-agent-004
```

### 4.3 Amendment Flow

```
Rep initiates amendment on accepted quote
  -> Quote cloned via POST /api/v1/quotes/{id}/clone
  -> cpq-agent-020 (Term & Co-Terming - proration calculation)
  -> cpq-agent-002 (Option & Feature Configuration - modify config)
  -> Re-enter pricing flow at cpq-agent-004
  -> cpq-agent-011 (Margin Enforcement on amended quote)
  -> cpq-agent-024 (Approval Routing)
```

### 4.4 Event-Driven Activations

| Channel | Publisher | Subscribing Agents | Action |
|---------|----------|-------------------|--------|
| `quote:accepted` (existing) | quote-service | cpq-agent-028, cpq-agent-030 | Register for renewal tracking; create contract |
| `quote:status-changed` (new) | quote-service | cpq-agent-036, cpq-agent-037 | Audit log; workflow tracking |
| `product:updated` (new) | product-service | cpq-agent-035 | Price book gap analysis |
| `customer:churn-score-changed` (new) | pricing-service | cpq-agent-028 | Renewal risk reassessment |
| `pricing:executed` (new) | pricing-service | cpq-agent-012, cpq-agent-036 | Profitability check; audit |

### 4.5 Conflict Resolution Between Agents

When multiple discount agents apply (volume + segment + promotional), conflict resolution follows the existing rule priority model from `cpq-pricing-prd.md` Section 6.4:

1. **Priority band ordering**: Policy guardrails (900-1000) > Configuration (700-899) > Line pricing (500-699) > Quote adjustments (300-499) > Approval routing (200-299)
2. **Within same priority**: Highest absolute commercial impact wins
3. **Tie-break**: Lexical rule ID ordering
4. **Stacking policy**: Controlled by explicit stacking flags -- discounts may compound or use best-of
5. **Margin enforcement override**: cpq-agent-011 (salience 930) always has final authority

---

## 5. Integration Architecture

### 5.1 ERP Adapter Service (erp-adapter-service, port 8085)

```
┌─────────────────────────────────────────────────────────────┐
│                 ERP Adapter Service (8085)                    │
│           Normalizes ERP data for agent consumption          │
├──────────┬──────────┬──────────┬──────────┬────────────────┤
│  Costs   │Inventory │ FX Rates │Contracts │Purchase History│
│  Module  │  Module  │  Module  │  Module  │    Module      │
└────┬─────┴────┬─────┴────┬─────┴────┬─────┴───────┬────────┘
     │          │          │          │             │
     ▼          ▼          ▼          ▼             ▼
┌─────────┐ ┌────────┐ ┌───────┐ ┌────────┐ ┌──────────┐
│   SAP   │ │ Oracle │ │  FX   │ │  SAP   │ │   ERP    │
│  S/4HANA│ │NetSuite│ │Service│ │ SD/MM  │ │  Sales   │
└─────────┘ └────────┘ └───────┘ └────────┘ └──────────┘
```

**Implementation Pattern:**
- Follows existing Spring Boot service patterns (BaseEntity, ApiResponse, MapStruct mappers)
- Redis caching with configurable TTL per data type
- Circuit breakers (Resilience4j) on all ERP connections
- Flyway migrations for adapter-specific tables in `erp` schema

### 5.2 Agent Service Architecture

```
┌─────────────────────────────────────────────────┐
│              Gateway Service (8080)               │
│         /api/v1/ai/agents/** -> ai-service       │
└────────────────────┬────────────────────────────┘
                     │
              ┌──────▼──────┐
              │  AI Service  │
              │    (8086)    │
              ├──────────────┤
              │ Agent Router │
              │ Tool Registry│
              │ State Manager│
              │ Audit Logger │
              └──┬───┬───┬──┘
                 │   │   │
    ┌────────────┘   │   └────────────┐
    ▼                ▼                ▼
┌────────┐    ┌──────────┐    ┌──────────┐
│ Spring │    │  Claude   │    │  Redis   │
│   AI   │    │Agent SDK  │    │  State   │
│ChatClient│  │Multi-turn │    │  Store   │
└────────┘    └──────────┘    └──────────┘
```

### 5.3 Data Flow: Agent to Backend Services

```
Agent Invocation
  │
  ├── READ Operations (no approval needed)
  │   ├── GET /api/v1/products/*          (product-service)
  │   ├── GET /api/v1/pricing-customers/* (pricing-service)
  │   ├── GET /api/v1/quotes/*            (quote-service)
  │   ├── GET /api/v1/deals/*             (crm-service)
  │   └── GET /api/v1/erp/*              (erp-adapter-service)
  │
  └── WRITE Operations (approval checkpoint)
      │
      ├── [Agent suggests action] ──► [Present to user]
      │                                      │
      │                              [User approves?]
      │                               /           \
      │                            YES              NO
      │                             │                │
      │                    [Execute action]    [Log rejection]
      │                             │
      ├── POST /api/v1/quotes/*/line-items    (add to quote)
      ├── PUT /api/v1/quotes/*                (update quote)
      ├── POST /api/v1/quotes/*/approvals     (submit approval)
      └── POST /api/v1/quotes/*/documents/*   (generate doc)
```

---

## 6. Non-Functional Requirements

### 6.1 Performance Targets

| Agent Tier | Response Time (p95) | Throughput | Notes |
|-----------|-------------------|------------|-------|
| Advisory (read-only) | < 500ms | 200 req/s | Simple data retrieval and analysis |
| Semi-autonomous (with validation) | < 1s | 100 req/s | Includes rule validation |
| Automated (standard rules) | < 200ms | 300 req/s | Cached rules, minimal computation |
| Orchestrator (multi-agent) | < 3s | 50 req/s | Coordinates multiple agent calls |
| LLM-powered analysis | < 5s | 20 req/s | Includes AI model inference with streaming |

### 6.2 Scaling Strategy

- Agents deployed as stateless Spring beans within the AI service (horizontally scalable)
- Agent conversation state stored in Redis (no session affinity required)
- LLM calls use connection pooling with rate limiting per provider
- Circuit breakers on all ERP and external CRM integration points
- Auto-scaling based on agent invocation queue depth

### 6.3 Observability

| Capability | Implementation |
|------------|----------------|
| Agent Audit Log | `ai.agent_audit_log` table: agent_id, action, input_context, suggestion, human_decision, latency_ms |
| Metrics | Micrometer: `cpq.agent.invocations.total`, `cpq.agent.invocations.duration`, `cpq.agent.suggestions.acceptance_rate` |
| Tracing | OpenTelemetry: correlation ID from gateway through agent chain |
| Dashboards | Agent performance dashboard, suggestion acceptance rates, SLA compliance |
| Alerting | Alert on: agent error rate > 1%, latency p99 > 10s, LLM provider errors |

### 6.4 Audit Requirements

- All agent invocations produce an immutable audit record
- Audit records include: full input context, agent suggestion, human decision (ACCEPTED/REJECTED/MODIFIED), modification details if applicable
- Retention: 7 years (aligned with pricing audit log retention from `cpq-pricing-prd.md`)
- Audit records are queryable by: agent_id, quote_external_id, customer_external_id, date range, human_decision

---

## 7. Rollout Phases

### Phase 1: Foundation + Core Pricing Agents (Months 1-2)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-004 (List Price Application) | Foundational -- initializes pricing waterfall |
| cpq-agent-011 (Minimum Margin Enforcement) | Critical guardrail -- prevents unprofitable deals |
| cpq-agent-024 (Multi-Level Approval Routing) | Governance -- automated approval routing |
| cpq-agent-031 (Quote Document Generation) | Delivery -- PDF proposal generation |

**Prerequisites**: AI service scaffolding, ERP adapter service MVP (cost endpoint only)

### Phase 2: Discount Agents + Guardrails (Months 3-4)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-007 (Volume Discount) | Leverages existing `volume-discount-named-seat.drl` |
| cpq-agent-008 (Customer Segment Discount) | Leverages existing customer segmentation data |
| cpq-agent-010 (Deal-Specific Discount) | High-impact for sales productivity |
| cpq-agent-012 (Profitability Alert) | Early warning before margin enforcement |
| cpq-agent-037 (Deal Desk Orchestration) | Workflow coordination for discount approvals |

### Phase 3: Product Configuration Agents (Months 5-6)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-001 (Guided Product Selection) | Entry point for guided selling |
| cpq-agent-002 (Option & Feature Configuration) | Reduces configuration errors |
| cpq-agent-003 (Bundle Assembly) | Bundle creation and optimization |
| cpq-agent-015 (Compatibility Rules) | Configuration validation |
| cpq-agent-016 (Validation Rules) | Error prevention |

### Phase 4: Subscription & Licensing Agents (Months 7-8)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-019 (SaaS Tier Pricing) | SaaS pricing model coverage |
| cpq-agent-020 (Term & Co-Terming) | Leverages existing `amendment-proration.drl` |
| cpq-agent-021 (Usage-Based Pricing) | Leverages existing `usage-overage-charge.drl` |
| cpq-agent-022 (Perpetual License) | Leverages existing `core-license-pack.drl` |
| cpq-agent-023 (Maintenance & Support Fee) | Leverages existing `perpetual-maintenance.drl` |

### Phase 5: Intelligence & Analytics Agents (Months 9-10)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-017 (Recommendation & Upsell) | Requires historical data from earlier phases |
| cpq-agent-026 (Win-Probability Analysis) | Requires deal outcome data accumulation |
| cpq-agent-027 (Margin Optimization) | Requires margin data from cpq-agent-006 |
| cpq-agent-028 (Renewal Opportunity Detection) | Requires contract data from cpq-agent-030 |
| cpq-agent-029 (Upgrade/Downgrade Path) | Leverages existing `migration-credit.drl` and `renewal-uplift.drl` |
| cpq-agent-034 (Competitive Pricing Intelligence) | Requires competitive data accumulation |

### Phase 6: Integration & Governance Agents (Months 11-12)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-030 (Contract Lifecycle Management) | Requires ERP adapter maturity |
| cpq-agent-032 (Currency & FX Management) | Requires ERP FX rate endpoint |
| cpq-agent-033 (Tax Calculation Integration) | Requires external tax service integration |
| cpq-agent-035 (Price Book Management) | Requires price book CRUD endpoints |
| cpq-agent-036 (Audit & Compliance Reporting) | Requires accumulated audit data |
| cpq-agent-025 (Exception Handling) | Requires mature approval workflow |

### Phase 7: Catalog Management Agents (Months 13+)

| Agent | Rationale |
|-------|-----------|
| cpq-agent-005 (Pricing Method) | Advanced pricing method support |
| cpq-agent-006 (Markup & Cost Integration) | Deep ERP integration maturity |
| cpq-agent-009 (Promotional Campaign) | Campaign management infrastructure |
| cpq-agent-013 (Product SKU Management) | Catalog governance tooling |
| cpq-agent-014 (Attribute & Feature Catalog) | Attribute management maturity |
| cpq-agent-018 (Dynamic Bundle Pricing) | Dynamic pricing model maturity |

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **Advisory** | Agent autonomy tier: read-only analysis, produces suggestions, never modifies data |
| **Automated with constraints** | Agent autonomy tier: auto-applies within guardrails, escalates exceptions |
| **ERP Adapter Service** | New microservice (port 8085) normalizing ERP data for agent consumption |
| **Human-in-the-Loop** | Governance model requiring human approval for commercial-impacting agent actions |
| **Semi-autonomous** | Agent autonomy tier: auto-applies non-commercial changes, requires approval for commercial changes |
| **Skill** | A specific capability of an agent, implemented as a tool function callable by the LLM |
| **Tool Registry** | Spring bean registry where agent skills are registered for LLM discovery |
| See also: `cpq-pricing-prd.md` Section 14 for pricing-specific glossary terms |
