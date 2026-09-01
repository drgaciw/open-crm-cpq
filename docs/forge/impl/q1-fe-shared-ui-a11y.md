---
status: done
id: q1-fe-shared-ui-a11y
---

# q1-fe-shared-ui-a11y

## Summary

Wave Q1 shared-ui accessibility polish: ConfirmDialog focus/`type="button"`/`aria-describedby`, EmptyState `role="status"`, PageHeader `hasSearchSlot()` false when no `[search]` projection, DataTable loading live region + keyboard row activation. APIs unchanged (`confirmColor` retained). No shell/routes/Java.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.ts` | `type="button"`; `cdkFocusInitial` on confirm; message `id` + root `aria-describedby` |
| `frontend/libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.spec.ts` | a11y assertions for type / focus / describedby |
| `frontend/libs/shared/ui/src/lib/empty-state/empty-state.component.ts` | root `role="status"` + `aria-live="polite"` |
| `frontend/libs/shared/ui/src/lib/empty-state/empty-state.component.spec.ts` | new: status / live region + action emit |
| `frontend/libs/shared/ui/src/lib/page-header/page-header.component.ts` | detect projected `[search]` via `viewChild` + `afterNextRender`; hide empty search padding |
| `frontend/libs/shared/ui/src/lib/page-header/page-header.component.spec.ts` | new: with/without search projection |
| `frontend/libs/shared/ui/src/lib/data-table/data-table.component.ts` | loading `role="status" aria-live="polite"`; row `tabindex` + Enter/Space → `rowClick` |
| `frontend/libs/shared/ui/src/lib/data-table/data-table.component.spec.ts` | loading live region + keyboard rowClick |
| `docs/forge/impl/q1-fe-shared-ui-a11y.md` | this status note |

## Acceptance

- [x] ConfirmDialog: `type="button"`, `cdkFocusInitial` on confirm, `aria-describedby` → message; `confirmColor` API kept
- [x] EmptyState: root `role="status"` (optional `aria-live`)
- [x] PageHeader: `hasSearchSlot()` false when no search projection (no empty padding on detail pages)
- [x] DataTable: loading live region; clickable rows keyboard (Enter/Space) → existing `rowClick`
- [x] Specs for the above
- [x] `nx test shared-ui` green — **72 passed** (`NX_DAEMON=false npx nx test shared-ui --skip-nx-cache`)
- [x] No `app.routes` / sidebar / tsconfig / quotes-detail / catalog / rule-sets / Java / NgRx / dual-read flip / `00-orchestrator-status.md`

## Out of scope (this wave)

- Shared `httpErrorMessage` helper extraction
- Q2–Q5 tracks; shell mobile sidenav; quotes/catalog/rule-sets banners
- Replacing the table library; rewriting EmptyState callers

## Notes

- GitNexus: ConfirmDialog / EmptyState / PageHeader / DataTable upstream **MEDIUM** (many CPQ/CRM importers). Additive a11y only — no input renames. `hasSearchSlot` impact **UNKNOWN** (template-only); confirmed via `rg` — no external callers.
- Index stale (~15 behind); `rg` used as SSOT for UNKNOWN symbols.
- PageHeader keeps `ng-content select="[search]"` always mounted (required for projection) and toggles `[hidden]` after `afterNextRender` counts projected children — avoids requiring every caller to import a marker directive.
