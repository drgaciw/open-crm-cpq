---
status: done
id: u3-fe-crm-deals-orgs-error
---

# u3-fe-crm-deals-orgs-error

## Summary

Wave U3 CRM deal + organization error UX: local H1 `httpErrorMessage` in `deal.service` / `organization.service` so `error.set` is `message [CODE]`; list/detail `role="alert"` banners (Retry + Dismiss) **before** empty-state so failed loads are not “No deals/organizations yet”; S1 `app-empty-state` retained for **true** empty. Detail loading wrappers get `role="status" aria-live="polite"`. No shell, leads/contacts, dashboard, Java, dual-read, T/R2, or `00-orchestrator-status`.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/deal.service.ts` | Local H1 helper; all `error.set` paths |
| `frontend/libs/crm/deals/src/lib/deal.service.spec.ts` | list/getById `message [CODE]` specs |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.ts` | Error banner before empty; hide empty when `error()` |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.spec.ts` | Banner / no false-empty / Retry / Dismiss |
| `frontend/libs/crm/deals/src/lib/deal-detail/deal-detail.component.ts` | Error banner + loading a11y |
| `frontend/libs/crm/organizations/src/lib/organization.service.ts` | Local H1 helper; all `error.set` paths |
| `frontend/libs/crm/organizations/src/lib/organization.service.spec.ts` | list/getById `message [CODE]` specs (new) |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts` | Error banner before empty; hide empty when `error()` |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.spec.ts` | Banner / no false-empty / Retry / Dismiss |
| `frontend/libs/crm/organizations/src/lib/organization-detail/organization-detail.component.ts` | Error banner + loading a11y |
| `docs/forge/impl/u3-fe-crm-deals-orgs-error.md` | this status note |

## Acceptance

- [x] `error.set` is `message [CODE]` when `errorCode` present (H1 local copy; no shared extract)
- [x] Deal/org list shows `role="alert"` banner on error; **not** `app-empty-state` for failed loads
- [x] S1 `app-empty-state` retained for true empty (no error)
- [x] Detail pages show error banner; loading `role="status" aria-live="polite"`
- [x] `nx test crm-deals` green — **32 passed** (`NX_DAEMON=false npx nx test crm-deals --skip-nx-cache`)
- [x] `nx test crm-organizations` green — **13 passed** (`NX_DAEMON=false npx nx test crm-organizations --skip-nx-cache`)
- [x] No shell / leads / contacts / dashboard / Java / dual-read / T / R2 / `00-orchestrator-status`

## Out of scope (this wave)

- U1 dashboard; U2 leads+contacts; U4 pricing-data-access; U5 docs
- Kanban error banner (service H1 still applies to `loadPipelineSummary`)
- Shared `httpErrorMessage` extract; Activities; NgRx

## Notes

- GitNexus impact on FE `DealService` / `OrganizationService` / list components: **UNKNOWN** / `partial` (index ~34 behind; binder degraded). Confirmed callers via `rg`: same-lib list/detail/form/kanban + org barrel used by contact/lead/deal forms (dropdowns only). Risk **LOW–MED** per Wave U plan (false-empty fix).
- Dual-read YAML untouched.
