# Gap Analysis Plan: Reuse from `deal-desk-service` → `open-crm-cpq`

**Status:** Plan / recommendations (not an implementation PR)  
**Date:** 2026-09-14  
**Source:** [drgaciw/deal-desk-service](https://github.com/drgaciw/deal-desk-service) (`main`)  
**Target:** this monorepo (`open-crm-cpq`)  
**Priority (architect decision 2026-09-14):** **(1) Approval / state-machine parity** → **(2) TCV / repricing edge-case handoff to pricing Track 1**  
**Related plan:** [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md) (pricing/TCV Track 1 already owns financial-metric harvest)  
**Method note:** Cursor **Sequential Thinking MCP is not available** in this session. Extended reasoning uses the same step-decomposition pattern.

---

## 1. Executive verdict

`deal-desk-service` is a **legacy ACI deal-lifecycle + Salesforce CPQ sync + Easy Rules** application. It is **not** a drop-in “deal desk microservice” for open-crm-cpq.

**Execution order (locked):** finish **Track D1** (approval/state-machine parity against quote BFF + `approval-routing`) to a done gate, then **Track D2** (catalog TCV/NPV/contingent/repricing gaps and **hand off** into pricing-ai-rules Track 1 backlog — no parallel TCV PRs). **Salesforce** stays parked until an explicit product ADR. Do not interleave Easy Rules or a third deal aggregate.

Open CRM/CPQ already has a **different architecture** for the same *business phrase* “deal desk”:

| Concept | Source (`deal-desk-service`) | Target (`open-crm-cpq`) |
|---------|------------------------------|-------------------------|
| “Deal” | MongoDB aggregate with DRAFT→SUBMITTED→APPROVED… (`/api/v1/deals`) | CRM pipeline `Deal` (`crm-service`) **or** CPQ `Quote` under approval |
| “Deal desk” | Whole product (rules + TCV + Salesforce) | **Approval tier** `DEAL_DESK` + quote BFF inbox + AI orchestrator skill |
| Approvals | Deal submit/approve/reject on deal resource | `approval-routing` state machine + quote-service BFF (S2C-12); dual-read **held** (ADR-005) |
| Pricing / TCV | Easy Rules + `TCVCalculationStrategy` in-process | `pricing-service` waterfall + `FinancialMetrics` + Drools |
| Rules engine | **jEasy Rules** (main) + experimental Drools adapter in `deal-desk-rules` | **Drools** (`pricing-service` / `libs/rules-engine`); OpenL **not** required for forge |
| Persistence | MongoDB (+ Flyway SQL for rule audit) | PostgreSQL / Flyway per service |
| Frontend | **None** (no product UI in repo) | Angular `@frontend/cpq-approvals` + CRM deal kanban |
| Integration | Salesforce Opportunities / CPQ | No Salesforce in monorepo today |

**Reuse posture:** selective **method/pattern harvest** only. Do **not** merge the service, introduce Easy Rules, or add a parallel `/api/v1/deals` CPQ lifecycle. Highest-value candidates are **approval/state-transition hardening**, **TCV/repricing edge-case specs** (coordinated with pricing Track 1), and optionally **Salesforce sync patterns** only if product commits to SF.

---

## 2. Sequential thinking (extended)

### Thought 1 — Naming trap

Harvesting “deal desk” by name will collide three target surfaces:

1. `crm-service` **Deal** (sales opportunity stages)  
2. `approval-routing` tier **`DEAL_DESK`** + quote BFF  
3. `DealDeskWorkflowOrchestrationAgent` / skill (AI sequencing)

Source “Deal” ≈ commercial package under approval + SF opportunity — closer to **Quote + ApprovalRequest** than to CRM Deal. Gap analysis must score against **quote/approval/pricing**, not CRM kanban, unless SF sync is in scope.

### Thought 2 — What the source actually is

Evidence from `main` (last meaningful app update ~2026-05; recent commits mostly GitNexus/skills):

- **Monolith** under `src/` (~132 Java files) + thin **`deal-desk-rules`** module (~9 Java files).  
- README describes three deployables (core / rules runtime / TCV processors); **repo tree is not a clean three-service multi-module reactor** — treat README ports as aspirational/ops docs until verified.  
- Controllers: `DealController` (`/api/v1/deals` CRUD + submit/approve/reject + sync/batch-sync), `RuleDefinitionController`.  
- Stack: **Java 21**, **Spring Boot 3.4.x** parent, MongoDB, OAuth2 JWT, MapStruct, Caffeine, Resilience4j, **easy-rules-core**, Spring Retry.  
- Rules migration notes: custom engine → **jEasy Rules** (SpEL `DynamicRule`, payment/credit/debit/profitability/TCV/status/validation rule classes).  
- `deal-desk-rules`: `RuleEngine` API with **Drools** + **EasyRules** adapters — useful as *port design* reference, not as a second runtime.  
- **No product frontend** (TS/HTML only under `.cursor/skills`).  
- Heavy generated/service markdown docs (`services-documentation-part*.md`) — mine for behavior, not for copy-paste structure.

### Thought 3 — What the target already owns

| Concern | Target owner | Status |
|---------|--------------|--------|
| Quote approval submit / pending / approve / reject | quote-service BFF → approval-routing | Landed; dual-read default **true** (HIGH flip held) |
| Approval state machine + tiers DEAL_DESK / SALES_VP / CFO | `approval-routing` | Landed; DTO wrap Wave W |
| Margin exception → approval | pricing DRL `approval-margin-exception` + events | Landed |
| TCV / financial metrics | `pricing-service` `FinancialMetrics` / `MetricsCalculator` | Landed; **pricing-ai-rules Track 1** owns further parity |
| CRM Deal CRUD / stages | `crm-service` | Landed; FE kanban |
| Deal desk AI orchestration | `DealDeskWorkflowOrchestrationAgent` + skill | Scaffold / simulated |
| Approvals FE inbox | `@frontend/cpq-approvals` | Landed (quote BFF only) |

### Thought 4 — Stack / architecture blockers

| Dimension | Source | Target | Implication |
|-----------|--------|--------|-------------|
| Boot | 3.4.3 | **4.0.6** | Port patterns, not modules |
| DB | MongoDB documents | PostgreSQL entities | No document model transplant |
| Rules | Easy Rules | Drools | **Exclude Easy Rules runtime** |
| Deal API | `/api/v1/deals` approve | Quote BFF `/api/v1/quotes/.../approvals` | Path/semantics remap |
| SF | First-class | Absent | Harvest only if roadmap says yes |
| FE | None | Angular | Nothing to harvest |

### Thought 5 — Overlap with pricing-ai-rules plan

TCV / contingent revenue / discounts / repricing in source **overlap** pricing Track 1 (`MetricsCalculator` hybrid, fixtures).  

**Rule:** deal-desk gap analysis may **catalog** TCV algorithms and point to pricing backlog; it must **not** open a second competing TCV harvest track. Coordinate via shared “financial edge cases” appendix or defer to pricing G1-T1.

### Thought 6 — Reuse modes

| Mode | Meaning | Typical deal-desk use |
|------|---------|------------------------|
| A | Already landed | Quote BFF approve/reject; DEAL_DESK tier |
| B | Port method/algorithm | TCV NPV/contingent formulas → pricing (coord) |
| C | Port pattern | State-transition guards; Resilience4j retry; rule versioning ideas |
| D | Shared lib | Only if ≥2 services need same code (unlikely first) |
| E | Optional companion | None strong (no CLI/FE) |
| F | Exclude | Easy Rules runtime, Mongo deal aggregate, SF unless requested, Boot 3 module copy |

### Thought 7 — Risks

- **Dual “Deal” domains** confusing CRM vs CPQ.  
- **Second rules engine** (Easy Rules) fragments Drools/OpenL roadmap.  
- **Bypass quote BFF** if someone mounts source-style deal approve APIs.  
- **Dual-read cutover** (ADR-005) — do not couple harvest PRs to flipping `APPROVALS_LEGACY_LOCAL_READ`.  
- **Salesforce secrets / .env** in source — never copy credentials; scan before any vendor client port.  
- Stale aspirational multi-service docs overstate modularity.

### Thought 8 — Strategy

Run a **short, approval-centric gap analysis** (1.5–2.5 days) that:

1. Maps source deal lifecycle → quote + approval-routing.  
2. Scores validation/status/profitability rules as **policy specs** for Drools or approval guards — not Easy Rules ports.  
3. Hands TCV/repricing items to **pricing Track 1** backlog.  
4. Parks Salesforce behind an explicit product decision.  
5. Confirms **zero FE harvest**.

---

## 3. Source capability snapshot

### 3.1 API surface (`DealController`)

| Method | Path | Target analog |
|--------|------|---------------|
| POST | `/api/v1/deals` | Not 1:1 — quote create / CRM deal create (choose intentionally) |
| GET/PUT/DELETE | `/api/v1/deals/{id}` | Quote or CRM deal — **do not add third aggregate** |
| GET | `/api/v1/deals` (filter) | Quote list / CRM deal list |
| POST | `.../submit` | `POST /api/v1/quotes/{id}/approvals` (BFF) |
| POST | `.../approve` / `.../reject` | Quote BFF approve/reject → routing |
| POST | `.../sync`, `.../batch-sync` | **Salesforce only** — no target analog |

### 3.2 Rules / TCV (main app)

- Executors: TCV, DealStatus, DealValidation  
- Impl rules: Payment*, Credit*, Debit*, Profitability*, TCVCalculation*, DealStatus*, DealValidation*  
- Support: versioning, caching, SpEL `DynamicRule`, `PricingRuleEngine` adapter  
- TCV model: components, pricing model, discounts/adjustments, contingent revenue, contract term, repricing triggers  
- Strategy API: `calculateTCV`, `calculateNPV`, `validateComponents`, `applyDiscounts`

### 3.3 `deal-desk-rules` module

Port-style `RuleEngine` + Drools + EasyRules engines — useful for **S2F “rule engine port”** design discussion; **do not** ship EasyRules beside Drools in production CPQ without an Accepted ADR.

---

## 4. Target owner map (where harvests would land)

| Capability class | Owner | Notes |
|------------------|-------|-------|
| Approval state machine / tiers / events | `approval-routing` | Prefer strengthening existing service |
| Approval BFF / dual-read | `quote-service` | Hold default flip |
| Margin → approval triggers | `pricing-service` DRL + events | Already linked |
| TCV / NPV / contingent / repricing math | `pricing-service` | **Via pricing-ai-rules Track 1**, not parallel PRs |
| Rule versioning / SpEL dynamic rules | Usually **exclude** or map to Drools/rule-set versioning | Forge: full SpEL held |
| Salesforce sync | New integration module or `crm-service` | Only if roadmap |
| Deal desk AI sequencing | `ai-service` | Prompt/workflow ideas from source docs/skills; code agents already scaffolded |
| FE | Angular approvals / CRM | No source UI |
| CRM Deal stages | `crm-service` | Only if source status rules inform stage gates (weak fit) |

---

## 5. Gap analysis workplan

Work is split into **Track D1** then **Track D2**. D0 is shared. Salesforce is **out of authorized tracks** until a product ADR.

### Phase D0 — Access & freeze (shared, 0.5 day)

1. Clone at **pinned SHA**; record SHA.  
2. Build with JDK 21: `mvn -DskipTests package`.  
3. Inventory controllers + public service methods (CSV/MD).  
4. Flag secrets: `.env`, Salesforce config — **read-only**; no copy into open-crm-cpq.  
5. Cross-link pricing plan: list source TCV types for Track D2 handoff (do not start pricing PRs here).

**Exit:** pinned SHA + inventory + secret-scan note.

---

### Track D1 — Approval / state-machine parity (priority 1)

#### Phase D1-M — Capability matrix, lifecycle only (0.5–0.75 day)

Rows **only** for:

1. Deal status enum + allowed transitions (submit / approve / reject / cancel)  
2. `InvalidDealStateTransition*` / validation failure semantics  
3. Submit/approve/reject API behavior vs quote BFF + `ApprovalService`  
4. DealValidation / DealStatus / Profitability / PaymentThreshold rules as **approval or pre-submit policy specs** (not Easy Rules ports)  
5. Exception taxonomy that maps to BFF/`BusinessException` codes  
6. Explicit defer: TCV math → Track D2; Salesforce → parked; rule versioning/SpEL → exclude or ticket  

Columns: source symbol, target analog, gap type, reuse mode A–F, owner, effort, risk, dependency, decision.

**Exit:** Track D1 matrix; top **6–8** approval/transition candidates ranked.

#### Phase D1-Deep — Deep compare (0.5 day)

1. Build vocabulary table: source `Deal` / `DealStatus` → target `Quote` + `ApprovalRequest` / `ApprovalStatus` / tiers.  
2. Diff transition guards vs `ApprovalRequest.approve|reject|cancel` and quote BFF paths.  
3. Check JDK 21 / Boot 4 portability of any method worth copying.  
4. Map validation/profitability scenarios to existing margin DRL + approval submit — mark **equivalent / partial / missing**.  
5. GitNexus impact notes for `ApprovalService`, quote `ApprovalService`, `ApprovalRequest`.

**Exit:** Track D1 harvest backlog with named tests; **no** dual-read flip in any PR.

#### Phase D1-Decide — Gate (0.25 day)

1. **Do now** — transition guards, missing validation scenarios, exception/code alignment  
2. **Defer** — AI `DealDeskWorkflowOrchestrationAgent` prompt enrichment (optional later)  
3. **Won’t do** — Easy Rules, `/api/v1/deals` approve API, CRM Deal conflation, ADR-005 flip  

**Track D1 done gate:** residual approval gaps closed, ticketed, or won’t-do. Only then open Track D2 for TCV cataloging.

#### Phase D1-Pilot — PRs (after D1-Decide)

| Order | Pilot | Owner |
|------:|-------|-------|
| 1 | Deal→Quote/Approval vocabulary + transition parity matrix (tests as living spec) | docs + approval/quote tests |
| 2 | Port **missing** invalid-state guards / clearer error codes into routing + BFF | `approval-routing`, `quote-service` |
| 3 | Missing profitability/payment-threshold **policy** only if gap vs `approval-margin-exception` / submit validation | `pricing-service` and/or routing |

---

### Track D2 — TCV / repricing handoff to pricing Track 1 (priority 2; after D1 gate)

#### Phase D2-M — Catalog only (0.5 day)

Inventory source TCV model + strategy methods:

- `TCVCalculationStrategy` (TCV, NPV, validate, discounts)  
- Contingent revenue, contract term, repricing triggers, discounts/adjustments  
- `TCVCalculationRules` / executor behavior as **expected vectors**  

For each: mark covered / partial / missing relative to `FinancialMetrics` / `MetricsCalculator` / pricing fixtures; **assign ticket ID into pricing-ai-rules T1 backlog** — do not open deal-desk-owned pricing PRs.

**Exit:** `docs/plans/deal-desk-tcv-handoff-to-pricing-t1.md` (short appendix ok).

#### Phase D2-Decide

1. **Handoff** — all additive TCV/repricing gaps → pricing Track 1 (hybrid metrics rule from pricing plan §5.1a still applies)  
2. **Won’t do here** — second TCV calculator in approval-routing or a new deal-desk module  

No D2 implementation pilots in this plan — execution lives under pricing harvest DAG.

---

### Parked — Salesforce (not in priority 1–2)

Requires explicit product ADR before D0 expands into SF client inventory for harvest. Patterns of interest later: Resilience4j + `@Retryable` auth/opportunity sync — still Mode C only, never `.env` copy.

---

## 6. Recommendations under locked priority

### 6.1 Track D1 — Do now (approval / state machine)

| Candidate | Mode | Land in | Why |
|-----------|------|---------|-----|
| Invalid state transition model / tests | C | `approval-routing` / quote BFF | Source hardened transitions (#57); maps to real CPQ “deal desk” |
| Deal→Quote/Approval vocabulary + parity matrix | C | docs + tests | Prevents CRM Deal conflation |
| Deal validation / profitability **acceptance scenarios** | B/C | Drools or approval pre-validate | Specs only if gap vs margin DRL / submit checks |
| Exception / error-code alignment | C | BFF + routing handlers | Optional, after transitions |

### 6.2 Track D2 — After D1 gate (handoff only)

| Candidate | Mode | Land in | Why |
|-----------|------|---------|-----|
| TCV/NPV/contingent/repricing formulas & fixtures | B | **pricing Track 1 backlog** | Single owner for financials; hybrid metrics (§5.1a pricing plan) still applies |
| `TCVCalculationRules` expected vectors | B | pricing goldens | Do not implement under approval-routing |

### 6.3 Parked / exclude

| Candidate | Status |
|-----------|--------|
| Salesforce sync / batch-sync | **Parked** — needs product ADR |
| Easy Rules / SpEL DynamicRule platform | **Exclude** |
| Mongo deal service / new `/api/v1/deals` approve | **Exclude** |
| FE | **Exclude** (none in source) |
| Dual-read default flip | **Exclude** (ADR-005 hold) |
| Second TCV stack in this workstream | **Exclude** |

### 6.4 Architecture rules for any harvest PR

1. Prefer **quote + approval-routing** owners; never invent a third commercial aggregate.  
2. FE and agents keep using **quote BFF** only (`CPQ_API_CONTRACT_MATRIX.md`).  
3. Do not flip dual-read default (ADR-005 / forge hold).  
4. DTOs at boundary (ADR-006).  
5. Drools remains the CPQ rules engine; Easy Rules stays reference-only.  
6. TCV work merges into [pricing-ai-rules](./pricing-ai-rules-reuse-gap-analysis.md) Track 1 backlog after D1 gate.  
7. GitNexus impact before edits; `detect_changes` before commit.

---

## 7. Deliverables

1. This plan (done; priority 1→2 locked).  
2. `docs/plans/deal-desk-capability-matrix-d1.md` — Track D1 at pinned SHA.  
3. `docs/plans/deal-desk-harvest-backlog-d1.md` — ranked approval/transition PRs.  
4. After D1 gate: `docs/plans/deal-desk-tcv-handoff-to-pricing-t1.md`.  
5. Salesforce ADR — **only** if product later prioritizes SF.

---

## 8. Blast radius (if harvest proceeds)

| Theme | Blast radius |
|-------|----------------|
| Approval transition guards | `approval-routing`, quote BFF, FE inbox (behavior only), dual-read tests |
| Policy → Drools | `pricing-service`, margin/approval events |
| TCV handoff | `pricing-service` tests/metrics (existing Track 1) |
| Salesforce (if ever) | New client, secrets, CRM opportunity mapping, gateway routes |

---

## 9. Out of scope for this document

- Implementing harvest PRs.  
- Flipping `APPROVALS_LEGACY_LOCAL_READ`.  
- Installing Sequential Thinking MCP in Cursor.  
- Replacing Angular approvals UI.

---

## 10. Decisions & open questions

### Decided

| Topic | Decision |
|-------|----------|
| Priority order | **1 = approval / state-machine parity**, then **2 = TCV handoff to pricing Track 1** |
| FE harvest | **No** |
| Easy Rules in target | **No** |
| New deal-desk microservice | **No** |
| TCV ownership | **pricing-service / pricing-ai-rules Track 1** (catalog in D2 only) |
| Primary mapping | Source Deal lifecycle → **Quote + approval-routing**, not CRM Deal |
| Salesforce | **Parked** until product ADR |
| Interleave D2 implementation into D1? | **No** |

### Still open (does not block starting D0 / D1-M)

1. After D1 parity matrix: are any profitability rules **must-have** for the next release, or documentation-only? (Architect default: **implement only if missing vs margin DRL / submit validation**; otherwise ticket.)  
2. Should D1 pilots be allowed while pricing Track 1 is in flight? (Architect default: **yes** — different owners; coordinate only on shared Drools files.)

---

## Appendix A — Quick links

| Resource | Location |
|----------|----------|
| Source repo | https://github.com/drgaciw/deal-desk-service |
| Target approvals matrix | `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §6 |
| Dual-read ADR | `docs/adr/ADR-005-approvals-legacy-dual-read.md` |
| Deal desk AI skill | `agents/skills/deal-desk-workflow-orchestration/SKILL.md` |
| Pricing reuse plan | `docs/plans/pricing-ai-rules-reuse-gap-analysis.md` |

## Appendix B — Method caveat

Sequential Thinking MCP (Cursor): **unavailable** here; analysis used manual multi-step decomposition.
