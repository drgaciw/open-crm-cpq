# S0A-1: Fix LeadServiceTest$GetStatusCounts by aligning the mock setup with LeadService.getStatusCounts() and LeadRepository.countGroupByStatus(), or by changing the service to intentionally call countByStatusAndDeletedFalse for all statuses.

**Tracking status:** Graphite tracking PR only; implementation is not complete in this PR.

**Stack lane:** `S0A`

**Service/Module:** backend/services/crm-service

**Actionable Todo:** Fix LeadServiceTest$GetStatusCounts by aligning the mock setup with LeadService.getStatusCounts() and LeadRepository.countGroupByStatus(), or by changing the service to intentionally call countByStatusAndDeletedFalse for all statuses.

**Stack Dependency:** None

**Reference Doc:** docs/CRM_TESTING_STRATEGY.md; backend/services/crm-service/DDD_SPEC.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
