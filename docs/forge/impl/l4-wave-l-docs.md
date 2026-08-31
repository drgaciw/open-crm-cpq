---
status: done
id: l4-wave-l-docs
---

# l4-wave-l-docs

## Summary

Wave L forge closeout. Dual-read default still `true`. Product-service validate still retained. WhatsApp #430 not undone.

## Landed

| ID | Result |
|----|--------|
| L1 | Quote documents panel (generate/list/download blob PDF) + Expire/Cancel/Clone; clone UX does not claim line-item copy |
| L2 | Catalog detail ConfirmDialog delete → list |
| L3 | Price-books list/detail/create on `/api/v1/price-books` |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store
