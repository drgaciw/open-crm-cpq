---
name: recommendation-upsell
description: Cross-sell and upsell suggestions based on affinity scoring and purchase history analysis.
---

# Recommendation & Upsell

Use this skill when the user needs cross-sell or upsell product suggestions for a customer, leveraging affinity scoring, purchase history, and product relationships.

## Scope

- Generate cross-sell recommendations based on product affinity scores
- Suggest upsell opportunities from current selections to higher-tier products
- Analyze customer purchase history for complementary product gaps
- Rank recommendations by relevance and revenue potential

## Inputs

- Customer external ID
- Current product selections or quote line items
- Product category or segment filter (optional)
- Maximum number of recommendations to return (optional)

## Workflow

1. Retrieve the customer's purchase history by querying existing quotes.
2. Identify the current product selections from the active quote or input.
3. Search the product catalog for related products using category and affinity data.
4. Score each candidate by affinity to current selections and gap analysis against purchase history.
5. Filter out products the customer already owns or has in the current quote.
6. Return ranked recommendations with affinity scores and suggested positioning (cross-sell vs. upsell).

## Minimum Checks

- Customer external ID resolves to a valid customer record
- Recommended products are in ACTIVE status
- Duplicate recommendations are removed
- Affinity scores are normalized and sortable
- Upsell suggestions represent a genuine tier upgrade over current selections

## Useful Commands

```bash
# Search products for recommendation candidates
curl -s GET '/api/v1/products/search?category=SOFTWARE&status=ACTIVE'

# Retrieve customer quote history
curl -s GET '/api/v1/quotes?customerExternalId={id}'
```
