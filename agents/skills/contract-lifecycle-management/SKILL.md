---
name: contract-lifecycle-management
description: Manages contract creation from quotes, milestone tracking, amendment processing, and renewal trigger automation.
---

# Contract Lifecycle Management

Use this skill when you need to create contracts from accepted quotes, track contract milestones, process amendments, or manage renewal triggers throughout the contract lifecycle.

## Scope

- Create contracts from accepted quotes with terms and conditions
- Track contract milestones (signature, go-live, review dates)
- Process contract amendments (scope changes, price adjustments, term extensions)
- Automate renewal triggers based on contract end dates

## Inputs

- Quote ID (for contract creation)
- Customer external ID (for contract lookup)
- Amendment details (scope, pricing, term changes)
- Milestone definitions and dates

## Workflow

1. Retrieve the accepted quote or existing contract for the customer.
2. For new contracts, generate a contract record from the quote with all line items, terms, and pricing.
3. Set up milestone tracking with key dates (signature deadline, go-live, first review, renewal notice).
4. For amendments, validate the requested changes against contract terms and pricing rules.
5. Apply amendments and create a versioned contract record with change history.
6. Configure renewal triggers to fire at the appropriate lead time before contract end.

## Minimum Checks

- Quote is in accepted status before contract creation
- All required contract fields are populated (parties, term, value, line items)
- Amendments are validated against existing contract terms
- Renewal triggers are set with sufficient lead time
- Contract versioning maintains a complete change history

## Useful Commands

```bash
# Retrieve contracts for a customer
curl -X GET "${CPQ_BASE_URL}/api/v1/erp/contracts/{customerExternalId}" \
  -H "Authorization: Bearer ${TOKEN}"

# Create a contract from an accepted quote
curl -X POST "${CPQ_BASE_URL}/api/v1/quotes/{id}/contract" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"effectiveDate": "2026-04-01", "termMonths": 12}'
```
