---
status: done
id: c2-cpq-data-access
---

# c2-cpq-data-access

## Summary
Angular HttpClient services for CPQ catalog products, quotes, and pricing (customers, price books, rules, execute) targeting gateway `/api` paths. Exported from respective lib `index.ts`.

## Files
- `frontend/libs/cpq/catalog/src/lib/product.service.ts`
- `frontend/libs/cpq/quotes/src/lib/quote.service.ts`
- `frontend/libs/cpq/pricing-data-access/src/lib/*Service.ts` (price-book, pricing-customer, pricing-execution, rule, rule-set)
- index exports updated

## Notes
HttpClientTesting specs can follow in a fast-follow if not included in this commit.
