---
status: done
id: g7-fe-quotes-http
wave: G
pr: G7
---

# g7-fe-quotes-http

## Summary

Wave G7: wire CPQ quotes list/detail UI to the existing `QuoteService` HttpClient (`GET /api/v1/quotes`, `GET /api/v1/quotes/:id`). Mock-only happy path removed; `MOCK_QUOTES` / `findMockQuote` retained as Vitest fixtures.

## Acceptance

| Criterion | Result |
|-----------|--------|
| UI loads `/api/v1/quotes` via existing `QuoteService` | Yes — list `ngOnInit` → `QuoteService.list` |
| Detail uses `QuoteService.getById` | Yes — no `findMockQuote` on happy path |
| Mock-only happy path gone | Yes — subtitle no longer “mock data”; fixtures only |
| Vitest green | `nx test cpq-quotes` / vitest config |
| No new HTTP stacks / NgRx | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.ts` | Inject `QuoteService`; server status/dealType + page; client search on current page |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | Inject `QuoteService`; loading / error / not-found |
| `frontend/libs/cpq/quotes/src/lib/quote.service.ts` | `getById` clears `selectedQuote` before fetch |
| `frontend/libs/cpq/quotes/src/lib/quotes.mock.ts` | Comment: fixtures only |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/*.spec.ts` | Mock `QuoteService`; assert `list` on init |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/*.spec.ts` | New: mock `getById`, loading/error/empty |
| `frontend/libs/cpq/quotes/README.md` | Document HTTP-backed list/detail |
| `docs/forge/impl/g7-fe-quotes-http.md` | This note |

## Behavior notes

- **Status / deal type / pagination** → `QuoteService.list(page, size, sort, filters)` (gateway `/api/v1/quotes`).
- **Search** → client-side filter on the current API page (no quote text-search endpoint in Wave C2 service).
- **Detail line items count** → `selectedQuote.lineItems?.length ?? 0` (API nested line items when present).
- Out of scope: draft create, quote builder, NgRx, G6 catalog / G8 simulator.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `QuotesListComponent` | **LOW** | `quotes.routes`, lib `index` → CRM `app.routes` |
| `QuotesDetailComponent` | **LOW** | same |
| FE `QuoteService` | **LOW** (disambiguate from BE) | index export / specs; UI becomes callers |
| `findMockQuote` | **LOW** | was detail only — replaced on happy path |

Repository: `open-crm-cpq` — worktree `feat/forge-g7-fe-quotes-http`. Index was ~9 commits behind; text `rg` confirmed FE-only callers.

## Verify

```bash
cd frontend
npx vitest run --config libs/cpq/quotes/vite.config.mts
# or: NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```
