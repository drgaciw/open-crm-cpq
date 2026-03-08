---
name: customer-segment-discount
description: Apply segment-specific pricing rules, combine discount multipliers, and enforce discount caps based on customer classification.
---

# Customer Segment Discount

Use this skill when pricing needs to reflect the customer's segment classification, applying segment-specific discount multipliers and enforcing cap rules.

## Scope

- Retrieve the customer's segment classification and associated discount rules.
- Apply segment-specific multipliers to the base or list price.
- Combine multiple discount multipliers in the correct precedence order.
- Enforce maximum discount caps defined per segment; do not exceed them.

## Inputs

- Pricing-customer ID (to retrieve segment and discount entitlements)
- Quote ID and line-item details (products and current prices)
- Any previously applied discounts (to correctly stack multipliers)

## Workflow

1. Retrieve the customer's pricing profile including segment, tier, and discount entitlements.
2. Identify all segment-specific discount rules that apply to the quoted products.
3. Calculate the combined discount by multiplying applicable multipliers in precedence order.
4. Check the combined discount against the segment's maximum discount cap.
5. Execute the pricing engine to apply the capped segment discount.
6. Return the discount breakdown showing each multiplier, the combined rate, and any cap adjustment.

## Minimum Checks

- Customer segment is correctly identified from the pricing-customer record.
- Multiplier combination follows the defined precedence order.
- The final discount does not exceed the segment's cap.
- Segment discount is applied after list price but before deal-specific adjustments.

## Useful Commands

```bash
curl -s GET /api/v1/pricing-customers/{customerId}
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","segmentId":"...","products":[...]}'
```
