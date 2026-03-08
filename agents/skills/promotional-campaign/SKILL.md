---
name: promotional-campaign
description: Discover active promotional campaigns, evaluate deal eligibility, and manage campaign discount stacking rules.
---

# Promotional Campaign

Use this skill when you need to find active promotional campaigns, check whether a deal qualifies, and apply campaign discounts while respecting stacking rules.

## Scope

- Discover currently active promotional campaigns and their eligibility criteria.
- Evaluate a specific deal or quote against campaign eligibility rules.
- Apply eligible campaign discounts and enforce stacking/exclusion rules.
- Do not create or modify campaign definitions.

## Inputs

- Quote ID or deal ID to evaluate for campaign eligibility
- Product ID(s) on the quote (campaigns may be product-specific)
- Customer segment (campaigns may be segment-restricted)

## Workflow

1. Retrieve all currently active promotional campaigns from the campaigns endpoint.
2. Filter campaigns to those matching the quote's products, customer segment, and date range.
3. Evaluate deal-specific eligibility criteria (minimum order value, required products, etc.).
4. Check stacking rules to determine which eligible campaigns can be combined.
5. Execute the pricing engine with the selected campaigns applied.
6. Return the list of applied campaigns, discount amounts, and any campaigns excluded by stacking rules.

## Minimum Checks

- Only campaigns within their active date range are considered.
- Eligibility criteria are fully evaluated before applying any campaign discount.
- Stacking rules are enforced; mutually exclusive campaigns are not combined.
- The total promotional discount does not push the price below the floor.

## Useful Commands

```bash
curl -s GET /api/v1/pricing/campaigns/active
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","campaignIds":["camp1","camp2"],"products":[...]}'
```
