---
name: usage-based-pricing
description: Overage calculation, prepaid credit tracking, and committed spend floor for usage-metered products.
---

# Usage-Based Pricing

Use this skill when the user needs to calculate overage charges for metered usage, track prepaid credit consumption, or enforce committed spend floors on usage-based products.

## Scope

- Calculate overage charges when usage exceeds prepaid or committed tiers
- Track prepaid credit balances and consumption rates
- Enforce committed spend floor (minimum charge regardless of actual usage)
- Apply the usage-overage-charge.drl rule (salience 610) for overage logic

## Inputs

- Product ID (type: SAAS_USAGE_METERED)
- Actual usage quantity for the billing period
- Prepaid credit balance or committed usage tier
- Overage unit rate
- Billing period start and end dates

## Workflow

1. Retrieve the product definition and confirm it is type SAAS_USAGE_METERED.
2. Determine the prepaid or committed usage allowance for the billing period.
3. Compare actual usage against the allowance to identify overage units.
4. Execute the pricing engine, which triggers usage-overage-charge.drl (salience 610).
5. Calculate the overage charge (overage units multiplied by the overage rate).
6. Apply the committed spend floor: if total charges fall below the floor, charge the floor amount.
7. Return the breakdown: base charge, usage consumed, overage units, overage charge, and total.

## Minimum Checks

- Product type is SAAS_USAGE_METERED
- Actual usage quantity is a non-negative number
- Overage rate is defined and positive
- Committed spend floor is enforced when total charges are below the minimum
- The usage-overage-charge.drl rule (salience 610) fires during pricing execution
- Prepaid credit balance is not negative after consumption

## Useful Commands

```bash
# Execute usage-based pricing
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-usage-001","type":"SAAS_USAGE_METERED","actualUsage":12000,"prepaidAllowance":10000,"overageRate":0.05,"committedSpendFloor":500.00}'
```
