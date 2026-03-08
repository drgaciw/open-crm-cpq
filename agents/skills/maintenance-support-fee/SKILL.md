---
name: maintenance-support-fee
description: Base maintenance calculation at 22%, support tier pricing, and lapse reinstatement for perpetual license contracts.
---

# Maintenance & Support Fee

Use this skill when the user needs to calculate annual maintenance fees (22% of license cost), determine support tier pricing for SUPPORT_BASIC, SUPPORT_PREMIUM, or SUPPORT_ENTERPRISE, or handle lapsed maintenance reinstatement.

## Scope

- Calculate base annual maintenance fee as 22% of the perpetual license cost
- Apply support tier multipliers for SUPPORT_BASIC, SUPPORT_PREMIUM, and SUPPORT_ENTERPRISE
- Handle lapsed maintenance reinstatement (back-payment of missed years plus penalty)
- Apply the perpetual-maintenance.drl rule (salience 580) for maintenance calculation

## Inputs

- Product ID (perpetual license product)
- Perpetual license fee (base cost for maintenance calculation)
- Support tier (SUPPORT_BASIC, SUPPORT_PREMIUM, or SUPPORT_ENTERPRISE)
- Customer external ID (for contract and lapse history lookup)
- Reinstatement date (if re-activating lapsed maintenance)

## Workflow

1. Retrieve the perpetual license cost from the quote or product pricing.
2. Calculate the base maintenance fee as 22% of the license cost.
3. Apply the support tier multiplier to the base maintenance fee.
4. Execute the pricing engine, which triggers perpetual-maintenance.drl (salience 580).
5. If maintenance has lapsed, look up the contract history to determine lapse duration.
6. Calculate reinstatement charges: back-payment for lapsed years plus any reinstatement penalty.
7. Return the annual maintenance fee, support tier details, and any reinstatement charges.

## Minimum Checks

- Base maintenance rate is exactly 22% of the perpetual license cost
- Support tier is one of SUPPORT_BASIC, SUPPORT_PREMIUM, or SUPPORT_ENTERPRISE
- The perpetual-maintenance.drl rule (salience 580) fires during pricing execution
- Lapse duration is calculated from contract end date to reinstatement date
- Reinstatement penalty is applied per the configured policy
- Customer external ID resolves to a valid contract record

## Useful Commands

```bash
# Execute maintenance and support pricing
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"productId":"prod-onprem-001","licenseCost":50000,"supportTier":"SUPPORT_PREMIUM","customerExternalId":"cust-001"}'

# Look up customer contracts for lapse history
curl -s GET '/api/v1/erp/contracts/{customerExternalId}'
```
