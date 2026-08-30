---
status: done
id: j1-quote-pricing-typed
---

# j1-quote-pricing-typed

## Summary

Wave J1: quote-service `PricingServiceClient` execute/simulate returns typed `ApiResponse<PricingExecuteView>` via **String body + Jackson 2 `ObjectMapper`** (never `body(JsonNode.class)` — same I2 Jackson 3 bind failure). `QuoteService.calculate` apply helpers (`fetchPricingData` / `applyPricingToLineItems` / `updateQuoteFromPricingData` / line indexing) consume quote-local views. Preserved `PRICING_SERVICE_UNAVAILABLE`, `PRICING_EXECUTION_FAILED`, `PRICING_RESULT_MISMATCH` and decimal fallbacks. No pricing-service Maven dependency. AI `CpqAgentToolClients`, gateway, and product validate untouched.

## Changes

| Area | Detail |
|------|--------|
| Views | `PricingExecuteView`, `PricingLineResultView`, `PricingFinancialMetricsView` under `com.opencrm.quote.client` |
| Client | `executePricing` / `simulatePricing` → `body(String.class)` then `ObjectMapper.readValue` to `ApiResponse<PricingExecuteView>` |
| Apply | `QuoteService.calculate` helpers refactored off JsonNode walks |
| Tests | `PricingServiceClientTest`, `QuoteServiceCalculateTest` |

## Acceptance

- [x] RestClient no longer binds Jackson 2 `JsonNode` for pricing execute/simulate
- [x] Fixtures/tests assert line apply + financial metrics + waterfall persistence
- [x] `PRICING_*` error codes and decimal fallbacks preserved
- [x] No pricing-service Maven dep; AI clients / gateway / product validate untouched
- [x] JDK 21 unit tests green

## Risk note

GitNexus (index ~51 behind): quote `PricingServiceClient` **LOW** (→ QuoteService); BE `QuoteService` **LOW** (→ ApprovalService file edge). Treat calculate apply as **MED** product risk. ProductServiceClient JsonNode getters remain out of J1 scope.
