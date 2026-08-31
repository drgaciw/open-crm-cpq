---
status: done
id: m2-fe-price-book-entries
wave: M
pr: M2
---

# m2-fe-price-book-entries

## Summary

Wave M2: wire price-book **entry** create / update / delete in `frontend/libs/cpq/price-books` against existing `PriceBookService.createEntry` / `updateEntry` / `deleteEntry`. API prefix **`/api/v1/price-books/{id}/entries/**` only** — never `/api/v1/pricing/price-books`. Published books (effectiveDate present and `<= today`) are read-only, mirroring BE `ensureNotPublished`. No shell / routes / sidebar edits (already L3). No quotes. No NgRx. No shared-ui DataTable actions-column expansion. Book metadata update/delete stretch **not** included.

## Acceptance

| Criterion | Result |
|-----------|--------|
| Entry create via `PriceBookService.createEntry` | Yes — `price-book-entry-dialog` create mode |
| Entry update via `PriceBookService.updateEntry` | Yes — dialog edit mode from per-row Edit |
| Entry delete via `PriceBookService.deleteEntry` | Yes — ConfirmDialog then delete |
| Reload `getEntries` after writes | Yes |
| Disable Add/Edit/Delete when published | Yes — `isPriceBookPublished` mirrors BE |
| Surface `PRICE_BOOK_PUBLISHED_IMMUTABLE` / `DUPLICATE_PRICE_BOOK_ENTRY` | Yes — banner includes `[CODE]` |
| API prefix `/api/v1/price-books` only | Yes — service + contract Vitest |
| No shared-ui DataTable actions column | Yes — local entries table + per-row buttons |
| No shell / quotes / NgRx / dual-read flip | Confirmed |
| Vitest green | `nx test cpq-price-books --skip-nx-cache` — **41 passed** |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/price-books/src/lib/price-book-entry-dialog/*` | New create/update dialog + Vitest |
| `frontend/libs/cpq/price-books/src/lib/price-book-detail/*` | Entry CRUD UI, published gate, ConfirmDialog delete |
| `frontend/libs/cpq/price-books/src/lib/price-books.api-contract.spec.ts` | Guard entry paths + entry dialog file |
| `frontend/libs/cpq/price-books/src/index.ts` | Export entry dialog |
| `docs/forge/impl/m2-fe-price-book-entries.md` | This note |

## Behavior notes

- **Published gate:** `effectiveDate` present and ISO date `<=` local today → disable Add / Edit / Delete (same rule as BE `ensureNotPublished`).
- **Dialog fields:** `productExternalId`, `listPrice` required; optional `floorPrice`, `currency` (uppercased when set). Prefills book currency on create.
- **Delete:** shared-ui `ConfirmDialogComponent`; on confirm calls `deleteEntry` then reloads current entries page.
- **Errors:** dialog and detail banners append `[PRICE_BOOK_PUBLISHED_IMMUTABLE]` / `[DUPLICATE_PRICE_BOOK_ENTRY]` when those codes are present on the thrown error / body.
- Entries list uses a **lib-local** table (not a new DataTable column type) so Edit/Delete stay in this feature lib.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-m2` (`feat/forge-m2-fe-price-book-entries`) — Index ~1 commit behind HEAD (Wave M architecture commit).

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `PriceBookDetailComponent` | **LOW** (4: barrel, routes → app.routes → app.config) | Safe to edit |
| FE `PriceBookService.createEntry` | **UNKNOWN** (0 upstream) | `rg`: only service + `price-book.service.spec` — M2 first UI caller |
| FE `PriceBookService.updateEntry` | **UNKNOWN** (0 upstream) | same |
| FE `PriceBookService.deleteEntry` | **UNKNOWN** (0 upstream) | same |

**WARN:** UNKNOWN ≠ unused — confirmed with `rg` before treating as first UI consumer. No HIGH/CRITICAL in-scope. Did **not** flip dual-read default. Did **not** touch quotes, shell, or WhatsApp #430.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache
# 41 passed (6 files)

# Wrong prefix must stay absent from runtime sources (guards/docs may mention it)
rg "basePath = '/api/v1/price-books'" frontend/libs/cpq/pricing-data-access/src/lib/price-book.service.ts
rg "createEntry|updateEntry|deleteEntry" frontend/libs/cpq/price-books
# → detail + entry-dialog callers
```

## Out of scope

- Book metadata update / soft-delete (stretch, non-blocker)
- Shell `app.routes` / sidebar (L3)
- Quotes / NgRx / dual-read flip / Activities / #430
- Expanding shared-ui DataTable actions column
- BE Java
