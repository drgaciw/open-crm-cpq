---
status: done
id: c1-quotes-stub
---

# c1-quotes-stub

## Summary

PR-FE-CPQ-06 **lite**: presentational quotes list/detail in `libs/cpq/quotes` mirroring the catalog stub (`b2-cpq-catalog-stub`). Uses `@frontend/shared-ui` primitives and mock data. Wired lazy `/quotes` into the CRM shell. No HTTP quote service, draft create, or quote builder.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes.model.ts` | Local quote list types (mirrors QuoteDto lite) |
| `frontend/libs/cpq/quotes/src/lib/quotes.mock.ts` | Mock quotes for list/detail |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/*` | List page: PageHeader, SearchInput, DataTable, EmptyState + filters |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/*` | Detail page: StatusBadge (quote), CurrencyFormatPipe |
| `frontend/libs/cpq/quotes/src/lib/quotes.routes.ts` | `QUOTES_ROUTES` |
| `frontend/libs/cpq/quotes/src/index.ts` | Export routes + components |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy `quotes` route |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Quotes nav link |
| `frontend/libs/shared/ui/.../status-badge.component.ts` | Add `quote` status type (QuoteStatus) |
| `docs/forge/impl/c1-quotes-stub.md` | This status note |

## Acceptance (lite)

- [x] `/quotes` lazy route works (shell + `QUOTES_ROUTES`)
- [x] List uses shared-ui (`PageHeader`, `SearchInput`, `DataTable`, `EmptyState`) — no inline MatTable happy path
- [x] Detail uses `StatusBadge` (quote) + `CurrencyFormatPipe`
- [x] Empty / loading / error surfaces (empty + error banner; loading overlay via DataTable)
- [x] Client-side status/deal-type (+ search) filters on mock data
- [x] Vitest for list component (`nx`/vitest cpq-quotes)
- [x] No draft-create mutations / quote builder
- [x] No activities API, S2B-11, S2C-12, CORS wildcard rewrites

## Verify

```bash
cd frontend
npx vitest run --config libs/cpq/quotes/vite.config.mts   # or: nx test cpq-quotes
NX_DAEMON=false npx nx build crm --skip-nx-cache
```

## Notes

- `docs/forge/12-wave-c-plan.md` was absent; implemented as catalog-mirrored list/detail stub (confirmed scope).
- GitNexus MCP unavailable; callers checked via grep. `StatusBadge` product consumers: catalog-detail only before this spike (**LOW** risk additive `quote` type).
- Full HTTP quotes service remains PR-FE-CPQ-03; draft create / lifecycle remains full PR-FE-CPQ-06.
- Browser E2E against `nx serve crm` not required for this stub; covered by component Vitest + production build.
