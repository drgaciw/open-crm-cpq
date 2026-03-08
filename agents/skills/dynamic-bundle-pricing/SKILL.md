---
name: dynamic-bundle-pricing
description: Bundle discount calculation, component price adjustment, and bundle-vs-individual price comparison.
---

# Dynamic Bundle Pricing

Use this skill when the user needs to calculate bundle pricing, apply component-level discounts within a bundle, or compare bundle pricing against individual product pricing.

## Scope

- Calculate total bundle price with component-level discount rules
- Apply dynamic discounts based on bundle composition and size
- Compare bundle price against sum of individual component prices
- Retrieve bundle definitions and component membership

## Inputs

- Bundle ID or bundle product identifier
- Component product IDs and quantities
- Pricing context (currency, customer segment, effective date)
- Discount override (optional, for what-if analysis)

## Workflow

1. Retrieve the bundle definition and its component list.
2. Resolve individual list prices for each component product.
3. Execute the pricing engine with the bundle context to apply bundle discount rules.
4. Calculate the bundle total and the individual sum for comparison.
5. Return the bundle price, savings amount, savings percentage, and per-component breakdown.

## Minimum Checks

- Bundle ID resolves to a valid, ACTIVE product bundle
- All component products exist and are in ACTIVE status
- Bundle discount does not produce a negative line price
- Savings percentage is calculated correctly (individual sum minus bundle price, divided by individual sum)
- Pricing engine returns a successful execution status

## Useful Commands

```bash
# Execute bundle pricing
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"bundleId":"bundle-001","components":[{"productId":"prod-a","quantity":1},{"productId":"prod-b","quantity":2}],"currency":"USD"}'

# Retrieve bundle definition
curl -s GET '/api/v1/product-bundles/{id}'
```
