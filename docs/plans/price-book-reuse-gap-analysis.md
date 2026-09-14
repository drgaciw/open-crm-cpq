# Gap Analysis Plan: Reuse from `price-book-service` → `open-crm-cpq`

**Status:** Plan / recommendations (not an implementation PR)  
**Date:** 2026-09-14  
**Source:** [drgaciw/price-book-service](https://github.com/drgaciw/price-book-service) (`main`)  
**Target:** this monorepo (`open-crm-cpq`)  
**Priority (decided 2026-09-14):** **(1) Catalog domain parity** — entry effective dating, selective book metadata, `resolve-batch` fidelity  
**Related plans:**  
- [pricing-ai-rules-reuse-gap-analysis.md](./pricing-ai-rules-reuse-gap-analysis.md) — owns Drools / financial / AI-rules harvest  
- [deal-desk-reuse-gap-analysis.md](./deal-desk-reuse-gap-analysis.md) — approvals / TCV handoff  
**Method note:** Cursor **Sequential Thinking MCP is not available** in this session. Extended reasoning uses the same step-decomposition pattern.

---

## 1. Executive verdict

`price-book-service` is a **2025-era multi-module prototype** (Spring Boot **3.2.5**, **MongoDB**, embedded **Drools 8.41**, Eureka, plus React/Vite and a separate Angular client). Open CRM/CPQ already has a **lean, production-shaped** `pricebook-service` (Boot 4 / Java 21 / PostgreSQL / Flyway) with Angular `@frontend/cpq-price-books` and an internal `resolve-batch` used by pricing.

**Execution order (locked):** finish **Track P1** (catalog domain parity: entry effective windows, selective header fields, resolve-batch correctness) to a done gate before bulk/coverage, Angular-only UX, or Cost ADR work. Do not interleave Drools/AI/SpEL into pricebook PRs.

**Reuse posture:** selective **capability / method harvest** into the existing `pricebook-service` (and, where rules/AI belong, into `pricing-service` / `ai-service`). Do **not** merge the source monorepo, reintroduce Mongo/Eureka, or mount Drools inside pricebook.

| Layer | Source | Target today | Reuse posture |
|-------|--------|--------------|---------------|
| Price book + entries CRUD | `backend/price-book-service` (`/api/pricebooks`) | `pricebook-service` (`/api/v1/price-books/**`) | **Partial — core landed**; harvest missing *setup* features (types, effective dating on entries, bulk, analysis) |
| Product catalog in pricebook | `ProductController` `/api/products` | `product-service` | **Exclude** — keep catalog ownership |
| Cost / margin | `CostController` `/api/costs` | None (FE IA backlog **removed** phantom `/api/v1/erp/costs`) | **Conditional** — only with product ADR; else derive margin from pricing execute |
| Drools apply-to-book | `PricingRuleController` / `DroolsRuleController` | Drools on `pricing-service` | **Hand off** to pricing Track 1 — do not put Drools in pricebook |
| Price models / bulk apply | `price-models-service` | None | **Selective** — bulk update / analysis patterns → pricebook or pricing |
| AI recommendations / SpEL | `pricing-rules-service` | `ai-service` + forge **full SpEL held** | **Exclude SpEL platform**; AI patterns → pricing-ai Track 2 / ai-service |
| FE React (Vite + Supabase) | repo root `src/` | Angular Nx | **Do not harvest React/Supabase** |
| FE Angular client | `angular-client/` (grid, filters, AI panel) | `@frontend/cpq-price-books` | **UX pattern harvest only** (signals/HttpClient rewrite) |
| Discovery | Eureka | Gateway + compose | **Exclude** |

---

## 2. Sequential thinking (extended)

### Thought 1 — Same name, different product shape

Both repos say “price book,” but:

- **Source** = pricing *setup* hub that also owns products, costs, Drools apply, AI recommend, price models.  
- **Target** = **list-price catalog service** for CPQ: books + entries + regional/default resolution for quote/pricing. Products live in `product-service`; commercial rules live in `pricing-service`.

Gap analysis must score **pricebook-owned** gaps first, and route everything else to the correct owner (or exclude).

### Thought 2 — What the source actually is

Evidence from `main` (last push ~2025-04; README still Vite template boilerplate):

| Module | Role |
|--------|------|
| `backend/price-book-service` | Core Mongo service: books, entries, products, costs, Drools apply |
| `backend/price-models-service` | Price models + apply to book + book analysis |
| `backend/pricing-rules-service` | Pricing rules CRUD, OpenAI/Azure recommendations, SpEL evaluate |
| `backend/eureka-server` | Service discovery |
| Root Vite/React | Dashboard: PriceBookGrid, FilterPanel, AIInsightsPanel, IntegrationStatus |
| `angular-client` | Angular 19 port of similar dashboard components |

Stack: Java 21, Boot 3.2.5, MongoDB + Mongock, Drools 8.41, Spring Security.

API prefix style: `/api/pricebooks`, `/api/costs`, `/api/products`, `/api/pricing-rules`, `/api/rules`, `/api/price-models` — **not** target `/api/v1/...` contract.

### Thought 3 — What the target already owns

| Concern | Target | Status |
|---------|--------|--------|
| Book CRUD + soft delete | `PriceBookController` | Landed; FE header update/delete Wave N1 |
| Entry CRUD + soft delete | same | Landed; FE Wave M2 |
| List/filter by region | `GET /api/v1/price-books?region=` | Agent-contract |
| Internal list-price resolve | `POST /internal/v1/price-books/resolve-batch` | Used by pricing-service |
| Angular portal | `@frontend/cpq-price-books` | Landed; **R2 list-row delete HELD** |
| Product catalog | `product-service` | Separate |
| Commercial Drools | `pricing-service` | Separate; pricing-ai Track 1 |
| Price book AI agent | `PriceBookManagementAgent` + skill | Scaffold (gap analysis / hierarchy in skill text ahead of BE) |

Target domain is thinner: book has `name/region/currency/isDefault/effectiveDate/expirationDate`; entry has `productExternalId/listPrice/floorPrice/currency` — **no** salesPrice, entry-level effective windows, book `type`, customer group, attributes map, or Cost entity.

### Thought 4 — Stack / architecture blockers

| Dimension | Source | Target | Implication |
|-----------|--------|--------|-------------|
| Boot | 3.2.5 | **4.0.6** | Port algorithms, not modules |
| DB | MongoDB documents | PostgreSQL + Flyway | Remodel; no Mongock |
| Paths | `/api/pricebooks` | `/api/v1/price-books` + gateway | Remap; update contract matrix for any new path |
| Drools in pricebook | Yes | **No** — pricing owns Drools | Hand off |
| SpEL service | Yes | Forge **full SpEL held** | Exclude |
| Eureka | Yes | Gateway | Exclude |
| Supabase FE | Yes | Angular signals | Exclude React stack |
| Secrets | `.env` in repo | Env/secret managers | Never copy |

### Thought 5 — Overlap with sibling reuse plans

| Source capability | Correct open-crm-cpq owner / plan |
|-------------------|-----------------------------------|
| Drools apply / pricing rules | **pricing-ai-rules Track 1** (not pricebook) |
| AI pricing recommendations | **pricing-ai Track 2** / `ai-service` (after Path B ADR) |
| SpEL evaluate | **Exclude** (forge hold) |
| Cost → margin | Prefer pricing `FinancialMetrics` / execute; Cost CRUD only if ADR |
| Product CRUD inside pricebook | **Exclude** — `product-service` |

### Thought 6 — Reuse modes

| Mode | Meaning | Typical price-book use |
|------|---------|------------------------|
| A | Already landed | Book/entry CRUD, resolve-batch, Angular portal |
| B | Port method/algorithm | Bulk update, entry effective dating, coverage/gap analysis |
| C | Port pattern | Filter/grid UX → Angular; lifecycle publish/immutable |
| D | Shared lib | Unlikely first |
| E | Optional companion | None strong |
| F | Exclude | Eureka, Mongo, React/Supabase, SpEL platform, product-in-pricebook, Drools-in-pricebook |

### Thought 7 — Risks

- **Re-bloating pricebook** into a second pricing engine (Drools/AI/costs).  
- **Phantom cost APIs** — FE backlog already deleted them; don’t resurrect without ADR.  
- **Path drift** — skills once documented wrong `/api/v1/pricing/price-books`.  
- **R2 hold** — list-row delete is forge-held; don’t “fix” via copy from Angular client.  
- **Published immutability** — target FE already disables edits when published; source lifecycle service may add BE enforcement worth harvesting carefully.  
- Stale dual FE (React + Angular) — pick Angular patterns only.

### Thought 8 — Strategy

Run a **pricebook-first** gap analysis (1.5–2.5 days) that:

1. Diffs book/entry **domain + APIs** vs target (types, effective dating, search, bulk, analysis).  
2. Scores FE UX from **angular-client** only (not React/Supabase).  
3. Hands Drools/AI/SpEL to sibling plans / excludes.  
4. Parks Cost until product says margin-from-cost is required.  
5. Produces a ranked harvest backlog owned by `pricebook-service` (+ FE lib).

---

## 3. Source capability snapshot

### 3.1 Core price-book-service APIs

| Area | Paths (source) | Target analog |
|------|----------------|---------------|
| Books | `GET/POST/PUT/DELETE /api/pricebooks`, by type/name/search/date/price-range/product | `/api/v1/price-books` — **partial** (list/get/CRUD; missing rich search/type) |
| Entries | Via services (entry service; rules apply to entry) | Nested `/api/v1/price-books/{id}/entries/**` — **landed CRUD** |
| Products | `/api/products/**` | **Exclude** → product-service |
| Costs | `/api/costs/**`, margin by product | **Missing** / conditional |
| Drools | `/api/pricing-rules/apply/...`, `/api/rules/apply-pricing/...` | **Hand off** → pricing-service |

### 3.2 Companion modules

| Module | Notable APIs | Disposition |
|--------|--------------|-------------|
| price-models | CRUD `/api/price-models`, apply to book, analysis | Harvest **apply/analysis** ideas into pricebook or pricing |
| pricing-rules | Recommendations, SpEL evaluate/apply | AI → Track 2; SpEL → exclude |
| eureka | Discovery | Exclude |

### 3.3 Frontend

| Surface | Stack | Disposition |
|---------|-------|-------------|
| Root app | React 18 + Vite + Radix + **Supabase** | **Exclude** wholesale |
| `angular-client` | Angular 19: grid, filters, AI insights, integration status | **Pattern harvest** into `@frontend/cpq-price-books` / shared-ui |
| AI insights panel | Calls recommendation APIs | Wire later via `ai-service` / agent — not React component copy |

---

## 4. Target owner map

| Capability class | Owner | Notes |
|------------------|-------|-------|
| Book / entry CRUD, resolve-batch | `pricebook-service` | Keep thin |
| Rich search / type / customer-group / attributes | `pricebook-service` (+ Flyway) | Only if product needs |
| Entry effective windows / price history | `pricebook-service` | Impacts resolve-batch semantics — ADR if changing |
| Bulk price update | `pricebook-service` | New endpoint + matrix row |
| Coverage / gap analysis | `pricebook-service` and/or agent 035 | Skill already describes gap analysis |
| Cost ledger | New ADR → pricebook **or** separate | Default: park |
| Product catalog | `product-service` | Never duplicate |
| Drools commercial rules | `pricing-service` | pricing-ai Track 1 |
| AI recommendations | `ai-service` | After Path B |
| FE portal | `@frontend/cpq-price-books` | Angular only; respect R2 hold |
| Gateway paths | `gateway-service` + contract matrix | Any new external path |

---

## 5. Gap analysis workplan

Work is split into **Track P1** (priority 1, authorized) then later tracks only after the P1 done gate.

### Phase P0 — Access & freeze (shared, 0.5 day)

1. Clone at **pinned SHA**; record SHA (source is older — note staleness).  
2. Attempt `backend/price-book-service` build (JDK 21, Boot 3.2) — document failures.  
3. Inventory all controllers → CSV/MD with path remap to `/api/v1`.  
4. Secret scan: `.env`, Supabase keys — **read-only**; never copy.  
5. Cross-link: Drools/AI/SpEL rows tagged for sibling plans / exclude.

**Exit:** pinned SHA + inventory + secret note.

---

### Track P1 — Catalog domain parity (priority 1)

#### Phase P1-M — Capability matrix, domain only (0.5–0.75 day)

Rows **only** for:

1. Book header: type, status, customer group, attributes vs target `region/isDefault/currency/effectiveDate/expirationDate`  
2. Entry: list vs sales vs floor; `effectiveFrom`/`effectiveTo`; compound uniqueness  
3. Current-price / as-of selection algorithms used when multiple entries exist  
4. Book-level effective windows vs entry-level (interaction with `resolve-batch` / `isEffective`)  
5. Lifecycle publish / immutability BE vs FE published gates  
6. Explicit defer: bulk, coverage APIs, Angular UX polish, Cost, Drools, AI, SpEL  

**Exit:** Track P1 matrix; top **5–8** domain candidates ranked.

#### Phase P1-Deep — Deep compare (0.5–0.75 day)

1. Field-level map: source Mongo docs → proposed Flyway columns (add / ignore / later).  
2. Spec `resolve-batch` behavior if entry dating is added (as-of = today; conflict resolution).  
3. Regression plan: pricing-service `PriceBookResolutionClient` + execute goldens.  
4. GitNexus impact for `PriceBookService`, `PriceBookInternalResolutionService`, entry repos.  
5. FE DTO/`pricing.model.ts` impact — no R2 list-row delete.

**Exit:** Track P1 harvest backlog with named tests.

#### Phase P1-Decide — Gate (0.25 day)

1. **Do now** — entry dating and/or selective header fields that improve resolve fidelity  
2. **Won’t map** — salesPrice if floor+list suffice; Mongo DBRefs; product embed  
3. **Defer** — bulk/coverage (Track P2), Angular UX (Track P3), Cost ADR  

**Track P1 done gate:** resolve-batch + entry/book dating semantics either landed, ticketed with owner, or explicitly won’t-do.

#### Phase P1-Pilot — PRs (after P1-Decide)

| Order | Pilot | Owner |
|------:|-------|-------|
| 1 | Domain parity matrix (source → target / won’t-map) living in docs + tests | docs + pricebook tests |
| 2 | Entry effective windows + resolve-batch as-of selection (if gap confirmed) | `pricebook-service` |
| 3 | Selective book metadata (e.g. type/status) only if CPQ needs beyond region/default | `pricebook-service` + FE models |
| 4 | BE publish/immutability aligned with FE published gates | `pricebook-service` |

---

### Later tracks (after P1 gate — not authorized yet)

| Track | Focus |
|-------|--------|
| **P2** | Bulk update + coverage/gap analysis APIs — **scored backlog:** [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md) |
| **P3** | Angular-client UX patterns only (see P2-5 in P2 backlog) |
| **P4** | Cost ledger — **ADR required** |

---

## 6. Recommendations under locked priority

### 6.1 Track P1 — Do now (catalog domain parity)

| Candidate | Mode | Land in | Why |
|-----------|------|---------|-----|
| Entry effective dating / current-price selection | B | entries + `resolve-batch` | Highest fidelity gap vs source compound index + effective windows |
| Book effective windows (already partial) vs entry-level interaction | B | resolve + tests | Avoid double-dating bugs |
| Book type / status / attributes (**selective**) | B | Flyway + DTOs | Only fields with clear CPQ use; default lean |
| Publish / immutability BE enforcement | C | pricebook | Align with FE published gates |
| Domain parity matrix as living spec | C | docs + tests | Prevents Mongo field dump |

### 6.1b After P1 gate (not first)

| Candidate | Mode | Land in | Why |
|-----------|------|---------|-----|
| Bulk price update | B | new API + matrix | Admin productivity — Track P2 |
| Coverage / gap analysis | B/C | pricebook + product client | Agent 035 skill — Track P2 |
| Angular grid/filter UX patterns | C | FE price-books | Track P3; no React |

### 6.2 Hand off / coordinate

| Candidate | Destination |
|-----------|-------------|
| Drools apply-to-book/entry | pricing-ai-rules Track 1 / `pricing-service` |
| AI recommendation services | ai-service / pricing-ai Track 2 (Path B ADR) |
| Price analysis that is commercial (not catalog) | pricing execute / simulator |

### 6.3 Conditional (product ADR)

| Candidate | Gate |
|-----------|------|
| Cost entity + margin-by-product API | Explicit “cost-based pricing / ERP cost sync” ADR; otherwise keep margin from pricing execute |
| Customer-specific price books | Confirm hierarchy (global→regional→customer) vs region/default model |

### 6.4 Explicit excludes

- Merge source backend as a second pricebook deployable.  
- MongoDB / Mongock / Eureka.  
- React + Supabase frontend.  
- Product CRUD inside pricebook.  
- Drools or SpEL runtime inside pricebook.  
- Unholding **R2** list-row delete via this harvest.  
- Copying `.env` / API keys.  
- Phantom `/api/v1/erp/costs` without ADR (FE backlog already fixed this).

### 6.5 Architecture rules for any harvest PR

1. External paths stay under `/api/v1/price-books/**` (or new matrix-approved siblings) — never `/api/v1/pricing/price-books`.  
2. DTOs at boundary (ADR-006); Flyway for schema.  
3. Do not add Drools/AI dependencies to `pricebook-service`.  
4. `resolve-batch` behavior changes need pricing-service regression tests.  
5. Respect forge holds (R2, full SpEL).  
6. GitNexus impact before symbol edits; `detect_changes` before commit.  
7. FE changes verified in browser per project rules.

---

## 7. Deliverables

1. This plan (done; priority **catalog domain parity** locked).  
2. `docs/plans/price-book-capability-matrix-p1.md` — Track P1 at pinned SHA.  
3. `docs/plans/price-book-harvest-backlog-p1.md` — ranked domain/resolve PRs.  
4. `docs/plans/price-book-harvest-backlog-p2.md` — **done** (scored bulk/coverage/UX candidates; not implementation-authorized until P1 gate).  
5. Optional ADR: “Cost ledger / margin-from-cost” — only if prioritized.  
6. Optional ADR: “Price book hierarchy (customer-specific)” — if extending beyond region/default.

---

## 8. Blast radius (if harvest proceeds)

| Theme | Blast radius |
|-------|----------------|
| Schema / entry dating | `pricebook-service`, Flyway, `resolve-batch`, pricing-service client, FE models |
| Bulk / analysis APIs | gateway, contract matrix, agents 004/032/035, FE |
| UX patterns | `@frontend/cpq-price-books` only (ideally) |
| Cost (if ever) | new tables, FE IA reversal, possible ERP integration |

---

## 9. Out of scope for this document

- Implementing harvest PRs.  
- Lifting R2 or SpEL holds.  
- Installing Sequential Thinking MCP in Cursor.  
- Replacing Angular price-books with React/Supabase.

---

## 10. Decisions & open questions

### Decided

| Topic | Decision |
|-------|----------|
| Priority | **1 = Catalog domain parity** (entry dating, selective metadata, resolve-batch) |
| React / Supabase harvest | **No** |
| Eureka / Mongo merge | **No** |
| Drools inside pricebook | **No** (hand off) |
| SpEL platform | **No** (forge hold) |
| Product CRUD in pricebook | **No** |
| Primary harvest owner | **`pricebook-service`** (+ FE models as needed) |
| Bulk / UX / Cost | **After P1 gate** (Cost still ADR-gated) |

### Still open (does not block P0 / P1-M)

1. If multiple dated entries overlap, prefer **latest effectiveFrom**, **highest priority attribute**, or **reject overlapping writes**? (Architect lean during P1-Deep: **reject overlapping writes** + resolve by latest `effectiveFrom` ≤ as-of.)  
2. Is source `salesPrice` needed, or do target `listPrice` + `floorPrice` remain sufficient? (Architect lean: **sufficient** — don’t add salesPrice unless quote/pricing contracts require it.)

---

## Appendix A — Quick links

| Resource | Location |
|----------|----------|
| Source repo | https://github.com/drgaciw/price-book-service |
| Target service | `backend/services/pricebook-service` |
| Contract matrix §4b | `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` |
| FE lib | `frontend/libs/cpq/price-books` |
| Agent skill | `agents/skills/price-book-management/SKILL.md` |
| FE IA (phantom costs removed) | `frontend/PRICING_IA_BACKLOG.md` |
| Sibling plans | `docs/plans/pricing-ai-rules-reuse-gap-analysis.md`, `deal-desk-reuse-gap-analysis.md` |

## Appendix B — Method caveat

Sequential Thinking MCP (Cursor): **unavailable** here; analysis used manual multi-step decomposition.
