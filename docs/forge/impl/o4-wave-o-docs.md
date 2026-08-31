---
status: done
id: o4-wave-o-docs
---

# o4-wave-o-docs

## Summary

Wave O forge closeout. Dual-read default still `true`. Product-service validate still retained. WhatsApp #430 not undone. O1 owned shell (`app.routes`, sidebar Rule sets, `tsconfig` path, `test:cpq`). O3 is DTO+list only — no subscriptions FE.

## Landed

| ID | Result |
|----|--------|
| O1 | `@frontend/cpq-rule-sets` list/detail/create; Activate/Deprecate ConfirmDialog; nested rules + DRL `validateSyntax` on `/api/v1/rule-sets` + `/api/v1/rules` |
| O2 | Catalog-detail option groups (`/api/v1/product-options`) + attribute values (`/api/v1/product-attributes`); no shell |
| O3 | `SubscriptionDto` + MapStruct; paginated `GET /api/v1/subscriptions`; activate still `ErpAdapter`; no FE; ADR-006 remains **Proposed** |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store; product bundles portal

## Matrix addendum

- §5: O1 FE notes on activate/deprecate
- §7a: O2 FE notes on product-attributes list/values
- Dual-read flip hold text: Waves **J–O** (was J–N)
