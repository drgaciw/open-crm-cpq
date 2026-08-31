---
status: done
id: n4-wave-n-docs
---

# n4-wave-n-docs

## Summary

Wave N forge closeout. Dual-read default still `true`. Product-service validate still retained. WhatsApp #430 not undone. N2 owned shell (`app.routes`, sidebar, `tsconfig` path, `test:cpq`).

## Landed

| ID | Result |
|----|--------|
| N1 | Price-book header Edit/Save + ConfirmDialog delete on unpublished books; M2 entry CRUD kept |
| N2 | `@frontend/cpq-pricing-customers` list/detail/CRUD on `/api/v1/pricing-customers`; route `pricing-customers` |
| N3 | Quote detail ConfirmDialog soft-delete; ACCEPTED disabled (`QUOTE_ACCEPTED_NO_DELETE`) |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store; rule-set admin; product bundles
