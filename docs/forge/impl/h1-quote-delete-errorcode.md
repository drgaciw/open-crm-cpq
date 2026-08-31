---
status: done
id: h1-quote-delete-errorcode
wave: N′
pr: H1
---

# h1-quote-delete-errorcode

## Summary

Wave N hygiene H1: `quotes-detail` `httpErrorMessage` now surfaces `ApiResponse.errorCode` on HTTP errors as `message [CODE]`, matching the N1 `bookHttpErrorMessage` display shape. Soft-delete ConfirmDialog, ACCEPTED `canDelete` gate, and navigate `/quotes` unchanged. No shell, no BE Java, no dual-read flip.

## Residual fixed

N3 banner test previously mocked `error.message` that already embedded `QUOTE_ACCEPTED_NO_DELETE`, so the code field was never exercised. Real BE shape is `message` + separate `errorCode`.

## GitNexus (pre-edit)

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `httpErrorMessage` (`quotes-detail.component.ts`) | **MEDIUM** (7 same-file callers) | Additive display only — append `[code]` when present; no call-site signature change |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-h1` (`fix/forge-n3-quote-delete-errorcode`)  
CLI: `node .gitnexus/run.cjs impact httpErrorMessage --file frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts -d upstream -r …`  
**WARN:** MEDIUM — 7 same-file error handlers. Change is additive string formatting. UNKNOWN ≠ unused (not applicable here; epistemic exact).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.ts` | `httpErrorMessage`: read `e?.errorCode \|\| e?.error?.errorCode`; return `` `${message} [${code}]` `` when code present |
| `frontend/libs/cpq/quotes/src/lib/quotes-detail/quotes-detail.component.spec.ts` | Mock real BE shape `{ message, errorCode }`; assert `Cannot delete an accepted quote [QUOTE_ACCEPTED_NO_DELETE]` |
| `docs/forge/impl/h1-quote-delete-errorcode.md` | This note |

## Acceptance

- [x] Banner shows `message [QUOTE_ACCEPTED_NO_DELETE]` from `errorCode` field (not message-embedded code)
- [x] Spec mocks `{ message: 'Cannot delete an accepted quote', errorCode: 'QUOTE_ACCEPTED_NO_DELETE' }`
- [x] ConfirmDialog soft-delete, ACCEPTED disabled (`canDelete`), navigate `/quotes` retained
- [x] Vitest `cpq-quotes` green (7 files / 71 tests)
- [x] No shell; no BE Java; dual-read untouched
- [x] Did not edit `docs/forge/00-orchestrator-status.md`

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache
```

Result: **7 files / 71 tests passed**. No TestBed `hookTimeout` flake (H3 not needed).

## Out of scope

- Shell / routes / sidebar
- BE Java / Activities API
- Dual-read flip; WhatsApp #430
- Matrix docs (H2)
- Shared helper extraction across libs (local helper only, N1 pattern)
