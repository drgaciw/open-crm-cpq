---
status: done
id: m4-wave-m-docs
---

# m4-wave-m-docs

## Summary

Wave M forge closeout. Dual-read default still `true`. Product-service validate still retained. WhatsApp #430 not undone. Merge gate honored: M3 landed before M1.

## Landed

| ID | Result |
|----|--------|
| M3 | `QuoteService.clone` copies commercial line snapshot; totals like line-item rollup; **not** `Quote.recalculateTotals()` |
| M1 | Quote header `PUT` form on DRAFT\|REJECTED; clone copy says header+lines copied; Calculate still required for waterfall/ARR |
| M2 | Price-book entry create/update/delete on `/api/v1/price-books/{id}/entries`; published books immutable |

## Holds (unchanged)

- Activities API, CI #238, agent Kafka #241
- Delete product-service validate; gateway domain validation
- Flip `approvals.legacy-local-read-enabled` default → false (**HIGH**)
- Full SpEL; Kafka S2B-9; contract-asset FE; NgRx CPQ store
