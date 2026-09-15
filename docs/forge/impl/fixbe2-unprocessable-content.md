---
status: green
id: fixbe2-unprocessable-content
wave: FIX-BE-2
pr: FIX-BE-2
---

# fixbe2-unprocessable-content

## Summary

FIX-BE-2 (BE constant rename): replace deprecated Spring Framework 7 `HttpStatus.UNPROCESSABLE_ENTITY` with `HttpStatus.UNPROCESSABLE_CONTENT` (RFC 9110) in the six exclusive handler/test files catalogued by WARN-BE. HTTP status **422 is unchanged**. Dual-read stays `${APPROVALS_LEGACY_LOCAL_READ:true}`. JaCoCo line **0.80** not lowered. JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27). No handler logic changes. No dual-read YAML. Did **not** edit `docs/forge/00-orchestrator-status.md`. Did **not** steal w8 `arch-pi`. Did **not** implement FIX-BE-1 / FIX-BE-3 / FIX-BE-4.

**RED:** `rg UNPROCESSABLE_ENTITY` in exclusive files hit all six (crm / quote / pricing handler + pricing test / pricebook / product).

**GREEN:** `rg UNPROCESSABLE_ENTITY` empty in exclusive files; pricing tests still assert **422** (`getStatusCode().value() == 422` plus `HttpStatus.UNPROCESSABLE_CONTENT`).

## GitNexus

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-be2` (`fix/forge-fixbe2-unprocessable-content`)  
Index: `04909db`, **36 commits behind HEAD**. Plan 38: skip analyze. UNKNOWN ≠ unused.

`impact("GlobalExceptionHandler", direction=upstream)` is **ambiguous** (9 handler classes). Every candidate: **0 callers**, **risk UNKNOWN** (`riskNote`: Spring `@RestControllerAdvice` dispatch is not a Java caller edge). Confirmed with `rg UNPROCESSABLE_ENTITY` — used at HTTP 422 mapping sites. After `rg`: **constant rename, same 422**; risk is **not HIGH/CRITICAL**.

```bash
node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs impact "GlobalExceptionHandler" --direction upstream --repo /home/username01/Projects/open-crm-cpq
rg -n 'UNPROCESSABLE_ENTITY' backend/services/{crm,quote,pricing,pricebook,product}-service
```

## Toolchain

```text
JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

## Files

| Path | Change |
|------|--------|
| `backend/services/crm-service/src/main/java/com/opencrm/crm/config/GlobalExceptionHandler.java` | `UNPROCESSABLE_ENTITY` → `UNPROCESSABLE_CONTENT` (2 sites) |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/config/GlobalExceptionHandler.java` | same (2 sites) |
| `backend/services/pricing-service/src/main/java/com/opencrm/pricing/config/GlobalExceptionHandler.java` | same (2 sites) |
| `backend/services/pricing-service/src/test/java/com/opencrm/pricing/config/GlobalExceptionHandlerTest.java` | same + explicit `.value() == 422` |
| `backend/services/pricebook-service/src/main/java/com/opencrm/pricebook/config/GlobalExceptionHandler.java` | same (2 sites) |
| `backend/services/product-service/src/main/java/com/opencrm/product/config/GlobalExceptionHandler.java` | same (2 sites) |
| `docs/forge/impl/fixbe2-unprocessable-content.md` | This note |

## Dual-read / holds

```yaml
# backend/services/quote-service/src/main/resources/application.yml:89
legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}
```

YAML/env **untouched**. Default still **true**. Jacoco `<minimum>0.80</minimum>` **untouched** in crm + pricing poms.

- NgRx **held**; Activities API **not invented**; CI `#238` / Kafka `#241` **not started**
- **R2** price-book list-row delete **held**; subscriptions FE **held**; GLM-5 **not runtime**
- Boot **4.0.6**; JAVA_HOME **25.0.4.1 LTS** — never 27
- Did **not** edit `docs/forge/00-orchestrator-status.md`
- Did **not** steal FE panes or w8 `arch-pi`

## TDD

RED: exclusive-file `rg UNPROCESSABLE_ENTITY` — 12 handler sites + 2 test asserts.

GREEN: exclusive-file `rg UNPROCESSABLE_ENTITY` empty; tests still assert 422:

```bash
rg -n 'UNPROCESSABLE_ENTITY' \
  backend/services/crm-service/src/main/java/com/opencrm/crm/config/GlobalExceptionHandler.java \
  backend/services/quote-service/src/main/java/com/opencrm/quote/config/GlobalExceptionHandler.java \
  backend/services/pricing-service/src/main/java/com/opencrm/pricing/config/GlobalExceptionHandler.java \
  backend/services/pricing-service/src/test/java/com/opencrm/pricing/config/GlobalExceptionHandlerTest.java \
  backend/services/pricebook-service/src/main/java/com/opencrm/pricebook/config/GlobalExceptionHandler.java \
  backend/services/product-service/src/main/java/com/opencrm/product/config/GlobalExceptionHandler.java
# (empty)

rg -n '422' backend/services/pricing-service/src/test/java/com/opencrm/pricing/config/GlobalExceptionHandlerTest.java
# validation_returns422 / constraintViolation_returns422 + .value() == 422
```

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && newgrp docker <<'EOF'
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -B -pl services/crm-service,services/quote-service,services/pricing-service,services/pricebook-service,services/product-service -am test
EOF
```

Result (2026-09-15T09:14:46-05:00): **BUILD SUCCESS**. Total **09:29 min**. Temurin **25.0.4.1 LTS** via `newgrp docker`. Exclusive handler compiles no longer emit javac “uses or overrides a deprecated API” for `UNPROCESSABLE_ENTITY`. Remaining INFO (out of scope): `OutboxRelayPublisherTest` unchecked (FIX-BE-3), `QuoteServiceSubmitForReviewTest` unchecked (FIX-BE-4), `PricingExecuteGoldenFixtureTest` deprecated API (not exclusive).

| Module | Tests run | Failures | Errors | Skipped | Status | Wall |
|--------|----------:|---------:|-------:|--------:|--------|------|
| Open CRM Backend (pom) | — | — | — | — | SUCCESS | 0.019 s |
| Common Library | 86 | 0 | 0 | 0 | SUCCESS | 01:33 min |
| Security Library | 13 | 0 | 0 | 0 | SUCCESS | 17.903 s |
| Rules Engine Library | 56 | 0 | 0 | 0 | SUCCESS | 54.603 s |
| CRM Service | 202 | 0 | 0 | 0 | SUCCESS | 03:40 min |
| Pricing Service | 101 | 0 | 0 | 0 | SUCCESS | 01:33 min |
| Price Book Service | 7 | 0 | 0 | 0 | SUCCESS | 20.458 s |
| Product Service | 28 | 0 | 0 | 0 | SUCCESS | 30.019 s |
| Quote Service | 31 | 0 | 0 | 0 | SUCCESS | 33.488 s |

Pricing `GlobalExceptionHandler` tests: **7** run, 0 fail (includes `validation_returns422` and `constraintViolation_returns422`).

### detect-changes (pre-commit)

`node /home/username01/Projects/open-crm-cpq/.gitnexus/run.cjs detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq` from this worktree: **6 files / 15 symbols / 0 processes / risk LOW**. No `partial`/`truncated`. Checkout confirmed: this worktree. Symbols = five `GlobalExceptionHandler` classes + `handleValidationException` / `handleConstraintViolationException` on each. Impl note is docs-only (no indexed overlap). After `rg`: constant rename, HTTP **422** unchanged — **not HIGH/CRITICAL**.

## Acceptance

- [x] Only `HttpStatus.UNPROCESSABLE_ENTITY` → `HttpStatus.UNPROCESSABLE_CONTENT`
- [x] HTTP 422 unchanged (test `.value() == 422`)
- [x] Dual-read still `${APPROVALS_LEGACY_LOCAL_READ:true}`
- [x] Jacoco **0.80** not lowered
- [x] JAVA_HOME **Temurin 25.0.4.1 LTS** (never 27)
- [x] Exclusive files only (six Java + this impl note)
- [x] No dual-read YAML; no other handler logic; no FIX-BE-1/3/4
- [x] Maven `-am test` BUILD SUCCESS on the five services

## Out of scope

- FIX-BE-1 `MappingJackson2HttpMessageConverter`
- FIX-BE-3 `OutboxRelayPublisherTest` unchecked
- FIX-BE-4 `QuoteServiceSubmitForReviewTest` unchecked
- Dual-read flip; Activities; NgRx; R2; `--add-opens`; `-Werror`; `-nowarn`
