---
status: done
id: k2-fe-quote-lifecycle-lines
wave: K
pr: K2
---

# k2-fe-quote-lifecycle-lines

## Summary

Wave K2: finish FE CPQ quote detail lifecycle and line-item richness on the existing `QuoteService` HttpClient. Detail gains status-gated Approve/Reject/Send/Accept, lists `quote.lineItems` with inline edit/remove, and enriches add-line with optional `productName`/`notes`. Keep Calculate/Submit. No document-generation panel, no NgRx, no shell/routes/catalog/backend Java edits.

## Acceptance

| Criterion | Result |
|-----------|--------|
| Approve/Reject for `IN_REVIEW` \| `PENDING_APPROVAL` | Yes — `QuoteService.approve` / `reject` |
| Send for `APPROVED` | Yes — `QuoteService.send` |
| Accept for `SENT` | Yes — `QuoteService.accept` |
| Reject optional reason | Yes — `window.prompt`; cancel skips call; blank omits reason param |
| List `quote.lineItems` | Yes — table with product, name, qty, list/net price |
| Edit / remove lines via existing HTTP | Yes — `updateLineItem` / `removeLineItem`; reload via `getById` |
| Keep Calculate / Submit / add-line | Yes; add form optional `productName` / `notes` |
| Inline HTTP error banner | Yes — shared `actionError` banner |
| Vitest | `nx test cpq-quotes` — 40 passed |
| No app.routes / sidebar / catalog / BE Java / docgen / NgRx | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | Lifecycle buttons + line list/edit/remove + enriched add |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.spec.ts` | Status gates, reject prompt, line CRUD, error banners |
| `frontend/libs/cpq/quotes/README.md` | Document K2 detail behavior |
| `docs/forge/impl/k2-fe-quote-lifecycle-lines.md` | This note |

## Behavior notes

- Status gates mirror BE: Submit=DRAFT; Calculate/lines=DRAFT\|REJECTED; Approve/Reject=IN_REVIEW\|PENDING_APPROVAL; Send=APPROVED; Accept=SENT.
- Reject prompt: `null` cancels; trimmed empty string → reject without reason query param.
- Line edit keeps `productExternalId` from the row; editable fields are `productName`, `quantity`, `listPrice`.
- After add/update/remove success, detail reloads via `getById`. Lifecycle actions update `selectedQuote` from the response.
- Native `title` tooltips explain disabled actions; no MatTooltip module.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream |
|--------|------|----------|
| `QuotesDetailComponent` | **LOW** (4 impacted) | `index.ts`, `quotes.routes.ts` → CRM `app.routes` |
| FE `QuoteService` | **LOW** (disambiguated via `--file`; **not edited**) | index export; detail becomes additional caller of existing HTTP methods |

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-k2` (`feat/forge-k2-fe-quote-lifecycle-lines`) — Index ~66 commits behind (text-confirmed FE quotes-detail only; no BE symbols touched).

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```
