---
status: done
id: fix-uat1-product-dev-security
wave: test-38
pr: FIX-UAT1-PRODUCT-NO-DEV-SECURITY
---

# fix-uat1-product-dev-security

## Summary

UAT-1 #531: product-service `SecurityConfig` required `authenticated()` on `/api/v1/**` with **no** `@Profile("dev")` permitAll (CRM/gateway have it). Catalog list was **403** through gateway even though product health was UP. Same pattern on quote-service — folded here because it is the same finding and disjoint from FIX-UAT1-QUOTE-BOOT.

GREEN: CRM-style `@Profile("dev")` `permitAll` + `@Profile("!dev")` still `authenticated()`. CSRF stays `disable()` at the domain (ADR-004). Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched.

Tip: `origin/main` `bf54345e`. Branch `fix/forge-uat1-product-dev-security`. Worktree `/home/username01/Projects/open-crm-cpq/.worktrees/fix-uat-prodsec`.

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-uat-prodsec`
Index: `04909db`, **36 commits behind HEAD** (plan 38: skip analyze). UNKNOWN ≠ unused.

| Target | Direction | Risk | Notes |
|--------|-----------|------|-------|
| product `SecurityConfig` (Class) | upstream | **UNKNOWN** | 0 callers; Spring `@Configuration` bean wiring |
| quote `SecurityConfig` (Class) | upstream | **UNKNOWN** | same |
| `securityFilterChain` (both files) | upstream | **UNKNOWN** | Method + spring-bean CodeElement; no CALLS edges |

**rg confirmation (UNKNOWN ≠ unused):** no Java imports of these classes; `securityFilterChain` is only the `@Bean` method. Runtime surface is the servlet filter chain on every HTTP request:

- `ProductController` `@RequestMapping("/api/v1/products")`
- `QuoteController` `@RequestMapping("/api/v1/quotes")`
- Gateway `Path=/api/v1/products/**` → product-service
- FE `frontend/libs/cpq/catalog` `GET /api/v1/products`

**Architecture risk: HIGH** (auth path). Authorized by UAT-1 finding + this FIX exclusive-file list. Did **not** weaken `!dev`. Did not flip dual-read. Did not spawn arch-pi (w8).

## TDD

**RED** (tests only, pre-fix): `SecurityConfigDevProfileTest.unauthenticatedCatalogListIsPermitted` — `Status expected:<200> but was:<403>`. `!dev` already forbidden (pass). Evidence: UAT-1 `GET /api/v1/products` 403 via `:4200` proxy + gateway `X-RateLimit-*`.

**GREEN** after CRM-style profile split:

| Slice | Result |
|-------|--------|
| product `SecurityConfigDevProfileTest` + `SecurityConfigNonDevProfileTest` | 2/2 |
| quote `SecurityConfigDevProfileTest` + `SecurityConfigNonDevProfileTest` | 2/2 |
| `./mvnw -B -pl services/product-service,services/quote-service -am test` JAVA_HOME Temurin **25.0.4.1 LTS** | **BUILD SUCCESS** — product **30**, quote **33** |

## Files

| Path | Change |
|------|--------|
| `backend/services/product-service/.../SecurityConfig.java` | `@Profile("dev")` `devSecurityFilterChain` permitAll; `@Profile("!dev")` existing authenticated chain; CSRF still disabled |
| `backend/services/quote-service/.../SecurityConfig.java` | Same split |
| `backend/services/product-service/src/test/.../SecurityConfigDevProfileTest.java` | Unauthenticated `GET /api/v1/products` is 200 under `dev` |
| `backend/services/product-service/src/test/.../SecurityConfigNonDevProfileTest.java` | Unauthenticated catalog GET is 403 under `test` (`!dev`) |
| `backend/services/quote-service/src/test/.../SecurityConfig*ProfileTest.java` | Same for `GET /api/v1/quotes` |
| `docs/forge/impl/fix-uat1-product-dev-security.md` | This note |

Not touched (other FIX-* exclusive files): `PricingServiceClient` / `RestClientConfig`, `GlobalExceptionHandler`, dual-read YAML, NgRx, `docs/forge/00-orchestrator-status.md`.

## GitNexus (pre-commit)

`detect-changes --scope all -r /home/username01/Projects/open-crm-cpq` from this worktree:

```text
Changes: 2 files, 6 symbols
Affected processes: 0
Risk level: low
Changed symbols: product + quote SecurityConfig class / securityFilterChain method + spring-bean
```

No `partial` / `truncated`. New `devSecurityFilterChain` beans and test files are not in the stale index (36 behind) — expected. `git status` exclusive files only (two `SecurityConfig.java`, four `*SecurityConfig*Test.java`, this note). Auth-path risk remains **HIGH** despite detect-changes `low` (index has no CALLS edges; rg-confirmed HTTP surfaces above).

## Holds respected

- Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched
- `!dev` / production still `authenticated()` on `/api/v1/**`
- CSRF disabled at domain (ADR-004)
- JAVA_HOME Temurin 25.0.4.1 LTS (never 27)
- No NgRx; no Activities API
