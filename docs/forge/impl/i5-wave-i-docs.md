---
status: done
id: i5-wave-i-docs
---

# i5-wave-i-docs

## Summary

Wave I forge closeout. I1–I4 landed on main. Bolt #412 superseded by I3 (close PR). Holds unchanged.

## Landed

| ID | Result |
|----|--------|
| I1 | ADR + matrix S2B-11 hybrid **LANDED**; product validate retained, **no removal date** |
| I2 | AI `executePricing` / `getQuoteLineItems` unwrap `ApiResponse`; String + Jackson 2 parse (Jackson 3 cannot bind `JsonNode`) |
| I3 | Product `validateConfiguration` flat O(rules) — Bolt #412 salvage |
| I4 | FE CPQ HTTP-backed comments; MOCK_* off barrels; disabled CTA titles |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Bolt #430 WhatsApp; full SpEL; Kafka S2B-9
- Quote `PricingServiceClient` JsonNode rewrite → **Wave J1** (same Jackson 3 bind bug)

## Next

Wave J: `docs/forge/19-wave-j-architecture.md`
