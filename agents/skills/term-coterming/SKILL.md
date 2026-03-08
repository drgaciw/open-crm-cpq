---
name: term-coterming
description: Proration calculation, co-term date resolution, and amendment impact analysis for subscription terms.
---

# Term & Co-terming

Use this skill when the user needs to calculate prorated pricing for mid-term amendments, resolve co-termination dates across multiple subscriptions, or analyze the financial impact of term changes.

## Scope

- Calculate prorated charges for mid-term subscription amendments
- Resolve co-term dates to align multiple subscriptions to a common renewal date
- Analyze amendment impact on remaining term value and upcoming invoices
- Apply the amendment-proration.drl rule (salience 540) for proration logic

## Inputs

- Quote ID (for existing quote amendments)
- Amendment effective date
- Co-term target date (or derive from existing subscriptions)
- New quantity, tier, or product changes
- Original term start and end dates

## Workflow

1. Retrieve the existing quote and its current term details.
2. Determine the co-term target date from the earliest or latest subscription end date.
3. Calculate the remaining days in the current term from the amendment effective date.
4. Execute the pricing engine, which triggers amendment-proration.drl (salience 540) for proration.
5. Compute the prorated credit for the old configuration and prorated charge for the new configuration.
6. Return the net amendment impact, new term alignment date, and updated recurring cost.

## Minimum Checks

- Amendment effective date falls within the current term period
- Co-term date is not in the past
- Proration calculation uses actual day counts (not 30-day month approximations unless configured)
- The amendment-proration.drl rule (salience 540) fires during pricing execution
- Net amendment impact accounts for both credits and new charges

## Useful Commands

```bash
# Execute pricing with amendment proration
curl -s -X POST '/api/v1/pricing/execute' \
  -H 'Content-Type: application/json' \
  -d '{"quoteId":"q-001","amendmentDate":"2026-03-15","coTermDate":"2027-01-01","lineItems":[{"productId":"prod-001","quantity":10}]}'

# Retrieve existing quote details
curl -s GET '/api/v1/quotes/{id}'
```
