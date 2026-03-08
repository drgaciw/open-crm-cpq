---
name: margin-optimization
description: Performs waterfall analysis, line-level margin optimization, and product substitution to maximize deal profitability.
---

# Margin Optimization

Use this skill when you need to analyze and improve the margin profile of a quote through waterfall analysis, line-item adjustments, or product substitutions.

## Scope

- Perform price waterfall analysis from list price to pocket price
- Optimize margins at the line-item level by identifying low-margin items
- Recommend product substitutions that improve margin while meeting requirements
- Calculate blended margin targets across the entire quote

## Inputs

- Quote ID
- Target margin threshold (optional)
- Product substitution preferences (optional)
- Cost data for line items

## Workflow

1. Retrieve all line items for the quote and fetch current cost data in bulk.
2. Build a price waterfall for each line item (list price, discounts, rebates, cost, pocket margin).
3. Identify line items that fall below the target margin threshold.
4. For below-threshold items, evaluate product substitution options that meet functional requirements at better margins.
5. Generate an optimization plan with specific line-level recommendations.
6. Calculate the projected blended margin if all recommendations are applied.

## Minimum Checks

- Cost data is current and available for all line items
- Waterfall calculations account for all discount and rebate layers
- Product substitutions meet the original functional requirements
- Optimized margin meets or exceeds the target threshold

## Useful Commands

```bash
# Retrieve line items for a quote
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/{id}/line-items" \
  -H "Authorization: Bearer ${TOKEN}"

# Fetch bulk cost data
curl -X POST "${CPQ_BASE_URL}/api/v1/erp/costs/bulk" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productIds": ["PROD-001", "PROD-002", "PROD-003"]}'
```
