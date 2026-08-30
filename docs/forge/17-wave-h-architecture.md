# Wave H architecture — VALIDATES expression ∥ F6 AI validate ∥ CRM Bolt hygiene ∥ S2B-9 thin cache

**Status:** COMPLETE — merged to main (H1∥H2∥H3∥H4∥H5∥H6 → H7∥H8)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** worktree / `origin/main` @ `e427847` (Waves A–G COMPLETE)  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index **27 commits behind** HEAD — `ProductRuleEvaluationCore` / `ProductConfigurationRuleClient` **missing from index** → treat **UNKNOWN** until re-analyze); GitHub open issues/PRs; tree inventory; `docs/forge/16-wave-g-architecture.md`; `docs/forge/impl/g1–g9`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`; ADR configuration (Accepted)

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees; only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Agent-stack migration; out of this wave |
| **Delete / retire product-service validate** | **HOLD** | Dual-run parity not calendar-ready; prefer defer (PM non-negotiable unless HIGH gate documented + proven) |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` default → false** | **DEFER (prep only)** | G4 dual-read still default `true`; flipping without legacy-empty evidence is **HIGH** dual-store cutover |
| **Full SpEL / Drools / OpenL VALIDATES** | **REJECT day-1** | Over-scope; G1 fixture already implies constrained `attr:key=value` dialect |
| **S2B-9 Kafka / event invalidation** | **DEFER** | Event bus + product publishers not ready; thin TTL cache advances freshness without #241 |
| **Bolt #430 WhatsApp** | **REJECT** | Legacy `cpq_beta` / communication path; not CPQ cutover |
| **Bolt #412 product validate O(N)** | **DEFER** | Touches transitional product validator; CE owns agent evaluate post-F/G — low leverage vs dual-run drift risk |
| **Bolt #411 / #417** | **SUPERSEDED** | Fold into single #437-shaped JPA projections PR |
| **✅ Track 1: VALIDATES `conditionExpression` engine** | **IN** | G1 left `// Attribute/expression-only VALIDATES: deferred`; DTO + product column already exist; session has selection attrs |
| **✅ Track 2: F6 AI structured `ConfigurationValidationResult`** | **IN** | Wave F optional never landed; `CpqAgentToolClients` still opaque `JsonNode`; quote already structured (G5) |
| **✅ Track 3: CRM Bolt hygiene (#437 ∥ #436 ∥ #403 remainder)** | **IN** | Open Jules PRs; graph **LOW** on pipeline/status aggregates; kanban still O(stages×deals); deals+orgs still raw `(input)` |
| **✅ Track 4: S2B-9 thin TTL rule cache** | **IN** | Sync REST still hot-path on every evaluate; TTL cache is ADR-aligned first step without Kafka |

**PM authorization used:** “Architect picks Wave H from GitNexus+GitHub evidence.”

**Chosen theme:** **Largest safe forge** — finish S2B-8 expression gap + close F6 agent-contract hygiene + land safe CRM Bolt performance PRs + thin S2B-9 cache. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read default flip.

---

## 1. Problem / evidence

### H-T1 — VALIDATES expression engine still stubbed (MEDIUM product)

| Layer | Today (`e427847`) | Target |
|-------|-------------------|--------|
| `ProductRuleEvaluationCore.evaluateValidates` | Hard fail only when `targetProductId` missing from selection; comment defers expression | Constrained expression dialect evaluates when `conditionExpression` set |
| `ProductConfigurationRuleDto.conditionExpression` | Field present (G1) | Fed through; unit fixtures for `attr:color=red`-class expressions |
| product-service | `ConfigurationRule.conditionExpression` column + DTO already exist | Unchanged CRUD; CE consumes |
| Stateless evaluate | `List<Long>` only — **no attribute bag** | Keep agent body; expression-only rules **no-op when attrs absent** (avoid false agent failures); session path supplies attrs |
| Session `ConstraintSolver` | Maps numeric selection values → product IDs only | Pass `session.selections` (and productId) as attribute map into core |

**Evidence:**

```text
ProductRuleEvaluationCore.java:160 — "Attribute/expression-only VALIDATES: deferred (no expression engine in G1)"
StatelessConfigurationEvaluatorTest — fixture conditionExpression "attr:color=red" (target-only assert today)
InternalConfigurationEvaluateController — @RequestBody List<Long> (agent-contract body unchanged)
```

**GitNexus:** `ProductRuleEvaluationCore` **not in index** (stale 27 commits) → **UNKNOWN**. Text callers: `StatelessConfigurationEvaluator`, `ConstraintSolver`, unit tests. Re-analyze before H1 edits. Treat behavior change as **MED product** even if graph returns LOW after analyze.

### H-T2 — AI tools still treat validate as opaque (LOW–MED contract)

| Consumer | Post-G state |
|----------|--------------|
| quote `ProductServiceClient.validateConfiguration` | Returns `ConfigurationValidationResult` (G5) |
| `CpqAgentToolClients.validateConfiguration` | `JsonNode` via `postJson`; no `valid` / `violations[]` assertion |
| Wave F **F6** | Optional — **never implemented** (no `docs/forge/impl/f6-*`) |

**GitNexus:** `CpqAgentToolClients` upstream **LOW** (1 direct → `CpqAgentTools`). `validateConfiguration` (AI) **LOW**. Agent-contract still warrants fixture tests (treat opaque→structured as **LOW–MED**).

### H-T3 — Open Bolt CRM hygiene (LOW graph / MED UX perf)

Confirmed open issues: **only #238, #241** (held).

Confirmed open Bolt PRs (Jules):

| PR | Title | Tree status @ `e427847` | Wave H |
|----|-------|-------------------------|--------|
| **#437** | JPA projections Lead/Deal aggregates | Still `List<Object[]>` on `countGroupByStatus` / `findPipelineSummary` | **IN** (supersedes #411/#417) |
| **#411**, **#417** | Earlier projection variants | Same surface as #437 | **SUPERSEDED** by H3 |
| **#436** | Deal kanban O(N) | `columns` computed still `filter` per stage → O(stages×deals) | **IN** |
| **#403** | SearchInput lists | Leads + contacts already on `SearchInputComponent`; **deals + orgs** still raw `(input)` | **IN** (remainder only) |
| **#430** | WhatsApp lookup | Legacy | **OUT** |
| **#412** | product validate O(N) | Transitional product path | **DEFER** |

**GitNexus:**

| Symbol | Risk | Note |
|--------|------|------|
| `DealService.getPipelineSummary` | **LOW** (1 direct) | Controller HTTP; FE `loadPipelineSummary` via text |
| `LeadService.getStatusCounts` | **LOW** (BE) / FE method **UNKNOWN** | Confirm with `rg` |
| `DealKanbanComponent` | **LOW** graph (routes → app) | Pure FE recompute |
| Controller HTTP entrypoints | often **UNKNOWN** | UNKNOWN ≠ unused |

### H-T4 — Rule fetch uncached (S2B-9 partial) (MEDIUM perf / LOW–MED correctness)

Every evaluate calls `ProductConfigurationRuleClient.getRulesForProduct` per product ID with **no cache**. ADR frozen #3: sync REST first; cache = S2B-9. Full event invalidation needs product publishers + bus (#241 adjacency) → **thin TTL** only this wave.

**GitNexus:** `ProductConfigurationRuleClient` **not in index** → **UNKNOWN**. Text callers: `StatelessConfigurationEvaluator`, `ConstraintSolver` (+ tests).

### H-T5 — Legacy approval dual-read still default true (HIGH if flipped)

```yaml
approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

G4 single-writer is on main; default still prefers legacy fallback. **Wave H includes prep only** (tests proving flag-off behavior, matrix note, ops runbook) — **does not** flip default without PM HIGH gate + empty-legacy evidence.

---

## 2. Decisions + rejected alternatives

### Track 1 — VALIDATES expression

| Topic | Decision | Rejected |
|-------|----------|----------|
| Dialect | Constrained **`attr:<key>=<value>`** (and documented minimal extensions: `attr:key!=value`, `in:` list later if needed) matching G1 fixture | Full SpEL, MVEL, Drools DRL, OpenL day-1 |
| Core API | Extend `ProductRuleEvaluationCore.evaluate(..., attributes)` optional `Map<String,String>` (nullable/empty = no expression eval) | Break `List<Long>` agent body |
| When expression present + attrs empty | **Skip expression branch** (log debug); keep `targetProductId` VALIDATES behavior | Fail-closed all VALIDATES when attrs missing (breaks agent List path) |
| Session wiring | `ConstraintSolver` passes `session.getSelections()` as attributes (keys = option keys) | Invent parallel attribute store |
| product validate | Untouched | Port expression engine into product-service |
| Gateway | Unchanged | Embed expression evaluation |

### Track 2 — F6 AI

| Topic | Decision | Rejected |
|-------|----------|----------|
| Parse | Prefer typed `ApiResponse<ConfigurationValidationResult>` (or JsonNode walk asserting `data.valid` + `data.violations`) inside validate path; keep `CpqToolResponse` envelope for tools | Silent continue as opaque forever |
| Other tools | Pricing / line-items may stay JsonNode this wave | Big-bang rewrite all tool clients |
| quote client | Already structured (G5) — **no forced rewrite**; optional shared helper only if trivial | Reopen S2D-3a |

### Track 3 — CRM Bolt

| Topic | Decision | Rejected |
|-------|----------|----------|
| Projections | Land #437-shaped interfaces + query aliases; update unit mocks | Keep `Object[]` casts |
| Kanban | Single-pass bucket `Map` O(N+stages) per #436 intent; Vitest | Micro-optimize reduce only |
| SearchInput | **deals-list + organization-list only**; do not regress leads/contacts | Re-apply #403 wholesale (merge conflicts / double work) |
| WhatsApp / #412 | Out / defer | Bundle into Day-1 |

### Track 4 — S2B-9 thin cache

| Topic | Decision | Rejected |
|-------|----------|----------|
| Shape | In-process TTL cache (Caffeine or simple `ConcurrentHashMap`+expiry) on `getRulesForProduct`; config TTL (e.g. 30–60s) | Kafka invalidation this wave; Redis shared cache day-1 |
| Semantics | Cache **misses and hits** per productId; invalidate on TTL only | Infinite cache; invent product→CE event contract |
| Tests | Unit clock/TTL + verify RestClient not re-hit within TTL | — |

### Dual-read

| Topic | Decision | Rejected |
|-------|----------|----------|
| Default | **Remain `true`** | Flip to `false` without HIGH gate |
| Prep PR | Flag-off tests + matrix/ops note for cutover | Silent env flip in forge |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **CE expression** ∥ **AI client** ∥ **CRM BE projections** ∥ **FE kanban** ∥ **FE SearchInput** ∥ **CE rule cache**.

```text
Track 1 (VALIDATES expr)     Track 2 (F6)        Track 3 (CRM Bolt)              Track 4 (S2B-9)
────────────────────────     ────────────        ──────────────────              ──────────────
H1 expression core           H2 AI structured    H3 JPA projections (#437)       H6 thin TTL cache
   + ConstraintSolver attrs     validate parse   H4 deal kanban O(N) (#436)
                                                 H5 SearchInput deals+orgs (#403)
        │                         │                    │                              │
        └─────────────────────────┴────────────────────┴──────────────────────────────┘
                                              ▼
                               H7 dual-read cutover PREP (default stays true)
                                              ▼
                               H8 docs/matrix + forge closeout
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **H1** | `feat(configuration-engine): VALIDATES conditionExpression dialect` | `ProductRuleEvaluationCore` (+ optional attrs overload); `ConstraintSolver` pass selections; fixtures `attr:key=value`; keep List\<Long\> evaluate; `docs/forge/impl/h1-validates-expression.md` | **MED** | Expression-only VALIDATES hard-fail when attrs mismatch; targetProductId path unchanged; agent List path no false fails when attrs empty; no gateway/product validate edits |
| **H2** | `feat(ai): parse ConfigurationValidationResult in validate tool` | `CpqAgentToolClients` (+ tests); assert `valid` / violations when present; `docs/forge/impl/h2-ai-validate-parse.md` | **LOW–MED** | Unit fixture with structured gateway payload green; pricing/line-item paths unchanged unless shared helper |
| **H3** | `refactor(crm): JPA projections for lead/deal aggregates` | `LeadRepository`/`DealRepository` + projection interfaces + service mapping + unit tests; salvage #437; `docs/forge/impl/h3-jpa-projections.md` | **LOW** | No `Object[]` in those two queries; unit tests green; HTTP pipeline/status envelopes unchanged |
| **H4** | `perf(crm-fe): deal kanban columns O(N)` | `deal-kanban.component.ts` (+ spec); salvage #436; `docs/forge/impl/h4-kanban-on.md` | **LOW** | Single-pass bucketing; Vitest covers totals; no API changes |
| **H5** | `perf(crm-fe): SearchInput on deals + organizations` | `deal-list`, `organization-list` (+ specs); `docs/forge/impl/h5-search-input.md` | **LOW** | Raw `(input)` gone on those two; leads/contacts untouched |
| **H6** | `feat(configuration-engine): TTL cache for product rule fetch (S2B-9 thin)` | `ProductConfigurationRuleClient` (+ config + tests); `docs/forge/impl/h6-rule-cache-ttl.md` | **MED** | Second fetch within TTL does not call RestClient; TTL expiry refetches; no Kafka |
| **H7** | `test(quote): approval legacy dual-read flag-off prep` | Quote approval tests + matrix/ops note; **default remains true**; `docs/forge/impl/h7-dual-read-prep.md` | **MED** (doc **HIGH** if anyone proposes default flip) | Tests prove routing-only when flag false; no default flip in this PR |
| **H8** | `docs(cpq): Wave H matrix + forge closeout` | Matrix S2B-8 expression / S2B-9 thin / F6 rows; orchestrator COMPLETE; `docs/forge/impl/h8-wave-h-docs.md` | **LOW** | Docs match runtime; holds listed |

### Parallelism rules

**Day-1 max safe parallel (6 panes):** **H1 ∥ H2 ∥ H3 ∥ H4 ∥ H5 ∥ H6**

| Pair | Conflict? | Rule |
|------|-----------|------|
| H1 vs H6 | Both CE; different primary files | **Parallel OK** — H1 = evaluate/constraint; H6 = `ProductConfigurationRuleClient` only. Serialize if both touch shared Spring config beans |
| H1 vs H2 | CE vs AI | Parallel |
| H3 vs H4/H5 | BE vs FE | Parallel |
| H4 ∥ H5 | Different FE libs/files | Parallel (both under `frontend/libs/crm` — avoid shared index churn) |
| H7 | quote-service | After Day-1; **do not** combine with default flip |
| H8 | Docs | After H1+H2+H6 at minimum (CRM notes can land earlier) |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, or starts #238/#241.

---

## 4. Blast radius / GitNexus risks

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| H1 `ProductRuleEvaluationCore` | Stateless evaluate; session ConstraintSolver; G dual-run fixtures | **UNKNOWN** (not indexed) — re-analyze | **MED** product |
| H1 `ConstraintSolver` | `ConfigurationSessionService` validate/complete | Was LOW pre-G; re-impact | **MED** |
| H2 `CpqAgentToolClients` | `CpqAgentTools` | **LOW** | **LOW–MED** contract |
| H3 projections | Controllers + FE pipeline/status | Service methods **LOW**; HTTP **UNKNOWN** | **LOW** |
| H4 kanban | CRM deals routes | **LOW** | **LOW** |
| H5 SearchInput | deal/org lists | FE | **LOW** |
| H6 rule client cache | All evaluate paths | Client **UNKNOWN** | **MED** (stale rules within TTL) |
| H7 dual-read prep | quote `ApprovalService` | Ambiguous name → quote uid **UNKNOWN** | Prep **MED**; flip would be **HIGH** |
| product `/validate` | Must remain | Do not delete | — |
| gateway validate | Route+adapt only | Do not embed rules | — |

**Explicit HIGH callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default in Wave H without PM HIGH gate + evidence legacy rows empty/migrated.  
2. **Index 27 commits behind** — `ProductRuleEvaluationCore`, `ProductConfigurationRuleClient` unresolved. Forge agents **must** `node .gitnexus/run.cjs analyze` (or `npx gitnexus analyze`) on tip before symbol edits; treat **UNKNOWN** as unresolved + confirm with `rg`.

**UNKNOWN callout:** quote `ApprovalService`, CE post-G symbols missing from index, CRM controller HTTP entrypoints. Empty caller sets ≠ safe to delete.

---

## 5. Herdr forge layout (6 Day-1 panes)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wh-validates-expr` | `feat/forge-h1-validates-expression` | H1 |
| 2 | `wh-ai-validate` | `feat/forge-h2-ai-validate-parse` | H2 |
| 3 | `wh-jpa-projections` | `feat/forge-h3-jpa-projections` | H3 |
| 4 | `wh-kanban-on` | `feat/forge-h4-kanban-on` | H4 |
| 5 | `wh-search-input` | `feat/forge-h5-search-input` | H5 |
| 6 | `wh-rule-cache` | `feat/forge-h6-rule-cache-ttl` | H6 |
| 7 (Day-2) | `wh-dual-read-prep` | `feat/forge-h7-dual-read-prep` | H7 |
| 8 (closeout) | `wh-docs` | `docs/forge-h8-wave-h-closeout` | H8 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Re-analyze GitNexus on tip; `impact` before symbol edits; disambiguate `ApprovalService` / `validateConfiguration` by `file_path`/`uid`; warn HIGH/CRITICAL/UNKNOWN.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, or WhatsApp #430.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- Full SpEL/Drools/OpenL expression engines  
- S2B-9 Kafka / Redis shared invalidation bus  
- Flipping `approvals.legacy-local-read-enabled` default to `false`  
- Bolt #430 WhatsApp; Bolt #412 product validate micro-opt  
- NgRx CPQ store, approval inbox FE, FULL_PREFLIGHT ERP  
- Changing agent-facing validate URL or requiring attribute bag on `List<Long>` body  

---

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave H per docs/forge/17-wave-h-architecture.md in four parallel tracks: (1) S2B-8 VALIDATES conditionExpression — constrained attr:key=value dialect in ProductRuleEvaluationCore + ConstraintSolver passes session selections as attributes; skip expression when attrs empty so agent List<Long> path does not false-fail; do not delete product validate; no gateway domain rules; (2) F6 — CpqAgentToolClients parse structured ConfigurationValidationResult (valid + violations) with unit fixtures; (3) CRM Bolt hygiene — H3 JPA projections (#437 salvage), H4 deal kanban O(N) (#436), H5 SearchInput on deals+orgs only (#403 remainder); (4) S2B-9 thin TTL cache on ProductConfigurationRuleClient — no Kafka. Day-1 parallelize H1∥H2∥H3∥H4∥H5∥H6; Day-2 H7 dual-read flag-off prep with default STILL true; H8 docs closeout. Use GitNexus impact before symbol edits; re-analyze (index was 27 behind); warn HIGH/CRITICAL/UNKNOWN. Do not invent Activities API, start #238/#241, flip legacy dual-read default, merge WhatsApp #430, or embed validation in gateway. Evidence: docs/forge/impl/h*.md done, expression fixtures green, AI validate asserts valid, CRM bolt PRs equivalent landed, TTL cache tests green, merge+push main when authorized.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–G complete on tip `e427847`  
- [x] GitHub: open issues **only #238, #241** (held); open Bolt PRs inventoried (#437/#436/#430/#417/#412/#411/#403)  
- [x] Evidence: VALIDATES expression deferred in core; AI JsonNode opaque; CRM `Object[]` + kanban O(N²) + deals/orgs raw input; rule client uncached; dual-read default true  
- [x] GitNexus: AI clients **LOW**; CRM aggregates **LOW**; CE post-G symbols **UNKNOWN** (stale index) — **warned**  
- [x] Dual-read default flip **deferred**; prep-only H7 — **rejects silent HIGH cutover**  
- [x] Product validate delete **held**; gateway domain rules **rejected**  
- [x] PR DAG max parallelism with disjoint files documented (**6** Day-1 panes)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp #430, full SpEL, Kafka S2B-9  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Forge execution authorized (`forge-wave-h` / goal §7)  
- [x] Day-1 + Day-2 merged; H8 matrix + orchestrator COMPLETE  

**PM WARNING:** Re-analyze before CE edits (**UNKNOWN** ≠ safe). Do **not** flip approval legacy dual-read default in this wave.

**WAVE H — PLANNING (architect draft).**
