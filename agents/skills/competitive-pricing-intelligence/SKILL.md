---
name: competitive-pricing-intelligence
description: Analyzes competitive landscape, tracks win rates by competitor, and recommends pricing positioning strategies.
---

# Competitive Pricing Intelligence

Use this skill when you need to understand the competitive landscape for a deal, analyze historical win rates against specific competitors, or get pricing positioning recommendations.

## Scope

- Analyze the competitive landscape for a given deal or market segment
- Track and report win/loss rates segmented by competitor
- Recommend pricing positioning strategies based on competitive data
- Identify deals where specific competitors are present

## Inputs

- Deal ID (for deal-specific analysis)
- Competitor name (for competitor-specific queries)
- Product category or market segment (optional)
- Time range for historical analysis (optional)

## Workflow

1. Retrieve the deal record to identify known competitors and deal attributes.
2. Query historical deals involving the same competitor to build a win/loss dataset.
3. Calculate win rate, average discount to win, and deal size distribution by competitor.
4. Analyze pricing patterns in won vs. lost deals against this competitor.
5. Generate a positioning recommendation based on competitive patterns (price leader, value differentiation, feature advantage).
6. Return competitive intelligence summary with win rate, pricing benchmarks, and strategy.

## Minimum Checks

- Deal or competitor data is available for analysis
- Historical dataset is sufficiently large for meaningful patterns
- Win rate calculations exclude in-progress deals
- Positioning recommendations are grounded in data, not assumptions
- Competitor names are normalized to avoid duplicates

## Useful Commands

```bash
# Retrieve deal details with competitor information
curl -X GET "${CPQ_BASE_URL}/api/v1/deals/{id}" \
  -H "Authorization: Bearer ${TOKEN}"

# Query deals by competitor
curl -X GET "${CPQ_BASE_URL}/api/v1/deals?competitor={name}" \
  -H "Authorization: Bearer ${TOKEN}"
```
