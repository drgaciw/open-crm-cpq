---
name: price-book-management
description: Manages price book hierarchies, maintains price entries, and performs gap analysis to ensure complete product coverage.
---

# Price Book Management

Use this skill when you need to manage price book hierarchies, create or update price book entries, or identify gaps in product pricing coverage.

## Scope

- Manage price book hierarchy (global, regional, customer-specific)
- Create, update, and maintain price book entries
- Perform gap analysis to identify products missing from price books
- Validate price book consistency across hierarchy levels

## Inputs

- Price book ID (for specific book operations)
- Product ID or list (for entry management)
- Price book level (global, regional, customer)
- Region or customer filter (optional)

## Workflow

1. Retrieve the price book hierarchy to understand the inheritance chain (global -> regional -> customer).
2. For entry management, validate the product exists and determine the correct price book level.
3. Create or update price entries with list price, floor price, and effective dates.
4. For gap analysis, compare the product catalog against price book entries to identify uncovered products.
5. Validate that child price books do not violate parent constraints (e.g., floor prices).
6. Report on price book health including coverage percentage, stale entries, and constraint violations.

## Minimum Checks

- Price book exists and is active before modifications
- New entries include list price, floor price, and effective date
- Gap analysis covers the full active product catalog
- Child price book entries respect parent floor prices
- Effective dates do not create overlapping entries for the same product

## Useful Commands

```bash
# Retrieve price books
curl -X GET "${CPQ_BASE_URL}/api/v1/pricing/price-books" \
  -H "Authorization: Bearer ${TOKEN}"

# Add or update a price book entry
curl -X POST "${CPQ_BASE_URL}/api/v1/pricing/price-books/{id}/entries" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001", "listPrice": 1000.00, "floorPrice": 750.00, "effectiveDate": "2026-04-01"}'
```
