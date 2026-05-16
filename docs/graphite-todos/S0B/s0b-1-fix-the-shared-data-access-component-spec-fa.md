# S0B-1: Fix the shared-data-access component/spec failure by aligning standalone component metadata and Vitest Angular TestBed setup.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S0B`

**Service/Module:** frontend/libs/shared/data-access

**Actionable Todo:** Fix the shared-data-access component/spec failure by aligning standalone component metadata and Vitest Angular TestBed setup.

**Stack Dependency:** None

**Reference Doc:** frontend/libs/shared/data-access/README.md; AGENT.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- The shared data access component is standalone and its Vitest TestBed setup imports the standalone component.
