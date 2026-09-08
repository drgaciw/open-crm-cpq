---
status: done
id: s1-fe-crm-empty-state
---

# s1-fe-crm-empty-state

## Summary

Wave S1 CRM deal + organization list empty-state polish: replace custom `.empty-state` divs with shared `app-empty-state` (lead/contact pattern), add loading live region (`role="status" aria-live="polite"`), and adopt `app-page-header` (stretch). Create CTA only when unfiltered on deals (stage tab). No routes/shell/Java/NgRx.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.ts` | `app-page-header` + `app-empty-state`; loading a11y; `hasActiveFilters()` for stage tab |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.spec.ts` | empty-state / filtered CTA / loading a11y specs |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts` | `app-page-header` + `app-empty-state`; loading a11y |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.spec.ts` | empty-state + loading a11y specs |
| `docs/forge/impl/s1-fe-crm-empty-state.md` | this status note |

## Acceptance

- [x] Deal list empty uses `app-empty-state` with Create Deal when unfiltered; no Create when stage filter active
- [x] Organization list empty uses `app-empty-state` with Create Organization
- [x] Loading wrappers: `role="status" aria-live="polite"`
- [x] Stretch: `app-page-header` like leads/contacts
- [x] Specs for empty-state + loading a11y
- [x] `nx test crm-deals` green — **27 passed** (`NX_DAEMON=false npx nx test crm-deals --skip-nx-cache`)
- [x] `nx test crm-organizations` green — **7 passed** (`NX_DAEMON=false npx nx test crm-organizations --skip-nx-cache`)
- [x] No `app.routes` / sidebar / shell / tsconfig / quotes / bundles / price-books / simulator / Java / NgRx
- [x] Dual-read YAML untouched; no `00-orchestrator-status.md`; no shared `httpErrorMessage` extract; no `app-data-table` rewrite

## Out of scope (this wave)

- S2–S5 tracks
- Activities / #238 / #241 / WhatsApp #430 / subscriptions FE / Accept ADR-006 / R2 list-row delete

## Notes

- GitNexus: `DealListComponent` upstream **LOW** (routes import only). `OrganizationListComponent` upstream **LOW** (routes + form imports of org barrel at d=3; list template change only). Index stale (~23 behind); analyze skipped per plan; `rg` SSOT for UNKNOWN.
- Day-1 stretch `app-page-header` landed with empty-state.
