# Track P1 capability matrix (E1)

**Scope:** Catalog domain parity only — entry effective dating, selective book header, `resolve-batch` as-of selection, publish/immutability.  
**Parent plan:** [price-book-reuse-gap-analysis.md](./price-book-reuse-gap-analysis.md) (architect **APPROVED-WITH-GATES**).  
**Harvest backlog:** [price-book-harvest-backlog-p1.md](./price-book-harvest-backlog-p1.md).  
**P2 scored ideas:** [price-book-harvest-backlog-p2.md](./price-book-harvest-backlog-p2.md) — **not** code-authorized until E-GATE.  
**Dual-read:** unchanged (`${APPROVALS_LEGACY_LOCAL_READ:true}`). Do not flip.  
**Track 2:** **HOLD** — no AI / Spring AI / agent rows as “do now.”  
**Paths:** External stays `/api/v1/price-books/**`. Internal stays `POST /internal/v1/price-books/resolve-batch`. Never `/api/v1/pricing/price-books`. P1 expects **no** new external path.

---

## Source SHA pin

| Field | Value |
|-------|-------|
| Source repo | [drgaciw/price-book-service](https://github.com/drgaciw/price-book-service) |
| Local clone | `/home/username01/Projects/price-book-service` |
| Pinned commit | `1349a190a6a1f7c6e0396fe333a13d811e427744` |
| Pin message | `Refactor code structure for improved readability and maintainability. Contined integration of Drools rules engine.` |
| Pin date | 2025-04-07 (cloned 2026-09-14 — **stale**) |
| Verify | `git -C /home/username01/Projects/price-book-service rev-parse HEAD` → `1349a19…` |

---

## Matrix legend

| Column | Meaning |
|--------|---------|
| **Disposition** | `landed` · `port-method (B)` · `port-pattern (C)` · `defer` · `won't-do` · `hold` |
| **Do now?** | Track P1 authorized work only. Bulk / coverage / Angular UX / Cost / Drools / AI / SpEL = never “do now” here. |
| **PR** | Backlog ID (`P1-1`…`P1-4`) |

Locked P1-Deep leans (parent §10 open questions — **closed here**):

| Question | Decision |
|----------|----------|
| Overlapping dated entries | **Reject overlapping writes** on create/update. Resolve-batch picks the unique window that contains as-of (today). Tie-break if data already overlaps: **latest `effectiveFrom` ≤ as-of**. |
| `salesPrice` | **Don't add.** Target `listPrice` + `floorPrice` remain sufficient. |
| Entry dates | `LocalDate` inclusive windows, matching book-level `isEffective` — not source `LocalDateTime`. |
| `asOf` query param on resolve-batch | **No** in P1. As-of = `LocalDate.now()`. Keep `ResolveBatchRequest` = `{ region, productExternalIds }`. |

---

## 1. Book header (selective)

Target book today: `name`, `region`, `currency`, `isDefault`, `effectiveDate`, `expirationDate` (`PriceBook` / `PriceBookDto` / create+update requests). Schema owner: `pricing-service` Flyway `V1__create_pricing_schema.sql` (`price_books` in schema `pricing`); `pricebook-service` has `flyway.enabled: false` + `ddl-auto: validate`.

| ID | Capability | Source (`1349a19`) | Target (open-crm-cpq) | Gap | Disposition | Do now? | PR |
|----|------------|--------------------|------------------------|-----|-------------|---------|-----|
| H1 | Name | `PriceBook.name` unique Mongo index | `name` required | Landed | `landed` | — | — |
| H2 | Region / default | none (customerId / customerGroup instead) | `region` unique among non-deleted; `isDefault` singleton | Target-ahead CPQ hierarchy | `landed` | — | — |
| H3 | Currency | `currencyCode` | `currency` default `USD` | Landed (name remap) | `landed` | — | — |
| H4 | Book effective window | `effectiveFrom` / `effectiveTo` (`LocalDateTime`) | `effectiveDate` / `expirationDate` (`LocalDate`); `isEffective` in resolve-batch | Landed; inclusive both ends | `landed` | — | — |
| H5 | Type enum | `PriceBookType` STANDARD / PROMOTIONAL / BULK / SEASONAL / CUSTOM; `GET /api/pricebooks/type/{type}` | none | No contract-matrix or FE demand beyond region/default | `won't-do` in P1 (residual if product asks) | No | P1-4 residual |
| H6 | Status string | `status` default `"ACTIVE"`; `findActiveBooks` | **Derived** published = `effectiveDate != null && !effectiveDate.isAfter(today)` | Do not persist a second lifecycle column | `won't-do` persist; optional derived DTO in P1-3 | Tests only | P1-3 |
| H7 | Customer group / customerId | `customerGroup`, `customerId` + Mongo finders | none (region/default only) | Hierarchy ADR if ever | `defer` (optional ADR in parent §7.6) | No | — |
| H8 | Attributes map | `Map<String,String> attributes` | none | Mongo field dump | `won't-do` | No | — |
| H9 | Description | `description` | none | No CPQ caller | `defer` | No | — |
| H10 | Embedded products | `@DBRef List<Product> products` | entries via `productExternalId` | Catalog stays `product-service` | `won't-do` | No | — |

**Selective-header verdict:** P1 does **not** add `type` / `status` / `customerGroup` / `attributes`. Region + default + book dates already cover CPQ.

---

## 2. Entry domain

Target entry today: `productExternalId`, `listPrice`, `floorPrice`, `currency`. Java uniqueness: one non-deleted row per `(priceBookId, productExternalId)` (`DUPLICATE_PRICE_BOOK_ENTRY`). **No** unique SQL constraint; **no** entry dates.

Source entry: compound unique `{priceBookId, productId, effectiveFrom}`; `listPrice` + `salesPrice`; `effectiveFrom` (required) / `effectiveTo`; `isActive`; derived `PriceStatus` ACTIVE / SCHEDULED / EXPIRED / SUPERSEDED. **No** `floorPrice`. **No** entry REST controller (service-only).

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| E1 | List price | `listPrice` | `listPrice` required, ≥ 0 | Landed | `landed` | — | — |
| E2 | Floor price | none | `floorPrice` optional, ≤ list | Target-ahead | `landed` | — | — |
| E3 | Sales price / discount helpers | `salesPrice`, `applyDiscount`, `getCurrentPrice()` (= sales else list) | none | Quote/pricing contracts use list+floor | `won't-do` | No | — |
| E4 | Entry effective window | `effectiveFrom` / `effectiveTo` `LocalDateTime`; `isValidForDate` | **Missing** on entity / DTO / create+update / FE `PriceBookEntryDto` | Highest fidelity gap. Agent skill already assumes `effectiveDate` on create. | `port-method (B)` — `LocalDate effectiveFrom` / `effectiveTo` | Yes | **P1-1** |
| E5 | Compound uniqueness | unique `(priceBookId, productId, effectiveFrom)` — **overlaps with different from-dates allowed** | unique `(book, product)` among non-deleted | Must allow history **and** reject overlapping windows | `port-method (B)` — change uniqueness; overlap check on write | Yes | **P1-1** |
| E6 | `isActive` flag | boolean; inactive → `PriceStatus.EXPIRED` | soft-delete (`deleted`) | Extra flag duplicates soft-delete + window | `won't-do` | No | — |
| E7 | Persisted `priceStatus` | enum + `updatePriceStatus()` | none | Derive in tests/DTO if needed | `won't-do` persist | No | — |
| E8 | `priceChangeReason` | string on sales/discount | none | Defer with P2 bulk audit | `defer` | No | — |
| E9 | Product DBRef | `@DBRef Product` + `productId` | `productExternalId` string | Keep external id | `landed` (remap) | — | — |
| E10 | Nested entry API | none | `/api/v1/price-books/{id}/entries/**` | Target-ahead; extend DTOs in place | `landed` + field add | Yes (fields) | **P1-1** |

### Source vs target as-of helper (do not copy Mongo)

Source `PriceBookEntryRepository.findCurrentEntry`:

```
priceBookId, productId,
effectiveFrom ≤ date,
effectiveTo ≥ date OR effectiveTo == null,
isActive == true
```

`findCurrentEntryAsOfNow` uses `new Date()` in the Mongo JSON (inclusive `$gte` on `effectiveTo`). Model method `isValidForDate` uses `effectiveTo.isAfter(date)` (**exclusive** end). **Do not port the inconsistency.** Target P1: **inclusive** `LocalDate` windows, same rule as book `isEffective`:

```
(effectiveFrom == null || !effectiveFrom.isAfter(asOf))
&& (effectiveTo == null || !effectiveTo.isBefore(asOf))
```

Open window (both null) = always valid — preserves today’s one-row-per-product data.

---

## 3. Current-price / as-of selection + `resolve-batch`

Target `POST /internal/v1/price-books/resolve-batch` (`InternalPriceBookController` → `PriceBookInternalResolutionService`):

1. Pick book: regional `findByRegionAndDeletedFalse` if `isEffective(today)`, else default `findByIsDefaultTrueAndDeletedFalse` if effective; else `NO_PRICE_BOOK`.
2. One `IN` query `findByPriceBookIdAndProductExternalIdInAndDeletedFalse` (N+1 fix, forge C6 / #456).
3. Map by `productExternalId`; duplicate keys **first-wins** `(first, ignored) -> first`.
4. No entry-level date filter. Request has **no** `asOf`.

Caller: `pricing-service` `RestPriceBookResolutionClient` → same path. Any behavior change **must** retest that client + execute goldens.

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| R1 | Book pick by region/default + book window | `findEffectiveBooks(date)` (all matching; no region) | regional then default + `isEffective` | Landed CPQ shape | `landed` | — | — |
| R2 | Entry as-of selection | `getCurrentPriceBookEntry` → `findCurrentEntryAsOfNow` | none — any non-deleted row | **Confirmed gap** once E4 lands | `port-method (B)` — as-of = today; pick the containing window | Yes | **P1-2** |
| R3 | Overlap at resolve time | Mongo query returns `Optional` (undefined if several match) | `toMap` first-wins | After P1-1 writes cannot overlap; resolver still tie-breaks latest `effectiveFrom` ≤ as-of for dirty data | `port-method (B)` | Yes | **P1-2** |
| R4 | Scheduled future entry | `PriceStatus.SCHEDULED` when `effectiveFrom > now` | would currently match if uniqueness allows only one row | After dating: future rows must **not** match as-of=today | `port-method (B)` | Yes | **P1-2** |
| R5 | Expired entry | `effectiveTo < now` | n/a today | Must miss as-of=today | `port-method (B)` | Yes | **P1-2** |
| R6 | `asOf` on the HTTP DTO | `findCurrentEntry(..., date)` exists | request = `{region, productExternalIds}` | Historical resolve not required for pricing execute | `won't-do` in P1 | No | — |
| R7 | Return `salesPrice` as current | `getCurrentPrice()` | return `listPrice` + `floorPrice` (`ProductEntryResolveDto`) | Keep list+floor | `landed` / `won't-do` sales | — | — |
| R8 | Pricing client contract | n/a | `RestPriceBookResolutionClient` + `RestPriceBookResolutionClientTest` | Regression required when R2–R5 change | `port-pattern (C)` tests | Yes | **P1-2** |

Book vs entry interaction (parent P1-M row 4):

| As-of = today | Book window | Entry window | Resolve |
|---------------|-------------|--------------|---------|
| Book not yet effective / expired | miss | n/a | `NO_PRICE_BOOK` (existing tests) |
| Book effective | miss (future/expired/absent entry) | `entryMatched=false` |
| Book effective | containing window | match list+floor |
| Book expired but “published” for writes | miss | writes still immutable (P1-3) |

---

## 4. Lifecycle publish / immutability

| ID | Capability | Source | Target | Gap | Disposition | Do now? | PR |
|----|------------|--------|--------|-----|-------------|---------|-----|
| L1 | BE write gate | `PriceBookLifecycleService.updatePriceBook` does **not** block on status/dates | `PriceBookService.ensureNotPublished`: `effectiveDate != null && !effectiveDate.isAfter(today)` → `PRICE_BOOK_PUBLISHED_IMMUTABLE` on header + entry writes | Target-ahead | `landed` | Tests to lock | **P1-3** |
| L2 | FE gate | Angular client grid (pattern only) | `isPriceBookPublished` in `price-book-detail.component.ts` — same formula; disables Edit/Delete/Add | Aligned | `landed` | — | P1-3 if DTO `published` added |
| L3 | Expiration vs publish | source `status` / entry `EXPIRED` | Expired book (`expirationDate < today`) is still **immutable** (L1 ignores expiration) but **not** chosen by resolve (`isEffective` false) | Correct; document + test | `port-pattern (C)` tests | Yes | **P1-3** |
| L4 | Null `effectiveDate` | n/a | unpublished / writable | Landed | `landed` | — | — |
| L5 | Explicit publish action | none | setting `effectiveDate` ≤ today publishes | Keep; no new path | `won't-do` extra API | No | — |
| L6 | R2 list-row delete | hard delete in source | FE list-row delete **HELD** | Do not un-hold | `hold` | No | — |

---

## 5. Explicit defer / exclude (not P1)

| ID | Capability | Disposition | Track |
|----|------------|-------------|-------|
| D1 | Bulk price update | `defer` | P2-2 |
| D2 | Coverage / gap API | `defer` | P2-1 |
| D3 | Health / staleness | `defer` | P2-3 (hard-dep P1 dating) |
| D4 | Admin search filters (name/type/product/dates) | `defer` | P2-4 |
| D5 | Angular-client grid/filter UX | `defer` | P2-5 / P3 |
| D6 | Cost ledger / margin-by-product | `won't-do` without ADR | P4 |
| D7 | Product CRUD in pricebook | `won't-do` | — |
| D8 | Drools apply-to-book/entry | `won't-do` in pricebook (hand off; T1 closed) | — |
| D9 | AI recommendations | `hold` Track 2 | Queue B |
| D10 | SpEL evaluate/apply | `won't-do` (forge hold) | — |
| D11 | Eureka / Mongo / Mongock / React / Supabase | `won't-do` | — |
| D12 | New `/api/v1/price-books/**` siblings | `won't-do` in P1 | P2 matrix rows later |

---

## 6. Field map (Mongo → proposed Flyway)

Schema owner today: **`pricing-service`** Flyway (`V1`, `V1_3`, `V2`, `V3`) on schema `pricing`. `pricebook-service` does **not** run Flyway. P1-1 adds the next pricing migration (likely `V4__price_book_entry_effective_windows.sql`) **and** JPA/DTO changes in `pricebook-service`.

### Book (`price_books`) — no P1 column adds

| Source field | Target column | Decision |
|--------------|---------------|----------|
| `name` | `name` | keep |
| `description` | — | ignore / later |
| `type` | — | ignore (P1-4 residual) |
| `status` | — | ignore; derive published |
| `products` DBRef | — | ignore |
| `customerId` / `customerGroup` | — | ignore / hierarchy ADR |
| `effectiveFrom` | `effective_date` | keep |
| `effectiveTo` | `expiration_date` | keep |
| `currencyCode` | `currency` | keep |
| `attributes` | — | ignore |

### Entry (`price_book_entries`) — P1-1 adds

| Source field | Target column | Decision |
|--------------|---------------|----------|
| `productId` | `product_external_id` | keep |
| `listPrice` | `list_price` | keep |
| `salesPrice` | — | ignore |
| `floor` (none) | `floor_price` | keep |
| `effectiveFrom` | **`effective_from DATE`** | **add** (nullable = open) |
| `effectiveTo` | **`effective_to DATE`** | **add** (nullable = open) |
| `isActive` | `is_deleted` | ignore extra flag |
| `priceStatus` | — | ignore |
| `priceChangeReason` | — | ignore / later |

Partial unique index (non-deleted): `(price_book_id, product_external_id, effective_from)` is **not** enough to reject overlaps (source had the same hole). **Application-level overlap reject** is the gate; optional exclusion constraint later.

Existing rows: both new columns **NULL** = always-valid open window; current one-row-per-product data keeps resolving.

---

## 7. Ranked P1 domain candidates (5–8)

| Rank | Candidate | Do now? | PR |
|-----:|-----------|---------|-----|
| 1 | Entry `effectiveFrom` / `effectiveTo` + overlap-reject writes | Yes | P1-1 |
| 2 | Uniqueness: allow history, still one active window per as-of | Yes | P1-1 |
| 3 | `resolve-batch` as-of=today + latest-`effectiveFrom` tie-break | Yes | P1-2 |
| 4 | Book×entry dating interaction tests (incl. C6 single-IN query) | Yes | P1-2 |
| 5 | Pricing `RestPriceBookResolutionClient` + execute golden regression | Yes | P1-2 |
| 6 | Publish/immutability tests (expired book still immutable; FE formula) | Yes (tests) | P1-3 |
| 7 | Selective header `type` / `status` / attributes | **No** | P1-4 residual |
| 8 | `salesPrice` / `isActive` / Mongo DBRefs | **No** | won't-do |

---

## 8. GitNexus preview (E3 later — not this docs PR)

Docs-only: no symbol impact required now. When E3 starts, impact **upstream** with `-r /home/username01/Projects/open-crm-cpq` (and `--file` to disambiguate):

| Symbol | Why | Confirm |
|--------|-----|---------|
| `PriceBookService` | create/update entry uniqueness + overlap | `rg` + tests |
| `PriceBookInternalResolutionService` | as-of filter; HIGH for pricing | existing unit tests + pricing client |
| `PriceBookEntry` / `PriceBookEntryRepository` | new columns + finder | UNKNOWN ≠ unused |
| `RestPriceBookResolutionClient` | DTO shape must stay compatible | `RestPriceBookResolutionClientTest` |
| FE `PriceBookEntryDto` / create+update requests | `frontend/libs/shared/models/src/lib/pricing.model.ts` | browser verify |

`UNKNOWN` is unresolved — confirm with `rg` before treating a symbol as unused.

---

## 9. Out of scope for this matrix

- E3 production Java/TS.  
- Queue F / P2-1…P2-5 implementation.  
- Dual-read flip. R2 list-row delete. Mongo/Eureka/Drools-in-pricebook/React/Supabase.  
- Editing `docs/plans/00-harvest-index.md` or `docs/forge/00-orchestrator-status.md`.
