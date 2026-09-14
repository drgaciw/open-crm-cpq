# Price-book Track P2 — Harvest candidate backlog (scored)

**Status:** Recommendations only (not implementation-authorized until Track **P1 done gate**)  
**Date:** 2026-09-14  
**Parent plan:** [price-book-reuse-gap-analysis.md](./price-book-reuse-gap-analysis.md)  
**Source:** [drgaciw/price-book-service](https://github.com/drgaciw/price-book-service)  
**Target baseline:** `pricebook-service` CRUD + `resolve-batch`; Angular `@frontend/cpq-price-books`; agents **004** (list price) / **035** (price book management skill + PRD)

---

## 1. Purpose

Score **post-P1** harvest candidates from the source repo against what open-crm-cpq already has, so Track P2 (bulk / analysis / coverage) is ready when catalog domain parity closes.

**Effort:** S ≤ 1–2 days · M ≈ 3–5 days · L ≥ 1 week (incl. contract + FE + tests)  
**Value:** how much it closes agent-035 / admin workflows vs source-only novelty  
**Depends on P1:** whether entry dating / resolve semantics should land first

---

## 2. Target gaps (evidence)

| Need (agent / FE) | Landed today | Source has |
|-------------------|--------------|------------|
| Entry effective dates | **Missing** on `PriceBookEntry` / create DTO (skill + agent-035 assume them) | Entry `effectiveFrom`/`effectiveTo`, `getCurrentPriceBookEntry` |
| Bulk updates | **Missing** | `BulkPriceUpdateRequest` + `bulkUpdatePrices`; price-models apply |
| Coverage / gap matrix | **Missing** API | Partial via `analyzePriceBook` (market-style stats, not catalog coverage) |
| Health / staleness | **Missing** | Not first-class; skill requires it |
| Book list filters | Region + page only | Type, name search, date ranges, product, price-range |
| Publish immutability | FE + `ensureNotPublished` on writes | Lifecycle/status fields richer |
| Apply discount / salesPrice | **N/A by design** | `applyDiscount`, `setSalesPrice` — exclude or defer |
| Market ABOVE/BELOW analysis | **N/A** | `PriceAnalysis` market averages — hand off / exclude |

---

## 3. Ranked P2 candidates

### P2-1 — Coverage / gap analysis API — **Priority A**

| | |
|--|--|
| **Source** | Workflow implied by skill; source `analyzePriceBook` is **not** the same (market stats). Harvest the *idea* + product×book matrix shape from agent-035, not Mongo analysis code. |
| **Target land** | `GET /api/v1/price-books/{id}/coverage` and/or `GET /api/v1/price-books/coverage?region=` |
| **Owner** | `pricebook-service` (+ RestClient to `product-service` ACTIVE products) |
| **Effort** | **M** |
| **Value** | **High** — core of agent-035 / skill (“coverage matrix”, ≥95% KPI) |
| **Depends on P1** | Soft — works with current one-entry-per-product; better after entry dating (staleness) |
| **Acceptance** | Given book B and ACTIVE catalog, returns missing `productExternalId`s, coverage %, optional stale flag; Vitest/FE optional later; matrix row + agent skill curl updated |
| **Risk** | MED — product-service coupling; large catalogs need paging |
| **Decision** | **Harvest (adapt)** after P1 gate |

### P2-2 — Bulk entry update API — **Priority A**

| | |
|--|--|
| **Source** | `BulkPriceUpdateRequest` (FIXED / PERCENTAGE / AMOUNT), `PriceBookService.bulkUpdatePrices`, price-models `applyPriceModel` |
| **Target land** | `POST /api/v1/price-books/{id}/entries:bulk` (or `/bulk-entries`) — unpublished books only |
| **Owner** | `pricebook-service` + FE batch UI later |
| **Effort** | **M** |
| **Value** | **High** — agent-035 “bulk price update assistance”; admin annual/FX lifts |
| **Depends on P1** | Soft for FIXED; **Hard** if bulk sets effective dates |
| **Acceptance** | Batch upsert/update by `productExternalId`; reason logged; partial failure report; respects `ensureNotPublished` + floor≤list; contract matrix + tests |
| **Risk** | MED — partial failure semantics; don’t auto-notify customers (`notifyCustomers` → exclude) |
| **Decision** | **Harvest (adapt)** — drop notifyCustomers; map `newPrice` → listPrice; optional floor adjust policy explicit |
| **Suggested PR order** | After or parallel with P2-1; FE can follow |

### P2-3 — Price book health summary — **Priority B**

| | |
|--|--|
| **Source** | Not a clean source endpoint; agent-035 health score (coverage%, staleness%, issues) |
| **Target land** | `GET /api/v1/price-books/{id}/health` composing coverage + dating rules |
| **Owner** | `pricebook-service` |
| **Effort** | **S–M** (S if built on P2-1) |
| **Value** | **Medium–High** for dashboards / agent |
| **Depends on P1** | **Hard** for meaningful “stale effective dates” |
| **Acceptance** | JSON health object; unit tests with fixtures |
| **Decision** | **Harvest (spec-driven)** after P2-1; don’t port `PriceAnalysis` market enums |

### P2-4 — Admin search filters (name / type / product) — **Priority B**

| | |
|--|--|
| **Source** | `GET .../search`, `/type/{type}`, `/product/{productId}`, date ranges |
| **Target land** | Query params on `GET /api/v1/price-books` (nameContains, productExternalId); type only if P1 adds type |
| **Owner** | `pricebook-service` + FE list filters |
| **Effort** | **S** (name/product) · **M** if type+date indexes |
| **Value** | **Medium** — UX; agents mostly use region list |
| **Depends on P1** | Type filter depends on P1 metadata |
| **Decision** | **Harvest selective** — name + product first; skip price-range-of-products as low CPQ value |

### P2-5 — Angular filter/grid UX patterns — **Priority B (Track P3 overlap)**

| | |
|--|--|
| **Source** | `angular-client` FilterPanel / PriceBookGrid |
| **Target land** | `@frontend/cpq-price-books` list/detail |
| **Effort** | **S–M** |
| **Value** | **Medium** — no BE |
| **Depends on P1** | No |
| **Decision** | **Pattern harvest** — can start anytime; prefer after P2-4 if filters need new query params |
| **Exclude** | React/Supabase, AIInsightsPanel until recommendation API exists |

### P2-6 — Bulk apply “price model” abstraction — **Priority C**

| | |
|--|--|
| **Source** | `price-models-service` CRUD + apply/apply-all |
| **Target land** | Optional later: stored adjustment recipes **or** just document as FE/agent-side calc feeding P2-2 |
| **Effort** | **L** if full service · **S** if agent-only planning |
| **Value** | **Medium** for ops · **Low** for core quote path |
| **Decision** | **Defer** — prefer agent-035 planning → P2-2 batch; no new microservice |

### P2-7 — Market / competitive `PriceAnalysis` — **Priority C / hand off**

| | |
|--|--|
| **Source** | `PriceAnalysis` ABOVE/AT/BELOW_MARKET |
| **Target** | `competitive-pricing-intelligence` / pricing agents — not pricebook |
| **Decision** | **Hand off / exclude** from pricebook harvest |

### P2-8 — Entry discount / salesPrice helpers — **Exclude**

| | |
|--|--|
| **Source** | `applyDiscount`, `setSalesPrice` |
| **Target** | Discounts live in **pricing waterfall**, not list-price catalog |
| **Decision** | **Exclude** |

### P2-9 — Cost APIs — **Parked (ADR)**

Unchanged from parent plan — not P2.

### P2-10 — R2 list-row delete — **Forge hold**

Not a harvest item.

---

## 4. Recommended P2 DAG (after P1 gate)

```text
P1 done gate
    │
    ├─► P2-1 Coverage/gap API ─────────────┐
    │         │                            │
    │         └─► P2-3 Health (optional) ──┤
    │                                      ▼
    ├─► P2-2 Bulk entries API ──► FE bulk assist (agent-035 / admin)
    │
    ├─► P2-4 Name/product filters ──► P2-5 Angular filter UX
    │
    └─► P2-6 Price-model service     (defer)
        P2-7 Market analysis         (hand off)
        P2-8 salesPrice/discount     (exclude)
```

**Suggested first two PRs after P1:** **P2-1** then **P2-2** (or parallel if different owners: BE coverage ∥ BE bulk).

---

## 5. Contract / blast-radius checklist (when implementing)

- [ ] Add rows to `docs/backlog/CPQ_API_CONTRACT_MATRIX.md` §4b  
- [ ] Gateway routes if new paths  
- [ ] Update `agents/skills/price-book-management/SKILL.md` curls  
- [ ] Pricing-service regression if resolve-batch touched (should be rare in pure P2)  
- [ ] GitNexus impact on `PriceBookService` / new controller methods  
- [ ] FE browser verify for any UI slice  
- [ ] ADR-006 DTOs only  

---

## 6. Scoreboard summary

| ID | Candidate | Effort | Value | P1 dep | Decision |
|----|-----------|--------|-------|--------|----------|
| P2-1 | Coverage / gap API | M | High | Soft | **Do after P1** |
| P2-2 | Bulk entry update | M | High | Soft/Hard* | **Do after P1** |
| P2-3 | Health summary | S–M | Med–High | Hard | After P2-1 |
| P2-4 | Search filters | S–M | Med | Partial | Selective |
| P2-5 | Angular UX patterns | S–M | Med | None | Anytime / after P2-4 |
| P2-6 | Price-model service | L | Med | — | Defer |
| P2-7 | Market PriceAnalysis | — | — | — | Hand off |
| P2-8 | salesPrice / discount | — | — | — | Exclude |
| P2-9 | Cost ledger | — | — | — | ADR park |
| P2-10 | R2 list delete | — | — | — | Hold |

\*Hard if bulk payload includes effective dating from P1.

---

## 7. Out of scope for this backlog

- Implementing any of the above  
- Opening P1 schema PRs  
- Lifting forge holds  
- Merging source modules into the monorepo  

---

## Appendix — Source → target remap cheat sheet

| Source | Target shape |
|--------|----------------|
| `POST` bulk via client DTO | `POST /api/v1/price-books/{id}/entries:bulk` |
| `analyzePriceBook` market stats | **Don’t copy** → build **coverage** vs `GET /api/v1/products?status=ACTIVE` |
| `applyPriceModel` | Agent calc → bulk API |
| `GET /api/pricebooks/search` | `GET /api/v1/price-books?nameContains=` |
| Angular FilterPanel | signals + `PriceBookService.list(...)` |
