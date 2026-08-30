---
status: done
id: e2-align-csrf-disable
---

# e2-align-csrf-disable

## Summary

Aligned `pricing-service` and `pricebook-service` `SecurityConfig` to `csrf.disable()` + `SessionCreationPolicy.STATELESS`, matching CRM/quote/product and Wave E ADR (gateway-edge browser CSRF only). Rejects Jules #402/#404 Cookie CSRF on STATELESS microservices.

Gateway `SecurityConfig` / `CookieServerCsrfTokenRepository` was **not** touched.

## Files

| Path | Change |
|------|--------|
| `backend/services/pricing-service/.../SecurityConfig.java` | Replace `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `csrf.disable()`; CRM-style class Javadoc (gateway JWT + edge CSRF) |
| `backend/services/pricebook-service/.../SecurityConfig.java` | Same CSRF alignment; keep `/internal/**` permitAll; CRM-style Javadoc |
| `docs/forge/impl/e2-align-csrf-disable.md` | This status note |

## Pre-edit gate (UNKNOWN → text inventory)

GitNexus `impact SecurityConfig --direction upstream`: **risk UNKNOWN** (Spring `@Configuration` — 0 resolvable callers). Proceeded only after text confirmation:

- `rg` FE `xsrf` / `csrf` / `withCredentials`: **0 hits** under `frontend/`
- FE `proxy.conf.json` targets gateway `http://localhost:8080` only (no `:8082` / `:8089`)
- CPQ data-access comments state price-books go **via gateway**
- No client sends XSRF headers directly to pricing/pricebook

## Acceptance

- [x] `rg 'CookieCsrfTokenRepository' backend/services --glob '**/SecurityConfig.java'` → **no servlet matches** (gateway uses `CookieServerCsrfTokenRepository` only)
- [x] pricing + pricebook use `csrf.disable()` + `STATELESS`
- [x] Gateway CSRF unchanged
- [x] No FE / proxy changes

## Verify (executed)

```text
rg CookieCsrfTokenRepository backend/services --glob '**/SecurityConfig.java'
  → (empty — gateway-only CookieServerCsrfTokenRepository remains)

JAVA_HOME=.../mise/installs/java/21.0.2
./mvnw -pl services/pricing-service,services/pricebook-service -am test -DskipITs
```
