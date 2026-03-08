---
name: win-probability-analysis
description: Analyzes historical win/loss data, generates feature-based predictions, and matches comparable deals to estimate win probability.
---

# Win Probability Analysis

Use this skill when you need to estimate the likelihood of winning a deal based on historical outcomes, deal characteristics, and comparable deal matching.

## Scope

- Analyze historical win/loss patterns for similar deals and customers
- Generate feature-based win probability predictions using deal attributes
- Match current deals against comparable closed deals for benchmarking
- Provide actionable recommendations to improve win probability

## Inputs

- Deal ID
- Customer external ID
- Deal attributes (size, industry, product mix, discount level)
- Competitor presence (if known)

## Workflow

1. Retrieve the deal record and associated quote history for the customer.
2. Query historical deals with similar attributes (size, industry, product mix) to build a comparison set.
3. Analyze win/loss outcomes in the comparison set to identify patterns and correlations.
4. Calculate a feature-based win probability score using deal characteristics as inputs.
5. Identify the top comparable closed-won and closed-lost deals with reasoning.
6. Return the win probability score, key factors, and recommendations to improve odds.

## Minimum Checks

- Deal record exists and has sufficient attributes for analysis
- Comparison set contains a statistically meaningful number of deals
- Win probability score is between 0 and 100
- Comparable deals are ranked by similarity and recency

## Useful Commands

```bash
# Retrieve deal details
curl -X GET "${CPQ_BASE_URL}/api/v1/deals/{id}" \
  -H "Authorization: Bearer ${TOKEN}"

# Retrieve quote history for a customer
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes?customerExternalId={id}" \
  -H "Authorization: Bearer ${TOKEN}"
```
