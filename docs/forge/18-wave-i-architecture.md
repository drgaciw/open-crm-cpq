# Wave I architecture — contract/docs hygiene ∥ AI JsonNode remainder ∥ Bolt #412 ∥ FE CPQ polish

**Status:** COMPLETE on main (I1–I4 merged; I5 closeout) — PM authorized 2026-08-30 (architect pick)  
**Architect:** `opencrm-architect`  
**Evidence date:** 2026-08-30  
**Baseline:** worktree / `origin/main` @ `d90b489` (Waves A–H COMPLETE)  
**Sources:** GitNexus CLI on `/home/username01/Projects/open-crm-cpq` (index @ `c5075a8`, **41 commits behind** HEAD — re-analyze before symbol edits); GitHub open issues/PRs; tree inventory; `docs/forge/17-wave-h-architecture.md`; `docs/forge/impl/h1–h8`; `docs/backlog/ADR-configuration-api-reconciliation.md`; `docs/backlog/CPQ_API_CONTRACT_MATRIX.md`

## 0. Wave selection (decision record)

| Candidate | Verdict | Why |
|-----------|---------|-----|
| **Activities API** | **HOLD** | No Spring schema/controller; forge non-negotiable: do not invent |
| **CI runners (#238)** | **HOLD** | Billing/runner infra — not forgeable in app worktrees; only open issues with #241 |
| **Agent Kafka (#241)** | **HOLD** | Agent-stack migration; out of this wave |
| **Delete / retire product-service validate** | **HOLD** | Dual-run parity not calendar-ready; prefer defer |
| **Gateway domain validation** | **REJECT** | ADR + forge DO NOT TOUCH — route+adapt only |
| **Flip `approvals.legacy-local-read-enabled` default → false** | **HOLD** | H7 prep only; flipping without empty-legacy evidence is **HIGH** dual-store cutover |
| **Full SpEL / Drools / OpenL** | **REJECT** | Over-scope; H1 constrained dialect already landed |
| **S2B-9 Kafka / event invalidation** | **HOLD** | Event bus + product publishers not ready; H6 thin TTL already advances freshness |
| **Bolt #430 WhatsApp** | **REJECT** | Legacy `cpq_beta` / communication path; not CPQ cutover |
| **Quote `PricingServiceClient` / `ProductServiceClient` JsonNode** | **DEFER** | Quote already walks `success`/`data`/`lineResults` carefully; larger apply-path blast than AI tool envelope — not day-1 |
| **✅ Track 1: ADR + matrix S2B-11 text drift** | **IN** | ADR header says Wave F hybrid landed, but body still claims “implementation deferred” / “runtime today product authoritative” / matrix “may delegate in S2B-11” |
| **✅ Track 2: Remaining AI JsonNode clients** | **IN** | H2 structured validate only; `executePricing` + `getQuoteLineItems` still opaque `postJson`/`getJson` |
| **✅ Track 3: Bolt #412 product validate O(N)** | **IN** | Tree @ `d90b489` still `groupingBy` + nested `for (Long productId)` — Jules PR gap still real; GitNexus method **LOW** |
| **✅ Track 4: FE CPQ polish** | **IN** | HTTP wired (G6–G8) but stub comments, public MOCK barrel exports unused by apps, disabled create affordances undocumented |

**PM authorization used:** “Architect picks Wave I from GitNexus+GitHub.”

**Chosen theme:** **Largest safe forge** — close leftover contract/docs hygiene after Waves F–H, finish AI tool-client typing left by H2, land Bolt #412 on retained product validate (real O(N×M) gap), and FE CPQ presentational polish. No Activities / #238 / #241 / product-validate delete / gateway rules / dual-read default flip / WhatsApp / Kafka.

---

## 1. Problem / evidence

### I-T1 — ADR + matrix still describe pre-F S2B-11 (LOW docs / MED confusion)

| Doc surface | Drift @ `d90b489` | Target |
|-------------|-------------------|--------|
| ADR header **Implementation PR** | Correctly notes Wave F hybrid landed | Keep |
| ADR § Gateway behavior | “S2B-11 — **implementation deferred**” | Rewrite as **landed (Wave F hybrid)** + retained product validate |
| ADR AC-8 / Resolved open Q prose | “S2B-11 code remains **deferred**”; “constrain a **future** S2B-11”; “Runtime today … product-service validate remains authoritative” | Historical Accept notes preserved; add **Post-F/G/H reality** section so readers are not told shim never shipped |
| ADR Related docs row for matrix | “still documents current `List<String>` validate shape until S2B-11” | Agent path = structured DTO; product retained = `List<String>` |
| Matrix open-questions table | “gateway preserves agent path (**may delegate in S2B-11**)” | **Does** delegate gateway → CE evaluate (F3) |

**Evidence:** `rg` hits on “implementation deferred” / “Runtime today” in `docs/backlog/ADR-configuration-api-reconciliation.md`; matrix line still “may delegate in S2B-11”. Matrix validate row itself is largely current post-H8 — residual drift is the open-questions / ADR body.

### I-T2 — AI tools still opaque on pricing + line-items (LOW–MED contract)

| Consumer | Post-H2 state |
|----------|---------------|
| `CpqAgentToolClients.validateConfiguration` | Structured `ApiResponse<ConfigurationValidationResult>` (H2) |
| `executePricing` | `postJson` → opaque `JsonNode` |
| `getQuoteLineItems` | `getJson` → opaque `JsonNode` |
| `CpqToolResponse.data` | Remains `JsonNode` envelope (OK) — parse before wrap |

**Constraint:** `ai-service` depends on `libs/common` + `libs/security` only — **must not** add `pricing-service` / `quote-service` Maven deps. Prefer AI-local view records or `ApiResponse` + field asserts, then `valueToTree` into tool envelope (same pattern as H2).

**GitNexus** (`-r /home/username01/Projects/open-crm-cpq`, index **41 behind**):

| Symbol | Risk | Note |
|--------|------|------|
| `CpqAgentToolClients` | **LOW** (1 direct → `CpqAgentTools`) | Re-impact after analyze |
| `executePricing` | **LOW** (lower-bound; 1 dropped call site) | Confirm with `rg` |
| `getQuoteLineItems` | **LOW** | Tools registry only |

### I-T3 — Bolt #412 still a real tree gap (LOW graph / MED perf on retained path)

Confirmed open issues: **only #238, #241** (held).

Confirmed open Bolt PRs (Jules):

| PR | Title | Tree status @ `d90b489` | Wave I |
|----|-------|-------------------------|--------|
| **#412** | Optimize validation logic | `ConfigurationRuleService.validateConfiguration` still groups REQUIRES/EXCLUDES then nested-loops selected IDs | **IN** |
| **#430** | WhatsApp lookup | Legacy | **OUT** |

```text
ConfigurationRuleService.java — requiresRulesByProduct / excludesRulesByProduct groupingBy
  + for (Long productId : selectedProductIds) { nested rule loops }
```

Jules #412 intent: iterate `allRules` once (flat O(rules)), keep message text using `sourceProductId`. Semantics for REQUIRES/EXCLUDES unchanged; parity fixtures must stay green.

**GitNexus:** product `validateConfiguration` **LOW** (1 direct → controller). Class-level `ConfigurationRuleService` **UNKNOWN** (0 edges) — confirm controller + parity tests via `rg` (done). Treat **UNKNOWN ≠ unused**.

**Why safe now (vs Wave H DEFER):** Agent evaluate owns CE path; product validate is **retained transitional** for dual-run/parity. Micro-opt does not delete endpoint, does not change response shape (`List<String>`), does not touch gateway.

### I-T4 — FE CPQ polish leftovers (LOW UX / docs-in-code)

| Gap | Evidence |
|-----|----------|
| Stub comments | `quotes.model.ts` — “presentational stub”; `catalog.ts` — “PR-FE-CPQ-05 lite stub” |
| Public MOCK barrels | `catalog` / `quotes` / `pricing-simulator` `index.ts` export `MOCK_*`; **no** `frontend/apps` imports |
| Disabled creates | Catalog “New Product” / Quotes “New Quote” disabled with no title/tooltip explaining deferral |
| Happy path | G6–G8 already HTTP; do **not** reintroduce mock-as-runtime fallback |

---

## 2. Decisions + rejected alternatives

### Track 1 — Docs / ADR / matrix

| Topic | Decision | Rejected |
|-------|----------|----------|
| ADR body | Add **Post-implementation status (Waves F–H)**; retitle deferred gateway bullets as historical Accept freeze; state hybrid S2B-11 **landed**; product validate **retained** (no removal date) | Rewrite history to pretend Accept shipped code; delete Accept freeze section |
| Matrix | Fix residual “may delegate” / related-doc rows to match F3+H runtime | Big-bang rewrite of entire matrix |
| Scope | ADR + matrix (+ short forge note pointers). Optional one-line GRAPHITE backlog pointer **only if file present** | Touch MODERNIZATION_PLAN / agent PRDs wholesale |

### Track 2 — AI JsonNode remainder

| Topic | Decision | Rejected |
|-------|----------|----------|
| Pricing | Unwrap gateway `ApiResponse` (or JsonNode walk asserting `success` + `data`); assert agent-useful fields (`success`/`lineResults`/`approvalState`/`errorMessage` as available); keep `CpqToolResponse` envelope | Depend on `pricing-service` artifact; silent opaque forever |
| Line-items | Unwrap `ApiResponse<List<…>>` or JsonNode array; AI-local `QuoteLineItemView` (id, productExternalId, quantity, net/extended amounts) | Depend on quote-service DTO module |
| Envelope | `data` stays `JsonNode` via `valueToTree` (H2 pattern) | Change all tool schemas / break Spring AI tool binding |
| Quote BE clients | **Out** this wave | Bundle quote `PricingServiceClient` rewrite day-1 |

### Track 3 — Bolt #412

| Topic | Decision | Rejected |
|-------|----------|----------|
| Algorithm | Single pass over batched `allRules`; filter REQUIRES/EXCLUDES inline; `productSet` membership checks unchanged | Port CE expression engine into product; delete validate |
| Messages | Prefer `rule.getSourceProductId()` in default strings (Jules) — keep custom `errorMessage` first | Change API response to structured DTO |
| Tests | Parity fixtures + unit cases for requires-miss / excludes-hit | Skip tests |

### Track 4 — FE CPQ

| Topic | Decision | Rejected |
|-------|----------|----------|
| Comments / READMEs | Replace stub wording with “HTTP-backed list/detail” language | Revert to mock-only UI |
| MOCK exports | Keep mocks for Vitest; **stop public barrel re-export** if unused by apps (or document `@internal` / test-only path) | Delete mock files used by specs |
| Disabled CTAs | `title` / `matTooltip` “Create deferred — API not wired in portal” | Invent create wizards / NgRx this wave |
| Approval inbox / NgRx | Out | Scope creep |

---

## 3. PR DAG (max safe parallelism)

Disjoint surfaces: **docs** ∥ **AI clients** ∥ **product validate** ∥ **FE CPQ libs**.

```text
Track 1 (docs)          Track 2 (AI)              Track 3 (Bolt #412)        Track 4 (FE)
───────────────         ─────────────             ──────────────────         ────────────
I1 ADR + matrix         I2 AI pricing +           I3 product validate        I4 FE CPQ polish
   S2B-11 hygiene          line-items structured      O(N) (#412 salvage)        stub/MOCK/CTA
        │                      │                         │                          │
        └──────────────────────┴─────────────────────────┴──────────────────────────┘
                                               ▼
                                I5 docs/matrix forge closeout + orchestrator COMPLETE
```

| ID | Title | Scope (files) | Risk | Acceptance |
|----|-------|---------------|------|------------|
| **I1** | `docs(cpq): reconcile ADR/matrix to post-F S2B-11 reality` | `docs/backlog/ADR-configuration-api-reconciliation.md`; residual matrix rows; `docs/forge/impl/i1-adr-matrix-hygiene.md` | **LOW** | No reader can conclude gateway validate shim is still deferred; product validate retention explicit; no Java/TS runtime edits |
| **I2** | `feat(ai): structured parse for pricing execute + quote line-items tools` | `CpqAgentToolClients` (+ tests); AI-local view types as needed; `docs/forge/impl/i2-ai-jsonnode-remainder.md` | **LOW–MED** | Unit fixtures assert pricing `success`/`lineResults` (or documented fields) and line-item list shape; validate path unchanged; no new Maven module deps on pricing/quote |
| **I3** | `perf(product): O(N) configuration validate loop (#412)` | `ConfigurationRuleService.validateConfiguration` (+ parity/unit tests); salvage Jules #412; `docs/forge/impl/i3-product-validate-on.md` | **LOW** | Flat rules iteration; REQUIRES/EXCLUDES semantics + default messages green; endpoint retained `List<String>`; no gateway/CE edits |
| **I4** | `chore(fe-cpq): stub comment + MOCK barrel + disabled CTA polish` | `frontend/libs/cpq/{catalog,quotes,pricing-simulator}` comments/index/tooltips (+ specs if exports change); `docs/forge/impl/i4-fe-cpq-polish.md` | **LOW** | No presentational-stub claims on HTTP pages; apps still build; mocks remain for Vitest; create buttons explain deferral |
| **I5** | `docs(cpq): Wave I forge closeout` | Matrix touch-ups if I1 left residuals; orchestrator → COMPLETE; `docs/forge/impl/i5-wave-i-docs.md` | **LOW** | Docs match runtime; holds listed; open Bolt #412 closed or superseded note |

### Parallelism rules

**Day-1 max safe parallel (4 panes):** **I1 ∥ I2 ∥ I3 ∥ I4**

| Pair | Conflict? | Rule |
|------|-----------|------|
| I1 vs I5 | Same docs tree | **Serial** — I5 after I1 (and ideally after I2–I4 notes) |
| I2 vs I3 | AI vs product | Parallel |
| I3 vs CE | — | **Do not** edit CE / gateway in I3 |
| I4 vs I2 | FE vs AI | Parallel |
| I2 internal | pricing + line-items same class | **Single PR** — avoid split-file merge fights on `CpqAgentToolClients.java` |

**Do not** open a pane that deletes product validate, embeds gateway domain rules, flips dual-read default, merges WhatsApp #430, or starts #238/#241.

---

## 4. Blast radius / GitNexus risks

| Change | Upstream / dependents | GitNexus / text | Wave risk |
|--------|----------------------|-----------------|-----------|
| I1 ADR/matrix | Humans + future forge agents | Docs only | **LOW** |
| I2 `CpqAgentToolClients` | `CpqAgentTools` | **LOW** (stale index) | **LOW–MED** contract |
| I3 product `validateConfiguration` | `ConfigurationRuleController`; F4 parity fixtures | Method **LOW**; service class **UNKNOWN** | **LOW** (perf) / confirm parity |
| I4 FE CPQ libs | CRM shell routes | FE outside Java graph | **LOW** |
| product `/validate` retention | Dual-run / direct callers | Must remain | — |
| gateway validate | Route+adapt only | Do not embed rules | — |
| dual-read default | quote `ApprovalService` | Flip remains **HIGH** — not in wave | — |

**Explicit HIGH callouts:**

1. **Do not flip** `approvals.legacy-local-read-enabled` default in Wave I.  
2. **Index 41 commits behind** — re-analyze (`node .gitnexus/run.cjs analyze`) on tip before symbol edits; treat **UNKNOWN** as unresolved + confirm with `rg`.  
3. Disambiguate `validateConfiguration` (AI vs product vs quote client) by `file_path` / uid.

**UNKNOWN callout:** `ConfigurationRuleService` class (0 edges) — text shows controller + parity tests. Empty caller set ≠ safe to delete.

---

## 5. Herdr forge layout (4 Day-1 panes)

| Pane | Agent id | Worktree branch | PR |
|------|----------|-----------------|-----|
| 1 | `wi-adr-matrix` | `docs/forge-i1-adr-matrix-hygiene` | I1 |
| 2 | `wi-ai-jsonnode` | `feat/forge-i2-ai-jsonnode-remainder` | I2 |
| 3 | `wi-product-validate-on` | `feat/forge-i3-product-validate-on` | I3 |
| 4 | `wi-fe-cpq-polish` | `chore/forge-i4-fe-cpq-polish` | I4 |
| 5 (closeout) | `wi-docs` | `docs/forge-i5-wave-i-closeout` | I5 |

**Model:** Grok 4.5, `--always-approve`, worktree-isolated.  
**Mandatory:** Re-analyze GitNexus on tip; `impact` before symbol edits; disambiguate `validateConfiguration` by `file_path`/`uid`; warn HIGH/CRITICAL/UNKNOWN.  
**Do not** implement Activities, #238, #241, product validate deletion, gateway domain validation, dual-read default flip, WhatsApp #430, full SpEL, or Kafka S2B-9.

---

## 6. Out of scope (this wave)

- Activities API / phantom `/api/v1/activities/**`  
- CI runners (#238), agent Kafka (#241)  
- Deleting or calendar-deprecating product-service `POST /api/v1/configuration/validate`  
- Embedding domain validation in gateway  
- Full SpEL/Drools/OpenL expression engines  
- S2B-9 Kafka / Redis shared invalidation bus  
- Flipping `approvals.legacy-local-read-enabled` default to `false`  
- Bolt #430 WhatsApp  
- Quote-service `PricingServiceClient` / `ProductServiceClient` typed rewrite  
- NgRx CPQ store, approval inbox FE, quote/product create wizards, FULL_PREFLIGHT ERP  
- Changing agent-facing validate URL or response ownership  

---

## 7. Goal text (paste into `/goal` when forging)

```text
Implement Open CRM/CPQ Wave I per docs/forge/18-wave-i-architecture.md in four parallel tracks: (1) docs — reconcile ADR-configuration-api-reconciliation + residual CPQ matrix rows so S2B-11 hybrid is documented as landed (Waves F–H), not still “implementation deferred”; keep product validate retained with no removal date; (2) AI — CpqAgentToolClients structured parse for executePricing and getQuoteLineItems (ApiResponse / AI-local views + valueToTree); do not add pricing-service or quote-service Maven deps; leave validate path as H2; (3) Bolt #412 — product ConfigurationRuleService.validateConfiguration flat O(rules) loop; salvage Jules semantics; parity fixtures green; do not delete endpoint; (4) FE CPQ polish — remove presentational-stub claims, tighten MOCK barrel exports vs Vitest-only usage, tooltips on disabled New Product/New Quote. Day-1 parallelize I1∥I2∥I3∥I4; I5 docs closeout after. Use GitNexus impact before symbol edits; re-analyze (index was 41 behind); warn HIGH/CRITICAL/UNKNOWN; disambiguate validateConfiguration by file_path. Do not invent Activities API, start #238/#241, flip legacy dual-read default, merge WhatsApp #430, embed validation in gateway, or rewrite quote PricingServiceClient. Evidence: docs/forge/impl/i*.md done, ADR no longer claims S2B-11 deferred as current, AI fixtures assert pricing/line-item shapes, #412-equivalent landed, FE polish green, merge+push main when authorized.
```

---

## 8. Architect sign-off checklist

- [x] Waves A–H complete on tip `d90b489`  
- [x] GitHub: open issues **only #238, #241** (held); open Bolt PRs **#412, #430** inventoried (#430 out)  
- [x] Evidence: ADR body still “S2B-11 deferred”; AI pricing/line-items JsonNode opaque; product validate still groupingBy O(N×M); FE stub comments + MOCK barrels + disabled CTAs  
- [x] GitNexus: AI clients **LOW**; product `validateConfiguration` **LOW**; `ConfigurationRuleService` **UNKNOWN** (text-confirmed) — **warned**; index **41 behind**  
- [x] Dual-read default flip **held**; product validate delete **held**; gateway domain rules **rejected**  
- [x] PR DAG max parallelism with disjoint files documented (**4** Day-1 panes)  
- [x] Holds: Activities, #238, #241, product validate deletion, gateway domain rules, WhatsApp #430, full SpEL, Kafka S2B-9, quote JsonNode clients, dual-read flip  
- [x] PM authorized architect pick from GitNexus+GitHub evidence  
- [ ] Forge execution authorized (await `/goal` / Herdr)  
- [ ] Day-1 + closeout merged; I5 matrix + orchestrator COMPLETE  

**PM WARNING:** Re-analyze before product/AI edits (**UNKNOWN** / stale index ≠ safe). Do **not** flip approval legacy dual-read default in this wave.

**WAVE I — PLANNING (architect draft).**
