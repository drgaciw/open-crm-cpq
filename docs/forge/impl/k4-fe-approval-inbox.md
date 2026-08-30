---
status: done
id: k4-fe-approval-inbox
wave: K
pr: K4
---

# k4-fe-approval-inbox

## Summary

Wave K4: new FE lib `@frontend/cpq-approvals` (`frontend/libs/cpq/approvals`) — pending approval inbox against quote-service BFF. Day-1 surfaces list pending + Approve/Reject + link to quote detail. No NgRx. No history/SLA. Does **not** call approval-routing directly. Does **not** flip `approvals.legacy-local-read-enabled` (remains `${APPROVALS_LEGACY_LOCAL_READ:true}`).

## Acceptance

| Criterion | Result |
|-----------|--------|
| Pending list loads via `GET /api/v1/quotes/approvals/pending` | Yes — `ApprovalService.listPending` |
| Approve/Reject via quote BFF POSTs | Yes — `/api/v1/quotes/approvals/{id}/approve\|reject` |
| Never calls approval-routing `/api/v1/approvals/**` | Yes — Vitest asserts BFF base path |
| Shell route + sidebar | Yes — `/approvals` + Approvals nav item |
| Approval DTOs in this track | Yes — `shared-models` `approval.model.ts` |
| Link to quote detail | Yes — routerLink `/quotes/:quoteId` + Open quote |
| No NgRx / history / SLA | Confirmed |
| Do not flip legacy-local-read default | Confirmed — YAML untouched |
| Vitest green | `nx test cpq-approvals` — 14 passed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/shared/models/src/lib/approval.model.ts` | `ApprovalRequestDto`, tier/status unions, `ApprovalResponseRequest` |
| `frontend/libs/shared/models/src/index.ts` | Export approval models |
| `frontend/libs/shared/models/src/lib/cpq-models.spec.ts` | Tier/status guard + label coverage |
| `frontend/libs/cpq/approvals/**` | New Nx lib: service, inbox, routes, Vitest |
| `frontend/tsconfig.base.json` | Path `@frontend/cpq-approvals` |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy `approvals` route |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Approvals nav item |
| `docs/forge/impl/k4-fe-approval-inbox.md` | This note |

## Behavior notes

- `ApprovalService` uses Deal/Quote signal-state pattern; successful approve/reject removes the row from `pending` signal.
- Reject: optional `window.prompt` reason → confirm dialog → POST body `{ comments }` when non-blank.
- Approve: confirm dialog → POST `{}` (BFF resolves actor via `SecurityUtils` / `system`).
- Dual-read remains server-side behind quote BFF; FE does not read `approvals.legacy-local-read-enabled`.

## GitNexus (pre-edit)

| Symbol | Risk | Notes |
|--------|------|-------|
| `SidebarComponent` | **LOW** (3 impacted) | shell → routes → app.config |
| `appRoutes` | **UNKNOWN** graph | Text: `app.config.ts` `provideRouter(appRoutes)` — additive route only |
| New `ApprovalService` / inbox | n/a | New surface; no BE Java edits |

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-k4` (`feat/forge-k4-fe-approval-inbox`) — Index ~66 commits behind (FE-only; text-confirmed shell callers).

**WARN:** Index stale; UNKNOWN on `appRoutes` resolved via `rg` to `app.config.ts`. Did **not** flip dual-read default (**HIGH** if flipped).

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-approvals --skip-nx-cache
# 14 passed

# Contract guards
rg "legacy-local-read-enabled" backend/services/quote-service/src/main/resources/application.yml
# → default true unchanged
rg "/api/v1/approvals" frontend/libs/cpq/approvals/src
# → only comments / negative assertions; runtime basePath is /api/v1/quotes/approvals
```

## Out of scope

- Flipping `approvals.legacy-local-read-enabled`
- Calling approval-routing directly
- Approval history / SLA / submit-from-inbox
- NgRx store
- Quote detail lifecycle (K2) / catalog update (K3)
