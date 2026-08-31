---
status: done
id: n1-fe-price-book-header
wave: N
pr: N1
---

# n1-fe-price-book-header

## Summary

Wave N1: wire price-book **header** update / soft-delete in `frontend/libs/cpq/price-books` against existing `PriceBookService.update` / `delete`. API prefix **`/api/v1/price-books` only** — never `/api/v1/pricing/price-books`. Published books (`effectiveDate` present and `<= today`) are read-only for header writes, reusing `isPriceBookPublished` / `canEditEntries`. M2 entry CRUD left intact. No shell / routes / sidebar / `tsconfig` / `test:cpq` (N2 owns shell). No quotes. No Java. No NgRx.

## Acceptance

| Criterion | Result |
|-----------|--------|
| Header Edit/Save via `PriceBookService.update` matching `PriceBookUpdateRequest` | Yes — `price-book-edit-dialog` (name, region, currency, isDefault, effectiveDate, expirationDate) |
| Delete ConfirmDialog → `PriceBookService.delete` → `/price-books` | Yes |
| Disable header writes when published | Yes — same `canEditEntries` / `isPriceBookPublished` gate as M2 entries |
| Banner `PRICE_BOOK_PUBLISHED_IMMUTABLE` on 400 from published writes | Yes — edit dialog + detail delete banner include `[CODE]` |
| Do not regress M2 entry CRUD | Yes — entry Add/Edit/Delete + tests retained |
| Prefer `price-book-edit-dialog` child; no shared-ui DataTable rewrite | Yes |
| No shell / Java / quotes | Confirmed |
| API `/api/v1/price-books` only | Yes — service + contract Vitest |
| Vitest green | `NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache` — **52 passed** (7 files) |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/price-books/src/lib/price-book-edit-dialog/*` | New header edit dialog + Vitest |
| `frontend/libs/cpq/price-books/src/lib/price-book-detail/*` | Header Edit/Delete actions; published gate; reload `getById` after save |
| `frontend/libs/cpq/price-books/src/lib/price-books.api-contract.spec.ts` | Guard header update/delete + edit dialog file |
| `frontend/libs/cpq/price-books/src/index.ts` | Export edit dialog |
| `docs/forge/impl/n1-fe-price-book-header.md` | This note |

## Behavior notes

- **Published gate:** `effectiveDate` present and ISO date `<=` local today → disable header Edit/Delete and entry Add/Edit/Delete (mirrors BE `ensureNotPublished`).
- **Edit dialog fields:** match `PriceBookUpdateRequest`; name required; optional region/currency/dates omitted when empty; `isDefault` always sent; currency uppercased when set. Prefills from current book.
- **After save:** dialog closes with updated DTO; detail calls `getById` to refresh header.
- **Delete:** shared-ui `ConfirmDialogComponent`; on confirm calls `delete` then navigates `/price-books`.
- **Errors:** dialog and detail banners append `[PRICE_BOOK_PUBLISHED_IMMUTABLE]` when that code is present.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`) — Worktree: `.worktrees/forge-n1` (`feat/forge-n1-fe-price-book-header`) — Index ~1 commit behind HEAD (architecture commit; CLI stale from untracked `.worktrees/` treated as false stale per Wave N ADR).

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `PriceBookService.update` | **UNKNOWN** (0) + 2 dropped receivers | `rg`: service + service.spec only — N1 first UI caller |
| FE `PriceBookService.delete` | **UNKNOWN** (0) + 5 dropped | `rg`: service only — N1 first UI caller |
| FE `isPriceBookPublished` | **LOW** (→ `PriceBookDetailComponent`) | Reused as-is |
| FE `PriceBookDetailComponent` | **LOW** (barrel, routes → app.routes) | Safe within lib; did **not** edit shell |

**WARN:** UNKNOWN ≠ unused — confirmed with `rg` before treating as first UI consumer. No HIGH/CRITICAL in-scope. Did **not** flip dual-read default. Did **not** touch quotes, shell, WhatsApp #430, or Activities.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache

rg "basePath = '/api/v1/price-books'" frontend/libs/cpq/pricing-data-access/src/lib/price-book.service.ts
rg "openEditBook|confirmDeleteBook|PriceBookEditDialog" frontend/libs/cpq/price-books
# Wrong prefix must stay absent from runtime sources
rg "/api/v1/pricing/price-books" frontend/libs/cpq/price-books/src || true
```

## Out of scope

- Shell `app.routes` / sidebar / `tsconfig.base.json` / `package.json` `test:cpq` (N2)
- Quotes soft-delete (N3)
- List-row delete stretch
- BE Java / dual-read flip / Activities / #430 / NgRx
- Expanding shared-ui DataTable
