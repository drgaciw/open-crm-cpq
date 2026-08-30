---
name: list-price-application
description: Resolve the correct price book, look up list prices for products, and enforce price floor constraints.
---

# List Price Application

Use this skill when you need to determine the list price for one or more products by resolving the applicable price book and enforcing floor price rules.

## Scope

- Resolve which price book applies based on customer segment, currency, and effective date.
- Look up list prices for specified products within the resolved price book.
- Enforce floor price constraints; flag any price that would fall below the floor.
- Do not apply discounts or promotional adjustments at this stage.

## Inputs

- Product ID(s) for price lookup
- Customer segment or pricing-customer ID (for price book resolution)
- Currency code and effective date (optional, defaults to today)

## Workflow

1. Retrieve available price books and determine which one applies to the customer segment and currency.
2. Look up the list price for each requested product in the resolved price book.
3. Execute the pricing engine to confirm list prices and retrieve floor values.
4. Compare each list price against its floor; flag any violations.
5. Return the list price, floor price, and price book metadata for each product.

## Minimum Checks

- The correct price book is resolved for the customer's segment and currency.
- Every requested product has a list price returned (or an explicit "not found" status).
- Floor price enforcement is applied; violations are clearly flagged.
- Price book effective dates are validated against the requested date.

## Useful Commands

```bash
curl -s GET /api/v1/price-books
curl -s GET /api/v1/price-books/{priceBookId}/entries
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","products":[...]}'
```
