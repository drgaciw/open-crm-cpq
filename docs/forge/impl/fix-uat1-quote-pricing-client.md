---
status: done
id: fix-uat1-quote-pricing-client
wave: test-38
pr: FIX-UAT1-QUOTE-BOOT-PRICING-CLIENT
---

# fix-uat1-quote-pricing-client

## Summary

UAT-1 #531: quote-service failed to boot locally (`BeanCreationException` `pricingServiceClient` / `PricingServiceClient`: **No default constructor found**). Quote never bound `:8084`; gateway `GET /api/v1/quotes` → 500.

Root cause: two constructors (public `RestClient`+`ObjectMapper` and package-private `RestClient`). Spring will not choose among multiple constructors unless one is `@Autowired`; it then requires a no-arg default and fails. Sibling clients (`ProductServiceClient`, `ApprovalRoutingClient`) already have a single `@Qualifier` constructor.

Fix: one injection constructor `@Qualifier("pricingRestClient") RestClient`; Jackson 2 `ObjectMapper` constructed internally (J1 parse path). `RestClientConfig` bean names locked to the qualifier strings.

Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched. No SecurityConfig, no QuoteServiceSubmitForReviewTest, no GlobalExceptionHandler, no NgRx, no `00-orchestrator-status.md`. Jacoco floor 0.80 not lowered. JAVA_HOME Temurin 25.0.4.1 LTS.

## Exclusive files

| Path | Change |
|------|--------|
| `backend/services/quote-service/src/main/java/com/opencrm/quote/client/PricingServiceClient.java` | Single `@Qualifier("pricingRestClient")` constructor; `new ObjectMapper()` internally |
| `backend/services/quote-service/src/main/java/com/opencrm/quote/config/RestClientConfig.java` | Explicit `@Bean("pricingRestClient")` / `gatewayRestClient` / `approvalRoutingRestClient` |
| `backend/services/quote-service/src/test/java/com/opencrm/quote/client/PricingServiceClientTest.java` | Constructor-unambiguous unit (declared ctors; package-private counts) |
| `backend/services/quote-service/src/test/java/com/opencrm/quote/client/PricingServiceClientContextTest.java` | Slice context: three named RestClient beans + `PricingServiceClient` |
| `docs/forge/impl/fix-uat1-quote-pricing-client.md` | This note |

## GitNexus (pre-edit)

Repository: `open-crm-cpq` (`/home/username01/Projects/open-crm-cpq`)  
Worktree: `/home/username01/Projects/open-crm-cpq/.worktrees/fix-uat-quote`  
Index: 36 commits behind HEAD (plan 38: skip analyze). UNKNOWN ≠ unused — confirmed with `rg`.

| Symbol | Risk | Upstream / note |
|--------|------|-----------------|
| `PricingServiceClient` | **LOW** | d=1 `QuoteService.java` IMPORTS (field via `@RequiredArgsConstructor`). Constructor wiring only; no `QuoteService` signature change. |
| `pricingRestClient` | **UNKNOWN** | 0 graph callers. `rg` confirms `RestClientConfig` `@Bean` + `PricingServiceClient` `@Qualifier("pricingRestClient")` (Spring DI, not a Java call). |
| `RestClientConfig` | **UNKNOWN** | 0 graph callers. `rg` confirms `@Configuration` scanned under `com.opencrm.quote`; beans consumed by `PricingServiceClient`, `ProductServiceClient`, `ApprovalRoutingClient`. Explicit `@Bean("…")` names match existing method names — no qualifier string change. |

Not HIGH/CRITICAL. `QuoteService` (BE class) was not edited.

## TDD

**RED** (`JAVA_HOME=temurin-25.0.4+101.0.LTS`, `./mvnw -B -pl services/quote-service -am test -Dtest=PricingServiceClientTest,PricingServiceClientContextTest -Dsurefire.failIfNoSpecifiedTests=false`):

```
BeanCreationException: Error creating bean with name 'pricingServiceClient':
Failed to instantiate [com.opencrm.quote.client.PricingServiceClient]: No default constructor found
Caused by: java.lang.NoSuchMethodException: com.opencrm.quote.client.PricingServiceClient.<init>()
```

Matches UAT-1. Existing execute/simulate tests still passed (they `new` the package-private ctor).

**GREEN:** same command — Tests run: 5, Failures: 0 (`PricingServiceClientContextTest` 1 + `PricingServiceClientTest` 4 including constructor invariant). Context asserts beans `pricingRestClient`, `gatewayRestClient`, `approvalRoutingRestClient` and `PricingServiceClient`. BUILD SUCCESS. Temurin 25.0.4.1 LTS.

## GitNexus (pre-commit)

`detect-changes --scope all --repo /home/username01/Projects/open-crm-cpq` from this worktree: **3 files, 12 symbols, 0 processes, risk LOW**. No `partial`/`truncated`. Checkout matched (PricingServiceClient, RestClientConfig, PricingServiceClientTest). New untracked test + impl note are not indexed symbols.

## Holds respected

- Dual-read `${APPROVALS_LEGACY_LOCAL_READ:true}` untouched
- `docs/forge/00-orchestrator-status.md` not edited
- No NgRx; no Activities API
- Did not edit `SecurityConfig` (FIX-UAT1-PRODUCT-NO-DEV-SECURITY)
- Did not edit `QuoteServiceSubmitForReviewTest` (FIX-BE-4)
- Did not edit `GlobalExceptionHandler` (FIX-BE-2)
- Jacoco 0.80 not lowered
- JAVA_HOME Temurin 25.0.4.1 LTS (never 27)

## Verify

```bash
export JAVA_HOME=/home/username01/.local/share/mise/installs/java/temurin-25.0.4+101.0.LTS
cd backend && ./mvnw -B -pl services/quote-service -am test -Dtest=PricingServiceClientTest,PricingServiceClientContextTest -Dsurefire.failIfNoSpecifiedTests=false
```
