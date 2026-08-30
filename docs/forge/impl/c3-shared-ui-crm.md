---
status: done
id: c3-shared-ui-crm
---

# c3-shared-ui-crm

## Summary

PR-FE-5 (leads + contacts scope): Adopt `@frontend/shared-ui` on CRM **lead** and **contact** list pages — `PageHeader`, `SearchInput`, `EmptyState`, `StatusBadge` (leads), and `ConfirmDialog` for delete (and lead convert). No list/search/lifecycle behavior changes beyond confirm UX (native `confirm` → Material dialog).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.ts` | Shared page header, debounced search, empty state, status badge; ConfirmDialog for delete + convert |
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.spec.ts` | ConfirmDialog mocks for convert/delete; retained lifecycle/filter coverage |
| `frontend/libs/crm/contacts/src/lib/contact-list/contact-list.component.ts` | Shared page header, search, empty state; ConfirmDialog for delete |
| `frontend/libs/crm/contacts/src/lib/contact-list/contact-list.component.spec.ts` | New: list load, search, ConfirmDialog delete paths |
| `docs/forge/impl/c3-shared-ui-crm.md` | This status note |

## Acceptance

- [x] Lead list uses `PageHeader`, `SearchInput`, `EmptyState`, `StatusBadge`
- [x] Contact list uses `PageHeader`, `SearchInput`, `EmptyState` (no status column / badge — list never showed contact status)
- [x] `ConfirmDialog` replaces native `confirm` for list delete (leads + contacts) and lead convert
- [x] Lifecycle actions, tabs, priority filter, search→service wiring unchanged
- [x] `nx test crm-leads` / `nx test crm-contacts` green

## Out of scope (this wave)

- Deal / organization list shared-ui adoption (full PR-FE-5 four-list sweep)
- Lead/contact **detail** delete confirm migration
- Extending `StatusBadge` for `ContactStatus`

## Notes

- GitNexus MCP offline; callers checked via grep — list components are route-scoped (`leads.routes` / `contacts.routes`) plus specs (**LOW–MEDIUM** visual risk).
- Search debounce now owned by `SearchInput` (300ms); previous manual `setTimeout` removed.
- Status chip colors now come from shared `StatusBadge` lead palette (includes `UNQUALIFIED`).
