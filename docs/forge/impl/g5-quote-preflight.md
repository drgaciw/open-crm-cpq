---
status: done
id: g5-quote-preflight
---

# g5-quote-preflight

## Summary

S2D-3a quote configuration preflight: `submitForReview` calls gateway `POST /api/v1/configuration/validate` (Wave F shim → configuration-engine evaluate), parses structured `ConfigurationValidationResult` (`valid` + `violations[]`), and blocks with `BusinessException` when `valid=false`. Validate RestClient is retargeted to `services.gateway.url` (default `http://localhost:8080`), **not** product `:8083`. No inline REQUIRES/EXCLUDES in quote-service. `ApprovalService` untouched (G4).

## Changes

| Area | Detail |
|------|--------|
| `application.yml` / test yml | `services.gateway.url: ${GATEWAY_SERVICE_URL:http://localhost:8080}` |
| `RestClientConfig` | New `gatewayRestClient` bean; product RestClient retained for product GETs |
| `ProductServiceClient` | `validateConfiguration` uses `gatewayRestClient`; returns `ConfigurationValidationResult` from `ApiResponse` |
| `QuoteService.submitForReview` | CONFIGURATION_ONLY preflight on numeric line `productExternalId`s before status → `IN_REVIEW` |
| Tests | `ProductServiceClientTest` (MockRestServiceServer); `QuoteServiceSubmitForReviewTest` (Mockito) |

## Acceptance

- [x] `validateConfiguration` has domain caller (`QuoteService.submitForReview`)
- [x] Structured `valid` honored; `valid=false` → `CONFIGURATION_PREFLIGHT_FAILED`
- [x] Validate HTTP base URL is gateway (`:8080`), not product (`:8083`)
- [x] No inline rule engine / REQUIRES/EXCLUDES in quote-service
- [x] `ApprovalService` not edited
- [x] JDK 21 / MockRestServiceServer + unit tests

## Scope notes

- **CONFIGURATION_ONLY:** line product IDs only; `productExternalId` must parse as `Long`.
- **FULL_PREFLIGHT / ERP inventory:** out of scope (remain 501 stubs).
- Product-service `POST /api/v1/configuration/validate` retained elsewhere (Wave F).

## Risk note

GitNexus: quote `ProductServiceClient` / `validateConfiguration` **UNKNOWN** (0 graph callers) — confirmed dead via `rg` before wire-up. `submitForReview` **LOW** (controller only). Wave product risk **MED** (submit now fails closed on invalid config). Index was stale at edit time; text confirmation used.
