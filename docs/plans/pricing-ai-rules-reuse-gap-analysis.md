# Gap Analysis Plan: Reuse from `pricing-ai-rules-service` → `open-crm-cpq`

**Architect verdict: APPROVED-WITH-GATES** — 2026-09-14  
**Gates (must hold):** (1) **G0 clone serial** before any harvest PR; (2) **Track 2 HOLD** until Path B Spring AI ADR **Accepted**; (3) Drools **10.2.0** only as **T1-5** after T1-1…T1-4 green; (4) GitNexus `UNKNOWN` on `MetricsCalculator` / filters — confirm with `rg` each PR.  
**Status:** Architect-enriched plan — Track 1 implementation DAG authorized for Cursor `--model auto` workers; Track 2 **not** authorized.  
**Date:** 2026-09-14  
**Source:** [drgaciw/pricing-ai-rules-service](https://github.com/drgaciw/pricing-ai-rules-service) (`main` @ **`3aa45bf164469364389e096df07e591c81079f6a`**, 2026-09-01) — **not cloned on this host**; G0 is first serial worker.  
**Target:** this monorepo (`open-crm-cpq`)  
**Priority (decided 2026-09-14):** **(1) Deterministic pricing parity** → **(2) AI rule-authoring / agents**  
**Method note:** Cursor **Sequential Thinking MCP is not installed/available** in this session. Extended reasoning below follows the same step-decomposition pattern the source service uses via `SequentialThinkingMCPClient` (application-side MCP client), documented as an analysis substitute.

---

## 0. ARCHITECTURE.md alignment (architect gate)

Steer from [docs/ARCHITECTURE.md](../ARCHITECTURE.md) §§1–11. Pass = may proceed under this plan; Fail = refuse.

| Constraint | ARCHITECTURE.md | Verdict | Notes |
|------------|-----------------|---------|-------|
| Landed vs target | §2 | **PASS** | Do not treat GLM-5, NgRx, Activities, AKS, Elasticsearch, customer-portal as current. Track 1 stays in landed `pricing-service` + `libs/rules-engine`. |
| Dual-read default `true` | §7 | **PASS** | No touch of `approvals.legacy-local-read-enabled` / ADR-005. |
| ADR-006 DTOs only | §5 | **PASS** | Harvest uses existing execute/simulate DTOs + `ApiResponse<T>`; no entity leak; **no AI inside `pricing-service`**. |
| ADR-004 CSRF gateway-only | §4 | **PASS** | Do **not** import source Keycloak as IdP; domain stays `csrf.disable()` + STATELESS. |
| Runtime AI Azure gpt-4o / Spring AI 0.8.1 | §2 AI row | **PASS (Track 1)** / **HOLD (Track 2)** | Track 1 needs no AI. Track 2 Path B upgrade requires **new ADR Accepted** before any Spring AI 1.1.7 port. |
| Java 21 / Boot 4.0.6 | §2, §12 | **PASS** | Target stays Java **21** (not source 25). Boot **4.0.6** matches. |
| Paths `/pricing/execute` | §3, matrix | **PASS** | Map source `/evaluate` → target `POST /api/v1/pricing/execute` (+ simulate/bulk). New external paths need matrix row — Track 1 expects **none**. |
| Harvest owners | §3 | **PASS** | Track 1 lands only in `pricing-service` + `libs/rules-engine`. |
| Holds #238, #241, R2, Wave T, product validate delete, full SpEL, Kafka S2B-9, subscriptions FE, NgRx | §9 | **PASS** | Explicitly out of scope (§11 / §5.4). |
| GitNexus `-r` primary repo | §10 | **PASS** | Always `-r /home/username01/Projects/open-crm-cpq`; impact before symbol edits; UNKNOWN ≠ unused. |
| Jenkins CI SSOT | §8 | **PASS** | No inventing runners (#238). ITs: `JAVA_HOME` 21.0.2 + `newgrp docker` when needed. |
| Overlay Rhai / forge status edit | §8, §11 | **PASS** | Do not edit `docs/forge/00-orchestrator-status.md`. |

**Overall:** Track 1 **align**. Track 2 **hold** (Path B ADR). No forge-hold conflicts in the authorized DAG.

---

## 1. Executive verdict

`pricing-ai-rules-service` is a **mature monolith reference** (Price-Drools + Spring AI agents + CLI), not a drop-in service for this monorepo. Open CRM/CPQ has **already harvested** the highest-value deterministic pricing/rule core (waterfall, DRL catalog, rule-set CRUD, Angular CPQ surfaces). Remaining reuse is mostly **selective method/pattern harvest** into existing services—not wholesale module copy.

**Execution order (locked):** finish Track 1 (deterministic) to a clear “parity done / residual backlog” gate, then start Track 2 (AI agents) **only after** Path B ADR Accepted. Do not interleave Spring AI upgrade work into Track 1 PRs.

| Layer | Source | Target today | Reuse posture |
|-------|--------|--------------|---------------|
| Pricing / Drools engine | `price-drools-service` (monolith) | `pricing-service` + `libs/rules-engine` | **Partial — already harvested**; deepen via golden fixtures & Drools version alignment |
| AI agents / Spring AI | Embedded in same monolith (Spring AI **1.1.7**, OpenAI) | `ai-service` (`AgentRouter` / `CpqAgent`, Spring AI **0.8.1**, Azure OpenAI) | **HOLD** — pattern harvest only after Path B ADR **Accepted** |
| Rule authoring pipeline | Pipeline controllers + Liquibase tables | Rule CRUD + validate/impact on pricing-service; FE rule-sets | **Selective** (prompt feedback, compile pipeline UX) — Track 2+ |
| CLI | `price-drools-cli` (Picocli + TUI) | None | **Optional DX tool** — low priority |
| Frontend | README still mentions Next.js 16; **removed** on `main` (#8 Omaha Sports FE) | Angular Nx CRM + CPQ libs | **Do not harvest FE** — nothing product-relevant remains |
| Verticals | Sports pricing, vertical packs | SaaS/CPQ domain | **Defer / exclude** unless product asks |

---

## 2. Sequential thinking (extended)

### Thought 1 — What problem are we solving?

Not “should we merge two repos.” We need a **reuse inventory** that answers: which source capabilities close open-crm-cpq gaps faster than rewriting, without violating forge holds, gateway contracts, or ADR-006 (entities never at API boundary).

### Thought 2 — What is the source, really?

Evidence from `main` @ `3aa45bf` (2026-09-01):

- Modules: `price-drools-service` (~452 Java files), `price-drools-cli` (~17), docs/agents — **no `frontend/` tree** (removed in commit *chore: remove Omaha Sports Complex frontend*).
- Stack: **Java 25**, Spring Boot **4.0.6**, Drools **10.2.0**, Spring AI **1.1.7**, PostgreSQL, Redis, Keycloak, Liquibase, Prometheus.
- Surface: pricing evaluate/batch, rule/rule-set CRUD + validate/compile, CPQ controller, optimization, AI orchestration/metrics/RL, **15+ business-agent controllers**, rule pipeline / prompt feedback, sports & vertical packs.
- Path convention: servlet mappings `/v1/*` + context-path `/api` → effective `/api/v1/...`.
- Prior internal work: `.kilo/artifacts/endpoint-parity-matrix.md`, golden fixtures under `.kilo/artifacts/golden-fixtures/`, abandoned Python port.

### Thought 3 — What is the target, really?

Landed forge (waves **A–W COMPLETE**; T skipped): microservices under `backend/services/*`, Angular under `frontend/`.

Relevant target surfaces:

| Area | Location |
|------|----------|
| Deterministic pricing | `pricing-service` — execute/simulate, waterfall, `FinancialMetrics`, customers, rules/rule-sets |
| DRL assets | `pricing-service/src/main/resources/rules/*.drl` (8 files) |
| Rules lib | `backend/libs/rules-engine` (Phase 0 Drools evidence) |
| Config rules (not Drools) | `product-service` CRUD + `configuration-engine` evaluate |
| AI | `ai-service` — large agent scaffold; many stubs; Azure OpenAI **gpt-4o** |
| FE | `frontend/libs/cpq/{rule-sets,pricing-*,pricing-simulator,...}` |

Stack deltas that block naive copy:

| Dimension | Source | Target |
|-----------|--------|--------|
| Java | 25 | **21** |
| Drools (BOM) | 10.2.0 | Parent still **8.44.0.Final** (Phase 0 spike validated 10.2.0; parent not bumped) |
| Spring AI | 1.1.7 / OpenAI / `ChatModel` | **0.8.1** / Azure / `ChatClient`-oriented |
| Package | `com.cascade.price.drools` | `com.opencrm.*` |
| Schema tool | Liquibase | Flyway |
| Security edge | Embedded Keycloak | Gateway JWT / CSRF edge (ADR-004) |
| API envelope | Source DTOs | `ApiResponse<T>` + MapStruct (ADR-006) |

### Thought 4 — What was already harvested? (avoid re-harvest)

Documented in Phase 0:

- [`docs/phase0/phase0-drools-compatibility.md`](../phase0/phase0-drools-compatibility.md) — Drools 10.2.0 spike on `rules-engine`.
- [`docs/phase0/phase0-rule-catalog-reconciliation.md`](../phase0/phase0-rule-catalog-reconciliation.md) — mapped legacy `PricingRules.drl` → overlap / additive / defer.
- [`docs/phase0/phase0-ai-stack-decision.md`](../phase0/phase0-ai-stack-decision.md) — Path A (keep 0.8.1) vs Path B (upgrade then port). Source now **1.1.7** (memo said 1.0.0 — refresh in Path B ADR).
- Runtime concepts already in target: waterfall, financial metrics, rule validate/impact, pricing execute goldens (3 execute + 3 drl fixtures), Angular rule-sets / simulator / customers.

**Implication:** Gap analysis should score **net-new** or **materially better** source artifacts, not re-list landed CPQ basics.

### Thought 5 — Where are the real gaps?

Likely **source-ahead** capabilities not (fully) in target:

1. **AI-assisted rule authoring / validate / optimize loop** — **Track 2 HOLD**.
2. **MCP-backed sequential reasoning** — Track 2 optional later.
3. **Business-agent depth** — Track 2 HOLD.
4. **Rule pipeline governance** — defer past Track 2 first cut.
5. **Golden fixture corpus** beyond the 6 target fixtures — **Track 1**.
6. **Ops DX** CLI — parked.
7. **Drools version drift**: Phase 0 evidence for 10.2.0; parent BOM still 8.44 — **T1-5**.

Likely **target-ahead / do-not-copy**:

- Microservice boundaries + gateway contract matrix.
- Angular CPQ UX (source FE removed).
- Configuration-engine product rules vs pricing Drools separation.
- Forge holds (dual-read flip, NgRx, Activities API, full SpEL, agent Kafka #241, etc.).

### Thought 6 — Reuse modes (decision taxonomy)

| Mode | Meaning | When to use |
|------|---------|-------------|
| **A. Already landed** | Concept exists; stop | Waterfall, rule CRUD, basic DRL set |
| **B. Port method** | Copy algorithm / DRL / fixture into existing class | Metrics calc, DRL additive rules, goldens |
| **C. Port pattern** | Re-implement against target contracts | Agent tools, ChatModel→ChatClient adapters |
| **D. Extract shared lib** | New `backend/libs/*` | Only if ≥2 services need same code |
| **E. Optional companion** | Separate optional tool | CLI |
| **F. Exclude** | Domain/vertical/noise | Sports pricing, Omaha FE, abandoned Python |

### Thought 7 — Risks if we harvest carelessly

- **Monolith re-injection** into `pricing-service` (AI + Drools + pipeline in one process) undoes service split.
- **Spring AI version cliff** — copy-paste from 1.1.7 into 0.8.1 will not compile.
- **Java 25 APIs / language features** may not build on target JDK 21.
- **Duplicate agent catalogs** — source business agents vs target `agents/cpq-agent-*.md` + `ai-service` packages need ID mapping, not dual registries.
- **Security**: source Keycloak realm + permit lists ≠ gateway CSRF/JWT model.
- **Contract drift**: source `/api/v1/pricing/evaluate` vs target `/api/v1/pricing/execute` (names + payloads differ).

### Thought 8 — Recommended strategy

Implement **Track 1 Mode B** items per §10 PR DAG only. Track 2 stays analysis/ADR-draft until Path B Accepted.

---

## 3. Current state snapshot (evidence)

### 3.1 Source module map

```
pricing-ai-rules-service/
  price-drools-service/     # Spring Boot monolith (pricing + Drools + AI + pipeline)
  price-drools-cli/         # Picocli HTTP client + TUI
  docs/specs/               # PRD, TECH_SPEC, AI_SPEC
  .kilo/artifacts/          # endpoint parity + golden fixtures
  (frontend removed on main)
```

Notable controller groups:

- Core: `PricingController`, `RuleController`, `OptimizationController`, `CpqController`
- AI infra: `AIOrchestrationController`, `AIMetricsController`, `RuleOptimizationController`, `PerformanceAnalysisController`
- Agents: `RuleAuthor*`, `RuleValidator*`, `RuleOptimizer*`, strategist/negotiator/compliance/geo/migration/…  
- Pipeline: `RulePipeline*`, `PromptFeedback*`, `PromptVariant*`, `PipelineDashboard*`
- Vertical: `SportsPricingController`, `VerticalPackController` → **exclude by default**

### 3.2 Target owner map (where harvests would land)

| Capability class | Target owner | Notes |
|------------------|--------------|-------|
| DRL / KieSession / compile | `pricing-service` + `libs/rules-engine` | Prefer lib for engine; service for API |
| Execute / simulate / waterfall / metrics | `pricing-service` | Align names to contract matrix |
| Rule / rule-set lifecycle UI | Angular `rule-sets` + pricing APIs | Already present |
| Product config rules | `product-service` / `configuration-engine` | **Not** Drools harvest |
| LLM agents / orchestration | `ai-service` only | Do not put Spring AI in pricing-service |
| Gateway paths | `gateway-service` + `CPQ_API_CONTRACT_MATRIX.md` | Any new path needs matrix row |
| FE experiences | Angular Nx only | No Next.js import |

### 3.3 Prior Phase 0 dispositions (legacy DRL)

From rule-catalog reconciliation — treat as baseline, re-verify on pinned source SHA:

| Legacy rule themes | Disposition |
|--------------------|-------------|
| ARR/TCV/ACV/CLV calculators | Additive as metrics (often better as Java `MetricsCalculator`, not DRL) |
| Volume/TCV/ACV tier policies | Partial overlap with seeded volume/margin rules |
| Churn / enterprise bundle | Additive / defer |
| Sports / vertical packs | Out of CPQ scope |

### 3.4 GitNexus notes (primary index)

Index: `-r /home/username01/Projects/open-crm-cpq` (stale ~15 commits — skip analyze unless needed).

| Symbol | Impact (upstream) | Confirm |
|--------|-------------------|---------|
| `MetricsCalculator` | **UNKNOWN** (0 edges) | `rg` shows callers: `PricingExecutionService`, golden + unit tests — **not unused** |
| `PricingExecutionService` (Java) | Disambiguate vs FE TS class; use `--file` | Confirm with `--file backend/services/pricing-service/.../PricingExecutionService.java` |
| `drools.version` property | Parent BOM change → HIGH CI risk | Attribute failures after T1-1…T1-4 green |

---

## 4. Gap analysis workplan (how to conduct it)

Work is split into **Track 1** then **Track 2**. G0 is shared and **serial**. Track 2 **implementation** stays gated on Path B ADR Accepted (analysis/ADR draft may proceed after Track 1 gate; **no Cursor workers for Spring AI 1.1.7 ports** until then).

### Phase G0 — Access & freeze (shared, serial, 0.5 day)

**Host state:** source repo is **not** cloned. Pin is known via `gh`: `3aa45bf164469364389e096df07e591c81079f6a`.

1. **First serial worker only** — clone to a local path (e.g. `/home/username01/Projects/pricing-ai-rules-service`) and `git checkout 3aa45bf164469364389e096df07e591c81079f6a`.  
2. Confirm local build: `price-drools-service` `./mvnw -DskipTests package` (needs **JDK 25** on worker — target harvest still compiles on **JDK 21**).  
3. Export OpenAPI or controller inventory (reuse/update `.kilo/artifacts/endpoint-parity-matrix.md`).  
4. List golden fixtures and map to target fixture dirs under `pricing-service` tests.  
5. Write `docs/plans/pricing-ai-rules-capability-matrix-t1.md` stub with pinned SHA.

**Exit:** local clone at pinned SHA + endpoint inventory + fixture map. **Do not** start T1-1…T1-5 in parallel with G0.

---

### Track 1 — Deterministic pricing parity (priority 1)

#### Phase G1-T1 — Capability matrix, pricing/rules only (0.5–1 day)

Build rows **only** for:

1. Pricing evaluate / execute / batch / recalculate vs target execute/simulate/bulk  
2. Waterfall step model & audit trail  
3. Financial metrics (ARR/TCV/ACV/CLV and related)  
4. Rule / rule-set lifecycle & Drools compile/validate/cache  
5. DRL corpus + Phase 0 additive/defer residuals  
6. Golden fixtures ↔ target `@Tag("unit")` / IT fixtures  

Defer to Track 2 matrix: AI controllers, agents, MCP, pipeline/prompt, RL.

**Exit:** Track 1 matrix; top **8–12** deterministic candidates ranked.

#### Phase G2-T1 — Deep compare (0.5–1 day)

For each top Track 1 candidate:

1. Diff algorithm / DTO fields vs `pricing-service` + `libs/rules-engine`.  
2. Check JDK 21 compatibility (no Java 22+ APIs).  
3. Map fixture I/O to `POST /api/v1/pricing/execute` (and simulate) contracts — not source `/evaluate` names blindly.  
4. Propose landing PR shape (fixtures → metrics/DRL → Drools BOM bump order — see §10).  
5. GitNexus impact notes for symbols likely to change.

**Exit:** Track 1 harvest backlog with named acceptance tests.

#### Phase G3-T1 — Decide & gate (0.25 day)

Produce:

1. **Do now (Track 1)** — Mode B items, no AI upgrade  
2. **Defer within pricing** — e.g. batch semantics if product-unused  
3. **Won’t do** — sports/verticals, Liquibase-as-is, evaluate-path rename without matrix update  

**Track 1 done gate:** residual deterministic gaps either closed, ticketed with owner, or explicitly won’t-do. Only then open Track 2 **ADR** work (still no agent ports).

#### Phase G4-T1 — Pilot PRs → **see §10 implementation DAG**

---

### Track 2 — AI rule-authoring / agents — **HOLD**

**Architect: do not authorize Cursor workers for Spring AI 1.1.7 ports, ChatModel adapters, or RuleAuthor/Validator/Optimizer implementation until a Path B ADR is Accepted.**

Allowed before Accept (docs only, optional, after Track 1 gate):

- Refresh Phase 0 memo → **draft** Path B ADR (Azure OpenAI **only** — see §9).  
- Capability matrix T2 / backlog T2 as documentation.

Blocked until Accept:

- Upgrade `ai-service` to Spring AI 1.1.7.  
- Port source agents/tools.  
- Dual-provider OpenAI + Azure experiments.

#### Phase G1–G4-T2 (reference only — not authorized)

See prior draft phases in git history if needed; **frozen** here. First authorized AI pilots after Accept remain: thin adapter smoke → RuleValidator → RuleAuthor → RuleOptimizer.

---

## 5. Recommendations under locked priority

### 5.1 Track 1 — Do now (deterministic)

#### 5.1a Metrics vs DRL ownership (decided)

**Decision: Hybrid.**

| Concern | Owner | Rationale |
|---------|-------|-----------|
| **Derive** ARR, TCV, ACV, CLV (and similar pure financials) | Java `MetricsCalculator` → `FinancialMetrics` on execute/simulate results | Already the target pattern; Phase 0 called these additive as pre-processing; arithmetic belongs in unit-tested Java, not salience-ordered DRL |
| **Policy that consumes metrics** (e.g. volume-by-ARR, TCV tiers, ACV minimum commitment, churn adjustment) | DRL in `pricing-service` rule sets | Business-editable thresholds/conflict resolution stay in Drools; facts read precomputed metrics from context |
| Source `PricingRules.drl` “Calculate ARR/TCV/ACV/CLV” rules | **Do not port as DRL** | Treat as specification for `MetricsCalculator` parity tests / golden fixtures |
| Source rules that only adjust price from those metrics | **Port/adapt as DRL** (or skip if Phase 0 seeded rules already cover) | Map via G1-T1 matrix against the 8 seeded rules |

**Rejected alternatives:** all-Java (loses Drools for metric-gated commercial policy); all-DRL calculators (duplicates `MetricsCalculator`, weaker tests, fights ADR-006-friendly service layering).

| Candidate | Mode | Land in | Why |
|-----------|------|---------|-----|
| Golden fixtures (evaluate→execute mapping, waterfall, rule-validate/compile, amendment, renewal) | B | `pricing-service` tests | Cheap parity; source already curated |
| Metric **derivation** parity (ARR/TCV/ACV/CLV) from source calculate-* rules as **spec** | B | `MetricsCalculator` + goldens | Hybrid §5.1a — not DRL |
| Metric-**gated** policy DRL still missing after Phase 0 | B | `resources/rules` + seed if needed | Hybrid §5.1a — policy only |
| Rule compile/validate edge cases from source `DroolsService` | B/C | `RuleService` / `rules-engine` | Strengthen agent-contract validate |
| Drools BOM alignment to **10.2.0** | B | parent `pom.xml` + `rules-engine` | **T1-5 only** — after fixture/metric/DRL/validate PRs |

### 5.2 Track 2 — HOLD until Path B ADR Accepted

| Candidate | Mode | Land in | Blocker |
|-----------|------|---------|---------|
| RuleAuthor / Validator / Optimizer agents + tools | C | `ai-service` | Path B ADR not Accepted |
| `BusinessAgentOrchestrator` multi-step plans | C | `ai-service` orchestration | Same + avoid dual orchestrators |
| Prompt packs from `PRICING_AGENTS.md` / `DROOLS_AGENTS.yaml` | C | `agents/*.md` only | Docs-only OK; no dual registries |
| Sequential-thinking / Context7 MCP **clients** | C | `ai-service` (optional) | Defer until authoring agents prove value |
| Rule pipeline + prompt feedback schema | C | TBD ADR | **Not** in first Track 2 cut |
| RL pricing components | C/F | existing `RlPricingActorAgent` stub | Defer |

### 5.3 Parked (below priority 1–2)

| Candidate | Mode | Notes |
|-----------|------|-------|
| `price-drools-cli` | E | Optional DX after Track 1 |
| Static HTML dashboards | E/F | Prefer Angular if product wants UI |
| Rule pipeline productization | — | Separate decision after Track 2 authoring agents |

### 5.4 Explicit excludes

- Next.js / Omaha Sports frontend (deleted).  
- `SportsPricing*` / sports DRL.  
- Python port artifacts.  
- Liquibase changelogs as-is (translate concepts → Flyway if needed).  
- Embedded Keycloak as target IdP.  
- Copying AI stack into `pricing-service` (keep AI in `ai-service`).  
- Replacing Angular rule-sets with any source UI.  
- Interleaving Track 2 implementation into Track 1 PRs.  
- Forge holds: Activities, #238, #241, R2, Wave T/T1/T2, product-validate delete, full SpEL, Kafka S2B-9, subscriptions FE, NgRx, dual-read flip, SECURITY_OPEN, CORS `*.azurewebsites.net`.

### 5.5 Architecture rules for any harvest PR

1. **Owner service** must match §3.2; gateway paths updated in contract matrix **only if** a new external path is added (Track 1: expect none).  
2. **DTOs at boundary**; no entity leak (ADR-006).  
3. **GitNexus impact** before editing symbols (`-r /home/username01/Projects/open-crm-cpq`); warn on HIGH/CRITICAL/`UNKNOWN` and confirm with `rg`.  
4. Prefer **tests/fixtures first** (TDD) using source goldens as expected vectors.  
5. No forge **held** items unless wave plan lifts the hold.  
6. Track 2 harvests require an **Accepted** Spring AI Path B ADR (Azure-only — §9).

---

## 6. Suggested deliverables of the analysis engagement

1. This plan (architect **APPROVED-WITH-GATES**).  
2. `docs/plans/pricing-ai-rules-capability-matrix-t1.md` — Track 1 matrix at pinned SHA (G0/G1).  
3. `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` — Track 1 ranked PRs (align with §10).  
4. After Track 1 gate: `...-matrix-t2.md` + `...-backlog-t2.md` (**docs only**).  
5. ADR draft (Track 2 prerequisite): Spring AI Path B — **Azure OpenAI only**, target version aligned to source **1.1.7** (refresh Phase 0).  
6. Optional later ADR: rule pipeline ownership (pricing vs ai) — not blocking Track 1.

---

## 7. Blast radius (if harvest proceeds)

| Harvest theme | Likely blast radius |
|---------------|---------------------|
| Fixtures / DRL / metrics | `pricing-service`; quote-service client views if `FinancialMetrics` fields change; FE models only if wire fields added |
| Drools 10.2.0 bump | All Drools consumers (`pricing-service`, `rules-engine`, CI) — **HIGH** attributable risk |
| Agent port | `ai-service`, gateway agent routes — **HOLD** |
| Pipeline tables | Flyway, possibly new admin UI — out of Track 1 |
| CLI | Repo tooling only |

---

## 8. Out of scope for this plan document

- Implementing harvest PRs in this architect pane.  
- Re-indexing GitNexus on the external repo.  
- Enabling Sequential Thinking MCP in Cursor.  
- Changing forge holds / editing `00-orchestrator-status.md`.  
- Track 2 Spring AI upgrade or agent ports.

---

## 9. Decisions & remaining questions

### Decided (architect 2026-09-14)

| Decision | Value |
|----------|--------|
| Priority order | **1 = deterministic pricing parity**, then **2 = AI rule-authoring/agents** |
| Interleave Track 2 into Track 1? | **No** |
| FE harvest | **No** (source FE removed) |
| ARR/TCV/ACV/CLV implementation | **Hybrid** — see §5.1a |
| Source pin | **`3aa45bf164469364389e096df07e591c81079f6a`** (`main` tip as of 2026-09-14 via `gh`) |
| G0 clone | **Serial first worker** — not parallel with T1 harvest PRs |
| Drools **10.2.0** bump timing | **Authorize as T1-5** (last Track 1 code PR), after T1-1…T1-4 green. **Not** held for a release freeze. Phase 0 evidence supports bump; parent still `8.44.0.Final` so order is for failure attribution, not product freeze. |
| Track 2 Path B provider | **Azure OpenAI only** (runtime gpt-4o). **Reject** dual-provider (source OpenAI-compatible + Azure) for the first Path B ADR. Dual-provider may be a later ADR if product asks. |
| Track 2 workers | **HOLD** until Path B ADR **Accepted** |

### Closed — no longer open

1. ~~Track 1 metrics shape~~ → hybrid §5.1a.  
2. ~~Drools 10.2.0 bump timing~~ → T1-5 after T1-1…T1-4.  
3. ~~Path B Azure-only vs dual-provider~~ → Azure-only.

### Explicitly deferred (not blocking Track 1)

- Rule pipeline / prompt-feedback productization ADR.  
- CLI companion.  
- Batch/recalculate semantics if unused by FE.  
- Dual-provider LLM ADR (after Path B Azure lands, if ever).

---

## 10. Track 1 implementation PR DAG (authorized)

**Maven env (every code PR):**

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
# Unit tests (no docker):
cd backend && ./mvnw -B -pl services/pricing-service -am test -DskipITs
# ITs when Testcontainers needed and login lacks docker group:
newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/21.0.2
cd backend && ./mvnw -B -pl services/pricing-service -am test
EOF
# rules-engine-only (T1-4 / T1-5):
./mvnw -B -pl libs/rules-engine,services/pricing-service -am test -DskipITs
```

**Parallelism:** G0 alone first. Then G1–G3 docs may run as one worker. T1-1 → T1-5 are **serial** (each depends on prior green). No FE / gateway / ai-service files in Track 1.

| ID | Owner | Branch | Exclusive files (approx.) | Depends | Acceptance tests / gates |
|----|-------|--------|---------------------------|---------|--------------------------|
| **T1-G0** | docs + local clone | `chore/pricing-ai-rules-g0-pin` | Clone `/home/username01/Projects/pricing-ai-rules-service` @ `3aa45bf`; optional `docs/plans/pricing-ai-rules-capability-matrix-t1.md` (SHA + fixture inventory only) | — | Clone exists; `git rev-parse HEAD` = `3aa45bf…`; inventory lists source goldens ↔ target `fixtures/pricing/{execute,drl}` |
| **T1-D1** | docs | `docs/pricing-ai-rules-t1-matrix` | `docs/plans/pricing-ai-rules-capability-matrix-t1.md`, `docs/plans/pricing-ai-rules-harvest-backlog-t1.md` | T1-G0 | Matrix rows for execute/simulate/metrics/DRL/validate only; no AI rows as “do now” |
| **T1-1** | `pricing-service` | `test/pricing-golden-fixtures-harvest` | `backend/services/pricing-service/src/test/resources/fixtures/pricing/**`, `PricingExecuteGoldenFixtureTest.java`, optional `PricingDrulesGoldenFixtureTest.java` | T1-G0 (+ prefer T1-D1) | New/adapted goldens map source evaluate→**execute**; existing 01–03 + drl fixtures still pass; `@Tag("unit")` green |
| **T1-2** | `pricing-service` | `feat/pricing-metrics-calculator-parity` | `MetricsCalculator.java`, `FinancialMetrics.java` (only if fields needed), `MetricsCalculatorTest.java`, related execute goldens asserting metrics | T1-1 | ARR/TCV/ACV/CLV (+ existing churn/discount fields) match source calculate-* **spec** vectors; GitNexus impact + `rg MetricsCalculator` (UNKNOWN resolved); no DRL calculator ports |
| **T1-3** | `pricing-service` | `feat/pricing-metric-gated-drl` | `src/main/resources/rules/*.drl` (additive only), seed/Flyway **only if** rule-set seed required, drl golden JSON under `fixtures/pricing/drl/` | T1-2 | Policy DRL only (no Calculate ARR/TCV/ACV/CLV DRL); Phase 0 overlap skipped; sports excluded; compile + drl goldens green |
| **T1-4** | `rules-engine` + `pricing-service` | `feat/pricing-drools-validate-parity` | `backend/libs/rules-engine/src/main/java/**` (compiler/helpers), `RuleService.java` (+ tests), no parent `drools.version` yet | T1-3 | Compile/validate edge cases from source `DroolsService` adapted to JDK 21; `RuleServiceTest` + rules-engine tests green; **no** new `/api/v1/**` path |
| **T1-5** | parent BOM | `chore/drools-bom-10-2-0` | `backend/pom.xml` (`drools.version` **8.44.0.Final → 10.2.0**), `libs/rules-engine` tests, full pricing DRL corpus compile suite | T1-4 | All 8 DRLs compile; `rules-engine` + `pricing-service` test suites green; CI contract unchanged; document Phase 0 evidence link in PR |

**Rejected DAG shapes:** Drools bump before fixtures; AI/agent PRs in this wave; renaming execute→evaluate; Keycloak; putting Spring AI in `pricing-service`.

### Herdr / worker layout (recommendation)

| Pane | Role |
|------|------|
| 1 | G0 clone + pin verify (serial) |
| 2 | T1-D1 matrix/backlog docs |
| 3 | T1-1…T1-5 sequential implementer (one PR at a time) |
| — | **No** ai-service / Spring AI pane |

---

## 11. Holds (do not implement)

| Hold | Status |
|------|--------|
| Track 2 Spring AI 1.1.7 / agent ports | **HOLD** until Path B ADR Accepted |
| Dual-provider OpenAI+Azure | **HOLD** (rejected for first Path B ADR) |
| Activities `/api/v1/activities/**` | Hold |
| CI #238 | Hold |
| Agent Kafka #241 | Hold |
| Dual-read flip | Hold |
| NgRx | Hold |
| Subscriptions FE | Hold |
| Full SpEL / OpenL | Hold |
| Kafka S2B-9 | Hold |
| Gateway domain validation | Hold |
| Delete product-service validate | Hold |
| R2 price-book list-row delete | Hold |
| Named Wave T / T1 / T2 | Skipped / do not name |
| GLM-5 as production runtime | Not landed |
| Source Keycloak as IdP | Exclude |
| AI inside `pricing-service` | Exclude |

---

## Appendix A — Quick reference links

| Resource | URL / path |
|----------|------------|
| Source repo | https://github.com/drgaciw/pricing-ai-rules-service |
| Source pin | `3aa45bf164469364389e096df07e591c81079f6a` |
| Target contract matrix | `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` |
| Phase 0 AI decision | `docs/phase0/phase0-ai-stack-decision.md` |
| Phase 0 Drools | `docs/phase0/phase0-drools-compatibility.md` |
| Phase 0 rule catalog | `docs/phase0/phase0-rule-catalog-reconciliation.md` |
| Operating architecture | `docs/ARCHITECTURE.md` |
| Architect skill map | `.grok/skills/opencrm-architect/SKILL.md` |

## Appendix B — Method caveat

**Sequential Thinking MCP (Cursor):** not present in this workspace’s MCP catalog; analysis used manual multi-step decomposition instead.  
**Sequential Thinking in source code:** `com.cascade.price.drools.ai.sequential` + `SequentialThinkingMCPClient` is a **runtime product integration** candidate for `ai-service`, independent of Cursor tooling — **Track 2 HOLD**.
