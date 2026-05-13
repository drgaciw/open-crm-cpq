# S1B-4: Add a top-of-stack verification PR that proves backend modules can run under the chosen unit/integration split.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S1B`

**Service/Module:** backend

**Actionable Todo:** Add a top-of-stack verification PR that proves backend modules can run under the chosen unit/integration split.

**Stack Dependency:** S1B-3

**Reference Doc:** AGENT.md; docs/GRAPHITE_STACKED_PRS.md

## Implementation Notes

- Backend `mvn test` now uses Surefire excludes for `*IntegrationTest`, `*IT`, and `*LifecycleTest`, keeping the default unit verification path Docker-free.
- Backend `mvn verify -Pintegration` enables Failsafe for the same integration naming patterns, preserving the Docker-backed top-of-stack verification path.
- `backend/README.md` documents the default unit path and the explicit integration profile.
