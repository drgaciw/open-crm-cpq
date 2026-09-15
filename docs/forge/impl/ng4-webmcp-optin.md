---
status: done
id: ng4-webmcp-optin
wave: NG
pr: NG4
---

# ng4-webmcp-optin

## Summary

NG4 only: experimental WebMCP opt-in on **quotes-list** (XOR catalog — catalog create already owns the NG3 Signal Form). Shipped provider name confirmed via Angular CLI MCP `search_documentation` v22: **`provideExperimentalWebMcpTools`** (`@angular/core`). Read tools only: `listQuotes` / `filterQuotes` / `pageQuotes`. No writes. No `provideExperimentalWebMcpForms` (quotes-list has no Signal Form). Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. No NgRx, Activities API, #238/#241, R2, subscriptions FE, GLM-5 runtime, `frontend/package.json`, CI, `docs/ARCHITECTURE.md`, or `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/ng4`  
Index: 34 commits behind HEAD (stale; `rg` used as SSOT for UNKNOWN)

| Target | Risk | Notes |
|--------|------|-------|
| `provideExperimentalWebMcpTools` | **UNKNOWN** | Framework API, not indexed. Architect blast = **LOW–MED** (opt-in). `rg`: not present before this PR. |
| `appConfig` | **UNKNOWN** | 0 callers in graph. `rg`: `frontend/apps/crm/src/main.ts` bootstrap + `app.spec.ts`. Additive router feature only. |
| `QuoteService` (FE) | **MEDIUM** | 9 impacted, 6 direct. **Not edited** — tools `inject()` and call `list()` / read `quotes()` `pagination()` `loading()` `error()`. Java `QuoteService` not edited. |
| `QuotesListComponent` | **LOW** | 4 impacted. **Not edited** — tools live in sibling `quotes-list-webmcp.ts`. |

UNKNOWN ≠ unused. Did not treat empty callers as unused. **WARN:** FE `QuoteService` **MEDIUM** — proceeded (always-approve; additive callers only, no signature change). `appConfig` UNKNOWN confirmed via `rg`.

MCP: `search_documentation` v22 (`provideExperimentalWebMcpTools`, `provideExperimentalWebMcpForms`, `withExperimentalAutoCleanupInjectors`). `get_best_practices` (workspace `frontend/`). `list_projects` still needs `angular.json` (Nx `project.json` only; did **not** fake one). Aliases `provideExperimentalWebMcpTool` / `provideWebMcpTools` are **not** the shipped name.

## Files

| Path | Change |
|------|--------|
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list-webmcp.ts` | `provideExperimentalWebMcpTools` read tools: list / filter / page |
| `frontend/libs/cpq/quotes/src/lib/quotes-list/quotes-list-webmcp.spec.ts` | Provider registered; execute hits `QuoteService.list` + signals |
| `frontend/libs/cpq/quotes/src/lib/quotes.routes.ts` | List route `providers: [provideQuotesListWebMcpTools()]` — not create/detail |
| `frontend/apps/crm/src/app/app.config.ts` | `withExperimentalAutoCleanupInjectors()` so route tools unregister on navigate away |
| `frontend/apps/crm/src/app/app.spec.ts` | Router providers still registered |
| `docs/forge/impl/ng4-webmcp-optin.md` | This note |

Did **not** edit `frontend/package.json`, `backend/**`, `ci.yml`, Jenkins, `docs/ci-contract.yaml`, `docs/ARCHITECTURE.md`, or `docs/forge/00-orchestrator-status.md`. Proxy still `frontend/proxy.conf.json` → gateway `:8080`.

## Tools (read-only)

| Tool | Behavior |
|------|----------|
| `listQuotes` | `QuoteService.list(page, size, 'createdAt,desc')`; snapshot `quotes()` / `pagination()` / `loading()` / `error()` |
| `filterQuotes` | Status/dealType → `QuoteService.list` (server-side, same as the list UI). `query` → client-side filter on current `quotes()` (name/number/customer/description) |
| `pageQuotes` | `QuoteService.list(page, size, sort, {status, dealType})` |

No create/update/delete/submit/approve tools. Runtime-validate `status` / `dealType` / `page` / `size` / `query` (Angular docs: schema is not enforced).

## Skipped

- **`provideExperimentalWebMcpForms`** — quotes-list has no Signal Form (catalog create does; XOR).
- Catalog-list WebMCP — XOR; catalog create owns NG3 Signal Form.
- App-wide tools, approvals, NgRx, new portal, subscriptions FE, sidebar entries.
- X1 `ARCHITECTURE.md`.

## Holds (unchanged)

- Dual-read default `${APPROVALS_LEGACY_LOCAL_READ:true}`
- NgRx; Activities API; CI #238; Kafka #241; **R2** list-row delete
- Subscriptions FE; GLM-5 runtime; Track 2 Spring AI FE
- `docs/forge/00-orchestrator-status.md`

## GitNexus (pre-commit)

`detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq`: **3 indexed files, 2 symbols, 0 processes, risk low** (no `partial`/`truncated`). Symbols: `appConfig`, `QUOTES_ROUTES`. Unindexed until commit: `quotes-list-webmcp.ts` / spec / impl markdown. Framework `provideExperimentalWebMcpTools` remains UNKNOWN; tests/build are SSOT.

## Acceptance

- [x] Shipped name `provideExperimentalWebMcpTools` (MCP `search_documentation` v22)
- [x] Read tools list/filter/page on quotes-list only
- [x] No writes that bypass UI validation
- [x] No `provideExperimentalWebMcpForms`
- [x] Not app-wide / approvals / NgRx / new portal / subscriptions
- [x] `NX_DAEMON=false npx nx test cpq-quotes --skip-nx-cache` — **84 passed**
- [x] `NX_DAEMON=false npx nx test crm --skip-nx-cache` — **12 passed**
- [x] `NX_DAEMON=false npx nx build crm --configuration=production` — green; initial **670.95 kB** (warn 750kb)
- [x] Proxy still `:8080`
- [x] No NgRx; dual-read untouched
- [x] No browser UAT this PR (no visible UI/route/sidebar change); Vitest + production build are SSOT
