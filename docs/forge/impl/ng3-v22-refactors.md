---
status: done
id: ng3-v22-refactors
wave: NG
pr: NG3
---

# ng3-v22-refactors

## Summary

NG3 only: Angular 22 refactors on FE sources. Explicit `provideZonelessChangeDetection()` in `app.config.ts`. `ProductService` + `QuoteService` use `@Service()`. One Signal Form on **catalog create** (`ProductCreateDialogComponent`). Confirm-dialog keeps `cdkFocusInitial` and adds CDK `cdkTrapFocus` + `aria-labelledby`. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. No NgRx, Activities API, #238/#241, R2, subscriptions FE, GLM-5 runtime, `frontend/package.json`, CI, `docs/ARCHITECTURE.md`, `docs/forge/00-orchestrator-status.md`, or NG4/WebMCP.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/ng3`  
Index: 33 commits behind HEAD (stale; `rg` used as SSOT for UNKNOWN)

| Target | Risk | Notes |
|--------|------|-------|
| `provideZonelessChangeDetection` | **UNKNOWN** | Framework API, not indexed. Architect blast = **MED** (global CD). `rg`: not present before this PR; `appConfig` consumed by `main.ts` `bootstrapApplication`. |
| `appConfig` | **UNKNOWN** | 0 callers in graph. `rg`: `frontend/apps/crm/src/main.ts`. |
| `ProductService` (FE) | **LOW** | 7 impacted, 4 direct. Java `ProductService` is a different symbol — not edited. |
| `QuoteService` (FE) | **MEDIUM** | 9 impacted, 6 direct. Decorator swap only (`inject()` already used). Java `QuoteService` not edited. |
| `ConfirmDialogComponent` | **MEDIUM** | 42 impacted (20 CPQ/CRM dialog importers). Additive a11y only — no input/API rename. |
| `ProductCreateDialogComponent` | **LOW** | catalog-list only. |
| `SharedUtils` | **UNKNOWN** | 0 callers. `rg`: class + its spec only. **Not edited** — v22 default OnPush. |

UNKNOWN ≠ unused. Did not treat empty callers as unused. **WARN:** ConfirmDialog **MEDIUM** (20 overlay consumers) and QuoteService **MEDIUM** — proceeded (always-approve; additive decorator / a11y).

MCP (NG1/NG2): `get_best_practices` (workspace `frontend/`), `search_documentation` v22 (`provideZonelessChangeDetection`, `@Service`, Signal Forms, `@boundary`). `list_projects` still needs `angular.json` (Nx `project.json` only; did **not** fake one). `onpush_zoneless_migration` fails with `ENOTDIR .../app.spec.ts/angular.json` — remainder documented empty from `rg` + best practices.

## Files

| Path | Change |
|------|--------|
| `frontend/apps/crm/src/app/app.config.ts` | `provideZonelessChangeDetection()` first in providers |
| `frontend/apps/crm/src/app/app.spec.ts` | Smoke: `appConfig.providers` non-empty |
| `frontend/libs/cpq/catalog/src/lib/product.service.ts` | `@Injectable({providedIn:'root'})` → `@Service()` |
| `frontend/libs/cpq/quotes/src/lib/quote.service.ts` | same |
| `frontend/libs/cpq/catalog/src/lib/product-create-dialog/product-create-dialog.component.ts` | Signal Form (`form` / `FormField` / `FormRoot` / `submit`) |
| `frontend/libs/cpq/catalog/src/lib/product-create-dialog/product-create-dialog.component.spec.ts` | Model/field APIs instead of `FormGroup` |
| `frontend/libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.ts` | `CdkTrapFocus`; `cdkFocusInitial` kept; `aria-labelledby` + `aria-describedby` |
| `frontend/libs/shared/ui/src/lib/confirm-dialog/confirm-dialog.component.spec.ts` | trap + labelledby assertions |
| `docs/forge/impl/ng3-v22-refactors.md` | This note |

Did **not** edit `frontend/package.json` (`@angular/aria@22.1.6` already in NG2). Did **not** edit `backend/**`, `ci.yml`, Jenkins, `docs/ci-contract.yaml`, `docs/ARCHITECTURE.md`, or `docs/forge/00-orchestrator-status.md`. Proxy still `frontend/proxy.conf.json` → gateway `:8080`.

## Zoneless / OnPush remainder

- v22 name is still `provideZonelessChangeDetection()` (`@angular/core`, publicApi 20.2). Docs note zoneless is default in v21+; this PR makes it **explicit**.
- Analog `setupTestBed({ zoneless = true })` already injects the same provider in Vitest.
- `rg changeDetection`: every CRM/CPQ/shared-ui component already has `OnPush`. v22 best practices: do **not** set OnPush explicitly (it is the default). Existing properties left in place.
- `SharedUtils` has no `changeDetection` — unused scaffold (`rg` only spec). v22 default OnPush. **Not edited.**
- `rg NgZone|provideZoneChangeDetection|zone.js` in FE sources: none (lockfile peer only).
- MCP `onpush_zoneless_migration` unusable without `angular.json`. Remainder **empty**.

## `@Service`

Both services already use `inject()`, not constructor DI — `@Service()` is the documented shorthand for `@Injectable({ providedIn: 'root' })`. TestBed `{ provide: ProductService, useValue }` / `providers: [ProductService]` still work.

Left `@Injectable` on `BaseCrudService` / constructor-injection / other HTTP services (plan: start with Product + Quote only).

`npx nx g @angular/core:service-migration` would mass-convert; not run.

## Signal Form (one surface)

Catalog create dialog (architect default). Quote-header-edit left on `FormBuilder`.

- `form()` + schema: `required` / `maxLength` / `min` (listPrice ≥ 0)
- `[formField]` on `matInput` / `mat-select` (Material 22 `FORM_FIELD` token)
- `FormRoot` for `novalidate` / native submit intercept; Create still `type="button"` + `onSubmit()` → `submit()`
- Native `maxlength`/`min` attributes **removed** (NG8022: `[formField]` owns those from validators)
- HTTP errors still use the existing dialog banner (`error` signal)

## Confirm-dialog CDK a11y

Material 22 still composes CDK overlay + focus trap. **Did not** rewrite to `@angular/aria` Dialog (architect: no wholesale Aria rewrite).

- Kept `cdkFocusInitial` on confirm
- Imported `CdkTrapFocus`; `cdkTrapFocus` on the dialog root (overlay a11y)
- `aria-labelledby` → title id; kept `aria-describedby` → message
- Dropped unused `CommonModule`
- Did **not** add `cdkTrapFocusAutoCapture` (jsdom: `[cdkFocusInitial]` not focusable; MatDialog overlay already auto-captures)

## modernize / `@boundary`

- `rg '*ngIf|*ngFor'`: **zero** in `frontend/`
- `rg @Input(|@Output(|@HostBinding|@HostListener`: **zero**
- `npx nx g @angular/core:control-flow --path=libs --dry-run` listed 4 shared-ui files that **already** use `@if`; did **not** apply (would be format noise)
- `ng generate` without `angular.json` is "not available outside a workspace"; Nx collection path used for dry-run only
- `@boundary`: `search_documentation` v22 **no results**; core d.ts has no public `@boundary`. **SKIP** (not stable)

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}`
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime; Track 2 Spring AI FE
- NG4 WebMCP; X1 `ARCHITECTURE.md`
- `docs/forge/00-orchestrator-status.md`

## GitNexus (pre-commit)

`detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq`: **8 indexed files, 11 symbols, 0 processes, risk low** (no `partial`/`truncated`). Symbols: `appConfig` / `providers`, `ProductCreateDialogComponent` (form/onSubmit), `ConfirmDialogComponent`. `@Service` decorator swap on Product/Quote did not emit a distinct symbol delta (same class). Impl markdown is unindexed. Framework APIs remain UNKNOWN; tests/build are SSOT.

## Acceptance

- [x] `provideZonelessChangeDetection()` in `app.config.ts`
- [x] `onpush_zoneless_migration` remainder **empty** (documented; MCP blocked on missing `angular.json`)
- [x] `ProductService` + `QuoteService` use `@Service()`
- [x] modernize remainder empty — no `*ngIf`/`*ngFor` regressions
- [x] One Signal Form on catalog create
- [x] Confirm-dialog `cdkFocusInitial` + `cdkTrapFocus` + labelledby/describedby
- [x] `@boundary` skipped (not stable in 22.1.6)
- [x] `NX_DAEMON=false npx nx test cpq-catalog` — **73 passed**
- [x] `NX_DAEMON=false npx nx test cpq-quotes` — **79 passed**
- [x] `NX_DAEMON=false npx nx test shared-ui` — **73 passed**
- [x] `NX_DAEMON=false npx nx test crm` — **11 passed**
- [x] `NX_DAEMON=false npx nx build crm --configuration=production` — green; initial **669.76 kB** (warn 750kb)
- [x] Proxy still `:8080`
- [x] No NgRx; dual-read untouched
- [x] No browser UAT this PR (no Chrome/Playwright session); Vitest + production build are SSOT
