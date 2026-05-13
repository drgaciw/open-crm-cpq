# S1A-4: Split CI into fast unit checks and Docker-backed integration checks while preserving full mvn verify on the integration job.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S1A`

**Service/Module:** .github/workflows

**Actionable Todo:** Split CI into fast unit checks and Docker-backed integration checks while preserving full mvn verify on the integration job.

**Stack Dependency:** S1A-2

**Reference Doc:** docs/CRM_TESTING_STRATEGY.md; docs/CRM_CUTOVER_CHECKLIST.md; docs/GRAPHITE_STACKED_PRS.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
