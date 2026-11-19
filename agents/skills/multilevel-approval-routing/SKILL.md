---
name: multilevel-approval-routing
description: Determines approval tiers, creates workflows, and routes quotes based on discount and value thresholds.
---

# Multilevel Approval Routing

Use this skill when a quote requires approval and the correct approval tier, workflow, and routing path must be determined based on discount levels, deal value, or margin thresholds.

## Scope

- Determine the appropriate approval tier based on discount percentage and deal value
- Create and assign approval workflows with sequential or parallel routing
- Route quotes to the correct approvers based on threshold rules
- Track approval status and escalate overdue approvals

## Inputs

- Quote ID
- Discount percentage and deal value
- Product line or business unit
- Requestor identity and role

## Workflow

1. Retrieve the quote and its line items to calculate total discount and deal value.
2. Evaluate threshold rules to determine the required approval tier (e.g., manager, director, VP).
3. Create an approval workflow record with the determined tier and assigned approvers.
4. Route the approval request to the first approver in the chain.
5. Monitor approval responses and advance the workflow or escalate as needed.
6. Update the quote status once all required approvals are obtained or a rejection occurs.

## Minimum Checks

- Discount and deal value are within valid ranges before routing
- At least one approver is assigned for the determined tier
- Approval workflow is created before routing begins
- Quote status is updated to reflect current approval state

## Useful Commands

```bash
# Prepare a new submission from the current quote (set QUOTE_ID first).
quote_url="${CPQ_BASE_URL}/api/v1/quotes/${QUOTE_ID}"
quote=$(curl --fail --silent --show-error "$quote_url" \
  -H "Authorization: Bearer ${TOKEN}") || exit 1
approval_body=$(jq -ce '
  .data.version
  | select(type == "number" and . >= 0 and . == floor)
  | {tier: "DEAL_DESK", expectedVersion: .}
' <<< "$quote") || exit 1

# For a transport retry of the same command, rerun only this POST with the
# original approval_body; do not re-fetch the version or rebuild the body.
# A stale-version conflict requires reviewing the current quote before a new command.
curl --fail-with-body -X POST "${quote_url}/approvals" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary "$approval_body"

# Retrieve pending approvals
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/approvals/pending" \
  -H "Authorization: Bearer ${TOKEN}"
```
