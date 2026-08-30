---
status: done
id: g6-fe-catalog-http
wave: G
pr: G6
---

# g6-fe-catalog-http

## Summary

Wire CPQ catalog list/detail UI to the existing `ProductService` HttpClient (Wave C2) so the happy path loads `/api/v1/products` instead of `MOCK_CATALOG_PRODUCTS`. Mocks remain as Vitest fixtures only. Stub subtitle removed.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `CatalogListComponent` | **LOW** | `index.ts`, `catalog.routes.ts` → CRM `app.routes` |
| `CatalogDetailComponent` | **LOW** | same |
| FE `ProductService` | **LOW** (3 impacted) | index export consumers |
| BE `ProductService` | **UNKNOWN** (not edited) | disambiguated away |
| `findMockCatalogProduct` | **LOW** | was only `CatalogDetailComponent` |

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-g6` — Index ~9 commits behind (text-confirmed FE-only edits).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/catalog-list/catalog-list.component.ts` | Inject `ProductService`; `ngOnInit` → `list`/`search`; table from `products` signal; subtitle updated |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.ts` | Inject `ProductService`; `getById` on init; loading/error empty-state; drop mock lookup |
| `frontend/libs/cpq/catalog/src/lib/catalog-list/catalog-list.component.spec.ts` | Mock `ProductService`; seed with `MOCK_CATALOG_PRODUCTS` |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/catalog-detail.component.spec.ts` | New Vitest coverage for HTTP detail path |
| `frontend/libs/cpq/catalog/src/lib/catalog.mock.ts` | Comment: fixtures only |
| `frontend/libs/cpq/catalog/src/lib/catalog.model.ts` | Comment: fixture shape |
| `frontend/libs/cpq/catalog/README.md` | Document HTTP happy path |
| `docs/forge/impl/g6-fe-catalog-http.md` | This note |

## Acceptance

- [x] Catalog list loads via `ProductService.list` / `search` (`GET /api/v1/products`, `/search`)
- [x] Catalog detail loads via `ProductService.getById` (`GET /api/v1/products/:id`)
- [x] Stub subtitle (“presentational stub — mock data”) removed
- [x] `MOCK_CATALOG_PRODUCTS` / `findMockCatalogProduct` kept for unit tests/fixtures (still exported)
- [x] Vitest `cpq-catalog` green
- [x] No new HTTP stack; no NgRx; G7/G8 untouched

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-catalog --skip-nx-cache
```

## Notes

- Search + category/status together: text search hits `/search`; category/status refine client-side on the current search page (search API has no filter params). Without search, category/status go to `list` query params with server pagination.
- Browser E2E against live product-service not required for G6; covered by mocked `ProductService` component tests + existing `ProductService` HttpClientTesting specs.
