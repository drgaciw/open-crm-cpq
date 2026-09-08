---
status: done
id: s2-fe-quotes-overflow
wave: S
pr: S2
---

# s2-fe-quotes-overflow

## Summary

Wave S2: collapse secondary quote-detail lifecycle actions into a `mat-menu` “More” overflow. Keep **Back** plus status-gated primaries **Calculate / Submit / Send / Accept**. Move **Approve, Reject, Expire, Cancel quote, Clone, Delete** into More. Preserve ConfirmDialog delete **warn** + `canDelete` / ACCEPTED gate. Do **not** edit `httpErrorMessage` in `quotes-detail` (H1 byte-stable). Copy H1 `message [CODE]` into `quote.service.ts` list/get `error.set` so list banners show codes. No shell, Java, NgRx, dual-read flip; N delete semantics unchanged.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| `QuotesDetailComponent` | **LOW** | d=1: lib `index` + `quotes.routes` (do not edit shell `app.routes`) |
| FE `QuoteService` (`quote.service.ts`) | **MEDIUM** | d=1: detail/list/create/documents/header-edit — only list/get `error.set` shape changes |
| `httpErrorMessage` (quotes-detail) | **UNKNOWN** ambiguous (3 FE copies) | **Not edited** — H1 helper left byte-stable |
| `canDelete` | **LOW** | d=1: `onDelete` only — gate unchanged |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-s2` (`fix/forge-s2-fe-quotes-overflow`)  
Index: stale (~23 commits) — analyze skipped; `--repo` + `--file frontend/libs/cpq/quotes`; UNKNOWN ≠ unused (`rg` SSOT).  
Not HIGH/CRITICAL.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | More `mat-menu`; Approve/Reject/Expire/Cancel/Clone/Delete inside; primaries + Back outside; `httpErrorMessage` untouched |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.spec.ts` | More menu reaches Expire/Cancel/Clone/Delete; delete ConfirmDialog + ACCEPTED specs kept |
| `frontend/libs/cpq/quotes/src/lib/quote.service.ts` | Local H1 helper; list + getById `error.set(httpErrorMessage(err))` |
| `frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts` | list/get `error.set` asserts `message [CODE]` |
| `docs/forge/impl/s2-fe-quotes-overflow.md` | This note |

## Acceptance

- [x] Visible primaries: Back + Calculate/Submit/Send/Accept; secondary in More
- [x] Expire/Cancel/Clone/Delete reachable via More
- [x] Delete ConfirmDialog warn + ACCEPTED gate unchanged
- [x] `quotes-detail` `httpErrorMessage` not rewritten (H1 tests still pass)
- [x] list/get `error.set` is `message [CODE]` when `errorCode` present
- [x] No shell / `app.routes` / sidebar / tsconfig / `test:cpq`
- [x] No quotes-create / documents edits; N delete semantics not reopened
- [x] Vitest `cpq-quotes` green

## Verify

```bash
cd frontend
# symlink primary node_modules if missing in worktree
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```

## Out of scope

- quotes-create / documents (Q2 done)
- Shell (`app.routes` / sidebar / tsconfig / `test:cpq`)
- Java / NgRx / dual-read YAML flip
- N3 delete semantics reopen; R2 list-row delete
- Activities, #238, #241, WhatsApp #430, subscriptions FE, Accept ADR-006
- `docs/forge/00-orchestrator-status.md`
- Shared `httpErrorMessage` extract
