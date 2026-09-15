---
status: done
id: uat1-browser-java25-angular22
wave: test-38
pr: UAT-1
---

# uat1-browser-java25-angular22

## Summary

UAT-1 only: Playwright browser session against Angular 22 CRM (`nx serve crm` `:4200`, proxy `frontend/proxy.conf.json` → gateway `:8080`) on Temurin **25.0.4.1 LTS**. Exclusive file is this note. **No FIX-\* code, no WARN-FE, no sidebar/route/NgRx edits.** Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. Bugs found are recorded below for follow-up PRs.

Tip: `origin/main` `10093e56`. Branch `test/forge-uat1-browser`. Worktree `/home/username01/Projects/open-crm-cpq/.worktrees/fe-uat`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fe-uat`  
Index: stale vs tip (plan 38: skip analyze). New docs file only — **no source symbols edited.** UNKNOWN ≠ unused does not apply.

## Environment

| Item | Value |
|------|--------|
| JAVA_HOME | `/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS` |
| `java -version` | `openjdk version "25.0.4.1" 2026-08-18 LTS` (Temurin-25.0.4.1+1) |
| Node | v26.7.0 |
| Playwright | 1.63.0 (bundled Chromium HeadlessChrome/153; `/tmp/uat1-pw` — **not committed**) |
| Docker | Engine 29.7.2; `newgrp docker`; `opencrm-postgres` + `opencrm-redis` healthy (`5432` / `6379`) |
| UI | `http://localhost:4200/` (`NX_DAEMON=false npx nx serve crm --skip-nx-cache`) |
| Proxy | `frontend/proxy.conf.json` → `http://localhost:8080` (**unchanged**, not CRM-direct) |
| Date | 2026-09-15 |

Maven (this worktree, skipTests): `./mvnw -B -pl services/gateway-service,services/crm-service,services/quote-service,services/product-service -am package -DskipTests` → **BUILD SUCCESS** (3:08). Jars run with `--spring.autoconfigure.exclude` Azure token/verifier/AAD B2C/Event Hubs Kafka OAuth2 (same list already in CPQ `application.yml`). Gateway + CRM `--spring.profiles.active=dev`. Quote `--approvals.legacy-local-read-enabled=true`.

## Boot attempt (gateway :8080 + crm + quote + nx serve)

| Process | Result | Evidence |
|---------|--------|----------|
| `nx serve crm` :4200 | **UP** | Vite `Local: http://localhost:4200/`; HTTP 200. Serve log also printed browserslist `caniuse-lite` 8-month advisory (WARN-FE track — not fixed here). |
| gateway-service :8080 `dev` | **UP** | `Started GatewayApplication in 70.327 seconds` using Java 25.0.4.1. `GET /actuator/health` → `{"status":"UP"}`. |
| crm-service :8081 `dev` | **UP after local schema reset** | First start **FAILED** Flyway: `Found non-empty schema(s) "crm" but no schema history table` (leftover volume tables, no `crm.flyway_schema_history`). **Local-only** `DROP SCHEMA crm CASCADE; CREATE SCHEMA crm;` then restart → `Started CrmServiceApplication in 66.783 seconds`. `GET /api/v1/leads?page=0&size=5` via gateway **200** empty page. |
| quote-service :8084 | **DOWN** | `BeanCreationException` `pricingServiceClient` / `PricingServiceClient`: `No default constructor found` (constructor requires `@Qualifier("pricingRestClient") RestClient`). Process exited. Gateway `GET /api/v1/quotes` → **500**. |
| product-service :8083 (extra; catalog needs it) | **UP / APIs 403** | `Started ProductServiceApplication in 98.618 seconds`. Health UP. `GET /api/v1/products` direct **and** via gateway/proxy → **403 Forbidden**. `SecurityConfig` has **no** `@Profile("dev")` permitAll (unlike CRM/gateway). |

Proxy proof (not CRM-direct): `curl http://localhost:4200/api/v1/products?...` returned gateway `403` + `X-RateLimit-*` headers. `curl http://localhost:4200/api/v1/quotes?...` returned gateway **500** JSON (`path":"/api/v1/quotes"`).

Degrade: live catalog/quotes lists show error banners; Playwright `page.route` mocks used for table/page/ConfirmDialog. **Documented, not silent.**

## Flow results

| Flow | Verdict | Notes |
|------|---------|-------|
| Catalog list | **PASS** (live-degraded) | Heading `Product Catalog`, New Product CTA, 403 banner `Http failure response for http://localhost:4200/api/v1/products?...: 403 Forbidden`. Empty state "No products yet". |
| Catalog create Signal Form + validation | **PASS** | Dialog `New Product`. Touched empty SKU/Name → `mat-error` **SKU is required**, **Name is required**. Create disabled. List price `-1` paints invalid (red) **without** `mat-error` text — finding below. |
| Quotes list filter/status/page (live) | **PASS** (live-degraded) | Heading `Quotes`. Status + deal-type filters + search present. Status → DRAFT still shows 500 banner (quote-service down). |
| Quotes list filter/status/page (mock) | **PASS** | 20 rows, paginator `1 – 20 of 24` → next `21 – 24 of 24`. Status DRAFT → 8 rows all DRAFT. |
| WebMCP route-tool smoke | **PASS** (stub host) | Playwright Chromium does **not** expose `navigator.modelContext` (`webmcpExposed: false`). Stub `registerTool` on quotes-list registered **`listQuotes` / `filterQuotes` / `pageQuotes`**. Native browser MCP not present — skip of live MCP is allowed by the plan. |
| ConfirmDialog (catalog Delete) | **PASS** (mock product) | Title `Delete product`. Initial focus **Delete** (`cdkFocusInitial`, `cdk-program-focused`). Tab trap Cancel ↔ Delete (6 Tabs stayed in overlay). Cancel closed dialog. |
| Console errors | **PASS** (catalogued) | Live 403/500 resource failures (boot blockers). After option-group mock returned a **raw array** (not a Spring page), **zero** `TypeError` / zoneless overlay crashes. No `pageerror`. |

## Findings (do **not** fold into this PR)

| ID | Owner | Surface | Detail |
|----|-------|---------|--------|
| **FIX-UAT1-QUOTE-BOOT-PRICING-CLIENT** | BE | quote-service boot | `PricingServiceClient` bean fails locally: `No default constructor found`. Needs `@Qualifier("pricingRestClient") RestClient`. Quote never binds `:8084`. Exclusive file TBD on the fix PR. |
| **FIX-UAT1-PRODUCT-NO-DEV-SECURITY** | BE | product-service (and likely quote) HTTP | `SecurityConfig` requires `authenticated()` on `/api/v1/**` with **no** `dev` permitAll (CRM/gateway have `@Profile("dev")`). Catalog list is 403 through gateway even though product health is UP. Same pattern as quote `SecurityConfig`. |
| **FIX-UAT1-CATALOG-LISTPRICE-MIN-NOT-SHOWN** | FE | catalog create Signal Form | Schema has `min(path.listPrice, 0, { message: 'List price must be at least 0' })` but the template has **no** `mat-error` for `listPrice`. UAT: `-1` shows invalid styling, no message. SKU/Name errors **do** render. |

Not a confirmed product bug: currency `maxLength(3)` — `fill('USDD')` displayed `USD`; no `mat-error`. Inconclusive.

Not a code bug: CRM Flyway on leftover `opencrm-postgres` volume (non-empty `crm` schema, no history table). Local reset unblocked CRM. Do not set `baselineOnMigrate` in this wave.

Mock-only (not scored): first ConfirmDialog attempt returned a **paginated** envelope for `GET /api/v1/product-options/groups/product/{id}` (service is typed `ProductOptionGroupDto[]`). Catalog detail `@for` threw `newCollection[Symbol.iterator] is not a function` and left a blank overlay. Re-mock as `envelope([])` → dialog painted and a11y checks passed.

## Console triage

| Signal | Triage |
|--------|--------|
| `403 .../api/v1/products` (and proxy) | Expected: **FIX-UAT1-PRODUCT-NO-DEV-SECURITY**. |
| `500 .../api/v1/quotes` | Expected: quote-service down (**FIX-UAT1-QUOTE-BOOT-PRICING-CLIENT**). Snackbar: "A server error occurred." |
| `caniuse-lite is 8 months old` on `nx serve` | WARN-FE; not this PR. |
| Maven Jansi native-access / Guava `Unsafe` during package | Third-party Java 25 noise (WARN-BE catalog). Build SUCCESS. |

## Holds respected

- Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched
- `docs/forge/00-orchestrator-status.md` not edited
- No NgRx; no sidebar/route edits; no Activities API
- Proxy still gateway `:8080`
- No FIX-\* source, no WARN-FE lockfile, no BE work

## GitNexus (pre-commit)

See commit-time `detect-changes --scope all` output in the PR body / below. Docs-only; expect low risk, no `partial`/`truncated` (re-run if either flag appears).
