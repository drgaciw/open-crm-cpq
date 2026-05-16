# S2E-2: Add typed clients for CPQ published APIs after backend route contracts are available.

**Tracking status:** Implemented in impl/s2e-2 (supersedes tracking PR #199).

**Stack lane:** `S2E`

**Service/Module:** frontend/libs/shared/data-access

**Actionable Todo:** Add typed clients for CPQ published APIs after backend route contracts are available.

**Stack Dependency:** S2E-1

**Reference Doc:** AGENT.md; MODERNIZATION_PLAN.md

## Implementation Notes

- `CpqApiPaths` mirrors gateway `/api/v1/cpq/...` aliases.
- Injectable clients extend `BaseCrudService` per aggregate owner (pricing, catalog, quote flow).
