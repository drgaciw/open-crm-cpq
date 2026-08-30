# Wave F architecture — S2B-11 configuration validate compatibility (hybrid)

**Status:** APPROVED FOR FORGE — PM authorized 2026-08-30 (hybrid S2B-11; F3 HIGH acknowledged)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** worktree / `origin/main` @ `c5075a8` (Waves A–E complete; configuration ADR **Accepted**)  
**Sources:** GitNexus CLI (`impact` / `context` / `query`) on indexed `open-crm-cpq` @ `/home/username01/Projects/open-crm-cpq` (index **34 commits behind** HEAD — re-analyze before symbol edits); tree inventory; `docs/backlog/ADR-configuration-api-reconciliation.md`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`; `docs/forge/04-cpq-contracts.md`; Wave E Accept (`e3-adr-accept`)

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **S2C-12** quote→approval BFF | **HOLD** | Still needs ownership ADR + agent-contract fixtures; HIGH dual-stack risk |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees |
| **Agent Kafka (#241)** | **HOLD** | Agent-stack migration; unrelated to validate ownership |
| **Full S2B-8** ConstraintSolver rewrite (all rule types + graph) | **DEFER (follow-on)** | Graph blast radius on `ConstraintSolver` is **LOW** (session service + tests only), but full rule-type rewrite in the **same** wave as agent-contract shape change compounds HIGH risk and is unfinished scope |
| **Pure thin** (gateway only wraps product `List<String>` → `{valid,violations[]}`, no CE evaluate) | **REJECT as long-term shape** | Violates Accepted ADR frozen default #1 (prefer `POST /internal/v1/configurations/evaluate`); leaves duplicate ownership |
| **✅ Chosen: Wave F hybrid S2B-11** | **Largest safe forge** | Land shared DTO + CE stateless evaluate (REQUIRES/EXCLUDES **parity** with product today) + gateway route/adapt + contract dual-run; **retain** product validate; **do not** delete product path; **do not** embed domain rules in gateway; defer RECOMMENDS/DEFAULT/VALIDATES/graph to S2B-8 |

**PM authorization used:** “Architect picks thin vs full from GitNexus blast radius” for Wave F / S2B-11.

**Chosen approach:** **HYBRID** (S2B-11 plumbing + thin parity evaluator; not full S2B-8).

---

## 1. Problem / evidence

### F-P0 — Agent validate contract ≠ runtime shape (S2B-11)

| Layer | Today (`c5075a8`) | Agent PRD / ADR target |
|-------|-------------------|------------------------|
| External path | `POST /api/v1/configuration/validate` | **Unchanged** (agent-contract) |
| Gateway | Pass-through `Path=/api/v1/configuration/**` → **product-service** | Contract owner: route + adapt only; prefer CE evaluate |
| product-service | Body `List<Long>`; `ApiResponse<List<String>>` errors; REQUIRES/EXCLUDES only | Transitional; **retain** until parity |
| configuration-engine | Session validate only (`POST /api/v1/configurations/{id}/validate`); **no** `POST /internal/v1/configurations/evaluate` | Stateless evaluator home |
| `ConstraintSolver` | Placeholder (`color` / `option_a` / `premium_feature`) | Long-term engine (S2B-8 full types) |
| Consumers | `CpqAgentToolClients.validateConfiguration` (live tool); quote `ProductServiceClient.validateConfiguration` (**no domain callers** — preflight dead); agents 015/016/002 | Expect `{ valid, violations[] }` |

Confirmed tree:

```java
// product-service ConfigurationRuleController
@PostMapping("/validate")
public ApiResponse<List<String>> validate(@RequestBody List<Long> selectedProductIds)

// gateway application.yml
Path=/api/v1/configuration/**   → product-service
Path=/api/v1/configurations/**  → configuration-engine

// configuration-engine: session controller only — no /internal/v1/** evaluate
```

### F-P1 — Dual evaluation will drift until CE owns agent path

product-service `ConfigurationRuleService.validateConfiguration` implements real REQUIRES/EXCLUDES from DB.  
`ConstraintSolver` does **not** consume those rules. Leaving agents on product forever blocks ADR ownership; flipping gateway to CE without a parity evaluator would **regress** agent validation to placeholder nonsense.

### GitNexus blast-radius notes (pre-edit)

**Repository:** `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
**Worktree (this plan):** `/home/username01/.grok/worktrees/projects-open-crm-cpq/subagent-01a053e2-8e37-75d0-87fa-0567b78bd25e` @ `c5075a8`  
**Index:** **34 commits behind** HEAD — forge agents **must** re-analyze (`node .gitnexus/run.cjs analyze` / `npx gitnexus analyze`) before symbol edits.

| Symbol | Upstream impact | Text confirmation | Edit risk for Wave F |
|--------|-----------------|-------------------|----------------------|
| `ConstraintSolver` | **LOW** — 1 direct (`ConfigurationSessionService`); tests import; session flows AddSelection/RemoveSelection → Validate | Placeholder only; session beta APIs | **Warn LOW graph / MED product** if session behavior changes — Wave F prefers **not** replacing session solver with full S2B-8 |
| `validateConfiguration` (ambiguous ×3) | ai-service **LOW**; product **LOW**; quote `ProductServiceClient` **UNKNOWN** (0 callers) | Quote method exists, **no** quote domain callers (S2D-3a unwired); AI tools + MockMvc tests call gateway | Treat agent path as **HIGH** contract risk despite LOW graph counts |
| `ConfigurationRuleController` | **UNKNOWN** (0 graph callers) | HTTP entry for agents/gateway/product CRUD | **UNKNOWN ≠ safe** — agent-contract; do not delete `/validate` |
| `ConfigurationSessionService` | **UNKNOWN** | Controller + session flows | Touch only if evaluate shares code carefully |
| `ProductServiceClient` (quote) | **UNKNOWN** | Dead for preflight | Optional additive parse later; not required to land shim |

**PM WARNING:** Several critical HTTP/client symbols return **risk: UNKNOWN**. Proceed only with text inventory (done above). Re-run `impact` after re-analyze; stop and escalate if any planned edit flips to **HIGH/CRITICAL** without contract tests.

---

## 2. Decisions (thin vs full → hybrid)

| Topic | Decision | Rejected |
|-------|----------|----------|
| Wave shape | **Hybrid S2B-11:** shared DTO + CE `POST /internal/v1/configurations/evaluate` + gateway adapt + contract dual-run | Pure gateway wrap of product strings; big-bang S2B-8+S2B-11 |
| Evaluator depth this wave | **Thin parity:** REQUIRES + EXCLUDES only, fed by **sync REST** rule fetch from product-service (ADR frozen #3) | Full RECOMMENDS / DEFAULT / VALIDATES / graph conflict engine (S2B-8) |
| ConstraintSolver | **Leave session placeholder** for session APIs this wave; implement evaluate via dedicated **stateless evaluator** (may share helper types with future S2B-8) | Rewrite `ConstraintSolver.validate(ConfigurationSession)` to full rule engine in F2 |
| Shared DTO | `backend/libs/common` — e.g. `ConfigurationValidationResult { boolean valid; List<ConfigurationViolation> violations }` with `ruleId`, `ruleType`, `description`, `remediation` | Gateway-local DTOs as long-term home |
| Gateway role | Route split: `POST /api/v1/configuration/validate` → CE evaluate (+ map to agent shape); **CRUD** `/api/v1/configuration/rules*` remains product pass-through; **no** domain rule logic in filters | Embed REQUIRES/EXCLUDES in gateway; rename/remove agent path |
| product-service validate | **Retain** handler + `List<String>` (or dual-write structured later) until contract-test parity — **no removal** this wave | Delete or internal-only redirect in Wave F |
| Request body (wave F) | Accept current `List<Long>` selected product IDs at agent path (compat); map to evaluate request; structured configuration payload remains additive follow-on | Break agents by requiring session IDs or full attribute bags day-1 |
| Response body (wave F) | Gateway returns `ApiResponse<ConfigurationValidationResult>` (`valid` + `violations[]`); document migration for AI tool clients that currently treat payload as opaque `JsonNode` | Silent break of `List<String>` for gateway callers without matrix/tests |
| Quote preflight (S2D-3a) | **Out of scope** — client stays dead; optional F6 only updates parse helpers if capacity | Wire quote orchestration preflight this wave |
| Activities / S2C-12 / #238 / #241 | Remain **held** | Invent activities; start S2C-12; CI billing; Kafka agents |

### Why hybrid over thin or full

1. **Thin alone** fails ADR: agent evaluate would still be owned by product-service; CE remains session-only.  
2. **Full S2B-8 + S2B-11** is attractive because `ConstraintSolver` upstream is **LOW**, but:  
   - Session validate flows would change behavior for UI/beta clients in the same merge window as agent contract shape.  
   - RECOMMENDS/DEFAULT/VALIDATES + graph checks are unfinished design (rule projection DTOs, soft vs hard failures).  
   - Agent-contract HIGH risk should not share a single forge wave with a full rules engine rewrite.  
3. **Hybrid** is the largest **safe** forge: advances S2B-11 ownership + structured response + contract harness while matching today’s product semantics (REQUIRES/EXCLUDES only).

---

## 3. PR DAG (forge-ready)

```
F1 (libs/common ValidationResult DTO) ──┐
                                         ├──► F3 (gateway validate → CE evaluate + adapt)
F2 (CE /internal evaluate + thin parity)┘              │
F4 (contract / dual-run tests) ────────────────────────┤──► F5 (matrix + forge closeout)
                                                       │
                                    optional F6 (ai/quote client parse) ──┘
```

| ID | Title | Scope | Risk | Acceptance |
|----|-------|-------|------|------------|
| **F1** | `feat(common): ConfigurationValidationResult DTO for S2B-11` | `backend/libs/common` DTO(s) under `com.opencrm.common.dto` (or `...configuration`); unit serialization test; no service wiring yet; `docs/forge/impl/f1-validation-dto.md` | **LOW** | Types compile in `libs/common`; fields cover `valid` + `violations[{ruleId,ruleType,description,remediation}]`; ADR/matrix can reference FQCN |
| **F2** | `feat(configuration-engine): POST /internal/v1/configurations/evaluate (thin REQUIRES/EXCLUDES)` | New internal controller (not under public `/api/v1/configurations` session API); Product rule RestClient (`GET .../configuration/rules/product/{id}` or batch equivalent); **StatelessConfigurationEvaluator** (or equivalent) — **do not** replace session `ConstraintSolver` placeholder; unit tests for requires/excludes parity fixtures; `docs/forge/impl/f2-ce-evaluate.md` | **MED** | `curl`/MockMvc internal evaluate returns F1 DTO; empty selection → valid; known REQUIRES miss / EXCLUDES hit match product semantics; session APIs unchanged |
| **F3** | `feat(gateway): S2B-11 configuration validate compatibility layer` | Gateway route **more specific** than `configuration/**` for `POST .../validate` → configuration-engine internal evaluate (URI rewrite or dedicated BFF WebClient adapter); response map to F1 DTO inside `ApiResponse`; **CRUD** still product; **no** rule evaluation in gateway; update `CpqGatewayRoutesTest`; `docs/forge/impl/f3-gateway-validate-shim.md` | **HIGH** | External path unchanged; gateway validate returns structured `{valid,violations[]}`; product `/configuration/rules*` still routed to product; `rg` shows no domain REQUIRES/EXCLUDES logic in gateway Java; product validate endpoint **still present** |
| **F4** | `test(cpq): configuration validate contract + product↔CE dual-run parity` | Contract fixtures (Appendix A validate); dual-run test: same `List<Long>` against product validate vs CE evaluate → equivalent hard failures; gateway route predicate test for validate vs rules split; `docs/forge/impl/f4-validate-contracts.md` | **MED** | CI-green Docker-free unit/contract where possible; dual-run fails on drift; ERP/other Appendix A untouched |
| **F5** | `docs(cpq): Wave F matrix + S2B-11 closeout notes` | `CPQ_API_CONTRACT_MATRIX.md` validate row → structured shape + owners; ADR migration note “Wave F landed hybrid”; forge status; `docs/forge/impl/f5-s2b11-docs.md` | **LOW** | Matrix matches runtime; explicit “product validate retained”; S2B-8 still listed as follow-on |
| **F6** (optional capacity) | `chore(ai,quote): tolerate structured validate payload` | `CpqAgentToolClients` / tests assert `valid` when present; quote `ProductServiceClient` javadoc or JsonNode helper — **no** S2D-3a preflight wiring | **LOW–MED** | AI unit tests green against structured fixture; quote still no forced preflight |

**Parallelism:** F1 first (tiny). Then **F2 ∥ F4** (F4 can stub expected JSON from F1). **F3 after F1+F2**. **F5 after F3** (and F4 ideally). F6 anytime after F1 shape freezes.

**Predicate-order constraint (F3):** Spring Cloud Gateway must match `POST /api/v1/configuration/validate` (and cpq alias) **before** the broad `Path=/api/v1/configuration/**` product route — or use a filter that only rewrites validate. Wrong order = silent continue to product `List<String>` and false “done”.

---

## 4. Blast radius

| Change | Upstream / dependents | GitNexus / text |
|--------|----------------------|-----------------|
| F1 common DTO | All future validate producers/consumers; Maven reactor dependents of `libs/common` | Additive — LOW |
| F2 internal evaluate + thin evaluator | Gateway F3; future S2B-8; session APIs **untouched** if ConstraintSolver left alone | New surface; MED |
| F2 if someone edits `ConstraintSolver` anyway | `ConfigurationSessionService` selections/validate/complete; processes AddSelection/RemoveSelection → Validate | Graph **LOW** — still warn MED product behavior |
| F3 gateway validate split | Agents 002/015/016; `CpqAgentToolClients`; any FE hitting validate via `:8080`; product direct callers still on old shape | Controller impact **UNKNOWN**; treat **HIGH** agent-contract |
| F4 tests | CI time; locks semantics before S2B-8 | — |
| F5 docs | Humans / next waves | — |
| product-service validate | **Must remain** | Do not delete |

**Do not** put domain validation in gateway filters beyond route + DTO adaptation (ADR §3 / forge DO NOT TOUCH).

---

## 5. Forge layout (Herdr panes)

| Pane | Agent id | Worktree branch |
|------|----------|-----------------|
| 1 | `wf-dto` | `feat/forge-f1-validation-dto` |
| 2 | `wf-ce-evaluate` | `feat/forge-f2-ce-internal-evaluate` |
| 3 | `wf-gw-shim` | `feat/forge-f3-gateway-validate-shim` |
| 4 | `wf-contracts` | `test/forge-f4-validate-contracts` |
| 5 | `wf-docs` | `docs/forge-f5-s2b11-closeout` |
| 6 (opt) | `wf-clients` | `chore/forge-f6-validate-client-parse` |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Before F2/F3 symbol edits — re-analyze GitNexus on tip; `impact` on touched symbols (`ConstraintSolver` only if edited; any new evaluator; gateway route config classes); treat **UNKNOWN** as unresolved + confirm with `rg`.  
**Do not** implement Activities, S2C-12, #238, #241, or full S2B-8.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- **S2C-12** quote BFF → approval-routing  
- **Full S2B-8** ConstraintSolver rewrite (RECOMMENDS / DEFAULT / VALIDATES / graph)  
- **S2B-9** rule cache / event invalidation  
- **S2D-3a** quote preflight wiring (beyond optional F6 parse hygiene)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation logic in gateway  
- CI runners (#238), agent Kafka (#241)  
- Changing session URL into the agent contract path  
- ERP stub removal / live ERP adapter  

---

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave F hybrid S2B-11 per docs/forge/15-wave-f-architecture.md: (F1) add ConfigurationValidationResult (+ violations) DTO in backend/libs/common; (F2) add configuration-engine POST /internal/v1/configurations/evaluate with thin REQUIRES/EXCLUDES parity evaluator via sync REST rules from product-service — do NOT replace session ConstraintSolver placeholder with full S2B-8; (F3) gateway: route POST /api/v1/configuration/validate to CE evaluate and adapt to {valid,violations[]} — no domain rules in gateway; retain product-service validate and rules CRUD pass-through; (F4) contract + dual-run parity tests; (F5) update CPQ contract matrix and forge closeout. Use GitNexus impact before symbol edits; re-analyze if index stale; warn on HIGH/CRITICAL/UNKNOWN. Do not implement activities API, S2C-12, #238, #241, or full ConstraintSolver rewrite. Evidence: internal evaluate exists, gateway validate returns structured DTO, product validate still present, docs/forge/impl/f*.md status done, merge+push main when authorized.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–E complete on tip `c5075a8`; configuration ADR **Accepted** with frozen defaults  
- [x] Evidence confirmed: product `List<Long>`→`List<String>`; gateway pass-through; no CE `/internal/.../evaluate`; ConstraintSolver placeholder; AI client live; quote client dead for preflight  
- [x] GitNexus: `ConstraintSolver` **LOW**; `validateConfiguration` ambiguous (**LOW** / **UNKNOWN**); `ConfigurationRuleController` **UNKNOWN** — warned  
- [x] Thin vs full decided → **HYBRID** (largest safe forge)  
- [x] PR DAG parallelizable (F1 → F2∥F4 → F3 → F5) without deleting product validate  
- [x] Holds respected: Activities, S2C-12, #238, #241, full S2B-8  
- [x] Gateway must not embed domain validation — captured in F3 acceptance  
- [x] PM authorizes forge execution (`forge-wave-f` / goal text in §7) — **F3 HIGH risk acknowledged**  
- [ ] Post-merge: dual-run green; matrix updated; product validate retained  

**FORGE AUTHORIZED — HYBRID S2B-11.** F3 requires contract/dual-run evidence before merge.
