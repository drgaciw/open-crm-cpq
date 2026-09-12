---
status: done
id: v3-fe-deal-kanban-ux
---

# v3-fe-deal-kanban-ux

## Summary

Wave V3 deal-kanban load UX: `role="alert"` banner bound to `service.error()` with Retry + Dismiss (U3 deal-list pattern); loading wrapper `role="status" aria-live="polite"`; board (and per-column “No deals”) only when load **succeeded** (`!loading && !error`). No full-board `app-empty-state`. Win/lose `confirm()` / silent action errors out of scope. Did not edit `deal.service`, shell, `app.routes`, crm styles/`project.json`, or `product.service`.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.ts` | Error banner + loading a11y; gate board on `!error()`; `onRetry` / `clearError` |
| `frontend/libs/crm/deals/src/lib/deal-kanban/deal-kanban.component.spec.ts` | Banner / no false empty columns / Retry / Dismiss / loading a11y |
| `docs/forge/impl/v3-fe-deal-kanban-ux.md` | this status note |

## Acceptance

- [x] Loading wrapper has `role="status" aria-live="polite"`
- [x] Load error shows `role="alert"` banner + Retry; binds `service.error()`
- [x] Failed `list()` does **not** paint empty columns as success
- [x] Per-column `empty-column` “No deals” only when load succeeded
- [x] No full-board `app-empty-state`
- [x] `nx test crm-deals` green (`NX_DAEMON=false npx nx test crm-deals --skip-nx-cache`)
- [x] No deal.service / shell / app.routes / V1 styles / V2 product.service / Java / NgRx / dual-read / `00-orchestrator-status`

## Out of scope (this wave)

- Win/lose `confirm()` and silent action `error:` handlers
- V1 Sass/budget; V2 catalog ProductService; V4 docs
- Shared `httpErrorMessage` extract; Activities; R2; Wave T

## Notes

- GitNexus impact on `DealKanbanComponent` / `refreshBoard`: **UNKNOWN** / `partial` (index ~47 behind; binder degraded). Confirmed callers via `rg`: `deals.routes.ts` (`path: 'kanban'`) + spec. Risk **LOW** per Wave V plan.
- Dual-read YAML untouched.
