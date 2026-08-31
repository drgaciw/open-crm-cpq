---
status: done
id: m1-fe-quote-header-edit
wave: M
pr: M1
---

# m1-fe-quote-header-edit

## Summary

Wave M1: FE quote header update form on `quotes-detail` plus clone UX copy aligned with M3 (header **and** line items copied; Calculate still required for waterfall/ARR). Adds `QuoteHeaderEditComponent` matching `QuoteUpdateRequest`; wires `QuoteService.update` PUT `/api/v1/quotes/{id}`; Edit+Save when `canEditLines` (DRAFT|REJECTED); error banner includes `QUOTE_NOT_EDITABLE`; Vitest coverage for service PUT and header edit. No BE Java, no `app.routes`/sidebar, no dual-read flip, no Activities / WhatsApp #430.

## Acceptance

| Criterion | Result |
|-----------|--------|
| `quote-header-edit` child under `libs/cpq/quotes` | Yes — inline panel |
| Fields = `QuoteUpdateRequest` only (no customer/dealType/status/money) | Yes |
| Edit+Save visible when DRAFT\|REJECTED; hidden otherwise | Yes via `[editable]="canEditLines()"` |
| `QuoteService.update` PUT + reload `getById` after save | Yes |
| Error banner including `QUOTE_NOT_EDITABLE` | Yes — message + `(errorCode)` |
| Clone ConfirmDialog + clone-note: header AND lines copied; Calculate for waterfall/ARR | Yes (describes M3 semantics) |
| `quote.service.spec` PUT coverage | Yes |
| Vitest `nx test cpq-quotes` | **65 passed** (7 files) |
| No BE Java / app.routes / sidebar / dual-read / Activities / #430 | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quote-header-edit/*` | New panel + Vitest |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/*` | Embed panel; clone copy update |
| `frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts` | PUT `/api/v1/quotes/:id` test |
| `frontend/libs/cpq/quotes/README.md` | M1 wiring notes |
| `docs/forge/impl/m1-fe-quote-header-edit.md` | This note |

## Behavior notes

- Header form fields: name, description, dealExternalId, subscriptionType, currency, region, durationInMonths, startDate, endDate, notes.
- Customer external ID, deal type, status, and money totals remain read-only on the existing info-grid (not sent on PUT).
- After successful save the child calls `getById` and emits `saved` so detail clears line-edit state.
- Clone copy intentionally describes M3 BE behavior (lines copied, waterfall not). Merge gate: land M3 before or with M1.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| FE `QuoteService.update` (`--file` quote.service.ts) | **UNKNOWN** (0 + riskNote; 2 unresolved receivers) | `rg`: definition only before M1; first UI caller |
| `QuotesDetailComponent` | **LOW** (4) | index, quotes.routes → app.routes |
| `onClone` | **UNKNOWN** | Same-file template caller; copy-only edit |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`) — Worktree: `.worktrees/forge-m1` (`feat/forge-m1-fe-quote-header-edit`) — Index ~1 commit behind HEAD (stale WARN; FE-only; UNKNOWN ≠ unused, text-confirmed). No HIGH/CRITICAL in-scope.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```
