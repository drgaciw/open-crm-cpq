---
name: deal-desk-workflow-orchestration
description: Orchestrates end-to-end deal desk workflows, coordinates multi-agent sequencing, and manages approval pipelines as the primary orchestrator.
---

# Deal Desk Workflow Orchestration

Use this skill when you need to coordinate the full deal desk review process, sequencing multiple agents and managing the end-to-end workflow from quote submission through final approval and document generation. This is the PRIMARY ORCHESTRATOR that coordinates agents 024 (multilevel-approval-routing), 025 (exception-handling), and 031 (quote-document-generation).

## Scope

- Coordinate end-to-end deal desk review workflows from submission to completion
- Sequence multi-agent execution (approval routing, exception handling, document generation)
- Manage workflow state transitions and handle failures or rejections
- Provide unified workflow status tracking across all coordinated agents

## Inputs

- Quote ID
- Submission context (urgency, deal type, special instructions)
- Override flags (skip steps, fast-track approval)
- Requestor identity and role

## Workflow

1. Receive the quote submission and validate readiness for deal desk review.
2. Invoke agent 024 (multilevel-approval-routing) to determine the approval tier and route the quote.
3. If the quote requires exceptions, invoke agent 025 (exception-handling) to process exception requests.
4. Monitor approval progress and handle rejections or requests for revision.
5. Upon full approval, invoke agent 031 (quote-document-generation) to produce the final quote document.
6. Update the workflow status at each stage and notify stakeholders of completion or issues.
7. Record the complete workflow history for audit and reporting purposes.

## Minimum Checks

- Quote is valid and complete before initiating the workflow
- Each agent in the sequence completes successfully before the next is invoked
- Workflow status is updated at every state transition
- Failures and rejections halt the workflow and notify the requestor
- Final document is generated only after all approvals are obtained
- Complete workflow history is persisted for audit compliance

## Useful Commands

```bash
# Submit a quote for deal desk review
curl -X POST "${CPQ_BASE_URL}/api/v1/quotes/{id}/submit-for-review" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"urgency": "standard", "notes": "Q2 enterprise deal"}'

# Check workflow status for a quote
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/{id}/workflow-status" \
  -H "Authorization: Bearer ${TOKEN}"
```
