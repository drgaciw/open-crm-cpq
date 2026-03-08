---
name: deal-specific-discount
description: Analyze manual discount requests, review historical precedent, and assemble approval workflows for deal-specific pricing.
---

# Deal-Specific Discount

Use this skill when a sales rep requests a manual or one-off discount for a specific deal and you need to evaluate the request, find precedent, and route it for approval.

## Scope

- Accept and validate manual discount requests on a quote.
- Search historical deals for precedent discounts on similar products, segments, or deal sizes.
- Assemble the approval request with justification and precedent evidence.
- Do not auto-approve discounts; always route through the approval workflow.

## Inputs

- Quote ID receiving the discount
- Requested discount type (percentage, fixed amount, or override price) and value
- Justification or business rationale from the sales rep

## Workflow

1. Receive the discount request and validate it against basic constraints (non-negative, within system limits).
2. Query historical quotes and approvals for precedent discounts on similar products and customer segments.
3. Calculate the margin impact of the requested discount using current cost data.
4. Assemble the approval package: requested discount, margin impact, precedent data, and rep justification.
5. POST the discount to the quote and trigger the approval workflow.
6. Return the approval status, assigned approvers, and any conditions or flags.

## Minimum Checks

- The requested discount is within system-allowed bounds before submission.
- Margin impact is calculated and included in the approval package.
- Historical precedent search covers at least the same product family and customer segment.
- The approval workflow is triggered and an approval ID is returned.

## Useful Commands

```bash
curl -s POST /api/v1/quotes/{quoteId}/discounts -d '{"type":"percentage","value":15,"justification":"..."}'
curl -s GET /api/v1/quotes/approvals/pending
```
