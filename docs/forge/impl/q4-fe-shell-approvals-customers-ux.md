---
status: done
id: q4-fe-shell-approvals-customers-ux
wave: Q
pr: Q4
---

# q4-fe-shell-approvals-customers-ux

## Summary

Wave Q4: CRM chrome mobile sidenav + skip-link + Title Case nav labels; hide inert toolbar Notifications/Account; H1-shaped `httpErrorMessage` (`message [CODE]`) on approvals-inbox and pricing-customer-detail. **No** `app.routes.ts` / `tsconfig.base.json` / `package.json` `test:cpq`. Bundles path/label unchanged (P1). Did not edit shared-ui, quotes-detail, or Java.

## Acceptance

| Criterion | Result |
|-----------|--------|
| `<768px` sidenav `mode="over"`, start closed; hamburger opens | Yes — `BreakpointObserver` `(max-width: 767.98px)` |
| `≥768px` keep `side` + opened | Yes |
| Skip-link “Skip to main content” → `#main-content` on `<main>` | Yes — first focusable in shell |
| `aria` on sidenav (`role="navigation"`, `aria-label`) | Yes |
| Title Case: **Pricing Customers**, **Rule Sets**, **Pricing Simulator** | Yes |
| Keep **Bundles** path/label | Yes — `/product-bundles` |
| Optional CRM vs CPQ `<h3>` groups; no add/remove `routerLink` | Yes |
| Toolbar: hide inert Notifications/Account | Yes |
| Approvals + pricing-customer-detail H1 `errorCode` helper + specs | Yes |
| No `app.routes` / tsconfig / `test:cpq` | Confirmed |
| Vitest green | `cpq-approvals` + `cpq-pricing-customers` + crm layout specs |

## Files

| Path | Change |
|------|--------|
| `frontend/apps/crm/src/app/layout/shell.component.ts` | Mobile `over` sidenav; skip-link; `#main-content`; aria |
| `frontend/apps/crm/src/app/layout/shell.component.spec.ts` | New — mode/open, skip-link, aria, toggle |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Title Case labels; CRM/CPQ groups |
| `frontend/apps/crm/src/app/layout/sidebar.component.spec.ts` | New — labels, Bundles, route list |
| `frontend/apps/crm/src/app/layout/toolbar.component.ts` | Remove inert Notifications/Account |
| `frontend/apps/crm/src/app/layout/toolbar.component.spec.ts` | New — menu only |
| `frontend/libs/cpq/approvals/.../approvals-inbox.component.ts` | H1 `httpErrorMessage` |
| `frontend/libs/cpq/approvals/.../approvals-inbox.component.spec.ts` | `errorCode` banner assertion |
| `frontend/libs/cpq/pricing-customers/.../pricing-customer-detail.component.ts` | H1 `httpErrorMessage` |
| `frontend/libs/cpq/pricing-customers/.../pricing-customer-detail.component.spec.ts` | update/delete `errorCode` assertions |
| `docs/forge/impl/q4-fe-shell-approvals-customers-ux.md` | This note |

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `.worktrees/forge-q4` (`fix/forge-q4-fe-shell-approvals-customers-ux`)  
Index: stale (~15 commits behind) — `rg` confirmed layout-only callers.

| Symbol | Risk | Notes |
|--------|------|-------|
| `ShellComponent` | **LOW** exact (d=1 `app.routes` import); wave rates chrome **MED** (every page) | Additive UX; no route changes |
| `SidebarComponent` | **LOW** exact (d=1 shell) | Labels/groups only; no `routerLink` add/remove |
| `httpErrorMessage` (approvals-inbox) | **LOW** exact (1 same-file caller) | Additive `[code]` append |
| `httpErrorMessage` (pricing-customer-detail) | **LOW** exact (2 same-file callers) | Additive `[code]` append |

**WARN:** UNKNOWN ≠ unused — not applicable (epistemic exact). Confirmed with `rg` Shell/Sidebar only used from CRM layout/routes. Did **not** flip dual-read. Did **not** edit `app.routes` / Bundles / shared-ui / quotes-detail / `00-orchestrator-status.md`.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-approvals --skip-nx-cache
NX_DAEMON=false npx nx test cpq-pricing-customers --skip-nx-cache
NX_DAEMON=false npx nx test crm --skip-nx-cache
```

Pricing customers HTTP remains `/api/v1/pricing-customers` (never CRM `/api/v1/customers`).

## Out of scope

- `app.routes.ts` / `tsconfig.base.json` / `test:cpq`
- shared-ui (Q1); quotes-detail; product-bundles fight with P1
- Java / NgRx / dual-read flip / Activities / #238 / #241 / WhatsApp #430
- Subscriptions FE; Accept ADR-006; R2 list-row delete
- `docs/forge/00-orchestrator-status.md`
