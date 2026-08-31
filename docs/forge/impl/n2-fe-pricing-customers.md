---
status: done
id: n2-fe-pricing-customers
wave: N
pr: N2
---

# n2-fe-pricing-customers

## Summary

Wave N2: new Nx feature lib `frontend/libs/cpq/pricing-customers` (`@frontend/cpq-pricing-customers`, project `cpq-pricing-customers`) — list (optional segment filter), detail, create dialog, inline update, and ConfirmDialog soft-delete via existing `PricingCustomerService`. API prefix **`/api/v1/pricing-customers/**` only** — never `/api/v1/customers` or CRM contact/org APIs. Owns shell: `app.routes.ts`, `sidebar.component.ts` (label **Pricing customers**, path `pricing-customers`), `tsconfig.base.json` path alias, `frontend/package.json` `test:cpq`. Standalone + OnPush + signals; no NgRx; did not expand `pricing-ui` stub; did not edit quotes or price-books libs.

## Acceptance

| Criterion | Result |
|-----------|--------|
| New lib `@frontend/cpq-pricing-customers` mirroring price-books/approvals | Yes |
| List + optional segment filter | Yes — `list` / `listBySegment` |
| Detail + create dialog + update + ConfirmDialog delete | Yes |
| Via existing `PricingCustomerService` | Yes — `@frontend/cpq-pricing-data-access` |
| API `/api/v1/pricing-customers/**` only | Yes — service + contract Vitest |
| Never `/api/v1/customers` / CRM contacts/orgs | Guarded in api-contract spec |
| Sidebar **Pricing customers**; route `pricing-customers` | Yes |
| `tsconfig.base.json` path + `test:cpq` project | Yes |
| No NgRx / no pricing-ui / no quotes / no price-books edits | Confirmed |
| Vitest green | `nx test cpq-pricing-customers --skip-nx-cache` — **26 passed** |

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/pricing-customers/**` | New lib: list/detail/create-dialog + Vitest + API contract |
| `frontend/apps/crm/src/app/app.routes.ts` | Lazy route `pricing-customers` |
| `frontend/apps/crm/src/app/layout/sidebar.component.ts` | Nav item **Pricing customers** |
| `frontend/tsconfig.base.json` | `@frontend/cpq-pricing-customers` alias |
| `frontend/package.json` | Append `cpq-pricing-customers` to `test:cpq` |
| `docs/forge/impl/n2-fe-pricing-customers.md` | This note |

## Behavior notes

- **List:** Page header + segment `mat-select` filter; `DataTable` rows; create dialog → navigate to detail.
- **Create:** Required `externalId` + `name`; optional type/segment/region/industry/churnScore/annualRevenue.
- **Detail:** View/edit toggle (catalog pattern); `PricingCustomerService.update` then refresh `selectedCustomer`; ConfirmDialog → soft-delete → `/pricing-customers`.
- **Shell ownership:** N2 exclusive for routes/sidebar/tsconfig path/`test:cpq` this wave.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` — Worktree: `.worktrees/forge-n2` (`feat/forge-n2-fe-pricing-customers`)

| Symbol | Risk | Notes |
|--------|------|-------|
| FE `PricingCustomerService` | **LOW** exact (barrel + transitive data-access importers) | Safe; new UI is additive |
| Upstream `rg` | Service + spec + barrel + README/backlog only | **No UI callers** before N2 — confirmed |

**WARN:** UNKNOWN ≠ unused — this symbol was LOW/exact; still confirmed with `rg` that there were zero component callers before this portal. No HIGH/CRITICAL in-scope. Did **not** flip dual-read default. Did **not** reopen WhatsApp #430. Did **not** invent Activities.

## Verify

```bash
cd frontend
NX_DAEMON=false npx nx test cpq-pricing-customers --skip-nx-cache
# 26 passed (5 files)

rg "basePath = '/api/v1/pricing-customers'" frontend/libs/cpq/pricing-data-access/src/lib/pricing-customer.service.ts
rg "PricingCustomerService" frontend/libs/cpq/pricing-customers
# → list + detail + create-dialog callers

# Wrong CRM prefixes must stay absent from feature sources
rg "/api/v1/customers|/api/v1/contacts" frontend/libs/cpq/pricing-customers/src --glob '!**/*.spec.ts' || true
```

**Browser (local `nx serve crm` @ `:4210`, Playwright/Chromium):** sidebar **Pricing customers** → `/pricing-customers`; list heading + create CTA/empty-or-error; deep link; mobile nav label; adjacent `/price-books` still renders. Live API CRUD not exercised (no gateway on `:8080`).

## Out of scope

- Expanding `pricing-ui` waterfall stub
- Quotes / price-books libs
- NgRx CPQ store
- Dual-read default flip / Activities / WhatsApp #430
- BE Java
