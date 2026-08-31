---
status: done
id: l3-fe-price-books
wave: L
pr: L3
---

# l3-fe-price-books

## Summary

Wave L3: new FE lib `@frontend/cpq-price-books` (`frontend/libs/cpq/price-books`) — list/detail/create portal against existing `PriceBookService` (`@frontend/cpq-pricing-data-access`). Entries **read** on detail. Shell route `/price-books` + sidebar item owned by this track. API prefix **`/api/v1/price-books` only** — never `/api/v1/pricing/price-books`. No dual-read flip. No NgRx. No entry write CRUD Day-1. Does not expand `pricing-ui` stub.

## Acceptance

| Criterion | Result |
|-----------|--------|
| New Nx lib `@frontend/cpq-price-books` | Yes — `frontend/libs/cpq/price-books` |
| List loads via `PriceBookService.list` / `listByRegion` | Yes — optional region filter |
| Detail loads book + entries | Yes — `getById` + `getEntries` |
| Create book dialog | Yes — `PriceBookCreateRequest` → `create` → navigate to detail |
| Correct API prefix `/api/v1/price-books` | Yes — service + Vitest contract guard |
| Never `/api/v1/pricing/price-books` | Yes — contract spec + source scan |
| Shell route + sidebar | Yes — `/price-books` + Price Books nav |
| Reuse existing models (no model fight) | Yes — `PriceBookDto` / Create / Entry* in shared-models |
| Vitest green | `nx test cpq-price-books` — **23 passed** |
| No dual-read flip / no BE Java | Confirmed |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/price-books/**` | New Nx lib: list, detail, create dialog, routes, mocks, Vitest |
| `frontend/tsconfig.base.json` | Path `@frontend/cpq-price-books` |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy `price-books` route |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Price Books nav item |
| `frontend/package.json` | `test:cpq` includes `cpq-price-books` (+ `cpq-approvals`) |
| `docs/forge/impl/l3-fe-price-books.md` | This note |

## Behavior notes

- `PriceBookService` already uses Deal-style signal-state with basePath `/api/v1/price-books` (pricebook-service via gateway).
- List: DataTable + region filter (`listByRegion`) + New Price Book dialog.
- Detail: metadata card + entries DataTable (read-only Day-1).
- Create: name required; optional region, currency, isDefault, effective/expiration dates.
- Entry create/edit/delete deferred (Wave L stretch / later).

## GitNexus (pre-edit)

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `PriceBookService` | **LOW** (1 impacted: data-access `index.ts`) | Consumed only; no service edit |
| `SidebarComponent` | **LOW** (3 impacted) | shell → routes → app.config |
| `appRoutes` | **UNKNOWN** graph | Text: `app.config.ts` `provideRouter(appRoutes)` — additive route only |
| New price-books lib | n/a | New surface; no BE Java edits |

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-l3` (`feat/forge-l3-fe-price-books`) — Index ~77 commits behind (FE-only; text-confirmed shell callers).

**WARN:** Index stale; UNKNOWN on `appRoutes` resolved via `rg` to `app.config.ts`. Did **not** flip dual-read default (**HIGH** if flipped). Did **not** use `/api/v1/pricing/price-books`.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-price-books --skip-nx-cache
# 23 passed

# Contract guards
rg "/api/v1/pricing/price-books" frontend/libs/cpq/price-books frontend/libs/cpq/pricing-data-access
# → empty (wrong prefix absent)
rg "basePath = '/api/v1/price-books'" frontend/libs/cpq/pricing-data-access/src/lib/price-book.service.ts
# → present
rg "price-books" frontend/apps/crm/src/app/app.routes.ts frontend/apps/crm/src/app/layout/sidebar.component.ts
# → route + nav present
```

## Out of scope

- Entry write CRUD / activate workflows
- Expanding `pricing-ui` waterfall stub
- NgRx CPQ store
- Dual-read flip
- BE Java / Flyway
- L1 quotes documents / L2 catalog delete (parallel tracks)
