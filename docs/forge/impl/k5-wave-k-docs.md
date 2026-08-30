---
status: done
id: k5-wave-k-docs
---

# k5-wave-k-docs

## Summary

Wave K forge closeout. Dual-read default still `true`. Product-service validate still retained.

## Landed

| ID | Result |
|----|--------|
| K1 | Unused `ProductServiceClient` JsonNode getters deleted; `validateConfiguration` kept; no live `body(JsonNode.class)` in backend |
| K2 | Quote detail Approve/Reject/Send/Accept + line list/edit/remove |
| K3 | Catalog detail Edit/Save via `ProductService.update` |
| K4 | Approval inbox vs quote BFF `/api/v1/quotes/approvals/**` |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; docgen FE; NgRx CPQ store
