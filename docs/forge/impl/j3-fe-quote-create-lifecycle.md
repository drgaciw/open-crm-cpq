---
status: done
id: j3-fe-quote-create-lifecycle
wave: J
pr: J3
---

# j3-fe-quote-create-lifecycle

## Summary

Wave J3: enable FE CPQ quote create + detail lifecycle on the existing `QuoteService` HttpClient. New Quote opens a minimal reactive form matching `QuoteCreateRequest`; detail gains Calculate / Submit / optional Add line item with status-gated disable and inline HTTP error banners. No catalog lib edits, no NgRx, no backend Java.

## Acceptance

| Criterion | Result |
|-----------|--------|
| New Quote enabled; creates via `QuoteService.create` | Yes — list CTA → `/quotes/new`; form posts required `name` + `customerExternalId` |
| Navigate to detail on success | Yes — `router.navigate(['/quotes', quote.id])` |
| Detail Calculate + Submit via existing HTTP methods | Yes — `QuoteService.calculate` / `submit` |
| Optional add line item | Yes — minimal productExternalId + listPrice (+ quantity) |
| Disable by obvious status | Submit = DRAFT only; Calculate / Add line = DRAFT\|REJECTED |
| Inline error banner from HTTP errors | Create + detail action banners |
| Vitest for new behavior | `nx test cpq-quotes` — 28 passed |
| No catalog / NgRx / backend Java | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-create/quotes-create.component.ts` | New create form component |
| `frontend/libs/cpq/quotes/src/lib/quotes-create/quotes-create.component.spec.ts` | Vitest: validation, create+navigate, omit blanks, HTTP error |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.ts` | Enable New Quote → `/quotes/new` |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.spec.ts` | Assert New Quote enabled + navigate |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | Calculate / Submit / Add line; status gates; action error banner |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.spec.ts` | Lifecycle + disable + error coverage |
| `frontend/libs/cpq/quotes/src/lib/quotes.routes.ts` | `new` route before `:id` |
| `frontend/libs/cpq/quotes/src/index.ts` | Export `QuotesCreateComponent` |
| `frontend/libs/cpq/quotes/README.md` | Document create + lifecycle |
| `docs/forge/impl/j3-fe-quote-create-lifecycle.md` | This note |

## Behavior notes

- Create request omits blank optional fields (`currency`, `durationInMonths`, `dealExternalId`, `region`).
- Detail keeps load-error banner separate from action-error banner.
- After add line item success, detail reloads via `getById`.
- Native `title` tooltips explain why Calculate/Submit are disabled; no MatTooltip module.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `QuotesListComponent` | **LOW** (4 impacted) | `index.ts`, `quotes.routes.ts` → CRM `app.routes` |
| `QuotesDetailComponent` | **LOW** (4 impacted) | same |
| FE `QuoteService` | **LOW** (disambiguated via `--file`) | index export; list/detail/create become callers |

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-j3` (`feat/forge-j3-fe-quote-create-lifecycle`) — Index ~51 commits behind (text-confirmed FE quotes-only edits; no BE symbols touched).

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
# or: npx vitest run --config libs/cpq/quotes/vite.config.mts
```
