---
name: upgrade-downgrade-path
description: Generates upgrade paths, analyzes downgrade options, calculates migration credits (30%), and provides TCO analysis.
---

# Upgrade Downgrade Path

Use this skill when a customer needs to change their current product tier, and you must generate upgrade or downgrade options with migration credits and total cost of ownership analysis.

## Scope

- Generate upgrade paths from a current product to higher-tier alternatives
- Analyze downgrade options with feature loss impact assessment
- Calculate migration credits at 30% of remaining contract value via migration-credit.drl (salience 560)
- Provide total cost of ownership (TCO) comparison across paths

## Inputs

- Current product ID
- Customer external ID
- Remaining contract term and value
- Desired direction (upgrade or downgrade)
- Feature requirements (optional)

## Workflow

1. Retrieve the current product details and its position in the product hierarchy.
2. Identify available upgrade or downgrade targets based on product family and compatibility.
3. For each target, retrieve pricing via the pricing execution engine.
4. Apply the migration-credit.drl rule (salience 560) to calculate the 30% migration credit on remaining contract value.
5. Build a TCO comparison showing current cost, new cost, migration credit, and net impact over 1, 2, and 3 year horizons.
6. For downgrades, identify features that will be lost and assess business impact.
7. Return ranked path options with TCO analysis and migration credit details.

## Minimum Checks

- Current product exists and has valid upgrade/downgrade targets
- Migration credit is calculated at 30% of remaining contract value
- TCO analysis covers at least the next contract term
- Downgrade paths include feature loss documentation
- Pricing is validated through the pricing execution engine

## Useful Commands

```bash
# Retrieve product details
curl -X GET "${CPQ_BASE_URL}/api/v1/products/{id}" \
  -H "Authorization: Bearer ${TOKEN}"

# Execute pricing for a target product
curl -X POST "${CPQ_BASE_URL}/api/v1/pricing/execute" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-002", "quantity": 1, "customerExternalId": "CUST-001"}'
```
