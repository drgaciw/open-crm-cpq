---
status: done
id: u2-fe-crm-leads-contacts-error
wave: U
pr: U2
---

# u2-fe-crm-leads-contacts-error

## Summary

Wave U2: copy H1 `httpErrorMessage` into `lead.service` / `contact.service` `error.set` so banners surface `message [CODE]`. Lead/contact list + detail show `role="alert"` Dismiss/Retry banners; lists do **not** show empty-state on failed loads; detail loading wrappers use `role="status"`. No shell/dashboard (U1)/deals/orgs (U3)/Java; dual-read untouched; did not edit `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-u2` (`fix/forge-u2-fe-crm-leads-contacts-error`)  
Index: **stale** (~34 behind; analyze skipped per wave plan); depth walk **partial/degraded**.

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| FE `LeadService` | **UNKNOWN** (0 callers in index; BE class also matches) | `rg`: lead-list/detail/form + dashboard (U1 read-only — not edited) |
| FE `ContactService` | **UNKNOWN** | `rg`: contact-list/detail/form + lead-form + deal-form (list/search/get error paths only) |
| Local `httpErrorMessage` | **UNKNOWN** (module-scope; not indexed as shared) | Copied H1 locally; no shared extract |

**WARN:** UNKNOWN ≠ unused. Confirmed with `rg`. Plan risk **LOW–MED** (false-empty fix).

## Files

| Path | Change |
|------|--------|
| `frontend/libs/crm/leads/src/lib/lead.service.ts` | H1 helper; list/getById/search `error.set` |
| `frontend/libs/crm/leads/src/lib/lead.service.spec.ts` | Assert `message [CODE]` for list/get/search |
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.ts` | Error banner Dismiss/Retry; no empty on error; loading live region |
| `frontend/libs/crm/leads/src/lib/lead-list/lead-list.component.spec.ts` | Banner / no empty / dismiss / retry |
| `frontend/libs/crm/leads/src/lib/lead-detail/lead-detail.component.ts` | Error banner; loading `role="status"` |
| `frontend/libs/crm/contacts/src/lib/contact.service.ts` | H1 helper; list/getById/search `error.set` |
| `frontend/libs/crm/contacts/src/lib/contact.service.spec.ts` | New: list + `message [CODE]` asserts |
| `frontend/libs/crm/contacts/src/lib/contact-list/contact-list.component.ts` | Error banner Dismiss/Retry; no empty on error; loading live region |
| `frontend/libs/crm/contacts/src/lib/contact-list/contact-list.component.spec.ts` | Banner / no empty / dismiss / retry |
| `frontend/libs/crm/contacts/src/lib/contact-detail/contact-detail.component.ts` | Error banner; loading `role="status"` |
| `docs/forge/impl/u2-fe-crm-leads-contacts-error.md` | This note |

## Acceptance

- [x] `error.set` is `message [CODE]` when `errorCode` present
- [x] List shows `role="alert"` banner (Dismiss/Retry); not empty-state on failed load
- [x] Detail shows same banner; loading wrappers `role="status"`
- [x] No `app.routes` / sidebar / shell / dashboard / deals / orgs
- [x] No Java; dual-read YAML untouched; no `00-orchestrator-status.md`
- [x] No shared `httpErrorMessage` extract; no T/R2/S reopen
- [x] Vitest `crm-leads` + `crm-contacts` green

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test crm-leads --skip-nx-cache
NX_DAEMON=false npx nx test crm-contacts --skip-nx-cache
```

Result: **crm-leads** 3 files / **22 tests passed**; **crm-contacts** 3 files / **13 tests passed**.

## Out of scope

- U1 dashboard; U3 deals/orgs; U4 pricing-data-access; U5 docs
- Activities / #238 / #241 / dual-read flip / NgRx / subscriptions FE
- R2 list-row delete; named Wave T; N/S reopen
