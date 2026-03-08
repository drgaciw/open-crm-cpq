---
name: profitability-alert
description: Generate proactive margin warnings, analyze profitability trends, and alert when pricing approaches threshold boundaries.
---

# Profitability Alert

Use this skill when you need to proactively monitor a quote's profitability, detect margin erosion trends, or alert when pricing is approaching critical threshold boundaries.

## Scope

- Calculate current margin position relative to all defined thresholds.
- Analyze margin trends across line items to detect erosion patterns.
- Generate alerts when pricing is within a configurable proximity of a threshold boundary.
- Do not block or modify pricing; only observe and report.

## Inputs

- Quote ID to analyze
- Alert proximity threshold (optional, e.g., within 5% of a margin floor)
- Historical quote IDs for trend comparison (optional)

## Workflow

1. Retrieve all line items and their current pricing from the quote.
2. Fetch bulk cost data from the ERP system for all products on the quote.
3. Calculate per-item and overall quote margins.
4. Compare margins against threshold boundaries (35%, 20%, 0%) and flag items within the proximity zone.
5. If historical quotes are provided, compute margin trend direction (improving, stable, eroding).
6. Assemble and return a profitability report with alerts, trend indicators, and recommended actions.

## Minimum Checks

- Cost data is retrieved for every line item before margin calculations.
- Proximity alerts fire when any margin is within the configured distance of a threshold.
- Trend analysis uses at least two data points when historical data is available.
- The report clearly distinguishes between informational warnings and actionable alerts.

## Useful Commands

```bash
curl -s POST /api/v1/erp/costs/bulk -d '{"externalIds":["id1","id2","id3"]}'
curl -s GET /api/v1/quotes/{quoteId}/line-items
```
