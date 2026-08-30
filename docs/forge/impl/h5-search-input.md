---
status: done
id: h5-search-input
---

# h5-search-input

## Summary

Wave H5 / #403 remainder: Adopt shared `SearchInputComponent` on CRM **deal** and **organization** list pages only. Replace raw `(input)` + manual `setTimeout` debounce with debounced `searchChange`. Leads/contacts already on SearchInput (C3) — left untouched.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.ts` | `SearchInputComponent`; remove MatInput search field + local debounce |
| `frontend/libs/crm/deals/src/lib/deal-list/deal-list.component.spec.ts` | SearchInput presence + `onSearch` → service wiring |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.ts` | `SearchInputComponent`; remove MatInput search field + local debounce |
| `frontend/libs/crm/organizations/src/lib/organization-list/organization-list.component.spec.ts` | New: list load, SearchInput presence, search wiring |
| `docs/forge/impl/h5-search-input.md` | This status note |

## Acceptance

- [x] Deal list uses `SearchInputComponent` (no raw `(input)` search)
- [x] Organization list uses `SearchInputComponent` (no raw `(input)` search)
- [x] Manual 300ms `setTimeout` debounce removed; debounce owned by `SearchInput` (300ms)
- [x] Empty search reloads list (deals preserve current stage filter)
- [x] Leads / contacts list files untouched
- [x] `nx test crm-deals` / `nx test crm-organizations` green

## Out of scope (this wave)

- Full PageHeader / EmptyState / ConfirmDialog adoption on deals + orgs (broader PR-FE-5)
- Lead / contact SearchInput changes
- Kanban search

## Notes

- GitNexus: `DealListComponent` / `OrganizationListComponent` upstream **LOW** (route-scoped). Index was stale (~28 behind); class impact still exact for these symbols.
- Search debounce now owned by `SearchInput` (300ms); previous manual `setTimeout` removed.
