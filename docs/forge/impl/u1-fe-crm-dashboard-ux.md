---
status: done
id: u1-fe-crm-dashboard-ux
---

# u1-fe-crm-dashboard-ux

## Summary

Wave U1 CRM dashboard UX: loading live region (`role="status" aria-live="polite"`), compact card empties keep `.empty-text` with `role="status"` (not full `app-empty-state`), forkJoin load failure shows `role="alert"` banner + Retry, stretch `app-page-header`. No service edits, routes, shell, Java, or NgRx.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/dashboard/src/lib/dashboard.component.ts` | loading a11y; compact empty `role="status"`; `loadError` alert + Retry; `app-page-header` |
| `frontend/libs/crm/dashboard/src/lib/dashboard.component.spec.ts` | loading / empty / error banner / Retry / page-header specs |
| `docs/forge/impl/u1-fe-crm-dashboard-ux.md` | this status note |

## Acceptance

- [x] Loading wrapper: `role="status" aria-live="polite"`
- [x] Compact `.empty-text` retained with `role="status"`; **no** `app-empty-state` in cards
- [x] forkJoin failure: `role="alert"` banner + Retry (no silent empty KPIs)
- [x] Stretch: `app-page-header` (title/subtitle)
- [x] Specs for loading a11y, compact empty, error banner, Retry
- [x] `nx test crm-dashboard` green — **6 passed** (`NX_DAEMON=false npx nx test crm-dashboard --skip-nx-cache`)
- [x] No `lead.service` / `contact.service` / `deal.service` edits (U2/U3)
- [x] No `app.routes` / sidebar / shell / tsconfig / `test:cpq` / Java / NgRx / dual-read YAML / `00-orchestrator-status.md`
- [x] No Wave T / R2 / S reopen

## Out of scope (this wave)

- U2–U5 tracks
- Activities / #238 / #241 / WhatsApp #430 / subscriptions FE / Accept ADR-006 / R2 list-row delete
- Wiring `LoadingService` into shell; shared `httpErrorMessage` extract

## Notes

- GitNexus: `DashboardComponent` upstream **UNKNOWN** (partial/stale index, 0 callers resolved). `rg` SSOT: wired only via `dashboard.routes.ts` → barrel → CRM app lazy load. Template/UX-only change → **LOW** (matches plan). New `loadDashboard` / `retry` not in index; `rg` confirms no external callers.
- Day-1 stretch `app-page-header` landed with loading/error a11y.
