---
status: done
id: j4-fe-catalog-create
wave: J
pr: J4
---

# j4-fe-catalog-create

## Summary

Enable CPQ catalog **New Product** create dialog matching `ProductCreateRequest` (**required:** `sku`, `name`, `productType`, `category`; **optional:** `listPrice`, `currency`). No `status` field on create. On success, navigate to catalog detail. Vitest covers dialog + list wiring. Quotes lib and backend Java untouched.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `CatalogListComponent` | **LOW** (4 impacted) | `index.ts`, `catalog.routes.ts` → CRM `app.routes` / `app.config` |
| FE `ProductService` (`product.service.ts`) | **LOW** among resolved FE candidate | Ambiguous vs BE `ProductService` (**UNKNOWN**, not edited) |
| BE `ProductService` | **UNKNOWN** — not edited | Confirm text: create path already exists |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-j4`  
Index: ~51 commits behind HEAD at edit time (CLI impact; FE-only surface).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/product-create-dialog/product-create-dialog.component.ts` | New MatDialog form; calls `ProductService.create`; closes with `ProductDto` |
| `frontend/libs/cpq/catalog/src/lib/product-create-dialog/product-create-dialog.component.spec.ts` | Vitest: required fields, no status, optional omit, error banner |
| `frontend/libs/cpq/catalog/src/lib/catalog-list/catalog-list.component.ts` | Enable New Product; open dialog; navigate `/catalog/:id` on success |
| `frontend/libs/cpq/catalog/src/lib/catalog-list/catalog-list.component.spec.ts` | Button enabled; dialog open; navigate on create / not on cancel |
| `docs/forge/impl/j4-fe-catalog-create.md` | This note |

## Acceptance

- [x] New Product enabled (no deferred tooltip / disabled)
- [x] Create form matches `ProductCreateRequest` required + optional listPrice/currency
- [x] No `status` on create form / payload
- [x] Success navigates to catalog detail
- [x] Vitest `cpq-catalog` green
- [x] No quotes lib edits; no backend Java edits

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```
