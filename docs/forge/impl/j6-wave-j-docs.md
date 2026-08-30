---
status: done
id: j6-wave-j-docs
---

# j6-wave-j-docs

## Summary

Wave J forge closeout. Dual-read default still `true`. Product-service validate still retained. WhatsApp #430 not merged.

## Landed

| ID | Result |
|----|--------|
| J1 | Quote `PricingServiceClient` typed `ApiResponse<PricingExecuteView>` via String + Jackson 2; `QuoteService.calculate` apply helpers use views; never `body(JsonNode.class)` |
| J2 | VALIDATES `attr:key in:v1,v2` dialect; `=` / `!=` unchanged |
| J3 | FE New Quote create + detail Calculate/Submit |
| J4 | FE New Product create dialog (`sku`, `name`, `productType`, `category`) |
| J5 | Dual-read cutover checklist; YAML default remains `${APPROVALS_LEGACY_LOCAL_READ:true}` |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Bolt #430 WhatsApp; full SpEL; Kafka S2B-9
