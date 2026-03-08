---
name: minimum-margin-enforcement
description: Enforce the 3-tier margin floor check (35%/20%/0%), hard-block quotes below minimum margin, and route margin exceptions for approval.
---

# Minimum Margin Enforcement

Use this skill when a quote's pricing needs to be validated against minimum margin thresholds, or when a margin exception requires approval routing.

## Scope

- Execute the 3-tier margin floor check: 35% (preferred), 20% (minimum), 0% (hard block).
- Hard-block any quote or line item that falls below the 0% margin floor.
- Route margin exceptions between 0% and 35% through the appropriate approval chain.
- Do not override margin floors without a completed approval workflow.

## Inputs

- Quote ID to validate
- Line-item cost and price data (or retrieve from quote and ERP)
- Override request details (if seeking a margin exception)

## Workflow

1. Retrieve quote line items and their current pricing.
2. Fetch cost data from the ERP system for all line items.
3. Calculate gross margin for each line item and for the quote overall.
4. Execute the margin enforcement rule (approval-margin-exception.drl, salience 930).
5. Classify each item: green (>=35%), yellow (20%-35%, needs approval), red (<20%, escalated approval), or hard-block (<0%).
6. For items requiring approval, assemble and POST the approval request with margin details.
7. Return the margin analysis with per-item classification and any pending approvals.

## Minimum Checks

- Every line item has a calculated margin based on current ERP cost data.
- Items below 0% margin are hard-blocked with no override path.
- Items in the yellow or red zone have approval requests created.
- The approval-margin-exception rule (salience 930) is executed as part of the pricing engine pass.

## Useful Commands

```bash
curl -s POST /api/v1/pricing/execute -d '{"quoteId":"...","rules":["approval-margin-exception"]}'
curl -s POST /api/v1/quotes/{quoteId}/approvals -d '{"type":"margin-exception","lineItems":[...]}'
```
