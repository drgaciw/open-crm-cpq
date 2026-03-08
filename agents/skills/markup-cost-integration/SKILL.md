---
name: markup-cost-integration
description: Retrieve ERP cost data, calculate margins and markups, and apply vendor rebate adjustments for accurate cost-based pricing.
---

# Markup & Cost Integration

Use this skill when you need to pull cost data from the ERP system, calculate margins or markups, or factor in vendor rebates for pricing decisions.

## Scope

- Retrieve current cost data from the ERP system for one or more products.
- Calculate gross margin, markup percentage, and net cost after rebates.
- Surface vendor rebate eligibility and apply rebate adjustments to cost basis.
- Do not modify ERP cost records; treat them as read-only inputs.

## Inputs

- Product external ID(s) for ERP cost lookup
- Vendor rebate context (optional, if specific rebate programs apply)
- Target margin or markup percentage (optional, for margin analysis)

## Workflow

1. Retrieve cost data from the ERP system using the product external ID (single or bulk).
2. Identify any applicable vendor rebate programs and calculate the adjusted cost.
3. Compute gross margin and markup percentages against the current or proposed selling price.
4. If a target margin is specified, calculate the minimum selling price required to meet it.
5. Return a cost breakdown including raw cost, rebates applied, net cost, margin, and markup.

## Minimum Checks

- ERP cost data is successfully retrieved for all requested products.
- Margin and markup calculations are mathematically consistent with the cost and price data.
- Vendor rebates are applied only when the product and deal meet eligibility criteria.
- Cost data staleness is flagged if the ERP record's last-updated date exceeds a threshold.

## Useful Commands

```bash
curl -s GET /api/v1/erp/costs/{productExternalId}
curl -s POST /api/v1/erp/costs/bulk -d '{"externalIds":["id1","id2"]}'
```
