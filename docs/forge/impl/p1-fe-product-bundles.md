---
status: done
id: p1-fe-product-bundles
wave: P
pr: P1
---

# p1-fe-product-bundles

## Summary

Wave P1: new Nx feature lib `frontend/libs/cpq/product-bundles` (`@frontend/cpq-product-bundles`, project `cpq-product-bundles`) — paginated list, detail with items table, create dialog, add/remove items, delete bundle ConfirmDialog. Surfaces `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED` in banners. Models colocated in the lib (not `shared/models`). Owns shell: `app.routes.ts`, `sidebar.component.ts` (label **Bundles**, path `product-bundles`), `tsconfig.base.json` path alias, `frontend/package.json` `test:cpq`. Standalone + OnPush + signals; no NgRx; no Java; did not edit `docs/forge/00-orchestrator-status.md`; dual-read YAML untouched.

## Acceptance

| Criterion | Result |
|-----------|--------|
| New lib `@frontend/cpq-product-bundles` mirroring price-books / rule-sets | Yes |
| Paginated list `GET /api/v1/product-bundles` | Yes — optional productId filter via `GET /product/{id}` |
| Detail `GET /{id}` + items table | Yes |
| Create dialog (productId, name, optional description/discountType/discountValue/minItems/maxItems) | Yes |
| Add item `POST /{id}/items`; remove `DELETE /{bundleId}/items/{itemId}` | Yes |
| Delete bundle ConfirmDialog | Yes |
| Banner `BUNDLE_MAX_ITEMS_EXCEEDED` / `BUNDLE_MIN_ITEMS_REQUIRED` | Yes — `bundleHttpErrorMessage` |
| Models colocated; no `shared/models` index edit | Yes |
| Sidebar **Bundles**; route `product-bundles` | Yes |
| `tsconfig.base.json` path + `test:cpq` project | Yes |
| Never `/api/v1/configuration/**`, `/api/v1/subscriptions`, `/api/v1/cpq/product-bundles` | Guarded in api-contract spec |
| No NgRx / no Java | Confirmed |
| Vitest green | `nx test cpq-product-bundles --skip-nx-cache` — **33 passed** (8 files) |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/product-bundles/**` | New lib: list/detail/create/item dialogs + service + models + Vitest |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy route `product-bundles` |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Nav item **Bundles** |
| `frontend/tsconfig.base.json` | `@frontend/cpq-product-bundles` alias |
| `frontend/package.json` | Append `cpq-product-bundles` to `test:cpq` |
| `docs/forge/impl/p1-fe-product-bundles.md` | This note |

## Behavior notes

- **List:** Page header + optional product ID filter; `DataTable` rows; create dialog → navigate to detail.
- **Create:** Required `productId` + `name`; optional description / discountType / discountValue / minItems / maxItems.
- **Detail:** Items table from `ProductBundleDto.items`; add item dialog; remove item ConfirmDialog; delete bundle ConfirmDialog.
- **Errors:** Known codes appended as `[BUNDLE_MAX_ITEMS_EXCEEDED]` / `[BUNDLE_MIN_ITEMS_REQUIRED]` in dialogs and detail banner.
- **Shell ownership:** P1 exclusive for routes/sidebar/tsconfig path/`test:cpq` this wave.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-p1` (`feat/forge-p1-fe-product-bundles`)

| Symbol | Risk | Notes |
|--------|------|-------|
| `ProductBundleController` | **UNKNOWN** (0 upstream; HTTP) | Expected; confirmed with `rg` |
| Gateway | routes `/api/v1/product-bundles/**` (+ CPQ alias) | Confirmed |
| CE | `handleBundleStructureChanged` | Confirmed consumer of `BUNDLE_STRUCTURE_CHANGED` |

**WARN:** UNKNOWN ≠ unused — controller is live HTTP; FE mutations on items publish Kafka via existing BE (no Java in this PR). No HIGH/CRITICAL in-scope. Did **not** flip dual-read default. Did **not** call configuration/** or subscriptions. Did **not** edit `00-orchestrator-status.md`.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-product-bundles --skip-nx-cache

rg "basePath = '/api/v1/product-bundles'" frontend/libs/cpq/product-bundles/src/lib/product-bundle.service.ts

# Forbidden prefixes must stay absent from feature sources
rg "/api/v1/configuration/|/api/v1/subscriptions|/api/v1/cpq/product-bundles" frontend/libs/cpq/product-bundles/src --glob '!**/*.spec.ts' || true
```

## Out of scope

- `/api/v1/configuration/**` / product validate
- `/api/v1/subscriptions` FE
- `/api/v1/cpq/product-bundles` browser calls
- NgRx CPQ store / Java / dual-read flip
- Activities / WhatsApp #430 / ADR-006 Accept
- `docs/forge/00-orchestrator-status.md`
