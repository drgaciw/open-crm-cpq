---
status: done
id: q2-fe-quotes-ux
wave: Q
pr: Q2
---

# q2-fe-quotes-ux

## Summary

Wave Q2 (R1 + list empty CTA): copy H1-shaped `httpErrorMessage` into `quotes-create` and `quote-documents-panel` so banners show `message [CODE]` when `errorCode` / `error.errorCode` is present. Unfiltered quotes-list empty state gains `actionLabel` “New Quote” → existing `onNewQuote()`. **Do not** edit `quotes-detail` (H1 already landed). No shell, Java, NgRx, or dual-read flip.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| `httpErrorMessage` (`quotes-create.component.ts`) | **LOW** | d=1: `onSubmit` error handler only |
| `httpErrorMessage` (`quote-documents-panel.component.ts`) | **LOW** | d=1: `onGenerate` / `onDownload` / `reload` error handlers |
| `QuotesListComponent` | **LOW** | Imported by `quotes.routes` / lib index |
| `onNewQuote` | **UNKNOWN** (0 graph callers + riskNote) | **rg confirm:** template `(click)="onNewQuote()"` + list header button + specs — not unused |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-q2` (`fix/forge-q2-fe-quotes-ux`)  
Index: CLI via primary `.gitnexus/run.cjs` with `--repo` + `--file`; stale (~15 commits) — `rg` is SSOT for UNKNOWN.  
Not HIGH/CRITICAL.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-create/quotes-create.component.ts` | H1 `httpErrorMessage` with `errorCode` → `message [CODE]` |
| `frontend/libs/cpq/quotes/src/lib/quotes-create/quotes-create.component.spec.ts` | Mock `{ message, errorCode }`; assert banner `message [CODE]` |
| `frontend/libs/cpq/quotes/src/lib/quote-documents-panel/quote-documents-panel.component.ts` | Same H1 helper |
| `frontend/libs/cpq/quotes/src/lib/quote-documents-panel/quote-documents-panel.component.spec.ts` | Mock `{ message, errorCode }`; assert banner `message [CODE]` |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.ts` | Unfiltered empty: `actionLabel` “New Quote” → `onNewQuote()` |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list.component.spec.ts` | Unfiltered CTA present + navigates; filtered hides CTA |
| `docs/forge/impl/q2-fe-quotes-ux.md` | This note |

## Acceptance

- [x] R1 create + documents helpers match H1 (`errorCode` / `error.errorCode` → `message [CODE]`)
- [x] Specs mock `{ message, errorCode }` and assert banner text
- [x] Unfiltered list empty has New Quote CTA → `/quotes/new`
- [x] Filtered empty has no New Quote empty-state CTA
- [x] `quotes-detail` **not** in diff
- [x] No shell / `app.routes` / sidebar / tsconfig / `test:cpq`
- [x] Vitest `cpq-quotes` green

## Verify

```bash
cd frontend
# symlink primary node_modules if missing in worktree
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```

Result: 7 files / 75 tests passed (includes new errorCode + empty CTA specs).

## Out of scope

- `quotes-detail` lifecycle button density / H1 re-edit
- Shell (`app.routes` / sidebar / tsconfig / `test:cpq`) — Q4 owns chrome
- Java / NgRx / dual-read YAML flip
- Activities, #238, #241, WhatsApp #430, subscriptions FE, Accept ADR-006, N′′, R2
- `docs/forge/00-orchestrator-status.md`
