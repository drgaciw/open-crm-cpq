# Wave G architecture — S2B-8 ∥ S2C-12 ∥ S2D-3a+FE CPQ HTTP (three-track parallel)

**Status:** COMPLETE — merged to main (G1∥G3∥G5∥G6∥G7∥G8 → G2∥G4 → G9)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** worktree / `origin/main` @ `3952b20` (Waves A–F COMPLETE; hybrid S2B-11 on main)  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index @ `c5075a8`, **8 commits behind** HEAD — re-analyze before symbol edits); tree inventory; `docs/forge/15-wave-f-architecture.md`; `docs/forge/04-cpq-contracts.md`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`; ADR configuration (Accepted)

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees |
| **Agent Kafka (#241)** | **HOLD** | Agent-stack migration; out of this wave |
| **Delete product-service validate** | **HOLD** | Wave F retain; contract parity not calendar-deprecation-ready |
| **Gateway embeds domain validation** | **REJECT** | ADR + forge DO NOT TOUCH |
| **Silent dual-write quote + approval-routing ApprovalRequest** | **REJECT** | HIGH dual-store risk; forbidden by PM non-negotiable |
| **✅ Track 1: S2B-8 ConstraintSolver** | **IN** | Placeholder still hardcoded; Wave F left `StatelessConfigurationEvaluator` for reuse |
| **✅ Track 2: S2C-12 quote → approval-routing BFF** | **IN** | Matrix claims delegation; local store only today — HIGH, dual-read/cutover |
| **✅ Track 3: S2D-3a + FE CPQ HTTP** | **IN** | Validate path stable post-F; FE HttpClient services exist, UI still mocks |

**PM authorization used:** “1, 2, and 3 in parallel” for Wave G.

---

## 1. Problem / evidence per track

### G-T1 — S2B-8 ConstraintSolver still placeholder (MEDIUM product / LOW graph)

| Layer | Today (`3952b20`) | Target |
|-------|-------------------|--------|
| `ConstraintSolver` | Hardcoded `color` / `option_a`/`option_b` / `premium_feature` | Real product rules: REQUIRES / EXCLUDES / RECOMMENDS / DEFAULT / VALIDATES (+ graph conflict checks) |
| Session path | `ConfigurationSessionService` → `ConstraintSolver.validate` on add/remove/validate/complete | Same API; semantics from product rules |
| Stateless path (Wave F) | `StatelessConfigurationEvaluator` + `ProductConfigurationRuleClient` — **REQUIRES/EXCLUDES only** | Share/refactor evaluator core; extend rule types; session + agent stay consistent |
| product-service | `ConfigRuleType` has all five types; `validateConfiguration` still REQUIRES/EXCLUDES only | **Retain** product validate; CE becomes full evaluator |

**Evidence:**

```text
ConstraintSolver.java — placeholder comments: "would be loaded from configuration rules"
StatelessConfigurationEvaluator — RULE_TYPE_REQUIRES / EXCLUDES only
ProductConfigurationRuleDto — id, ruleType, source/targetProductId, errorMessage (no conditionExpression yet)
ConfigurationRule (product) — conditionExpression present for VALIDATES/DEFAULT-class rules
```

**GitNexus (pre-edit):**

| Symbol | Upstream | Risk | Text confirmation |
|--------|----------|------|-------------------|
| `ConstraintSolver` | 1 direct (`ConfigurationSessionService` IMPORTS) | **LOW** graph | Session add/remove/validate/complete; tests |
| `StatelessConfigurationEvaluator` | **not in index** (post-F) | treat **UNKNOWN** until re-analyze | `InternalConfigurationEvaluateController` |
| `ConfigurationSessionService` | 0 graph callers | **UNKNOWN** | HTTP session controller — confirm with `rg` |
| `ProductConfigurationRuleClient` | (F2) | re-impact after analyze | Stateless evaluator |

**PM WARNING:** Index **8 commits behind**; `StatelessConfigurationEvaluator` missing from index → **UNKNOWN ≠ safe**. Re-analyze before G1/G2 edits. Graph LOW on `ConstraintSolver` still means **MED product risk** if session validates flip from toy rules to real rules (false completes / blocked completes).

### G-T2 — S2C-12 quote BFF does not delegate (HIGH dual-store)

Matrix: `POST /api/v1/quotes/{quoteId}/approvals` “delegates to approval-routing (S2C-12)”.

**Actual:**

| Store | Behavior |
|-------|----------|
| **quote-service** `ApprovalService` | Persists local `ApprovalRequest` (`Long quoteId`, `ApprovalTier` DEAL_DESK/SALES_VP/CFO); flips `Quote` → `PENDING_APPROVAL` / APPROVED / REJECTED |
| **approval-routing** `ApprovalService` | Canonical state machine + outbox reject events; `quoteId` is **String**; `ApprovalType`; Kafka `DiscountApprovalEventConsumer` **creates** rows |
| **quote → routing client** | **Zero** `rg` hits for `approval-routing` / `:8085` / `/api/v1/approvals` under quote-service |
| **routing create HTTP** | **No** `POST /api/v1/approvals` create/submit — only GET pending, approve, reject |

Schema / API mismatch is material: BFF cannot “just call” routing without a **submit/create** surface and ID/tier mapping.

**GitNexus:**

| Symbol | Risk | Note |
|--------|------|------|
| quote `ApprovalService` | **UNKNOWN** (0 edges) | Text: `ApprovalController` only — **agent-contract** paths → treat **HIGH** |
| routing `ApprovalService` | **LOW** (1 direct) | Controller + Kafka consumer create path |
| Ambiguous `ApprovalService` name | **max UNKNOWN** | Always disambiguate by file_path / uid |

**PM WARNING (HIGH):** Dual-store pending queues already diverge (quote BFF vs routing Kafka discounts). Wave G must **not** silently dual-write conflicting approval rows. Prefer **dual-read transitional** or **single-writer cutover** with tests.

### G-T3 — S2D-3a preflight dead + FE still on mocks (MEDIUM / LOW)

| Gap | Evidence |
|-----|----------|
| Quote preflight | `ProductServiceClient.validateConfiguration` exists; **no domain callers**; `submitForReview` does not validate config |
| Client base URL | `productRestClient` → `services.product.url` (**direct product**), not gateway — misses Wave F structured `{valid,violations[]}` unless retargeted |
| FE catalog / quotes / simulator | Presentational stubs + `MOCK_*`; `ProductService` / `QuoteService` / `PricingExecutionService` already exist (Wave C2) but UI does not call them |

---

## 2. Decisions + rejected alternatives

### Track 1 — S2B-8

| Topic | Decision | Rejected |
|-------|----------|----------|
| Engine shape | **Share/refactor** Wave F evaluator into a reusable **rule evaluation core** used by (a) `StatelessConfigurationEvaluator` and (b) session `ConstraintSolver` | Forever-duplicate REQUIRES/EXCLUDES in two classes; big-bang rewrite without reuse |
| Rule feed | Keep **sync REST** via `ProductConfigurationRuleClient` (ADR frozen #3); extend DTO with `conditionExpression` (and any DEFAULT payload fields product already exposes) | S2B-9 event cache this wave; inventing rules in CE DB |
| Soft vs hard | **REQUIRES / EXCLUDES / VALIDATES** → hard failures (block `valid`); **RECOMMENDS** → soft warnings (do not fail session/agent `valid` alone); **DEFAULT** → apply missing selection defaults then re-evaluate | Treat RECOMMENDS as hard fail; skip DEFAULT |
| Session mapping | Project session to evaluator input: parse selection values that are product IDs / option product refs + attribute map for VALIDATES; document mapping in impl notes | Pretend `Map<optionKey,optionValue>` ≡ `List<Long>` without projection |
| Agent path | Stateless evaluate gains new rule types **in same core**; gateway remains route+adapt only | Put new rule types only in session solver (agent drift) |
| product validate | **Retain** | Delete / calendar-deprecate this wave |

### Track 2 — S2C-12

| Topic | Decision | Rejected |
|-------|----------|----------|
| Ownership | **approval-routing owns** approval request lifecycle; quote BFF **orchestrates** (quote status + agent-stable URLs) | Keep quote local table as second source of truth |
| Write policy | **Single writer** to routing for approval **records**; quote updates **Quote** projection fields (`status`, `approvalState`, `approvalTier`) only | Dual-write `ApprovalRequest` rows in both DBs |
| Read policy (transition) | **Dual-read preferred:** pending/get prefer routing; optional feature-flagged fallback to legacy quote rows for pre-cutover IDs; tests prove no conflicting statuses for same logical approval | Silent merge of two PENDING rows without provenance |
| Cutover end-state | Stop inserting into quote `approval_requests`; keep table read-only/legacy or migrate later | Big-bang drop table without dual-read window |
| Routing API gap | Add **`POST /api/v1/approvals`** (or `/submit`) create for BFF/orchestration — internal to agents; map quote tier → `approvalTier` + `ApprovalType` (e.g. QUOTE / GOVERNANCE) | Only Kafka create path; invent agent-facing `/api/v1/approvals` as primary |
| External paths | Agent paths under `/api/v1/quotes/**/approvals*` **unchanged** | Rename BFF URLs |
| Kafka discount flow | Leave `DiscountApprovalEventConsumer` intact; BFF create is additive | Collapse discount + quote submit into one type without tests |

### Track 3 — S2D-3a + FE

| Topic | Decision | Rejected |
|-------|----------|----------|
| Preflight call | Quote orchestration calls **gateway** `POST /api/v1/configuration/validate` (Wave F shim → CE evaluate) via RestClient; parse `ConfigurationValidationResult`; block submit/preflight on `valid=false` | Inline REQUIRES/EXCLUDES in quote-service; keep calling product `:8083` forever |
| Hook point | Wire on **submitForReview** and/or dedicated preflight method used before approval submit — CONFIGURATION_ONLY scope first (line product IDs) | FULL_PREFLIGHT / ERP inventory this wave |
| FE | Wire catalog list/detail → `ProductService`; quotes list/detail → `QuoteService`; simulator → `PricingExecutionService.execute` | New parallel HTTP stacks; NgRx this wave |
| Gateway domain rules | Unchanged — no validation logic in gateway | — |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **CE constraint** ∥ **approval-routing API** ∥ **quote approval BFF** ∥ **quote preflight** ∥ **FE libs** (catalog / quotes / simulator).

```text
Track 1 (S2B-8)                          Track 2 (S2C-12)                         Track 3 (S2D-3a + FE)
─────────────────                        ────────────────                         ────────────────────
G1 shared rule core                      G3 routing POST create/submit            G5 quote preflight → gateway validate
   │                                        │                                     G6 FE catalog → ProductService
   ▼                                        ▼                                     G7 FE quotes → QuoteService
G2 ConstraintSolver session wire         G4 quote ApprovalService BFF             G8 FE simulator → PricingExecutionService
   │                                     (dual-read / single-writer)                 │
   └──────────────────┬──────────────────────┴───────────────────────────────────────┘
                      ▼
                   G9 docs/matrix + forge closeout
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **G1** | `feat(configuration-engine): shared rule evaluation core for S2B-8 types` | Extract/extend from `StatelessConfigurationEvaluator`; enrich `ProductConfigurationRuleDto` (+ `conditionExpression`); RECOMMENDS soft / DEFAULT apply / VALIDATES hard; keep F2 endpoint green; unit fixtures; `docs/forge/impl/g1-rule-core.md` | **MED** | Dual-run REQUIRES/EXCLUDES parity still holds; new type fixtures green; no gateway edits; product validate untouched |
| **G2** | `feat(configuration-engine): ConstraintSolver consumes product rules (S2B-8)` | `ConstraintSolver` + session mapping; `ConfigurationSessionServiceTest`; soft warnings surface on session without blocking unless hard; `docs/forge/impl/g2-constraint-solver.md` | **MED** | Placeholder color/option_a gone; session validate uses product rules via shared core; complete still requires hard-valid |
| **G3** | `feat(approval-routing): POST create/submit for quote BFF` | `ApprovalController` + service create; DTO; map tier/type; unit tests; **do not** change agent BFF URLs; `docs/forge/impl/g3-routing-submit.md` | **MED** | HTTP create returns pending approval; Kafka discount path still works; no quote-service edits in this PR |
| **G4** | `feat(quote): ApprovalService BFF → approval-routing (S2C-12)` | New `ApprovalRoutingClient`; rewrite quote `ApprovalService` to **single-write routing** + Quote projection; dual-read GET pending; tests for parity / no dual-write; matrix note; `docs/forge/impl/g4-approval-bff.md` | **HIGH** | External `/api/v1/quotes/**/approvals*` unchanged; canonical record in routing; tests fail if both DBs receive conflicting writes on submit; agent 011 paths stable |
| **G5** | `feat(quote): S2D-3a preflight via gateway validate` | Retarget validate RestClient to gateway (or dedicated bean); call from submit/preflight; map violations → `BusinessException`; tests with WireMock/MockRestServiceServer; `docs/forge/impl/g5-quote-preflight.md` | **MED** | `validateConfiguration` has domain caller; structured `valid` honored; no inline rule engine in quote |
| **G6** | `feat(fe-cpq): catalog list/detail use ProductService` | `catalog-list` / `catalog-detail`; keep mocks as test fixtures or fallback flag; Vitest; `docs/forge/impl/g6-fe-catalog-http.md` | **LOW** | UI loads `/api/v1/products` via existing service; stub subtitle removed |
| **G7** | `feat(fe-cpq): quotes list/detail use QuoteService` | `quotes-list` / `quotes-detail`; Vitest; `docs/forge/impl/g7-fe-quotes-http.md` | **LOW** | UI loads `/api/v1/quotes`; mock-only path gone for happy path |
| **G8** | `feat(fe-cpq): pricing simulator calls PricingExecutionService` | `simulator-page`; error empty-state; Vitest; `docs/forge/impl/g8-fe-simulator-http.md` | **LOW–MED** | Simulate hits `/api/v1/pricing/execute` (or documented path); mock execute only in unit tests |
| **G9** | `docs(cpq): Wave G matrix + forge closeout` | Matrix S2B-8 / S2C-12 / S2D-3a rows; orchestrator COMPLETE; `docs/forge/impl/g9-wave-g-docs.md` | **LOW** | Docs match runtime; product validate still retained; holds listed |

### Parallelism rules

**Day-1 max safe parallel (6 panes):** **G1 ∥ G3 ∥ G5 ∥ G6 ∥ G7 ∥ G8**

| Pair | Conflict? | Rule |
|------|-----------|------|
| G1 vs G2 | Same CE packages | **Serial** — G2 after G1 |
| G3 vs G4 | routing then quote | **Serial** — G4 after G3 |
| G4 vs G5 | Both quote-service | **Parallel OK** if G4 stays under `approval/` + new client bean; G5 under `quote/` + `client/` — **serialize** `RestClientConfig.java` / `application.yml` merges |
| G5 vs G6–G8 | BE vs FE | Parallel |
| G6 ∥ G7 ∥ G8 | Different FE libs | Parallel |
| G9 | Docs | After G2 + G4 + G5 (FE docs can land earlier notes) |

**Do not** open a seventh pane that edits gateway validate predicates or deletes product validate.

---

## 4. Blast radius / risk table

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| G1 shared evaluator | F3 gateway consumers; dual-run fixtures; future session | Re-analyze; was UNKNOWN | **MED** |
| G2 `ConstraintSolver` | `ConfigurationSessionService` selections/validate/complete | Graph **LOW** | **MED** product behavior |
| G3 routing create | G4 BFF; Kafka consumer coexistence | routing `ApprovalService` LOW | **MED** |
| G4 quote `ApprovalService` | Agents **cpq-agent-011**; FE future inbox; Quote status machine | Graph **UNKNOWN** → treat **HIGH** | **HIGH** |
| G5 preflight | submitForReview / approval submit UX; ProductServiceClient | Client **UNKNOWN** (dead today) | **MED** |
| G6–G8 FE | CRM shell routes already wired | FE not in Java graph | **LOW–MED** |
| product-service `/validate` | Must remain | Do not delete | — |
| gateway validate | Route+adapt only | Do not embed rules | — |

**Explicit HIGH callout:** **S2C-12 (G4)** — dual-store / agent-contract. Forge agents must stop and escalate if cutover tests show conflicting PENDING/APPROVED for the same quote.

**UNKNOWN callout:** quote `ApprovalService`, `ProductServiceClient`, `ConfigurationSessionService`, `StatelessConfigurationEvaluator` (stale index). Confirm with `rg` + re-analyze; do not treat empty caller sets as unused.

---

## 5. Herdr forge layout (6+ panes)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wg-rule-core` | `feat/forge-g1-rule-evaluation-core` | G1 |
| 2 | `wg-routing-submit` | `feat/forge-g3-approval-routing-submit` | G3 |
| 3 | `wg-quote-preflight` | `feat/forge-g5-quote-preflight` | G5 |
| 4 | `wg-fe-catalog` | `feat/forge-g6-fe-catalog-http` | G6 |
| 5 | `wg-fe-quotes` | `feat/forge-g7-fe-quotes-http` | G7 |
| 6 | `wg-fe-simulator` | `feat/forge-g8-fe-simulator-http` | G8 |
| 7 (after G1) | `wg-constraint-solver` | `feat/forge-g2-constraint-solver` | G2 |
| 8 (after G3) | `wg-approval-bff` | `feat/forge-g4-approval-bff` | G4 |
| 9 (closeout) | `wg-docs` | `docs/forge-g9-wave-g-closeout` | G9 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Before G1/G2/G4/G5 symbol edits — `node .gitnexus/run.cjs analyze` (or `npx gitnexus analyze`) on tip; `impact` with disambiguated uid for `ApprovalService`; treat **UNKNOWN** as unresolved + `rg` confirmation.  
**Do not** implement Activities, #238, #241, product validate deletion, or gateway domain validation.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- S2B-9 rule cache / event invalidation  
- FULL_PREFLIGHT ERP inventory / live ERP adapters (keep **501**)  
- Dropping quote `approval_requests` table without dual-read window  
- Silent dual-write of approval records to quote + routing  
- NgRx CPQ store, quote-builder deep UX, approval inbox FE (pricing IA backlog later PRs)  
- Changing agent-facing BFF URL shapes  

---

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave G per docs/forge/16-wave-g-architecture.md in THREE parallel tracks: (1) S2B-8 — extract/share rule evaluation core from StatelessConfigurationEvaluator + ProductConfigurationRuleClient, extend RECOMMENDS/DEFAULT/VALIDATES, then wire ConstraintSolver session path — do not duplicate forever and do not delete product validate; (2) S2C-12 — add approval-routing submit/create, then quote ApprovalService BFF delegates with SINGLE-WRITER to routing + Quote status projection and dual-read pending — NO silent dual-write of conflicting ApprovalRequest rows; keep /api/v1/quotes/**/approvals* paths; (3) S2D-3a quote preflight calls gateway validate (structured ConfigurationValidationResult) plus FE catalog/quotes/simulator wire existing HttpClient services (no new stacks). Parallelize G1∥G3∥G5∥G6∥G7∥G8 then G2 after G1, G4 after G3, G9 closeout. Use GitNexus impact before symbol edits; re-analyze if index stale; warn HIGH/CRITICAL/UNKNOWN — expect S2C-12 HIGH. Do not invent Activities API, start #238 CI runners, #241 Kafka agent migration, or put domain validation in gateway. Evidence: docs/forge/impl/g*.md done, dual-run/approval cutover tests green, FE mocks replaced on happy path, merge+push main when authorized.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–F complete on tip `3952b20`; Wave F hybrid S2B-11 retained product validate  
- [x] Evidence: ConstraintSolver placeholder; StatelessConfigurationEvaluator REQUIRES/EXCLUDES; quote ApprovalService local-only; routing no HTTP create; ProductServiceClient dead; FE mocks + HttpClient services present  
- [x] GitNexus: `ConstraintSolver` **LOW**; quote `ApprovalService` **UNKNOWN** (text HIGH agent-contract); `ProductServiceClient` **UNKNOWN**; `StatelessConfigurationEvaluator` missing from stale index — **warned**  
- [x] S2C-12 strategy = single-writer routing + dual-read / cutover tests — **rejects silent dual-write**  
- [x] Prefer reuse of ProductConfigurationRuleClient / StatelessConfigurationEvaluator for S2B-8  
- [x] PR DAG max parallelism with disjoint files documented (6 day-1 panes)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules  
- [x] PM authorized tracks 1+2+3 in parallel  
- [x] Forge execution authorized (`forge-wave-g` / goal §7) — **G4 HIGH acknowledged**  
- [x] Day-1 + Day-2 merged; single-writer tests + ConstraintSolver product-rules on main  

- [ ] Post-merge: G9 matrix + orchestrator COMPLETE  

**PM WARNING:** S2C-12 is **HIGH** dual-store risk. Index stale (8 commits) + several **UNKNOWN** impacts — re-analyze before edits.

**WAVE G — PLANNING (architect draft).**
