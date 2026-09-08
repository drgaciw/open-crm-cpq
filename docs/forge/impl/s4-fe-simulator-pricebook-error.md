---
status: done
id: s4-fe-simulator-pricebook-error
wave: S
pr: S4
---

# s4-fe-simulator-pricebook-error

## Summary

Wave S4: H1-shaped `errorCode` on simulator HTTP failures and `PriceBookService` list/get/getEntries `error.set`; copy detail `bookHttpErrorMessage` into price-book create dialog. Keep idle/failed empty-states and form layout. No list-row delete (R2 held). No shell / quotes / Q1 DataTable / Java / NgRx / dual-read flip / `00-orchestrator-status`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-s4` (`fix/forge-s4-fe-simulator-pricebook-error`)  
CLI: `node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact … -r /home/username01/Projects/open-crm-cpq --file …`  
Index stale (~23 commits behind). **Analyze skipped.** UNKNOWN ≠ unused — confirmed with `rg`.

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `PriceBookService` | **LOW** | Barrel + price-books / pricing-customers consumers |
| FE `PriceBookService.list` | **LOW** (lower-bound; 2 dropped receivers) | `rg`: list + listByRegion → price-book-list |
| FE `PriceBookService.getById` / `getEntries` | **UNKNOWN** | `rg`: price-book-detail |
| FE `SimulatorPageComponent.onSimulate` | **UNKNOWN** | `rg`: template + spec only |
| FE `PriceBookCreateDialogComponent.onSubmit` | **UNKNOWN** | `rg`: template + list open + spec |

**WARN:** UNKNOWN resolved via `rg` — additive banner string formatting only. No HIGH/CRITICAL in-scope.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/pricing-simulator/.../simulator-page.component.ts` (+spec) | Local H1 `httpErrorMessage`; HTTP `error` handler; empty-state shows `message [CODE]`; `pricingResult.errorMessage` unchanged |
| `frontend/libs/cpq/price-books/.../price-book-create-dialog.component.ts` (+spec) | Copy detail `bookHttpErrorMessage` (narrow known codes) |
| `frontend/libs/cpq/pricing-data-access/.../price-book.service.ts` (+spec) | H1 `httpErrorMessage` on list / getById / getEntries `error.set` |
| `docs/forge/impl/s4-fe-simulator-pricebook-error.md` | This note |

## Acceptance

- [x] Simulator HTTP failures → `message [CODE]` on empty-state; idle/failed empty-states retained; no form redesign
- [x] Create dialog uses detail-shaped `bookHttpErrorMessage` (known codes only)
- [x] `PriceBookService` list/get banners get codes via H1 `error.set`
- [x] Specs mock `{ message, errorCode }` → `message [CODE]`
- [x] **No** price-book list-row delete; no quotes; no Q1 DataTable; no shell
- [x] No Java, NgRx, dual-read flip, `00-orchestrator-status.md`
- [x] Vitest `cpq-pricing-simulator` + `cpq-price-books` + `cpq-pricing-data-access` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-pricing-simulator --skip-nx-cache
NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache
NX_DAEMON=false npx nx test cpq-pricing-data-access --skip-nx-cache
```

## Out of scope

- Price-book list-row delete (R2 **HELD**)
- Quotes / shell / `app.routes` / sidebar / tsconfig / `test:cpq`
- Q1 ConfirmDialog / EmptyState / PageHeader / DataTable API changes
- Shared `httpErrorMessage` extract
- Java / NgRx / dual-read flip / Activities / #238 / #241 / WhatsApp #430 / subscriptions FE / Accept ADR-006
- `docs/forge/00-orchestrator-status.md`
