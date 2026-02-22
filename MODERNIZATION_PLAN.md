# Open CRM/CPQ Modernization Plan

## Executive Summary

This document outlines a comprehensive greenfield rebuild of the existing Frappe-based CRM into a modern, enterprise-grade CRM/CPQ platform using:

- **Backend**: Spring Boot 3.2+ / Spring AI / Spring Cloud
- **Frontend**: Angular 17+ / TypeScript / Tailwind CSS
- **Architecture**: Microservices + Microsites
- **Rules Engine**: OpenL Tablets + Drools
- **AI Agents**: Spring AI + Azure OpenAI + Anthropic Claude Agent SDK
- **Deployment**: Microsoft Azure (AKS)

---

## 1. Current State Analysis

### Existing Technology Stack
| Layer | Current Technology |
|-------|-------------------|
| Frontend | Vue 3, Tailwind CSS, Pinia, Vite |
| Backend | Frappe Framework (Python) |
| Database | MariaDB 10.6, Redis |
| Integrations | Twilio (VoIP), WhatsApp, Email |
| Infrastructure | Docker, GitHub Actions |

### Existing Capabilities
- Lead Management (25+ doctypes)
- Deal/Opportunity Tracking
- Contact & Organization Management
- Email/Call/WhatsApp Communication
- Task Management
- SLA Tracking
- Custom Views & Kanban Boards

### Migration Scope
- **153 Vue components** → Angular components
- **11 Python API modules** → Spring Boot services
- **25+ Frappe doctypes** → JPA entities
- **Twilio/WhatsApp integrations** → Preserve and modernize

---

## 2. Target Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MICROSITES (Angular)                            │
├─────────────────────┬─────────────────────┬─────────────────────────────────┤
│    CRM Portal       │   Customer Portal   │        Admin Console            │
│  (Sales & Service)  │   (Self-Service)    │      (Configuration)            │
└─────────┬───────────┴─────────┬───────────┴──────────────┬──────────────────┘
          │                     │                          │
          └─────────────────────┼──────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────────────────────┐
│                     Azure API Management (Gateway)                            │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────────────────────┐
│                    Spring Cloud Gateway (Internal)                            │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │
    ┌───────────────────────────┼───────────────────────────┐
    │                           │                           │
┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐
│Identity│ │ Lead  │ │ Deal  │ │Contact│ │ CPQ   │ │  AI   │
│Service │ │Service│ │Service│ │Service│ │Service│ │Agents │
└───┬───┘ └───┬───┘ └───┬───┘ └───┬───┘ └───┬───┘ └───┬───┘
    │         │         │         │         │         │
┌───▼─────────▼─────────▼─────────▼─────────▼─────────▼───┐
│                    Apache Kafka / Azure Service Bus      │
│                      (Event-Driven Messaging)            │
└───┬─────────┬─────────┬─────────┬─────────┬─────────┬───┘
    │         │         │         │         │         │
┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐ ┌───▼───┐
│Communi│ │Activity│ │Notifi-│ │Rules  │ │Analyt-│ │Search │
│cation │ │Service │ │cation │ │Engine │ │ics    │ │Service│
└───────┘ └────────┘ └───────┘ └───────┘ └───────┘ └───────┘
```

### 2.2 Microservices Breakdown

| Service | Responsibility | Database | Key Technologies |
|---------|---------------|----------|------------------|
| **Identity Service** | Authentication, authorization, user management | PostgreSQL | Spring Security, Azure AD B2C, OAuth2/OIDC |
| **Lead Service** | Lead lifecycle, scoring, assignment, conversion | PostgreSQL | Spring Data JPA, Rules Engine integration |
| **Deal Service** | Opportunities, pipeline stages, forecasting | PostgreSQL | Spring Data JPA, Event sourcing |
| **Contact Service** | Contacts, organizations, relationships, customer 360 | PostgreSQL | Spring Data JPA, Elasticsearch |
| **Communication Service** | Email, calls, WhatsApp, templates | PostgreSQL | Twilio SDK, SendGrid, WhatsApp Business API |
| **Activity Service** | Tasks, notes, audit trail, timeline | PostgreSQL | Event sourcing, temporal tables |
| **CPQ Service** | Products, configuration, pricing, quotes | PostgreSQL | Rules Engine, PDF generation |
| **Rules Engine Service** | Business rules execution, versioning | PostgreSQL | OpenL Tablets, Drools |
| **AI Agent Service** | LLM orchestration, agent workflows | PostgreSQL + Vector DB | Spring AI, Azure OpenAI, Claude SDK |
| **Notification Service** | Push, email, in-app notifications | Redis + PostgreSQL | WebSockets, Firebase, SendGrid |
| **Analytics Service** | Reporting, dashboards, predictions | PostgreSQL + ClickHouse | Apache Superset, ML models |
| **Search Service** | Full-text search, entity indexing | Elasticsearch | Spring Data Elasticsearch |
| **Gateway Service** | API routing, rate limiting, auth | Redis | Spring Cloud Gateway |

### 2.3 Microsite Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Nx Monorepo Structure                     │
├─────────────────────────────────────────────────────────────┤
│  apps/                                                       │
│  ├── crm-portal/          # Main CRM application            │
│  ├── customer-portal/     # Customer self-service           │
│  └── admin-console/       # System administration           │
│                                                              │
│  libs/                                                       │
│  ├── shared/                                                 │
│  │   ├── ui/              # Shared UI components            │
│  │   ├── data-access/     # API clients, state management   │
│  │   ├── utils/           # Common utilities                │
│  │   └── models/          # TypeScript interfaces           │
│  ├── crm/                                                    │
│  │   ├── leads/           # Lead feature library            │
│  │   ├── deals/           # Deal feature library            │
│  │   ├── contacts/        # Contact feature library         │
│  │   └── cpq/             # CPQ feature library             │
│  └── ai/                                                     │
│      ├── copilot/         # Sales copilot components        │
│      └── chatbot/         # Customer chatbot widget         │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Technology Stack Details

### 3.1 Backend Stack

```yaml
Core Framework:
  - Java 21 (LTS)
  - Spring Boot 3.2+
  - Spring Cloud 2023.x

Spring Modules:
  - Spring Data JPA (PostgreSQL)
  - Spring Security (OAuth2 Resource Server)
  - Spring AI (LLM integration)
  - Spring Cloud Gateway (API Gateway)
  - Spring Cloud Config (Centralized configuration)
  - Spring Cloud Circuit Breaker (Resilience4j)
  - Spring Cloud Stream (Kafka/Service Bus bindings)

Data & Messaging:
  - PostgreSQL 16 (Primary database)
  - Redis 7 (Caching, sessions)
  - Apache Kafka / Azure Service Bus (Event streaming)
  - Elasticsearch 8 (Search)

AI & Rules:
  - Spring AI 1.0+
  - Azure OpenAI Service (GPT-4)
  - Anthropic Claude SDK (Agents)
  - OpenL Tablets 5.x (Business rules)
  - Drools 8.x (Complex rules)

Observability:
  - Micrometer (Metrics)
  - OpenTelemetry (Distributed tracing)
  - Azure Monitor / Application Insights

Build & Deploy:
  - Gradle 8.x
  - Docker
  - Kubernetes (AKS)
  - Helm charts
```

### 3.2 Frontend Stack

```yaml
Core Framework:
  - Angular 17+ (Standalone components)
  - TypeScript 5.3+
  - RxJS 7.x

Build & Tooling:
  - Nx 18+ (Monorepo)
  - Vite (Build tool)
  - ESLint + Prettier

State Management:
  - NgRx 17+ (Global state)
  - NgRx Component Store (Local state)
  - NgRx Effects (Side effects)

UI & Styling:
  - Tailwind CSS 3.4+
  - Angular CDK (Accessibility, overlays)
  - PrimeNG or Spartan UI (Component library)
  - Lucide Icons

Data & Communication:
  - Angular HttpClient (REST)
  - Apollo Angular (GraphQL - optional)
  - Socket.io-client (Real-time)

Testing:
  - Jest (Unit tests)
  - Cypress (E2E tests)
  - Storybook (Component documentation)
```

### 3.3 Azure Infrastructure

```yaml
Compute:
  - Azure Kubernetes Service (AKS)
  - Azure Container Registry (ACR)

Data:
  - Azure Database for PostgreSQL (Flexible Server)
  - Azure Cache for Redis
  - Azure Cosmos DB (Optional - global distribution)

Messaging:
  - Azure Service Bus (Enterprise messaging)
  - Azure Event Grid (Event routing)

Identity:
  - Azure AD B2C (Customer identity)
  - Azure AD (Internal users)

AI Services:
  - Azure OpenAI Service
  - Azure AI Search (Vector search)
  - Azure Machine Learning (Custom models)

Storage:
  - Azure Blob Storage (Documents, attachments)
  - Azure CDN (Static assets)

Networking:
  - Azure API Management
  - Azure Front Door (Global load balancing)
  - Azure Private Link (Secure connectivity)

Monitoring:
  - Azure Monitor
  - Application Insights
  - Log Analytics
```

---

## 4. Rules Engine Architecture

### 4.1 Dual-Engine Approach

We recommend a **hybrid rules engine architecture** combining:

1. **OpenL Tablets** - For business-user maintained rules
2. **Drools** - For complex programmatic rules

```
┌─────────────────────────────────────────────────────────────┐
│                    Rules Engine Service                      │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐    ┌─────────────────────┐        │
│  │   OpenL Tablets     │    │      Drools         │        │
│  │   (Excel-based)     │    │   (DRL/DMN-based)   │        │
│  ├─────────────────────┤    ├─────────────────────┤        │
│  │ • Pricing rules     │    │ • Complex workflows │        │
│  │ • Discount matrices │    │ • Multi-step logic  │        │
│  │ • Territory maps    │    │ • Event processing  │        │
│  │ • Commission tables │    │ • Rule chaining     │        │
│  └─────────────────────┘    └─────────────────────┘        │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Rules Orchestration Layer                  ││
│  │  • Rule versioning & hot-reload                         ││
│  │  • A/B testing of rule sets                             ││
│  │  • Audit trail & explainability                         ││
│  │  • Performance monitoring                               ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Rule Categories

| Category | Engine | Description |
|----------|--------|-------------|
| **Product Rules** | OpenL | Product compatibility, valid configurations, constraints |
| **Pricing Rules** | OpenL | Base pricing, volume discounts, promotional pricing |
| **Discount Rules** | OpenL | Customer tier discounts, bundle discounts, approval thresholds |
| **Lead Scoring** | Drools | Multi-factor scoring, behavioral signals, firmographic data |
| **Lead Routing** | Drools | Territory assignment, round-robin, skill-based routing |
| **SLA Rules** | Drools | Response time requirements, escalation triggers |
| **Approval Workflows** | Drools | Multi-level approvals, delegation, exception handling |
| **Commission Rules** | OpenL | Sales commission calculations, split rules |

### 4.3 OpenL Tablets Example (Excel-based)

```
┌──────────────────────────────────────────────────────────────┐
│  PricingRules.xlsx                                           │
├──────────────────────────────────────────────────────────────┤
│  Sheet: VolumePricing                                        │
│  ┌─────────┬──────────┬──────────┬───────────┐              │
│  │ Product │ Min Qty  │ Max Qty  │ Discount% │              │
│  ├─────────┼──────────┼──────────┼───────────┤              │
│  │ SKU-001 │ 1        │ 10       │ 0         │              │
│  │ SKU-001 │ 11       │ 50       │ 5         │              │
│  │ SKU-001 │ 51       │ 100      │ 10        │              │
│  │ SKU-001 │ 101      │ MAX      │ 15        │              │
│  └─────────┴──────────┴──────────┴───────────┘              │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. AI Agent Architecture

### 5.1 Agent Framework

```
┌─────────────────────────────────────────────────────────────┐
│                    AI Agent Service                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              Spring AI + Agent Orchestration            ││
│  └─────────────────────────────────────────────────────────┘│
│                            │                                 │
│       ┌────────────────────┼────────────────────┐           │
│       │                    │                    │           │
│  ┌────▼────┐         ┌─────▼─────┐        ┌────▼────┐      │
│  │  Sales  │         │ Analytics │        │Customer │      │
│  │ Copilot │         │   Agent   │        │ Chatbot │      │
│  └────┬────┘         └─────┬─────┘        └────┬────┘      │
│       │                    │                    │           │
│  ┌────▼────────────────────▼────────────────────▼────┐     │
│  │              Shared Agent Components               │     │
│  │  • Tool Registry (CRM APIs, Search, Calendar)     │     │
│  │  • Memory Manager (Conversation, Long-term)       │     │
│  │  • RAG Pipeline (Azure AI Search + pgvector)      │     │
│  │  • Safety & Guardrails                            │     │
│  └───────────────────────────────────────────────────┘     │
│                            │                                 │
│  ┌─────────────────────────▼───────────────────────────────┐│
│  │                   LLM Providers                         ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ││
│  │  │ Azure OpenAI │  │   Claude     │  │   Fallback   │  ││
│  │  │   (GPT-4o)   │  │ (Agent SDK)  │  │   (Mixtral)  │  ││
│  │  └──────────────┘  └──────────────┘  └──────────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Agent Capabilities

#### Sales Copilot Agent
```yaml
Purpose: AI assistant for sales representatives

Capabilities:
  - Email Drafting: Generate contextual email responses
  - Call Summaries: Transcribe and summarize sales calls
  - Next Best Action: Recommend actions based on deal stage
  - Meeting Prep: Compile relevant info before customer meetings
  - Objection Handling: Suggest responses to common objections
  - Competitive Intel: Surface relevant competitive information

Tools Available:
  - CRM Read (leads, deals, contacts, activities)
  - Email Send (draft mode)
  - Calendar Access
  - Knowledge Base Search
  - Product Catalog Query

Guardrails:
  - No autonomous email sending without approval
  - PII redaction in logs
  - Confidence thresholds for recommendations
```

#### Analytics Agent
```yaml
Purpose: AI-powered analytics and forecasting

Capabilities:
  - Pipeline Analysis: Win probability scoring
  - Forecast Generation: Revenue predictions
  - Anomaly Detection: Unusual patterns in sales data
  - Natural Language Queries: "Show me deals closing this quarter"
  - Report Generation: Automated report creation

Tools Available:
  - Analytics Database Query (read-only)
  - Visualization Generator
  - Export to Excel/PDF

Data Sources:
  - Historical deal data
  - Activity patterns
  - Market signals (optional)
```

#### Customer Chatbot Agent
```yaml
Purpose: Customer self-service and lead qualification

Capabilities:
  - FAQ Answering: Product and service questions
  - Lead Qualification: BANT qualification flow
  - Meeting Scheduling: Calendar integration
  - Support Triage: Route to appropriate team
  - Quote Requests: Initiate CPQ workflow

Tools Available:
  - Knowledge Base Search (RAG)
  - Lead Creation API
  - Calendar Booking
  - Handoff to Human

Guardrails:
  - No access to internal CRM data
  - Escalation paths defined
  - GDPR-compliant data handling
```

### 5.3 Spring AI Integration

```java
@Configuration
public class AgentConfiguration {

    @Bean
    public ChatClient salesCopilotClient(
            AzureOpenAiChatModel chatModel,
            VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
            .defaultSystem("""
                You are a Sales Copilot assistant helping sales
                representatives be more effective. You have access
                to CRM data and can help with email drafting,
                call summaries, and deal recommendations.
                """)
            .defaultFunctions("searchCRM", "draftEmail",
                            "getCalendar", "searchKnowledge")
            .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore),
                new SafetyAdvisor()
            )
            .build();
    }
}
```

### 5.4 Claude Agent SDK Integration

```java
@Service
public class ClaudeAgentService {

    private final AnthropicClient anthropicClient;

    public AgentResponse executeAgentTask(AgentRequest request) {
        // Build agent with tools
        Agent agent = Agent.builder()
            .model("claude-sonnet-4-20250514")
            .tools(List.of(
                new CRMSearchTool(crmService),
                new EmailDraftTool(emailService),
                new CalendarTool(calendarService)
            ))
            .maxTurns(10)
            .build();

        // Execute with conversation history
        return agent.run(
            request.getMessages(),
            request.getSystemPrompt()
        );
    }

    /**
     * Execute with tool results streaming.
     */
    public Flux<AgentStreamEvent> streamAgentTask(AgentRequest request) {
        return Flux.create(sink -> {
            agent.stream(request.getMessages(), event -> {
                sink.next(event);
                if (event.isComplete()) {
                    sink.complete();
                }
            });
        });
    }
}
```

---

## 6. CPQ Module Design

### 6.1 CPQ Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      CPQ Service                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Product Catalog │  │  Configuration  │  │   Pricing   │ │
│  │     Module      │  │     Engine      │  │   Engine    │ │
│  └────────┬────────┘  └────────┬────────┘  └──────┬──────┘ │
│           │                    │                   │        │
│  ┌────────▼────────────────────▼───────────────────▼──────┐ │
│  │                  Quote Manager                          │ │
│  │  • Quote creation & versioning                         │ │
│  │  • Line item management                                │ │
│  │  • Discount application                                │ │
│  │  • Approval workflow integration                       │ │
│  └────────────────────────────────────────────────────────┘ │
│                            │                                 │
│  ┌─────────────────────────▼────────────────────────────┐   │
│  │              Document Generation                      │   │
│  │  • PDF quotes    • Proposals    • Contracts          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 CPQ Data Model

```
┌──────────────────┐     ┌──────────────────┐
│ ProductCategory  │────<│     Product      │
├──────────────────┤     ├──────────────────┤
│ id               │     │ id               │
│ name             │     │ sku              │
│ parent_id        │     │ name             │
└──────────────────┘     │ category_id      │
                         │ base_price       │
                         │ status           │
                         └────────┬─────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
┌───────▼────────┐    ┌───────────▼─────────┐    ┌─────────▼───────┐
│ ProductOption  │    │ ProductAttribute    │    │ ConfigRule      │
├────────────────┤    ├─────────────────────┤    ├─────────────────┤
│ id             │    │ id                  │    │ id              │
│ product_id     │    │ product_id          │    │ product_id      │
│ name           │    │ name                │    │ rule_type       │
│ price_modifier │    │ type                │    │ condition       │
│ is_required    │    │ values[]            │    │ action          │
└────────────────┘    └─────────────────────┘    └─────────────────┘

┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│      Quote       │────<│    QuoteLine     │────<│ QuoteLineOption  │
├──────────────────┤     ├──────────────────┤     ├──────────────────┤
│ id               │     │ id               │     │ id               │
│ quote_number     │     │ quote_id         │     │ quote_line_id    │
│ deal_id          │     │ product_id       │     │ option_id        │
│ status           │     │ quantity         │     │ value            │
│ total_amount     │     │ unit_price       │     │ price_impact     │
│ valid_until      │     │ discount_percent │     └──────────────────┘
│ version          │     │ line_total       │
└──────────────────┘     └──────────────────┘
```

### 6.3 CPQ Rules Examples

```java
// OpenL Tablets - Pricing Rule (defined in Excel)
@RulesTable(name = "VolumePricing")
public interface PricingRules {
    BigDecimal getVolumeDiscount(String productSku, int quantity);
    BigDecimal getCustomerTierDiscount(String customerId, String productCategory);
    boolean requiresApproval(BigDecimal discountPercent, BigDecimal dealValue);
}

// Drools - Complex Configuration Rule
rule "Incompatible Options Check"
when
    $config: ProductConfiguration(
        options contains "OPTION_A",
        options contains "OPTION_B"
    )
then
    $config.addValidationError(
        "Option A and Option B cannot be selected together"
    );
end

rule "Required Accessory"
when
    $config: ProductConfiguration(
        productSku == "ENTERPRISE-SERVER",
        options not contains "SUPPORT-PACKAGE"
    )
then
    $config.addWarning(
        "Enterprise Server requires a Support Package"
    );
end
```

---

## 7. Data Migration Strategy

### 7.1 Entity Mapping

| Frappe Doctype | PostgreSQL Entity | Notes |
|----------------|-------------------|-------|
| CRM Lead | `leads` | Add scoring fields |
| CRM Deal | `deals` | Add forecasting fields |
| CRM Contact | `contacts` | Normalize phone/email |
| CRM Organization | `organizations` | Add hierarchy support |
| CRM Task | `tasks` | Integrate with Activity |
| FCRM Note | `notes` | Merge with activities |
| CRM Call Log | `call_logs` | Preserve Twilio data |
| Communication | `communications` | Unified inbox model |

### 7.2 Migration Approach

```
Phase 1: Schema Creation
├── Create PostgreSQL schemas
├── Define JPA entities
├── Set up Flyway migrations
└── Create staging tables

Phase 2: Data Extraction
├── Export from MariaDB
├── Transform to new schema
├── Validate data integrity
└── Load to staging

Phase 3: Data Validation
├── Record count verification
├── Referential integrity checks
├── Business rule validation
└── User acceptance testing

Phase 4: Cutover
├── Final delta sync
├── Switch DNS/routing
├── Monitor for issues
└── Rollback plan ready
```

---

## 8. MVP: CRM Core API (Weeks 1-6)

The full 16-month roadmap (Section 9) is the long-term plan. **This MVP ships first** - a fully functional REST API for core CRM operations, running locally with Docker, that proves the architecture end-to-end.

### MVP Scope

**In Scope:**
- CRM Service: Contact, Organization, Lead, Deal full CRUD
- Lead lifecycle: create → contact → qualify → convert to deal (or lose)
- Deal pipeline: prospect → qualify → analyze → propose → negotiate → won/lost
- Search, filter, sort, paginate on all entities
- Gateway Service: API routing, basic auth validation
- PostgreSQL with Flyway migrations
- Redis caching for read-heavy endpoints
- JWT authentication (dev-mode issuer for local, Azure AD B2C ready)
- OpenAPI/Swagger UI for all endpoints
- Unit tests (80%+ service layer) + integration tests (TestContainers)
- Docker Compose: all services running with one command

**Out of Scope (deferred to later phases):**
- Azure cloud infrastructure (local Docker only)
- Angular frontend (API-first; Swagger UI is the initial "UI")
- Communication service (Twilio, email, WhatsApp)
- CPQ engine (products, pricing, quotes)
- AI agents
- Elasticsearch (use PostgreSQL `pg_trgm` for MVP search)
- Kafka/Service Bus (synchronous REST between services for MVP)
- Customer portal, admin console

### MVP Architecture

```
┌────────────────────────────────────────────────────────┐
│                    Docker Compose                        │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐                   │
│  │   Swagger UI  │    │   pgAdmin    │                   │
│  │   (auto)      │    │  :5050       │                   │
│  └──────┬───────┘    └──────────────┘                   │
│         │                                                │
│  ┌──────▼───────┐    ┌──────────────┐                   │
│  │   Gateway     │───>│  CRM Service │                   │
│  │   :8080       │    │  :8081       │                   │
│  └──────────────┘    └──────┬───────┘                   │
│                             │                            │
│            ┌────────────────┼────────────────┐          │
│            │                │                │          │
│     ┌──────▼──────┐ ┌──────▼──────┐         │          │
│     │ PostgreSQL  │ │    Redis    │         │          │
│     │ :5432       │ │    :6379   │         │          │
│     └─────────────┘ └─────────────┘         │          │
└────────────────────────────────────────────────────────┘
```

### MVP API Endpoints

```
Contacts:
  POST   /api/v1/contacts           Create contact
  GET    /api/v1/contacts           List contacts (paginated, filterable)
  GET    /api/v1/contacts/{id}      Get contact by ID
  PUT    /api/v1/contacts/{id}      Update contact
  DELETE /api/v1/contacts/{id}      Soft-delete contact

Organizations:
  POST   /api/v1/organizations      Create organization
  GET    /api/v1/organizations      List organizations (paginated)
  GET    /api/v1/organizations/{id} Get organization by ID
  PUT    /api/v1/organizations/{id} Update organization
  DELETE /api/v1/organizations/{id} Soft-delete organization
  GET    /api/v1/organizations/{id}/contacts  List org contacts

Leads:
  POST   /api/v1/leads              Create lead
  GET    /api/v1/leads              List leads (paginated, filterable by status)
  GET    /api/v1/leads/{id}         Get lead by ID
  PUT    /api/v1/leads/{id}         Update lead
  DELETE /api/v1/leads/{id}         Soft-delete lead
  POST   /api/v1/leads/{id}/contact     Mark as contacted
  POST   /api/v1/leads/{id}/qualify     Qualify lead
  POST   /api/v1/leads/{id}/convert     Convert to deal
  POST   /api/v1/leads/{id}/lose        Mark as lost

Deals:
  POST   /api/v1/deals              Create deal
  GET    /api/v1/deals              List deals (paginated, filterable by stage)
  GET    /api/v1/deals/{id}         Get deal by ID
  PUT    /api/v1/deals/{id}         Update deal
  DELETE /api/v1/deals/{id}         Soft-delete deal
  POST   /api/v1/deals/{id}/advance     Advance to next stage
  POST   /api/v1/deals/{id}/win         Mark as won
  POST   /api/v1/deals/{id}/lose        Mark as lost

Dashboard:
  GET    /api/v1/dashboard/pipeline     Deal pipeline summary
  GET    /api/v1/dashboard/lead-stats   Lead status breakdown
```

### MVP Sprint Plan

**Sprint 1 (Week 1-2): Foundation + Contact/Org APIs**
- [ ] Flyway migration scripts (CRM schema)
- [ ] application.yml for crm-service (DB, Redis, OpenAPI)
- [ ] Contact: Repository, DTO, Mapper, Service, Controller
- [ ] Organization: Repository, DTO, Mapper, Service, Controller
- [ ] Global exception handler (@ControllerAdvice)
- [ ] Unit tests for service layer
- [ ] Integration tests with TestContainers
- [ ] Verify: `docker compose up` → Swagger UI shows endpoints

**Sprint 2 (Week 3-4): Lead + Deal APIs**
- [ ] Lead: Repository, DTO, Mapper, Service, Controller
- [ ] Lead lifecycle endpoints (contact, qualify, convert, lose)
- [ ] Deal: Repository, DTO, Mapper, Service, Controller
- [ ] Deal pipeline endpoints (advance, win, lose)
- [ ] Lead-to-Deal conversion logic
- [ ] Search/filter with Spring Data Specifications
- [ ] Unit + integration tests

**Sprint 3 (Week 5-6): Gateway + Polish + Deploy**
- [ ] Gateway Service: routing config to CRM Service
- [ ] JWT validation in Gateway (dev-mode token issuer)
- [ ] Redis caching on read endpoints
- [ ] Dashboard aggregate endpoints
- [ ] API documentation polish (descriptions, examples)
- [ ] Docker Compose: full stack runs with `docker compose up`
- [ ] README with setup instructions
- [ ] Performance baseline (response times, throughput)

### MVP Definition of Done

- [ ] All 20+ API endpoints functional and documented in Swagger
- [ ] Lead full lifecycle works: create → contact → qualify → convert → deal won
- [ ] 80%+ unit test coverage on service layer
- [ ] Integration tests pass with TestContainers
- [ ] `docker compose up` starts the entire stack
- [ ] API response times < 100ms (p95) for CRUD operations
- [ ] No N+1 query issues (verified with SQL logging)

---

## 9. Full Implementation Roadmap

### Phase 1: Foundation (Months 1-3)

**Infrastructure Setup**
- [ ] Azure subscription and resource group setup
- [ ] AKS cluster provisioning
- [ ] PostgreSQL Flexible Server deployment
- [ ] Redis Cache deployment
- [ ] Azure Service Bus namespace
- [ ] Azure Container Registry
- [ ] CI/CD pipelines (GitHub Actions → Azure)

**Backend Foundation**
- [ ] Spring Boot parent project structure
- [ ] Shared libraries (common, security, messaging)
- [ ] Identity Service with Azure AD B2C
- [ ] API Gateway with Spring Cloud Gateway
- [ ] Configuration server setup
- [ ] Observability stack (metrics, tracing, logging)

**Frontend Foundation**
- [ ] Nx workspace initialization
- [ ] Angular project scaffolding
- [ ] Shared UI component library
- [ ] Tailwind CSS configuration
- [ ] Authentication integration
- [ ] State management setup (NgRx)

### Phase 2: Core CRM Services (Months 4-6)

**Backend Services**
- [ ] Contact Service (CRUD, search, relationships)
- [ ] Lead Service (lifecycle, scoring, assignment)
- [ ] Deal Service (pipeline, stages, forecasting)
- [ ] Activity Service (tasks, notes, timeline)
- [ ] Search Service (Elasticsearch integration)

**Frontend: CRM Portal**
- [ ] Dashboard and navigation
- [ ] Contact management views
- [ ] Lead management (list, detail, kanban)
- [ ] Deal management (pipeline view)
- [ ] Activity feeds and timelines
- [ ] Search and filtering

**Data Migration**
- [ ] Contact and organization data
- [ ] Lead and deal data
- [ ] Historical activities

### Phase 3: Communication Hub (Months 7-8)

**Backend Services**
- [ ] Communication Service core
- [ ] Twilio integration (calls, SMS)
- [ ] Email integration (SendGrid/Graph API)
- [ ] WhatsApp Business API integration
- [ ] Notification Service

**Frontend Features**
- [ ] Unified inbox
- [ ] Call interface with Twilio
- [ ] Email composer with templates
- [ ] Real-time notifications
- [ ] Communication history views

### Phase 4: CPQ Engine (Months 9-11)

**Backend Services**
- [ ] Product Catalog Service
- [ ] Configuration Engine
- [ ] Pricing Engine with rules
- [ ] Quote Management Service
- [ ] Approval Workflow Service
- [ ] Document Generation (PDF)

**Frontend Features**
- [ ] Product catalog browser
- [ ] Configuration wizard
- [ ] Quote builder interface
- [ ] Approval workflows UI
- [ ] Quote document preview

**Rules Engine**
- [ ] OpenL Tablets deployment
- [ ] Pricing rules implementation
- [ ] Configuration rules
- [ ] Approval rules
- [ ] Rule management UI

### Phase 5: AI Agents (Months 12-14)

**AI Infrastructure**
- [ ] Azure OpenAI Service setup
- [ ] Vector database (Azure AI Search)
- [ ] Knowledge base ingestion pipeline
- [ ] Agent orchestration framework

**Agent Implementation**
- [ ] Sales Copilot Agent
  - [ ] Email drafting
  - [ ] Call summarization
  - [ ] Next best action
- [ ] Analytics Agent
  - [ ] Natural language queries
  - [ ] Forecasting
- [ ] Customer Chatbot
  - [ ] FAQ handling
  - [ ] Lead qualification
  - [ ] Meeting scheduling

**Frontend AI Features**
- [ ] Copilot sidebar in CRM Portal
- [ ] Chat widget for Customer Portal
- [ ] AI-generated insights dashboard

### Phase 6: Advanced Features (Months 15-16)

**Customer Portal**
- [ ] Self-service portal frontend
- [ ] Knowledge base
- [ ] Support ticket submission
- [ ] Quote acceptance workflow

**Admin Console**
- [ ] System configuration
- [ ] User management
- [ ] Rules management
- [ ] Integration settings
- [ ] Audit logs

**Performance & Security**
- [ ] Load testing and optimization
- [ ] Security audit and penetration testing
- [ ] GDPR compliance verification
- [ ] SOC 2 preparation (if required)

---

## 9. Team Structure Recommendation

```
┌─────────────────────────────────────────────────────────────┐
│                    Project Organization                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Product Owner (1)          Technical Lead (1)              │
│       │                            │                         │
│       └────────────┬───────────────┘                        │
│                    │                                         │
│  ┌─────────────────┼─────────────────┐                      │
│  │                 │                 │                      │
│  ▼                 ▼                 ▼                      │
│ Backend Team    Frontend Team    Platform Team              │
│ (4-5 devs)      (3-4 devs)       (2 devs)                  │
│                                                              │
│ • Lead/Deal     • CRM Portal     • Infrastructure          │
│ • Contact       • Customer       • CI/CD                    │
│ • CPQ           • Admin          • Observability            │
│ • Rules         • Shared libs    • Security                 │
│ • AI Agents                                                  │
│                                                              │
│  QA Engineer (1-2)    DevOps Engineer (1)                   │
└─────────────────────────────────────────────────────────────┘

Recommended Team Size: 12-15 people
```

---

## 10. Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Scope creep | High | Fixed MVP scope, strict change control |
| Data migration issues | High | Extensive testing, rollback procedures |
| Integration complexity | Medium | API-first design, contract testing |
| AI reliability | Medium | Fallback mechanisms, human-in-loop |
| Performance at scale | Medium | Load testing early, caching strategy |
| Team ramp-up | Medium | Training, pair programming, documentation |

---

## 11. Success Metrics

**Technical Metrics**
- API response time < 200ms (p95)
- System availability > 99.9%
- Deployment frequency: Daily
- Mean time to recovery < 1 hour

**Business Metrics**
- User adoption rate > 80%
- Quote generation time reduced by 50%
- Sales rep productivity increase by 25%
- Customer self-service adoption > 30%

---

## 12. Next Steps

1. **Immediate Actions**
   - Finalize Azure subscription and budget
   - Set up development environment
   - Create initial project repositories
   - Begin Phase 1 infrastructure work

2. **Week 1-2**
   - Team onboarding and training
   - Architecture deep-dive sessions
   - Development environment setup
   - CI/CD pipeline creation

3. **Week 3-4**
   - Identity Service development
   - Frontend workspace setup
   - Database schema design
   - API contract definition

---

## Appendix A: Technology Versions

```yaml
# Backend
java: "21"                    # LTS - stable through 2029
spring-boot: "3.4.x"         # Updated from 3.2.x (Jan 2025 GA)
spring-cloud: "2024.0.x"     # Updated from 2023.0.x
spring-ai: "1.0.x"           # GA release
postgresql: "16"
redis: "7.x"
elasticsearch: "8.x"
openl-tablets: "5.27.x"
drools: "8.x"
flyway: "10.x"               # Added - schema migrations
mapstruct: "1.6.x"           # Added - DTO mapping
testcontainers: "1.20.x"     # Added - integration testing

# Frontend
angular: "19.x"              # Updated from 17.x (Nov 2024 GA)
typescript: "5.6.x"          # Updated from 5.3.x
nx: "20.x"                   # Updated from 18.x
tailwindcss: "4.x"           # Updated from 3.4.x (Jan 2025 GA)
ngrx: "19.x"                 # Updated from 17.x

# Infrastructure
kubernetes: "1.30+"           # Updated from 1.28+
terraform: "1.9+"             # Updated from 1.6+
helm: "3.x"
```

---

## 13. Testing Strategy

### 13.1 Testing Pyramid

```
          ┌───────────┐
          │   E2E     │  ~5%   Cypress (Angular), Playwright
          │  Tests    │        Full user journeys
          ├───────────┤
          │Integration│  ~25%  TestContainers + Spring Boot Test
          │  Tests    │        Service + DB + Kafka integration
          ├───────────┤
          │  Contract │  ~10%  Pact (provider/consumer contracts)
          │   Tests   │        Inter-service API contracts
          ├───────────┤
          │   Unit    │  ~60%  JUnit 5 + Mockito (backend)
          │   Tests   │        Jest (frontend)
          └───────────┘
```

### 13.2 Backend Testing

```yaml
Unit Tests:
  Framework: JUnit 5 + Mockito
  Coverage Target: 80%+ line coverage
  Focus:
    - Service layer business logic
    - Entity state transitions (Lead.markContacted(), Deal.advanceStage())
    - Mapper correctness (MapStruct)
    - Validation rules
    - Exception handling

Integration Tests:
  Framework: Spring Boot Test + TestContainers
  Containers:
    - PostgreSQL 16 (via @Testcontainers)
    - Redis 7
    - Kafka (Confluent image)
    - Elasticsearch 8
  Focus:
    - Repository queries (custom JPQL, native SQL)
    - REST endpoint request/response (MockMvc)
    - Event publishing and consumption
    - Security filter chain

Contract Tests:
  Framework: Pact (JVM)
  Strategy: Consumer-Driven Contracts
  Focus:
    - API contracts between gateway and services
    - Event schema contracts for Kafka
    - Breaking change detection in CI
```

### 13.3 Frontend Testing

```yaml
Unit Tests:
  Framework: Jest + Angular Testing Library
  Coverage Target: 75%+
  Focus:
    - Component rendering and interactions
    - NgRx reducers, selectors, effects
    - Service methods and interceptors
    - Pipe and directive behavior

Component Tests:
  Framework: Storybook + Chromatic
  Focus:
    - Visual regression testing
    - Component documentation
    - Design system compliance

E2E Tests:
  Framework: Cypress (primary) + Playwright (cross-browser)
  Focus:
    - Critical user journeys (login, create lead, build quote)
    - Cross-browser compatibility
    - Accessibility (axe-core integration)
```

---

## 14. API Design & Versioning

### 14.1 API Standards

```yaml
Versioning: URI-based (/api/v1/, /api/v2/)
Format: JSON (application/json)
Pagination: Cursor-based for large datasets, offset for simple lists
Filtering: RSQL or custom query parameters
Sorting: ?sort=field,direction (e.g., ?sort=createdAt,desc)
Errors: RFC 7807 Problem Details (application/problem+json)

Rate Limiting:
  Default: 100 requests/minute per API key
  Authenticated: 1000 requests/minute per user
  Headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
```

### 14.2 API Error Response Format

```json
{
  "type": "https://api.opencrm.com/errors/validation",
  "title": "Validation Failed",
  "status": 422,
  "detail": "Request body contains invalid fields",
  "instance": "/api/v1/leads/123",
  "errors": [
    {
      "field": "email",
      "message": "must be a valid email address",
      "rejectedValue": "not-an-email"
    }
  ],
  "traceId": "abc123-def456"
}
```

### 14.3 Versioning Strategy

| Scenario | Approach |
|----------|----------|
| New field added to response | Non-breaking - no version bump |
| Field removed from response | Breaking - new version (v2) |
| New optional request field | Non-breaking - no version bump |
| Required field added to request | Breaking - new version (v2) |
| Endpoint behavior change | Breaking - new version (v2) |
| Deprecation | 6-month sunset with `Sunset` header |

---

## 15. Security Implementation

### 15.1 Authentication Flow

```
┌──────────┐     ┌───────────────┐     ┌────────────────┐
│  Browser  │────>│ Azure AD B2C  │────>│ Spring Gateway │
│  (Angular)│<────│ (OAuth2/OIDC) │<────│ (JWT Validate) │
└──────────┘     └───────────────┘     └────────┬───────┘
                                                │
                                       ┌────────▼───────┐
                                       │  Microservice   │
                                       │ (Resource Server│
                                       │  JWT + RBAC)    │
                                       └────────────────┘
```

### 15.2 Security Measures

| Category | Implementation |
|----------|---------------|
| **Authentication** | Azure AD B2C (OIDC), JWT access tokens (RS256) |
| **Authorization** | RBAC with Spring Security, method-level @PreAuthorize |
| **API Security** | Rate limiting, CORS, CSRF protection, input validation |
| **Data Protection** | TLS 1.3 in transit, Azure encryption at rest, field-level encryption for PII |
| **Secrets** | Azure Key Vault, no secrets in code/config files |
| **Injection Prevention** | Parameterized queries (JPA), input sanitization, CSP headers |
| **Dependency Scanning** | Dependabot (GitHub), OWASP Dependency Check in CI |
| **SAST** | SonarQube or Semgrep in CI pipeline |
| **Penetration Testing** | Annual third-party pentest, OWASP ZAP in CI for DAST |
| **Audit Logging** | All data mutations logged with userId, timestamp, before/after |

### 15.3 RBAC Model

```yaml
Roles:
  ADMIN:          Full system access, user management
  SALES_MANAGER:  Team management, reports, approval authority
  SALES_REP:      Lead/Deal CRUD (own records), quote creation
  SUPPORT_REP:    Contact management, communication, read-only deals
  VIEWER:         Read-only access to assigned records
  API_CLIENT:     Service-to-service (machine) identity

Permissions (examples):
  leads:read, leads:write, leads:delete, leads:assign
  deals:read, deals:write, deals:approve
  quotes:read, quotes:write, quotes:send, quotes:approve
  reports:read, reports:export
  admin:users, admin:settings, admin:rules
```

---

## 16. CI/CD Pipeline Design

### 16.1 Pipeline Architecture

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│   Push    │───>│  Build   │───>│  Test    │───>│ Security │
│ (GitHub)  │    │ (Gradle/ │    │ (Unit +  │    │ (SAST +  │
│           │    │  Nx)     │    │ Integ)   │    │ Deps)    │
└──────────┘    └──────────┘    └──────────┘    └────┬─────┘
                                                     │
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌────▼─────┐
│Production│<───│ Staging  │<───│   Dev    │<───│  Docker  │
│ (Manual  │    │ (Auto +  │    │ (Auto    │    │  Build   │
│  Gate)   │    │ Smoke)   │    │ Deploy)  │    │  + Push  │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
```

### 16.2 Environment Strategy

| Environment | Trigger | Purpose | Approval |
|------------|---------|---------|----------|
| **Dev** | Push to feature branch | Development testing | Automatic |
| **Staging** | Merge to `develop` | Integration + UAT | Automatic + smoke tests |
| **Production** | Merge to `main` | Live traffic | Manual approval gate |

### 16.3 GitHub Actions Workflows

```yaml
Workflows:
  ci.yml:
    - Gradle build (all services in parallel)
    - Unit tests + coverage report
    - Integration tests (TestContainers)
    - Contract tests (Pact)
    - SonarQube analysis
    - OWASP dependency check
    - Nx build (all apps in parallel)
    - Frontend unit tests + coverage
    - Lighthouse CI (performance budget)

  cd-dev.yml:
    - Docker image build (multi-stage)
    - Push to ACR
    - Helm upgrade to dev AKS namespace

  cd-staging.yml:
    - Promote images from dev
    - Helm upgrade to staging namespace
    - Run smoke tests
    - Run E2E tests (Cypress)

  cd-production.yml:
    - Manual approval gate
    - Blue-green or canary deployment
    - Automated rollback on health check failure
    - Post-deploy smoke tests
```

---

## 17. Observability & Monitoring

### 17.1 Three Pillars

```yaml
Logging:
  Format: Structured JSON (logback-logstash-encoder)
  Fields: timestamp, level, service, traceId, spanId, userId, message, context
  Sink: Azure Log Analytics (via Application Insights)
  Retention: 30 days hot, 1 year cold (Azure Blob)

Metrics:
  Framework: Micrometer (Spring Boot Actuator)
  Export: Azure Monitor / Prometheus (via Application Insights)
  Key Metrics:
    - Request rate, latency (p50, p95, p99), error rate (RED)
    - JVM heap, GC pauses, thread pool utilization
    - Database connection pool, query latency
    - Kafka consumer lag, processing latency
    - Business metrics (leads created/hr, quotes generated/hr)

Tracing:
  Framework: OpenTelemetry (auto-instrumentation agent)
  Export: Azure Monitor / Jaeger
  Propagation: W3C TraceContext (traceparent header)
  Sampling: 10% in production, 100% in dev/staging
```

### 17.2 Alerting Rules

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High Error Rate | >5% 5xx responses for 5 min | Critical | Page on-call |
| High Latency | p95 > 500ms for 10 min | Warning | Slack notification |
| DB Connection Exhaustion | Pool usage > 80% | Warning | Auto-scale check |
| Kafka Consumer Lag | Lag > 10,000 for 15 min | Warning | Investigate |
| Pod Crash Loop | >3 restarts in 10 min | Critical | Page on-call |
| Certificate Expiry | <30 days remaining | Warning | Renewal ticket |

### 17.3 Health Checks

```yaml
Per Service:
  /actuator/health:       Aggregate health (DB, Redis, Kafka)
  /actuator/health/liveness:  Pod liveness (always UP if JVM running)
  /actuator/health/readiness: Ready to serve traffic (DB + cache connected)

Kubernetes Probes:
  livenessProbe:  /actuator/health/liveness  (failureThreshold: 3)
  readinessProbe: /actuator/health/readiness (failureThreshold: 3)
  startupProbe:   /actuator/health/readiness (failureThreshold: 30, periodSeconds: 5)
```

---

## 18. Caching Strategy

### 18.1 Cache Layers

```
┌──────────────────────────────────────────────────┐
│                   Client Layer                     │
│  Browser Cache (Cache-Control headers)            │
│  Angular Service Worker (static assets)           │
└─────────────────────┬────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────┐
│                   CDN Layer                        │
│  Azure Front Door (static assets, public APIs)    │
└─────────────────────┬────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────┐
│              Application Layer                     │
│  Redis 7 (API responses, sessions, rate limits)   │
│  Spring Cache (cache-aside pattern)               │
│  Pattern: Cache-Aside with TTL-based expiration   │
└─────────────────────┬────────────────────────────┘
                      │
┌─────────────────────▼────────────────────────────┐
│               Database Layer                       │
│  PostgreSQL (materialized views for reports)       │
│  pg_stat_statements (query plan caching)          │
└──────────────────────────────────────────────────┘
```

### 18.2 Cache Policies

| Data Type | TTL | Eviction | Invalidation |
|-----------|-----|----------|-------------|
| Product catalog | 1 hour | LRU | Event-driven (catalog update event) |
| Pricing rules | 30 min | LRU | Event-driven (rule publish event) |
| User sessions | 24 hours | TTL | Explicit logout |
| API rate limits | 1 minute | TTL | Automatic |
| Search results | 5 minutes | LRU | TTL-based |
| Report data | 15 minutes | LRU | On-demand refresh |

---

## 19. Event Schema & Contracts

### 19.1 Event-Driven Architecture

```yaml
Event Bus: Apache Kafka / Azure Service Bus
Schema Registry: Confluent Schema Registry (Avro) or JSON Schema
Topic Naming: {domain}.{entity}.{action} (e.g., crm.lead.created)

Event Envelope:
  id: UUID (idempotency key)
  type: string (e.g., "crm.lead.status-changed")
  source: string (e.g., "crm-service")
  timestamp: ISO 8601
  correlationId: string (trace context)
  userId: string (who triggered)
  data: object (event payload)
  version: integer (schema version)
```

### 19.2 Domain Events

| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `crm.lead.created` | CRM Service | Activity, AI, Notification | New lead tracking |
| `crm.lead.status-changed` | CRM Service | Activity, Analytics, AI | Pipeline analytics |
| `crm.deal.stage-changed` | CRM Service | Activity, Analytics, Notification | Pipeline tracking |
| `crm.deal.won` | CRM Service | Analytics, Notification, CPQ | Revenue tracking |
| `cpq.quote.generated` | CPQ Service | Activity, Notification, CRM | Quote lifecycle |
| `cpq.quote.approved` | CPQ Service | Notification, CRM, Document | Approval workflow |
| `comm.email.sent` | Communication | Activity, AI | Communication log |
| `comm.call.completed` | Communication | Activity, AI | Call logging |
| `ai.insight.generated` | AI Service | Notification, CRM | AI recommendations |

### 19.3 Event Schema Evolution

```yaml
Rules:
  - Adding optional fields: BACKWARD compatible (allowed)
  - Removing fields: BREAKING (requires new topic version)
  - Changing field types: BREAKING (requires new topic version)
  - Renaming fields: BREAKING (add new field, deprecate old)

Strategy:
  - All events versioned (v1, v2, etc.)
  - Consumers must handle unknown fields gracefully
  - Schema validation in CI pipeline
  - 30-day deprecation window for old schema versions
```

---

## 20. Feature Flags & Progressive Delivery

### 20.1 Strategy

```yaml
Framework: Azure App Configuration (Feature Management) or LaunchDarkly
Granularity: Per-user, per-tenant, percentage rollout

Categories:
  Release Flags:
    - Temporary flags for gradual rollout
    - Remove after 100% rollout + 2 weeks stability
  Ops Flags:
    - Circuit breakers, kill switches
    - Permanent (e.g., disable-ai-features, enable-maintenance-mode)
  Experiment Flags:
    - A/B testing (e.g., new-quote-builder-v2)
    - Tracked with analytics, removed after experiment concludes
```

### 20.2 Key Feature Flags

| Flag | Type | Purpose |
|------|------|---------|
| `enable-ai-copilot` | Release | Gradual rollout of Sales Copilot |
| `enable-whatsapp-channel` | Release | WhatsApp integration rollout |
| `new-pricing-engine-v2` | Release | New rules engine migration |
| `disable-email-sending` | Ops | Emergency kill switch |
| `enable-maintenance-mode` | Ops | Planned maintenance |
| `copilot-model-selection` | Experiment | A/B test GPT-4o vs Claude |

---

## 21. Disaster Recovery & Business Continuity

### 21.1 RPO/RTO Targets

| Tier | RPO | RTO | Services |
|------|-----|-----|----------|
| Tier 1 (Critical) | 5 min | 30 min | CRM Service, Identity, Gateway |
| Tier 2 (Important) | 15 min | 1 hour | CPQ, Communication, Search |
| Tier 3 (Standard) | 1 hour | 4 hours | Analytics, AI Agents, Notification |

### 21.2 Backup Strategy

```yaml
PostgreSQL:
  Method: Azure automated backups (point-in-time restore)
  Retention: 35 days
  Geo-replication: Read replica in secondary region
  Testing: Monthly restore drill to verify backup integrity

Redis:
  Method: AOF persistence + RDB snapshots
  Retention: 7 days
  Recovery: Cold restart acceptable (cache rebuild)

Elasticsearch:
  Method: Snapshot to Azure Blob Storage
  Retention: 14 days
  Recovery: Re-index from PostgreSQL as fallback

Blob Storage:
  Method: GRS (Geo-Redundant Storage)
  Retention: Indefinite (documents, attachments)
```

### 21.3 Failover Procedures

```yaml
AKS Cluster Failure:
  1. Azure Traffic Manager routes to secondary region
  2. Secondary AKS cluster auto-scales
  3. PostgreSQL failover to read replica (promoted to primary)
  4. DNS TTL: 60 seconds

Database Failure:
  1. Automatic failover to standby (Azure managed)
  2. Application retries with exponential backoff
  3. Redis serves cached data during failover window
  4. Alert: page on-call + Slack notification

Kafka/Service Bus Failure:
  1. Dead letter queue preserves undelivered messages
  2. Services degrade gracefully (sync fallback for critical paths)
  3. Replay from offset after recovery
```

---

## Appendix B: Reference Architecture Diagrams

Detailed architecture diagrams will be provided in separate technical design documents for:
- Network topology
- Security architecture
- Data flow diagrams
- Integration patterns
- Deployment architecture

---

## Appendix C: AI Agent Development Guide

See `AGENT.md` for comprehensive guidance on using Claude Code agents for development, including:
- Agent-to-phase mapping
- Development conventions
- Quality gates
- MCP server recommendations

---

*Document Version: 2.0*
*Last Updated: 2026-02-05*
*Authors: AI Architecture Team*
