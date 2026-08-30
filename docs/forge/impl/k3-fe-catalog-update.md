---
status: done
id: k3-fe-catalog-update
wave: K
pr: K3
---

# k3-fe-catalog-update

## Summary

Enable CPQ catalog **Edit/Save** on product detail matching `ProductUpdateRequest` (**name**, **description**, **productType**, **category**, **status**, **listPrice**, **currency** at minimum). Save calls existing `ProductService.update`, stays on detail, and shows an error banner on failure. Quotes lib and backend Java untouched.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `CatalogDetailComponent` | **LOW** (4 impacted) | `index.ts`, `catalog.routes.ts` → CRM `app.routes` / `app.config` |
| FE `ProductService` (`product.service.ts`) | **LOW** (3 impacted) — **not edited** | index → shell routes |
| FE `ProductService.update` | **UNKNOWN** (0 resolved callers + riskNote; 2 dropped receiverTyping) | Text confirm: method exists; no UI caller before K3 |
| BE `ProductService` | **UNKNOWN** — not edited | Disambiguated away |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-k3`  
Index: ~66 commits behind HEAD at edit time (CLI impact; FE-only surface).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts` | Edit mode + reactive form; Save → `ProductService.update`; stay on detail; error banner |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.spec.ts` | Vitest: edit populate, save, omit empty optionals, error banner, cancel, invalid form |
| `docs/forge/impl/k3-fe-catalog-update.md` | This note |

## Acceptance

- [x] Edit/Save on catalog detail matching `ProductUpdateRequest` fields at minimum
- [x] Save via `ProductService.update`; stay on detail after success
- [x] Error banner on save failure (edit mode retained)
- [x] Vitest `cpq-catalog` green
- [x] No quotes lib edits; no backend Java edits

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

Result: 5 files / 32 tests passed.
