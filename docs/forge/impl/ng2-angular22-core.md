---
status: done
id: ng2-angular22-core
wave: NG
pr: NG2
---

# ng2-angular22-core

## Summary

NG2 only: Angular **21.1 → 22.1.6** lockstep with CDK/Material **22.1.6**, Nx **23.2.1**, TypeScript **6.0.3**, Analog **2.7.2**, Vite **8.3.0**. Added `@angular/aria@22.1.6` so NG3 does not need a `package.json` exclusive-file bump. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. No NgRx, Activities API, #238/#241, R2, subscriptions FE, GLM-5 runtime, CI files, `docs/ARCHITECTURE.md`, or `docs/forge/00-orchestrator-status.md`. Proxy still `frontend/proxy.conf.json` → gateway `:8080`.

## GitNexus (pre-edit)

| Target | Risk | Notes |
|--------|------|-------|
| `@angular/core` | **UNKNOWN** | `impact` error: target not found |
| `@angular/cdk` | **UNKNOWN** | same |
| `@angular/material` | **UNKNOWN** | same |
| `nx` | **UNKNOWN** | same |

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/ng2`  
Index: `04909db` (2026-09-13)

UNKNOWN ≠ unused. Angular packages **UNKNOWN → HIGH** by policy. `rg` shows `@angular/{core,cdk,material,forms,common,router}` imports across `frontend/apps/crm` + `frontend/libs/{crm,cpq,shared}/**`. Blast radius is the entire frontend workspace. Did not skip the walk; did not treat empty callers as unused.

MCP (NG1): `get_best_practices` (workspace `frontend/`), `search_documentation` v22 (`provideZonelessChangeDetection` is NG3; `@angular/aria` is stable; `withXhr` is the v22 HttpClient XHR opt-in). `list_projects` still needs `angular.json` (Nx `project.json` only; did **not** fake one). Zoneless provider / `@Service` / Signal Forms / WebMCP are **NG3/NG4**.

## Versions (landed)

| Package | From | To |
|---------|------|----|
| `@angular/{core,common,compiler,forms,platform-browser,router,compiler-cli,language-service}` | `~21.1.0` | **22.1.6** |
| `@angular/{animations,cdk,material}` | `^21.1.3` | **22.1.6** |
| `@angular/aria` | (none) | **22.1.6** |
| `@angular/{build,cli}` / `@angular-devkit/{core,schematics}` / `@schematics/angular` | `~21.1.0` | **22.1.8** |
| `nx` / `@nx/*` | 22.4.5 | **23.2.1** |
| `typescript` | `~5.9.2` | **6.0.3** |
| `@analogjs/{vite-plugin-angular,vitest-angular}` | `~2.1.2` | **2.7.2** |
| `vite` | `^7.0.0` | **8.3.0** |
| `angular-eslint` | `^21.0.1` | **22.5.0** |
| `vitest` | `^4.0.8` | `^4.0.8` (unchanged) |

Nx migrate latest (Nx **23.2.1**) + Angular 22 schematics. Analog bumped **2.6.4 → 2.7.2** after migrate (latest stable that peers Angular 22 + Vite 8). `npm ci` **requires `--legacy-peer-deps`**: Analog 2.7.2 optional peer `@angular-devkit/build-angular` resolves `@angular/localize@21` vs `compiler-cli@22`. Pre-migrate `npm ci` without the flag worked.

## Files (migrate emit + aria)

| Path | Change |
|------|--------|
| `frontend/package.json` + lockfile | Angular 22 / Nx 23 / TS 6 / Analog 2.7.2 / Vite 8 / `@angular/aria` |
| `frontend/nx.json` | ESLint target-default inputs (`^default`, `tools/eslint-rules`) |
| `frontend/tsconfig.base.json` + per-project `tsconfig.json` | TS6 `ignoreDeprecations: "6.0"` + pre-TS6 default pins |
| `frontend/apps/crm/tsconfig.app.json` | v22 `extendedDiagnostics` suppress for optional-chain/nullish |
| `frontend/libs/**/project.json` | Vitest `reportsDirectory` `{projectRoot}/...` prefix |
| `frontend/libs/**/vite.config.mts` | `__dirname` → `import.meta.dirname` (Vite 8 native config loader) |
| `frontend/vitest.config.ts` | Vitest 4 workspace replacement for `vitest.workspace.ts` |
| `frontend/.gitignore` / `.prettierignore` | Nx 23 `.nx/{polygraph,self-healing,migrate-runs}`, `.claude/*` |
| `frontend/apps/crm/src/app/app.config.ts` | `provideHttpClient(withXhr(), …)` — v22 Fetch-default opt-out |
| `frontend/libs/cpq/pricing-simulator/.../simulator-page.component.ts` | `$safeNavigationMigration(...)` on two `?.` template exprs |
| `frontend/tools/ai-migrations/**` | Skipped prompt-migration notes (Vite 8 / Vitest 3–4 / eslint) |
| `docs/forge/impl/ng2-angular22-core.md` | This note |

Did **not** commit `frontend/migrations.json` (consumed). Did **not** add `frontend/angular.json`. Did **not** edit `.cursor/mcp.json`, `ci.yml`, Jenkins Dockerfile, `docs/ci-contract.yaml`, `docs/ARCHITECTURE.md`, `backend/**`, or `docs/forge/00-orchestrator-status.md`. Node **22.22.3** is JDK2 (`#518` on `main`).

Prompt migrations skipped (already true in tree): Vite 8 `rollupOptions` absent; Vitest 4 already in use; ESLint already flat `eslint.config.mjs`; no `@typescript-eslint/ban-types`. NG3 owns zoneless `provideZonelessChangeDetection()`, `@Service`, Signal Forms, Aria usage.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}` — YAML/env untouched
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime; Track 2 Spring AI FE
- NG3/NG4; X1 `ARCHITECTURE.md`
- `docs/forge/00-orchestrator-status.md`

## GitNexus (pre-commit)

`detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq`: **68 files, 21 symbols, 0 processes, risk low** (no `partial`/`truncated`). Symbols are `appConfig` + per-lib `vite.config`. Package-name pins remain **UNKNOWN → HIGH**; tests/build are SSOT.

`withXhr` / `provideHttpClient`: **UNKNOWN** (framework APIs). `rg` + production build + 20-project Vitest.

## Acceptance

- [x] `@angular/core` **22.1.6**; CDK/Material **22.1.6**; `nx` **23.2.1**; `typescript` **6.0.3**
- [x] `@angular/aria` **22.1.6** (NG3 exclusive-file hygiene)
- [x] Analog 2.7.2 + Vite 8.3.0 peer-compatible
- [x] `cd frontend && npm ci --legacy-peer-deps` (flag **required**)
- [x] `NX_DAEMON=false npx nx build crm --configuration=production` — green; initial **669.10 kB** (warn 750kb)
- [x] `NX_DAEMON=false npx nx run-many -t test --all --parallel=3` — **20/20**
- [x] `npm run test:cpq` + `npm run test:crm` — green (cache)
- [x] Proxy still `:8080`
- [x] No NgRx; dual-read untouched
