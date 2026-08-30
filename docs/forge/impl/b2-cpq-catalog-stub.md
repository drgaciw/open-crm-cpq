---
status: done
id: b2-cpq-catalog-stub
---

# b2-cpq-catalog-stub

## Summary

PR-FE-CPQ-05 **lite**: presentational product catalog list/detail in `libs/cpq/catalog` using `@frontend/shared-ui` primitives and mock data. Wired lazy `/catalog` into the CRM shell. No HTTP catalog service and no quote builder.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/catalog/src/lib/catalog.model.ts` | Local catalog product types (mirrors ProductDto lite) |
| `frontend/libs/cpq/catalog/src/lib/catalog.mock.ts` | Mock products for list/detail |
| `frontend/libs/cpq/catalog/src/lib/catalog-list/*` | List page: PageHeader, SearchInput, DataTable, EmptyState + filters |
| `frontend/libs/cpq/catalog/src/lib/catalog-detail/*` | Detail page: StatusBadge (product), CurrencyFormatPipe |
| `frontend/libs/cpq/catalog/src/lib/catalog.routes.ts` | `CATALOG_ROUTES` |
| `frontend/libs/cpq/catalog/src/index.ts` | Export routes + components |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy `catalog` route |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Catalog nav link |
| `frontend/libs/shared/ui/.../status-badge.component.ts` | Add `product` status type (DRAFT/ACTIVE/DEPRECATED/RETIRED) |
| `frontend/libs/shared/ui/.../data-table.component.ts` | Fix `rowClick.observed` → `clickable` input (Angular output API) |
| `frontend/libs/shared/ui/.../currency-format.pipe.ts` | Map `symbol-narrow` → Intl `narrowSymbol` for TS build |
| `docs/forge/impl/b2-cpq-catalog-stub.md` | This status note |

## Acceptance (lite)

- [x] `/catalog` lazy route works (shell + `CATALOG_ROUTES`)
- [x] List uses shared-ui (`PageHeader`, `SearchInput`, `DataTable`, `EmptyState`) — no inline MatTable happy path
- [x] Detail uses `StatusBadge` (product) + `CurrencyFormatPipe`
- [x] Empty / loading / error surfaces (empty + error banner; loading overlay via DataTable)
- [x] Client-side category/status (+ search) filters on mock data
- [x] Vitest for list component (`nx`/vitest cpq-catalog: 9 passed)
- [x] `nx build crm` green after shared-ui compile fixes
- [x] No quote builder

## Verify

```bash
cd frontend
npx vitest run --config libs/cpq/catalog/vite.config.mts   # or: nx test cpq-catalog
NX_DAEMON=false npx nx build crm --skip-nx-cache
```

## Notes

- GitNexus MCP unavailable; callers checked via grep. `StatusBadge` had zero feature consumers before this spike (**LOW** risk). DataTable/`CurrencyFormatPipe` fixes required for first shared-ui consumer build.
- Full HTTP catalog service remains PR-FE-CPQ-03; shared product models remain PR-FE-CPQ-02.
- Browser E2E against `nx serve crm` not run in this worktree (no interactive serve verification); covered by component Vitest + production build.
