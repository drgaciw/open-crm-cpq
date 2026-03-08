---
name: exception-handling
description: Manages below-cost exceptions, strategic deal exceptions, and competitive match exceptions for non-standard pricing approvals.
---

# Exception Handling

Use this skill when a quote requires pricing that falls outside standard approval thresholds, such as below-cost deals, strategic account exceptions, or competitive match scenarios.

## Scope

- Process below-cost exception requests with supporting justification
- Handle strategic deal exceptions for key accounts or market entry
- Manage competitive match exceptions with competitor evidence
- Validate exception requests against policy guardrails

## Inputs

- Quote ID
- Exception type (below-cost, strategic, competitive-match)
- Justification narrative and supporting evidence
- Competitor name and pricing (for competitive match)
- Expected deal outcome and strategic value

## Workflow

1. Retrieve the quote and identify line items that require exception pricing.
2. Classify the exception type and validate that required supporting evidence is provided.
3. Calculate the financial impact of the exception (margin erosion, revenue at risk).
4. Submit the exception request with justification to the appropriate approval authority.
5. Track the exception through the approval process and capture the decision.
6. Apply approved exception pricing to the quote and document the rationale.

## Minimum Checks

- Exception type is valid and justification narrative is provided
- Financial impact is calculated before submission
- Competitive match exceptions include verifiable competitor evidence
- Below-cost exceptions have executive-level approval routing
- Exception decision is recorded in the audit trail

## Useful Commands

```bash
# Submit an exception request for a quote
curl -X POST "${CPQ_BASE_URL}/api/v1/quotes/{id}/approvals/exceptions" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"exceptionType": "competitive-match", "justification": "Competitor offering 40% below list", "competitorName": "Acme Corp"}'

# Retrieve approval status for a quote
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/{id}/approvals" \
  -H "Authorization: Bearer ${TOKEN}"
```
