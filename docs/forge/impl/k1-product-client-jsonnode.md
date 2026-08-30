---
status: done
id: k1-product-client-jsonnode
---

# k1-product-client-jsonnode

## Summary

Wave K1: delete unused quote-service `ProductServiceClient.getProductById` / `getProductBySku` that still bound RestClient responses with `body(JsonNode.class)` (Jackson 3 landmine). Drop orphaned `productRestClient` field/ctor/`@Bean`. Leave `validateConfiguration` (gateway typed path) untouched. YAML `services.product.url` left harmless.

## Changes

| Area | Detail |
|------|--------|
| Client | Removed `getProductById` / `getProductBySku` + `JsonNode` import; ctor now gateway-only |
| Config | Removed `productRestClient` `@Bean` + `services.product.url` `@Value` from `RestClientConfig` |
| Tests | `ProductServiceClientTest` ctor no longer passes product RestClient |

## Acceptance

- [x] `rg 'body\(JsonNode' backend` → empty (except comments)
- [x] `rg getProductById|getProductBySku` → no production callers (methods deleted)
- [x] `validateConfiguration` unchanged; unit tests green
- [x] Orphaned `productRestClient` wiring removed
- [x] JDK 21 quote-service tests green

## Risk note

GitNexus (index ~66 behind): quote `ProductServiceClient` / getters / `productRestClient` **UNKNOWN** (0 upstream + riskNote). Text-confirmed unused via `rg` before delete. `validateConfiguration` callers in `QuoteService` preserved.
