# S2E-2: Add typed clients for CPQ published APIs after backend route contracts are available.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S2E`

**Service/Module:** frontend/libs/shared/data-access

**Actionable Todo:** Add typed clients for CPQ published APIs after backend route contracts are available.

**Stack Dependency:** S2E-1

**Reference Doc:** AGENT.md; MODERNIZATION_PLAN.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Added shared CPQ DTO/request models and an Angular `CpqApiService` covering the published product, price book, pricing, quote, approval, configuration, and document routes.
