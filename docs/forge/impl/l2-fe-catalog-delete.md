---
status: done
id: l2-fe-catalog-delete
wave: L
pr: L2
---

# l2-fe-catalog-delete

## Summary

Enable CPQ catalog **Delete** on product detail: `ConfirmDialog` → existing `ProductService.delete` (HTTP soft-delete) → navigate to catalog list. Error banner on failure. Quotes lib, shell routes/sidebar, and backend Java untouched.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| `CatalogDetailComponent` | **LOW** (4 impacted) | `index.ts`, `catalog.routes.ts` → CRM `app.routes` / `app.config` |
| FE `ProductService.delete` (`product.service.ts`) | **UNKNOWN** (0 resolved + riskNote; 5 dropped receiverTyping) | **rg confirm:** no catalog UI callers before L2; method already defined |
| BE `ProductService.delete` | **LOW** (→ `ProductController.delete`) — **not edited** | Soft-delete + `@CacheEvict`; FE only |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-l2`  
Index: ~77 commits behind HEAD at edit time (CLI impact via main `.gitnexus/run.cjs`; FE-only surface).  
**UNKNOWN ≠ unused** — confirmed with `rg` that FE delete had no UI callers; L2 adds the first.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts` | Delete button (view mode); ConfirmDialog; `ProductService.delete` → `/catalog`; error banner; busy `deleting` signal |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.spec.ts` | Vitest: render Delete, confirm→delete→navigate, cancel dialog, delete error banner, no delete while editing |
| `docs/forge/impl/l2-fe-catalog-delete.md` | This note |

## Acceptance

- [x] Delete on catalog detail via ConfirmDialog (warn)
- [x] Soft-delete only via existing `ProductService.delete` → navigate to list
- [x] Error banner on delete failure (stay on detail)
- [x] Vitest `cpq-catalog` green
- [x] No quotes/shell edits; no backend Java edits

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

Result: 5 files / 37 tests passed.
