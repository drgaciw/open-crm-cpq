# Cowork + OpenClaw Strategy for open-crm-cpq

**Prepared:** 2026-03-15
**Context:** 1 FTE + AI agents, Phase 4 CPQ Engine, Spring Boot 3.4+ / Angular 19+ / 37 CPQ agents

---

## The Core Model: Three Tools, Three Roles

The project already has a working tool triangle. The key is not to use each tool for everything — it's to use each for the class of work it handles best and route output between them.

```
┌─────────────────────────────────────────────────────────────────┐
│                     COGNITIVE BANDWIDTH                          │
│              (your constraint as a 1-FTE team)                   │
└────────────────────────┬────────────────────────────────────────┘
                         │ managed by
        ┌────────────────┼────────────────────┐
        │                │                    │
        ▼                ▼                    ▼
┌──────────────┐  ┌─────────────┐  ┌──────────────────┐
│ Claude Code  │  │   Cowork    │  │    OpenClaw       │
│              │  │             │  │                   │
│ THE BUILDER  │  │ ARCHITECT'S │  │   BACKGROUND      │
│              │  │ ASSISTANT   │  │   ORCHESTRATOR    │
│ All active   │  │             │  │                   │
│ implementation│  │ ADRs, docs, │  │ Async research,  │
│ in worktrees │  │ stub gen,   │  │ parallel agents,  │
│ Testing,     │  │ PRD→code    │  │ memory, long-     │
│ debugging    │  │ pipelines   │  │ running tasks     │
└──────────────┘  └─────────────┘  └──────────────────┘
        │                │                    │
        └────────────────┴────────────────────┘
               connected via freema/openclaw-mcp
               (MCP bridge installed in Layer 1)
```

**The rule:** Claude Code never stops to do research or write documentation. OpenClaw never writes production code. Cowork bridges design artifacts into code-ready inputs for Claude Code.

---

## ⚠️ Critical Issue to Fix First: Spring AI Version

Before implementing the 37 agents, upgrade Spring AI. Your `backend/services/ai-service/pom.xml` declares:

```xml
<spring-ai.version>0.8.1</spring-ai.version>
```

Spring AI 1.0 GA shipped in May 2024 (now at 1.0.x). The tool-calling / function-calling API changed significantly between 0.8.x and 1.0. Building 37 agents on 0.8.1 will require a full rewrite of every `@Description`-annotated tool registration when you eventually upgrade.

**Fix now before building agents:**
```xml
<spring-ai.version>1.0.0</spring-ai.version>
```

Key API changes affecting your 37-agent design:
- `@Description` on tool methods → `@Tool` annotation in 1.0
- `ChatClient.Builder` API changed (builder pattern revised)
- `FunctionCallback` → `ToolCallback` in 1.0
- Azure OpenAI starter artifact ID changed

Use Cowork + a Claude Code session to do this upgrade as a standalone task before implementing any CPQ agents.

---

## Pain Point 1: Architecture & ADRs

**Current friction:** You're deep in CPQ engine code and hit an architecture decision. You stop coding to think it through and write it up. Or worse — you skip writing it and the decision lives only in your head.

**The workflow:**

### Step 1 — Cowork as ADR Draft Engine

Cowork can read your PRD files and generate ADR drafts without you context-switching from Claude Code. The pattern:

```
Cowork reads:
  - agent-prd.md (agent architecture overview)
  - agents/skills/{agent}/SKILL.md (individual agent spec)
  - MODERNIZATION_PLAN.md (ADRs already captured)

Cowork generates:
  - docs/adr/ADR-{n}-{title}.md  (structured ADR)
  - Saves directly to project folder
```

Trigger phrase for Cowork: *"Draft an ADR for [topic] based on the PRD and existing architecture decisions"*

Example: "Draft an ADR for the Spring AI tool registry pattern — deciding between @Tool annotations vs FunctionCallback beans for the 37 CPQ agents."

### Step 2 — OpenClaw Critic + Surveyor for Async Validation

Once a draft ADR exists, dispatch it to OpenClaw's critic and surveyor agents in the background:

```
In Claude chat (with freema/openclaw-mcp active):
→ "Have the critic agent review this ADR draft and challenge the assumptions:
   [paste ADR or reference file path]"
→ Returns: task_id for async polling

You continue coding in Claude Code.

20 minutes later:
→ "openclaw_task_status task_id=xxx"
→ Returns: critic's counterarguments and alternative approaches
```

This keeps architectural review parallel to implementation, not sequential.

### ADR Topics Queue for This Project

Based on the current CPQ engine phase, these decisions need ADRs before implementation:

1. Spring AI tool registration pattern (0.8.1 → 1.0 migration)
2. Agent state management — Redis keyspace design for 37-agent conversation state
3. Drools rule invocation from Spring AI agents — synchronous vs event-driven
4. Human-in-the-loop approval checkpoint — REST polling vs WebSocket push vs SSE
5. ERP adapter service — circuit breaker strategy (Resilience4j config per ERP type)
6. pgvector embedding strategy for the Recommendation & Upsell agent (cpq-agent-017)
7. Agent audit log schema design — write path (sync vs async Kafka)

---

## Pain Point 2: PRD-to-Code Translation (37 Agents)

This is the highest-leverage problem to solve. You have 37 detailed `agents/skills/{name}/SKILL.md` files. Each describes inputs, workflow steps, and validation rules — essentially a functional spec. Translating each into a Spring Boot implementation is currently a manual, sequential process.

**The insight:** Your SKILL.md files already contain all the information needed to generate the agent's code scaffold. The scaffold for every agent is structurally identical:

```
For each cpq-agent-NNN:
  ├── AgentNNNController.java      (@RestController, 3 endpoints: invoke/stream/status)
  ├── AgentNNNService.java         (@Service, implements AgentService interface)
  ├── AgentNNNTools.java           (Spring AI @Tool methods, one per SKILL.md workflow step)
  ├── AgentNNNAuditEntity.java     (extends BaseEntity, maps to ai.agent_audit_log)
  ├── AgentNNNRequest.java         (DTO, fields from SKILL.md Inputs section)
  ├── AgentNNNResponse.java        (DTO, wraps in ApiResponse<T>)
  └── AgentNNNTest.java            (unit test stubs)
```

### The Cowork Stub Factory Pipeline

Set up this workflow in Cowork once, then run it for all 37 agents:

1. **Cowork reads** `agents/skills/{agent}/SKILL.md`
2. **Cowork generates** the full 7-file scaffold following your existing conventions (BaseEntity, ApiResponse, @Builder, MapStruct, @Transactional on service)
3. **Cowork writes** output to `backend/services/ai-service/src/main/java/com/opencrm/ai/agents/{agent}/`
4. **Claude Code** opens the generated scaffold and implements the actual business logic — which is the high-value work requiring reasoning about Drools rules and Spring AI patterns

**The output of this pipeline for your Phase 1 rollout agents (cpq-agent-004, 011, 024, 031):**
- 4 agents × 7 files = 28 files generated in one Cowork session
- Each file is ~40-60 lines of compilable stub code with the correct package, imports, annotations, and method signatures
- Claude Code then fills in the `// TODO: implement` bodies

**Example Cowork trigger:**
*"Generate the full Spring Boot scaffold for cpq-agent-011 (Minimum Margin Enforcement) by reading agents/skills/minimum-margin-enforcement/SKILL.md. Use BaseEntity, ApiResponse, @Builder, MapStruct patterns from backend/libs/common. Output to backend/services/ai-service/src/main/java/com/opencrm/ai/agents/minimummargin/"*

### Batch Generation Strategy

Run Cowork stub generation in groups that match your rollout phases:

| Batch | Agents | Cowork Session Focus |
|-------|--------|---------------------|
| Batch A | 004, 011, 024, 031 | Phase 1 — core pricing + approval + doc gen |
| Batch B | 007, 008, 010, 012, 037 | Phase 2 — discounts + deal desk |
| Batch C | 001, 002, 003, 015, 016 | Phase 3 — product configuration |
| Batch D | 019, 020, 021, 022, 023 | Phase 4 — subscription/licensing |

After each batch, a single Claude Code worktree implements the batch before the next is generated. This prevents the "too many open stubs" problem.

---

## Pain Point 3: Boilerplate Spring Boot / Angular Code

### Spring Boot: Cowork as Service Scaffolder

Beyond the AI agents, you have services like the ERP adapter (port 8085) and additional CPQ microservices that need the full Spring Boot scaffold. Cowork can generate complete service skeletons:

**Cowork prompt pattern:**
*"Scaffold a new Spring Boot service called erp-adapter-service following the conventions in backend/libs/common and backend/services/crm-service. Service ports: 8085. Schemas: erp. Endpoints: [list from MODERNIZATION_PLAN.md Section 5.1]."*

Cowork reads your existing service for patterns (package structure, pom.xml dependencies, Flyway migration format, Docker Compose entry) and generates a consistent new service.

### Angular: OpenClaw Coder Agent for Component Generation

For the Angular CPQ frontend, the OpenClaw coder agent handles bulk component generation well. The pattern:

```
In OpenClaw (or via MCP from Claude chat):
"Using the Angular component conventions from the crm-portal
 app in frontend/, generate standalone OnPush components for:
 - quote-builder (smart container)
 - quote-line-item (dumb presentational)
 - approval-status-badge (dumb presentational)

 Follow the NgRx + async pipe + Tailwind pattern shown in
 [reference component path]."
```

The coder agent handles the boilerplate. Claude Code reviews and integrates. This keeps your Angular component generation parallel to your Spring Boot implementation work.

---

## The OpenClaw Agent Fleet as CPQ Development Crew

Your installed Tier 3 `openclaw-agents` fleet (planner, ideator, critic, surveyor, coder, writer, reviewer, scout) maps directly onto CPQ development workflow tasks:

| OpenClaw Agent | CPQ Development Use |
|---------------|---------------------|
| **planner** | Decompose each CPQ agent PRD into an ordered implementation task list; identify cross-agent dependencies |
| **surveyor** | Research Spring AI 1.0 tool calling patterns; research Drools 8 integration patterns; monitor for breaking API changes |
| **ideator** | Generate alternative approaches for complex agent logic (e.g., margin enforcement conflict resolution) |
| **critic** | Challenge ADR decisions; review agent workflow sequences for edge cases |
| **coder** | Generate Angular component stubs; generate Drools rule stubs from business specs |
| **writer** | Keep agent PRDs in sync with implementation; generate OpenAPI specs from controllers |
| **reviewer** | Pre-review Spring Boot code before Claude Code commits; catch pattern deviations |
| **scout** | Find relevant Spring AI examples, Drools GitHub issues, competitive CPQ implementations |

**Key principle:** Dispatch to OpenClaw agents when the task is **well-defined but time-consuming** and doesn't require you to be in the loop while it runs. The async polling via `openclaw_chat_async` + `openclaw_task_status` is what makes this practical.

---

## The Integration Circuit

With `freema/openclaw-mcp` installed, you have a closed loop between all three tools:

```
1. COWORK SESSION (architecture / planning / stub generation)
   → Reads PRDs, generates ADR drafts, generates code stubs
   → Saves artifacts to project folder (open-crm-cpq/)
   → Dispatches research tasks to OpenClaw (MCP bridge)

2. OPENCLAW BACKGROUND (async, while you're elsewhere)
   → Surveyor researches architectural questions
   → Critic validates ADR decisions
   → Coder generates Angular component stubs
   → Writer drafts doc updates
   → Results available via task polling

3. CLAUDE CODE WORKTREE (implementation)
   → Opens generated stubs from step 1
   → Implements business logic
   → Can query OpenClaw for research results via MCP
   → Commits and moves to next agent
```

**The full cycle for one CPQ agent (cpq-agent-011 as example):**

```
Monday AM — Cowork
├── Read: agents/skills/minimum-margin-enforcement/SKILL.md
├── Generate: Spring Boot scaffold (7 files) → backend/services/ai-service/
├── Generate: ADR draft for Drools rule invocation pattern
└── Dispatch to OpenClaw surveyor: "Research Drools 8.44 salience 930 invocation from Spring AI @Tool"

Monday AM — OpenClaw (background, 20 min)
└── Surveyor returns: Drools 8 API patterns + 3 GitHub examples

Monday midday — Claude Code (worktree: feat/CPQ-011-margin-enforcement)
├── Opens generated scaffold
├── Implements DroolsMarginEnforcementService using surveyor's research
├── Implements Spring AI @Tool methods
├── Writes unit tests
└── Commits
```

One agent, one focused implementation session, no research interruptions.

---

## Concrete Cowork Workflows to Set Up

### Workflow 1: PRD Sync Checker (weekly)
*"Read all 37 SKILL.md files in agents/skills/ and compare against the Java source files in backend/services/ai-service/src/. Identify which agents have SKILL.md specs but no corresponding Java implementation. Generate a status report saved to docs/agent-implementation-status.md"*

### Workflow 2: ADR Generator (on-demand)
*"Draft an ADR for [decision topic] by reading MODERNIZATION_PLAN.md existing ADRs and the relevant SKILL.md files. Save to docs/adr/ADR-{next-number}-{kebab-title}.md"*

### Workflow 3: OpenAPI Spec Generator (after each Sprint)
*"Read the @RestController files in backend/services/ai-service/src/ and generate an OpenAPI 3.0 spec for all agent endpoints. Save to docs/api/cpq-agents-openapi.yaml"*

### Workflow 4: Drools Rule Stub Generator (for Pricing Phase)
*"Read the discount and pricing SKILL.md files and generate Drools 8 .drl rule stubs for: volume-discount, customer-segment-discount, minimum-margin-enforcement. Follow the existing rule patterns in backend/services/pricing-service/. Save to backend/services/pricing-service/src/main/resources/rules/"*

### Workflow 5: Angular Feature Scaffold (per UI sprint)
*"Read the feature spec in [SKILL.md or task description] and generate the full Nx feature library scaffold for [feature]. Follow conventions in frontend/libs/. Include: feature.routes.ts, +state/ (NgRx), components/ (smart + dumb), and barrel index.ts."*

---

## What NOT to Do

**Don't use Cowork to write production Spring Boot or Angular code that goes directly to main.** Cowork-generated code should always go through Claude Code for review and test-driven completion. Treat Cowork output as a highly-capable first draft, not production-ready code.

**Don't use OpenClaw for tasks requiring immediate response.** OpenClaw's async model is a feature for long-running tasks, not a substitute for Claude Code when you need answers in the next 60 seconds. Use Claude Code for in-session questions; use OpenClaw for background work you dispatch and check on later.

**Don't build on Spring AI 0.8.1.** Upgrade to 1.0.x before implementing agents. The tool registration API break between 0.8.x and 1.0 will cost more time to fix later than the upgrade costs now.

**Don't generate all 37 agent stubs at once.** Generate in batches aligned to the rollout phases. Having 37 half-implemented stubs in the codebase simultaneously increases cognitive overhead and compile noise.

---

## Immediate Next Actions

In priority order:

1. **Upgrade Spring AI to 1.0.x** — Use a Claude Code worktree. This is a blocker for all agent work.

2. **Run Cowork Workflow 1 (PRD sync checker)** now — Establish the baseline of what's implemented vs specced.

3. **Generate Batch A stubs (cpq-agents 004, 011, 024, 031)** via Cowork — These are your Phase 1 agents. Getting all four scaffolded in one Cowork session means Claude Code can start implementing immediately.

4. **Draft the 7 architecture ADRs listed above** — One Cowork session per ADR, dispatch critic agent validation to OpenClaw in parallel.

5. **Set up the OpenClaw surveyor agent on a standing research task:** "Monitor spring-ai GitHub releases and Drools 8 changelogs; summarize breaking changes weekly." This runs in the background and surfaces issues before they hit your implementation.
