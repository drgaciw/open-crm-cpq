---
name: saas-tier-pricing
description: Tier price resolution, billing frequency discounts, and feature gating for SaaS subscription products.
---

# SaaS Tier Pricing

Use this skill when the user needs to resolve pricing for SaaS subscription products, apply billing frequency discounts (monthly vs. annual), or determine feature access by tier.

## Scope

- Resolve unit price based on SaaS product type (SAAS_SEAT_NAMED, SAAS_SEAT_CONCURRENT, SAAS_FLAT_RATE)
- Apply billing frequency discounts (e.g., annual commitment discount)
- Determine feature entitlements and gating per subscription tier
- Calculate total recurring cost for a given seat count and billing period

## Inputs

- Product ID (SaaS product)
- Product type (SAAS_SEAT_NAMED, SAAS_SEAT_CONCURRENT, or SAAS_FLAT_RATE)
- Seat count or user quantity
- Billing frequency (MONTHLY, ANNUAL)
- Subscription tier (e.g., Starter, Professional, Enterprise)

## Workflow

1. Retrieve the product definition and confirm its SaaS product type.
2. Determine the base unit price for the requested tier.
3. Apply billing frequency discount if an annual commitment is selected.
4. For SAAS_SEAT_NAMED or SAAS_SEAT_CONCURRENT, multiply the unit price by the seat count.
5. For SAAS_FLAT_RATE, use the flat tier price regardless of user count.
6. Execute the pricing engine and return the total recurring cost with breakdown.

## Minimum Checks

- Product type is one of SAAS_SEAT_NAMED, SAAS_SEAT_CONCURRENT, or SAAS_FLAT_RATE
- Seat count is a positive integer (ignored for SAAS_FLAT_RATE)
- Billing frequency discount is applied only when the frequency matches the discount rule
- Feature gating aligns with the selected tier (no access to higher-tier features)
- Pricing engine execution returns a successful status

## Useful Commands

```bash
# Execute SaaS tier pricing
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-saas-001","type":"SAAS_SEAT_NAMED","quantity":50,"billingFrequency":"ANNUAL","tier":"Professional"}'

# Retrieve SaaS product details
curl -s GET '/api/v1/products/{id}'
```
