---
status: done
id: l1-fe-quote-docs-terminal
wave: L
pr: L1
---

# l1-fe-quote-docs-terminal

## Summary

Wave L1: FE quote documents panel + terminal lifecycle on existing quote-service HTTP. Adds `DocumentType` / `QuoteDocumentDto` models; `QuoteService.generateDocuments` / `listDocuments` / `downloadDocument` (blob) against `/api/v1/quotes/{id}/documents/**`; embeds `quote-documents-panel` on `quotes-detail`; status-gated Expire (SENT) + Cancel (not ACCEPTED|CANCELLED) with ConfirmDialog; Clone navigates to new DRAFT with honest UX that BE clone does **not** copy line items. No BE Java, no `app.routes`/sidebar, no dual-read flip.

## Acceptance

| Criterion | Result |
|-----------|--------|
| `DocumentType` + `QuoteDocumentDto` in shared-models | Yes — PROPOSAL \| ORDER_FORM \| SOW |
| `generateDocuments` / `listDocuments` / `downloadDocument` | Yes — download `responseType: 'blob'` |
| Documents panel: list / generate (default PROPOSAL) / download | Yes — `QuoteDocumentsPanelComponent` on detail |
| Expire SENT-only + ConfirmDialog | Yes |
| Cancel not ACCEPTED (also not CANCELLED) + ConfirmDialog | Yes |
| Clone → navigate new DRAFT; UX says lines not copied | Yes — dialog + page note |
| Vitest | `nx test cpq-quotes` — 57 passed; `shared-models` — 11 passed |
| No BE Java / app.routes / sidebar / dual-read flip | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/shared/models/src/lib/quote.model.ts` | `DocumentType`, labels, `QuoteDocumentDto` |
| `frontend/libs/shared/models/src/lib/cpq-models.spec.ts` | DocumentType guards |
| `frontend/libs/cpq/quotes/src/lib/quote.service.ts` | document HTTP + blob download |
| `frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts` | expire/cancel/docs tests |
| `frontend/libs/cpq/quotes/src/lib/quote-documents-panel/*` | panel + Vitest |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/*` | Expire/Cancel/Clone + panel embed |
| `frontend/libs/cpq/quotes/README.md` | L1 behavior |
| `docs/forge/impl/l1-fe-quote-docs-terminal.md` | This note |

## Behavior notes

- Download is raw PDF (`ResponseEntity<byte[]>`), not `ApiResponse`. FE uses `responseType: 'blob'` + object-URL save-as; filename from DTO when present.
- Generate query param `type` defaults to `PROPOSAL` (matches BE).
- Expire gate: SENT only. Cancel gate: status ≠ ACCEPTED and ≠ CANCELLED.
- Clone ConfirmDialog and page note state that backend copies header only — line items are not copied (BE `QuoteService.clone` today). Fixing clone-lines is out of L1.
- Detail reloads when route `:id` changes so clone navigation to `/quotes/{newId}` refreshes the same component instance.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / confirmation |
|--------|------|-------------------------|
| FE `QuoteService` (`--file` quote.service.ts) | **LOW** (3) | index → app.routes |
| `QuotesDetailComponent` | **LOW** (4) | index, quotes.routes |
| `expire` / `cancel` / `cloneQuote` | **UNKNOWN** | rg: service + specs only before L1; first UI callers |
| `QuoteDocumentController` | **UNKNOWN** | HTTP entry; FE absent confirmed |
| `QuoteDto` | **MEDIUM** if mutated | Appended document types only; DTO untouched |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`) — Worktree: `.worktrees/forge-l1` (`feat/forge-l1-fe-quote-docs-terminal`) — Index ~77 commits behind (stale WARN; FE-only; UNKNOWN ≠ unused, text-confirmed).

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test shared-models --skip-nx-cache
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```
