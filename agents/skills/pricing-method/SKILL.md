---
name: pricing-method
description: Map and execute the correct pricing method (block, percent-of-total, cost-plus, formula) for each product on a quote.
---

# Pricing Method

Use this skill when you need to determine which pricing method applies to a product and execute the corresponding price calculation logic.

## Scope

- Identify the pricing method assigned to each product (block, percent-of-total, cost-plus, formula).
- Execute the pricing engine with the correct method-specific parameters.
- Return calculated prices with a breakdown showing how the method was applied.
- Do not override the product's assigned pricing method without explicit instruction.

## Inputs

- Product ID(s) to price
- Quote ID (for percent-of-total context)
- Cost data (for cost-plus method, if not retrieved automatically)

## Workflow

1. Retrieve product records to determine the assigned pricing method for each product.
2. Gather method-specific inputs: block tiers, base totals, cost figures, or formula variables.
3. Execute the pricing engine with the appropriate method context.
4. Parse the engine response to extract the calculated price and method breakdown.
5. Return per-product results showing the method used, input values, and final calculated price.

## Minimum Checks

- Each product's pricing method is correctly identified from the product record.
- Method-specific inputs are complete before execution (e.g., cost data for cost-plus).
- The pricing engine response is successfully parsed with no calculation errors.
- The breakdown clearly shows how the final price was derived from the method.

## Useful Commands

```bash
curl -s GET /api/v1/products/{productId}
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","pricingMethod":"block","products":[...]}'
```
