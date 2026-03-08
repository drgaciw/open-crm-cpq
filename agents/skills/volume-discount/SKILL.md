---
name: volume-discount
description: Apply tiered volume discounts using all-units or graduated pricing models based on quantity thresholds.
---

# Volume Discount

Use this skill when a quote line item qualifies for volume-based discount tiers and you need to calculate the correct discounted price using all-units or graduated models.

## Scope

- Determine which volume discount schedule applies to a product and customer combination.
- Calculate discounts using all-units (single tier applies to all) or graduated (each tier applies to its range) models.
- Enforce tier boundaries and ensure quantity thresholds are correctly evaluated.
- Do not modify volume discount schedule definitions.

## Inputs

- Quote ID and line-item details (product, quantity)
- Customer segment (for segment-specific volume schedules)
- Pricing method context (to layer volume discounts correctly)

## Workflow

1. Identify the applicable volume discount schedule for the product and customer segment.
2. Determine the quantity and map it to the correct discount tier(s).
3. Calculate the discount using the appropriate model (all-units or graduated).
4. Execute the pricing engine to apply the volume discount (volume-discount-named-seat.drl, salience 620).
5. Return the discount breakdown showing tiers applied, quantities per tier, and final unit price.

## Minimum Checks

- The correct volume discount schedule is resolved for the product-customer combination.
- Quantity-to-tier mapping is accurate with no gaps or overlaps.
- All-units vs. graduated model is correctly applied per the schedule definition.
- The final discounted price does not fall below any applicable price floor.

## Useful Commands

```bash
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","rules":["volume-discount-named-seat"],"products":[...]}'
```
