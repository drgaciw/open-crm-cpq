---
name: audit-compliance-reporting
description: Generates audit trails, produces SOX compliance reports, and maintains pricing decision history for regulatory compliance.
---

# Audit Compliance Reporting

Use this skill when you need to generate audit trails for pricing decisions, produce SOX compliance reports, or retrieve the history of pricing and approval actions.

## Scope

- Generate comprehensive audit trails for pricing decisions and quote modifications
- Produce SOX compliance reports covering pricing controls and approval workflows
- Retrieve and analyze pricing decision history with full attribution
- Validate that all pricing actions have proper authorization records

## Inputs

- Date range (start and end dates for report period)
- Quote ID (for quote-specific audit trail)
- Report type (audit-trail, sox-compliance, pricing-history)
- User or role filter (optional)

## Workflow

1. Define the reporting scope based on date range, quote ID, or report type.
2. Query the pricing audit log for all actions within scope.
3. Query the approvals history for all approval decisions within scope.
4. Correlate pricing actions with their corresponding approvals to verify authorization.
5. Identify any pricing actions that lack proper approval records (compliance gaps).
6. Assemble the report with action details, timestamps, actors, and authorization status.

## Minimum Checks

- Date range is valid and does not exceed system retention limits
- Audit log entries include actor, action, timestamp, and before/after values
- SOX reports flag any pricing actions without corresponding approvals
- Report output is complete and covers the full requested scope
- Compliance gaps are clearly identified with remediation guidance

## Useful Commands

```bash
# Retrieve pricing audit log
curl -X GET "${CPQ_BASE_URL}/api/v1/pricing/audit-log?startDate=2026-01-01&endDate=2026-03-31" \
  -H "Authorization: Bearer ${TOKEN}"

# Retrieve approvals history
curl -X GET "${CPQ_BASE_URL}/api/v1/quotes/approvals/history?startDate=2026-01-01&endDate=2026-03-31" \
  -H "Authorization: Bearer ${TOKEN}"
```
