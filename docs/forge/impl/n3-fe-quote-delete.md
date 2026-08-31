---
status: done
id: n3-fe-quote-delete
wave: N
pr: N3
---

# n3-fe-quote-delete

## Summary

Enable CPQ quote **Delete** on detail: `ConfirmDialog` (warn) → existing `QuoteService.delete` (HTTP soft-delete) → navigate `/quotes`. Delete disabled when `status === ACCEPTED` (match BE `QUOTE_ACCEPTED_NO_DELETE`). Other statuses including CANCELLED/EXPIRED/DRAFT allowed. Error banner includes `QUOTE_ACCEPTED_NO_DELETE`; stay on detail on failure. Soft-delete only — not hard delete. Shell (`app.routes` / sidebar), BE Java, M1 header edit, and clone copy untouched.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `QuoteService.delete` (`quote.service.ts`) | **UNKNOWN** (0 resolved + riskNote; 5 dropped receiverTyping) | **rg confirm:** definition only — no UI callers before N3 |
| BE `QuoteService.delete` | **LOW** (→ `QuoteController`) — **not edited** | Soft-delete; ACCEPTED → `QUOTE_ACCEPTED_NO_DELETE` |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-n3` (`feat/forge-n3-fe-quote-delete`)  
Index: CLI via primary `.gitnexus/run.cjs`; impact file-scoped.  
**UNKNOWN ≠ unused** — confirmed with `rg` that FE delete had no component callers; N3 adds the first. Not HIGH/CRITICAL.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | Delete button; `canDelete` (!ACCEPTED); ConfirmDialog soft-delete → `/quotes`; action error banner |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.spec.ts` | Vitest: confirm→delete→navigate; dismiss skips; ACCEPTED disabled; CANCELLED/EXPIRED allowed; `QUOTE_ACCEPTED_NO_DELETE` banner |
| `frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts` | DELETE `/api/v1/quotes/:id` coverage |
| `docs/forge/impl/n3-fe-quote-delete.md` | This note |

## Acceptance

- [x] ConfirmDialog (warn) Delete → `QuoteService.delete` → `/quotes`
- [x] Delete disabled when `status === ACCEPTED`; CANCELLED/EXPIRED/DRAFT allowed
- [x] Error banner includes `QUOTE_ACCEPTED_NO_DELETE`; stay on detail
- [x] Soft-delete messaging only (no hard-delete claim)
- [x] `quote.service.spec` DELETE `/api/v1/quotes/:id`
- [x] Vitest `cpq-quotes` green
- [x] No shell edits; no BE Java; no M1/clone rewrite

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```

Result: 7 files / 71 tests passed.

## Out of scope

- `app.routes` / sidebar (N2 owns shell)
- BE Java / Activities API
- Dual-read flip; WhatsApp #430
- Price-book header (N1); pricing-customers portal (N2)
