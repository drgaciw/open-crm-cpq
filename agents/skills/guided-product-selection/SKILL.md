---
name: guided-product-selection
description: Search the product catalog, analyze customer context, and deliver AI-driven product recommendations for a deal or quote.
---

# Guided Product Selection

Use this skill when a sales rep needs help finding the right products for a customer based on deal context, industry, or past purchase history.

## Scope

- Search and filter the product catalog using keyword, category, or attribute criteria.
- Retrieve customer context (segment, pricing tier, purchase history) to inform recommendations.
- Return ranked product suggestions; do not add line items or modify quotes directly.

## Inputs

- Customer ID or pricing-customer ID
- Deal ID (optional, for deal-specific context)
- Search keywords, category filters, or product attributes

## Workflow

1. Retrieve customer context via the pricing-customers endpoint to understand segment, tier, and history.
2. If a deal ID is provided, fetch the deal record to capture opportunity-specific requirements.
3. Execute a product catalog search with the supplied keywords and filters.
4. Rank and filter results against customer context (eligible segments, compatible product families).
5. Return a prioritized list of recommended products with rationale for each suggestion.

## Minimum Checks

- Customer context is successfully retrieved before recommendations are made.
- Search results are filtered to only products the customer is eligible to purchase.
- Each recommendation includes a brief rationale linking product to customer need.
- No duplicate products appear in the final recommendation list.

## Useful Commands

```bash
curl -s GET /api/v1/products/search?keyword={term}&category={cat}
curl -s GET /api/v1/pricing-customers/{customerId}
curl -s GET /api/v1/deals/{dealId}
```
