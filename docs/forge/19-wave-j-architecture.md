# Wave J architecture — quote typed pricing apply ∥ VALIDATES `attr:in:` ∥ FE CPQ create+lifecycle ∥ dual-read readiness

**Status:** COMPLETE on main (J1–J5 merged; J6 closeout) — PM authorized 2026-08-30 (architect pick + orchestrator review after Wave I land)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** Wave I COMPLETE on forging tip (I1–I4 merged; I5 closeout in same docs PR as this authorize)  
**Survey tip:** `main` @ `9292e9e` (Wave I closeout + Wave J authorize)  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index **stale ~51 commits** @ `c5075a8` → tip — re-analyze before symbol edits); GitHub open issues/PRs; I2 Jackson 3 bind proof; `docs/forge/18-wave-i-architecture.md`; `docs/forge/impl/h7-dual-read-prep.md`; held list from PM

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees; only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Stack B2 simulated agents + S2A-2 Kafka topics; agent-stack migration out of forge wave |
| **Delete / retire product-service validate** | **HOLD** | Dual-run parity not calendar-ready; prefer defer |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` default → false** | **HOLD** | H7 prep only; empty-legacy evidence still required — **HIGH** dual-store cutover |
| **Full SpEL / Drools / OpenL** | **REJECT** | Over-scope; H1 constrained dialect + `!=` already landed |
| **S2B-9 Kafka / event invalidation** | **HOLD** | Event bus + product publishers not ready; H6 thin TTL already advances freshness |
| **Bolt #430 WhatsApp** | **REJECT** | Legacy `cpq_beta` / communication path; not CPQ cutover |
| **Approval inbox FE / NgRx CPQ store** | **DEFER** | No FE approval surface yet; larger UX track than Day-1 create+lifecycle |
| **FULL_PREFLIGHT / live ERP** | **REJECT** | Gateway 501 stubs remain until dedicated ERP adapter track |
| **Document-generation / contract-asset FE** | **DEFER** | BE modules exist; portal vertical slice not Day-1 after I |
| **✅ Track 1: Quote `PricingServiceClient` typed apply-path** | **IN** | Wave I explicitly deferred; `executePricing` + `QuoteService.calculate` still JsonNode walk; GitNexus client **LOW** |
| **✅ Track 2: VALIDATES `attr:key in:a,b,c` dialect** | **IN** | H1 doc left “`in:` list later”; core already skips unsupported dialects; additive, fixture-driven |
| **✅ Track 3: FE CPQ quote create + detail lifecycle** | **IN** | `QuoteService.create` / `calculate` / `submit` / `addLineItem` exist; list CTA disabled; detail is read-only |
| **✅ Track 4: FE CPQ catalog create** | **IN** | `ProductService.create` exists; list CTA disabled; disjoint from quotes lib |
| **✅ Track 5: Dual-read cutover readiness (no default flip)** | **IN** | H7 proved flag-off; Wave J adds ops checklist + matrix gate — **docs-only** (no quote Java probe) so J5 stays parallel with J1; **default stays true** |

**PM authorization used:** “Architect picks Wave J from GitNexus+GitHub.” Orchestrator review after I2: quote `PricingServiceClient.body(JsonNode.class)` has the **same Jackson 3 bind failure** I2 just fixed on AI clients.

**Chosen theme:** **Largest safe forge** — type the quote pricing apply-path left after AI I2, extend VALIDATES with `attr:in:` without SpEL, enable FE CPQ create+lifecycle on already-wired HTTP clients, and advance dual-read cutover readiness without flipping the default. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read default flip / WhatsApp / Kafka S2B-9.

**Orchestrator rule:** Wave I COMPLETE is the forge gate. This file is the authorized Wave J plan.

---

## 1. Problem / evidence

### J-T1 — Quote pricing apply-path still opaque JsonNode (MED contract / apply correctness)

| Layer | Today (`9292e9e` post-I) | Target |
|-------|--------------------------|--------|
| AI `CpqAgentToolClients.executePricing` | **I2:** `.body(String.class)` + Jackson 2 `ObjectMapper.readTree` | Unchanged by J1 |
| quote `PricingServiceClient` | **Still** `executePricing` / `simulatePricing` → `.body(JsonNode.class)` | Quote-local views; **NEVER** `RestClient.body(JsonNode.class)` — **read body as `String` then Jackson 2 `ObjectMapper.readTree` / bind views** (I2 proof: RestClient Jackson 3 cannot construct `com.fasterxml.jackson.databind.JsonNode`); **no** `pricing-service` Maven dep |
| `QuoteService.calculate` | `fetchPricingData` / `applyPricingToLineItems` / `updateQuoteFromPricingData` walk JsonNode paths (`success`, `data`, `lineResults`, `financialMetrics`, `approvalState`, …) | Consume typed views; preserve decimal/fallback semantics |
| `ProductServiceClient.getProductById/Sku` | Still `.body(JsonNode.class)`; **no production callers** (only typed `validateConfiguration` used) | Optional: type or `@Deprecated` unused getters — do **not** block J1 (same Jackson 3 landmine if revived) |

**Evidence:**

```text
quote PricingServiceClient.java:21-36 — STILL body(JsonNode.class) on /pricing/execute and /simulate
  **I2 proof (must apply here):** CpqAgentToolClients javadoc + tests:
  Spring RestClient Jackson 3 converters cannot bind com.fasterxml.jackson.databind.JsonNode;
  I2 fixed AI path via String + ObjectMapper.readTree. Quote calculate is broken the same way until J1.
QuoteService.java (package com.opencrm.quote.quote):354+ — JsonNode pricingData = fetchPricingData(...);
  path("lineResults") / financialMetrics / approvalState
ProductServiceClient.validateConfiguration — already ConfigurationValidationResult (G5)
ProductServiceClient.getProductById/Sku — still body(JsonNode.class); text: no production callers
rg simulatePricing — definition only (unused)
```

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq`, index **~51 behind** — **UNKNOWN ≠ unused**):

| Symbol | Risk | Note |
|--------|------|------|
| `PricingServiceClient` (quote) | **LOW** (`--file …/quote/client/PricingServiceClient.java`: 1 direct → `QuoteService.java` IMPORTS; depth-2 ApprovalService file edge) | Re-impact after analyze |
| `QuoteService` (BE) | **LOW** graph (stale edges); **text:** `QuoteController` calls `calculate` / lifecycle | `--file …/quote/quote/QuoteService.java` (not `…/service/…`); treat **MED product** for apply semantics |
| `ProductServiceClient` (quote) | **UNKNOWN** (0 edges + riskNote) | Text: `QuoteService` validate + tests; UNKNOWN ≠ unused |
| Ambiguous `QuoteService` name | FE vs BE | Always `--file` / `--uid` (wrong path → Target not found / UNKNOWN) |

### J-T2 — VALIDATES dialect still missing `in:` lists (LOW–MED product)

H1 landed `attr:key=value` and `attr:key!=value`. Unsupported expressions are skipped (debug log). Wave H decision record explicitly deferred **`in:` list**.

```text
ProductRuleEvaluationCore — ATTR_PREFIX "attr:"; equals / != only; unsupported → null (skip)
```

**GitNexus:** `ProductRuleEvaluationCore` **not in index** (stale) → treat **UNKNOWN** until re-analyze. Text callers: `StatelessConfigurationEvaluator`, `ConstraintSolver`, unit tests.

### J-T3 — FE quotes HTTP-ready but create/lifecycle UI missing (LOW UX)

| Surface | State |
|---------|-------|
| `frontend/.../quote.service.ts` | `create`, `submit`, `calculate`, `addLineItem`, … already HttpClient |
| `quotes-list` | “New Quote” **disabled** (I4 adds tooltip only) |
| `quotes-detail` | Read-only cards; **no** Calculate / Submit / Add line actions |

GitNexus FE `QuoteService` **LOW** (barrel → routes).

### J-T4 — FE catalog create still disabled (LOW UX)

| Surface | State |
|---------|-------|
| `product.service.ts` | `create` / `update` / `delete` exist |
| `catalog-list` | “New Product” **disabled** |
| `catalog-detail` | Read-only |

Disjoint files from quotes → safe parallel with J3.

### J-T5 — Dual-read default still true; cutover gate incomplete (HIGH if flipped)

H7: `ApprovalServiceSingleWriterTest.getPendingWithLegacyFlagOffDoesNotReadLocal` + ops note. Production default:

```yaml
approvals.legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

Single-writer submit already skips local `ApprovalRequest` persist. Flip remains **HIGH** without empty-legacy evidence. Wave J readiness only: **docs/ops checklist** (empty local PENDING; flag-off soak; rollback) — **no Java probe**, **no** `application.yml` default change (**default stays `true`**).

### GitHub inventory (held)

| Kind | IDs | Wave J |
|------|-----|--------|
| Open issues | **#238**, **#241** | **HOLD** |
| Open Bolt PRs | **#412** (superseded by I3 — close), **#430** WhatsApp | **OUT** |

---

## 2. Decisions + rejected alternatives

### Track 1 — Quote typed pricing apply

| Topic | Decision | Rejected |
|-------|----------|----------|
| DTO home | Quote-local view types under `com.opencrm.quote.client` / `dto` mirroring fields already read (`success`, `errorMessage`, `lineResults[]`, `financialMetrics`, `approvalState`, `approvalTier`, `waterfall`) | Add Maven dep on `pricing-service`; move `PricingResult` into `libs/common` Day-1 |
| HTTP bind | **String body → Jackson 2 parse** (same as I2). **NEVER** `RestClient.body(JsonNode.class)` — I2 proved Jackson 3 cannot bind `com.fasterxml.jackson.databind.JsonNode`; quote client still does this today | Keep JsonNode RestClient bind (known Jackson 3 failure) |
| Client API | `executePricing` returns typed `ApiResponse<PricingExecuteView>` (or parse once into view); `simulatePricing` same shape or leave unused | Big-bang rewrite all RestClients |
| Apply path | Refactor `fetchPricingData` / `applyPricingToLineItems` / `updateQuoteFromPricingData` to views; keep decimal fallbacks + `PRICING_*` error codes | Behavioral change to totals formulas |
| Product getters | Out of critical path; optional tiny follow-up or javadoc “unused” | Force product DTO module |
| AI clients | Already I2 — **do not** re-edit `CpqAgentToolClients` in J1 | Couple AI+quote in one PR |

### Track 2 — `attr:in:` dialect

| Topic | Decision | Rejected |
|-------|----------|----------|
| Syntax | `attr:<key> in:<v1>,<v2>,...` (comma-separated; trim; case-sensitive values matching `=` dialect). Parse ` in:` **before** `=` / `!=` | Full SpEL; regex; nested boolean grammar |
| Failure | When key missing or value not in set → hard VALIDATES fail (same as `=` mismatch) | Soft-fail |
| Unsupported | Non-`attr:` / unknown ops still skip | Fail-closed all unknown expressions (breaks legacy junk data) |
| Scope | `ProductRuleEvaluationCore` + unit/ConstraintSolver fixtures only | product-service validate port |

### Track 3 — FE quotes create + lifecycle

| Topic | Decision | Rejected |
|-------|----------|----------|
| Create | Enable “New Quote”; minimal reactive form matching `QuoteCreateRequest` (**required:** `name`, `customerExternalId`; optional currency, durationInMonths, dealExternalId, region); navigate to detail on success | Full wizard / NgRx |
| Detail actions | Calculate + Submit (+ optional Add lineItem minimal row) using existing `QuoteService` methods; disable by status when obvious | Approval inbox; accept/send full matrix Day-1 |
| Errors | Inline banner / snackbar from HTTP error body | Silent fail |

### Track 4 — FE catalog create

| Topic | Decision | Rejected |
|-------|----------|----------|
| Create | Enable “New Product”; minimal form matching `ProductCreateRequest` (**required:** sku, name, productType, category; optional listPrice/currency). **No** `status` field on create DTO | Full catalog admin / config rules UI |
| Post-create | Navigate to detail | Inline-only list mutate without detail |

### Track 5 — Dual-read readiness

| Topic | Decision | Rejected |
|-------|----------|----------|
| Default | **Remain `true`** | Flip to `false` in forge |
| Deliverable | Matrix/ops cutover checklist (empty local PENDING; flag-off soak; rollback). **Docs-only** — no quote-service Java probe this wave (keeps J5 file-disjoint from J1) | Production env flip; Java count probe in `ApprovalService`; delete `approval_requests` table |
| Risk label | Docs **HIGH** callout if anyone proposes default flip in PR | — |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **quote BE pricing apply** ∥ **CE dialect** ∥ **FE quotes** ∥ **FE catalog** ∥ **docs/ops dual-read**.

```text
Track 1 (quote BE)     Track 2 (CE)           Track 3 (FE quotes)      Track 4 (FE catalog)   Track 5 (dual-read)
─────────────────      ──────────────         ───────────────────      ──────────────────    ────────────────
J1 typed PricingServiceClient   J2 attr:in: dialect   J3 quote create +      J4 product create     J5 cutover checklist
   + QuoteService.calculate        in ProductRule…       detail lifecycle         dialog                 (docs-only)
        │                         │                         │                      │                      │
        └─────────────────────────┴─────────────────────────┴──────────────────────┴──────────────────────┘
                                                         ▼
                                          J6 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **J1** | `refactor(quote): typed pricing execute apply-path` | `PricingServiceClient` + quote-local views; **NEVER** `body(JsonNode.class)` — String + Jackson-2 bind (I2); `QuoteService.calculate` helpers; unit tests preserving `PRICING_*` codes / decimals; `docs/forge/impl/j1-quote-pricing-typed.md` | **MED** | Zero `body(JsonNode.class)` in quote `PricingServiceClient`; fixtures assert line apply + financial metrics; no pricing-service Maven dep; AI `CpqAgentToolClients` untouched |
| **J2** | `feat(configuration-engine): VALIDATES attr:in: list dialect` | `ProductRuleEvaluationCore` (+ tests / ConstraintSolver fixture); `docs/forge/impl/j2-validates-attr-in.md` | **LOW–MED** | `attr:color in:red,blue` match/mismatch covered; `=` / `!=` unchanged; unsupported still skip |
| **J3** | `feat(fe-cpq): quote create + detail calculate/submit` | `quotes-list`, `quotes-detail`, optional create component; Vitest; `docs/forge/impl/j3-fe-quote-create-lifecycle.md` | **LOW** | New Quote enabled and creates via API; detail Calculate/Submit call existing service; list/detail still green |
| **J4** | `feat(fe-cpq): catalog product create dialog` | `catalog-list` (+ create form); Vitest; `docs/forge/impl/j4-fe-catalog-create.md` | **LOW** | New Product enabled; create navigates to detail; no quotes lib edits |
| **J5** | `docs(quote): dual-read cutover readiness (default stays true)` | Matrix/ops checklist; **no** Java; **no** `application.yml` default flip; `docs/forge/impl/j5-dual-read-readiness.md` | **LOW** docs (**HIGH** if anyone flips default) | Checklist lists empty-legacy evidence gates; default still true; H7 tests remain green |
| **J6** | `docs(cpq): Wave J forge closeout` | Matrix touch-ups; orchestrator → COMPLETE; `docs/forge/impl/j6-wave-j-docs.md` | **LOW** | Docs match runtime; holds listed |

### Parallelism rules

**Day-1 max safe parallel (5 panes):** **J1 ∥ J2 ∥ J3 ∥ J4 ∥ J5**

| Pair | Conflict? | Rule |
|------|-----------|------|
| J1 vs J3/J4 | BE vs FE | Parallel |
| J3 vs J4 | Different FE libs | Parallel (avoid shared `shared-ui` API churn) |
| J1 vs J5 | Both quote-service possible | **J5 is docs-only** — parallel OK |
| J2 vs all | CE only | Parallel |
| J6 | Docs | After Day-1 notes |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, merges WhatsApp #430, or starts #238/#241.

---

## 4. Blast radius / GitNexus risks

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| J1 `PricingServiceClient` | `QuoteService.calculate` | **LOW** graph | **MED** apply correctness |
| J1 `QuoteService` (BE) | `QuoteController` | **LOW** | **MED** product |
| J2 `ProductRuleEvaluationCore` | Evaluator + ConstraintSolver | **UNKNOWN** (missing index) | **LOW–MED** — re-analyze + `rg` |
| J3 FE quotes | Shell routes | FE **LOW** | **LOW** |
| J4 FE catalog | Shell routes | FE outside Java graph | **LOW** |
| J5 dual-read docs | Humans / ops | Flip = **HIGH** — not in wave | **LOW** docs discipline |
| Ambiguous names | `QuoteService`, `ApprovalService`, `ProductServiceClient` | Always `--file` / `--uid` | — |

**Explicit HIGH callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default in Wave J (**HIGH** cutover if flipped).  
2. **Index ~51 commits behind** — re-analyze (`node .gitnexus/run.cjs analyze`) on tip before symbol edits; treat **UNKNOWN** as unresolved + confirm with `rg` (UNKNOWN ≠ unused).  
3. Disambiguate `QuoteService` (FE vs BE — BE path `…/quote/quote/QuoteService.java`) and `ApprovalService` (quote vs routing) by `file_path` / uid.  
4. Wave I is **COMPLETE** on tip — forge gate satisfied; do not reopen I tracks in J panes.

**UNKNOWN callout:** quote `ProductServiceClient` (0 edges + riskNote) and `ProductRuleEvaluationCore` (not in index / Target not found) — text-confirmed callers/tests; empty/missing ≠ safe to delete.

---

## 5. Herdr forge layout (5 Day-1 panes)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wj-quote-pricing-typed` | `feat/forge-j1-quote-pricing-typed` | J1 |
| 2 | `wj-validates-attr-in` | `feat/forge-j2-validates-attr-in` | J2 |
| 3 | `wj-fe-quote-lifecycle` | `feat/forge-j3-fe-quote-create-lifecycle` | J3 |
| 4 | `wj-fe-catalog-create` | `feat/forge-j4-fe-catalog-create` | J4 |
| 5 | `wj-dual-read-ready` | `docs/forge-j5-dual-read-readiness` | J5 |
| 6 (closeout) | `wj-docs` | `docs/forge-j6-wave-j-closeout` | J6 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Re-analyze GitNexus on tip; `impact` before symbol edits; disambiguate ambiguous symbols; warn HIGH/CRITICAL/UNKNOWN.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, WhatsApp #430, full SpEL, or Kafka S2B-9.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka / Stack B2 (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- Full SpEL/Drools/OpenL expression engines  
- S2B-9 Kafka / Redis shared invalidation bus  
- Flipping `approvals.legacy-local-read-enabled` default to `false`  
- Bolt #430 WhatsApp  
- Approval inbox FE, NgRx CPQ store, FULL_PREFLIGHT ERP, document/contract portal slices  
- Adding `pricing-service` as a Maven dependency of `quote-service`  
- Changing agent-facing validate URL or response ownership  

---

## 7. Goal text (paste into `/goal` when forging — only after Wave I COMPLETE)

```text
Implement Open CRM/CPQ Wave J per docs/forge/19-wave-j-architecture.md in five parallel tracks: (1) quote — typed PricingServiceClient views; NEVER body(JsonNode.class) — String + Jackson 2 parse like I2; refactor QuoteService.calculate apply helpers; no pricing-service Maven deps; leave AI CpqAgentToolClients alone; (2) configuration-engine — VALIDATES attr:key in:v1,v2 dialect in ProductRuleEvaluationCore; parse in: before =/!=; keep = and !=; unsupported still skip; (3) FE quotes — enable New Quote create form (name + customerExternalId required) + detail Calculate/Submit via existing QuoteService HTTP; (4) FE catalog — enable New Product create (sku, name, productType, category) via ProductService.create; (5) dual-read readiness checklist docs-only with default approvals.legacy-local-read-enabled remaining true. Day-1 parallelize J1∥J2∥J3∥J4∥J5; J6 docs closeout after. Use GitNexus impact before symbol edits; re-analyze; warn HIGH/CRITICAL/UNKNOWN; disambiguate QuoteService and ApprovalService by file_path/uid. Do not invent Activities API, start #238/#241, flip legacy dual-read default, merge WhatsApp #430, embed validation in gateway, or full SpEL/Kafka S2B-9. Evidence: docs/forge/impl/j*.md done, quote calculate typed, attr:in: green, FE create/lifecycle green, dual-read default still true.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–I complete on forging tip (I1–I4 merged; I5 closeout)  
- [x] GitHub: open issues **only #238, #241** (held); Bolt **#412** close as superseded by I3; **#430** out  
- [x] Evidence: quote `PricingServiceClient` JsonNode; `QuoteService.calculate` JsonNode apply; VALIDATES no `in:`; FE New Quote/Product disabled; detail lifecycle absent; dual-read default true (H7 prep only)  
- [x] GitNexus: `PricingServiceClient` **LOW**; BE `QuoteService` **LOW** (treat MED product; text → `QuoteController`); `ProductServiceClient` **UNKNOWN**; `ProductRuleEvaluationCore` missing → **UNKNOWN** — **warned**; index **~51 behind**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**  
- [x] PR DAG max parallelism with disjoint files documented (**5** Day-1 panes: J1∥J2∥J3∥J4∥J5 → J6)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp #430, full SpEL, Kafka S2B-9, dual-read flip  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [x] Wave I COMPLETE on forging tip (gate before `/goal`)  
- [x] Orchestrator → Wave J FORGING  
- [x] Forge execution authorized (PM standing orchestrator + architect pick)  
- [x] Day-1 + closeout merged; J6 matrix + orchestrator COMPLETE  

**PM WARNING:** Re-analyze before quote/CE edits (**UNKNOWN** / stale index ≠ unused/safe). Do **not** flip approval legacy dual-read default (**HIGH**). J1 uses String+Jackson 2 parse (never `body(JsonNode.class)`). J5 docs-only; default stays `true`.

**WAVE J — COMPLETE.**
