---
status: done
id: i2-ai-jsonnode-remainder
---

# i2-ai-jsonnode-remainder

## Summary

Wave I2: `CpqAgentToolClients.executePricing` and `getQuoteLineItems` unwrap gateway `ApiResponse` and expose pricing `success`/`lineResults` and the line-item array on tool `data`. Bodies are read as `String` then parsed with Jackson 2 `ObjectMapper` — Spring RestClient Jackson 3 converters cannot bind `com.fasterxml.jackson.databind.JsonNode`. No pricing-service or quote-service Maven deps. Validate path unchanged (H2).

## Acceptance

- [x] Pricing fixture asserts `success` + `lineResults`
- [x] Line-items fixture asserts array + `quantity`/`productExternalId`
- [x] Validate path left as H2
