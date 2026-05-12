# S1B-1: Establish shared-library verification as the bottom PR so service stacks can depend on stable common code.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S1B`

**Service/Module:** backend/libs/common, backend/libs/security, backend/libs/rules-engine

**Actionable Todo:** Establish shared-library verification as the bottom PR so service stacks can depend on stable common code.

**Stack Dependency:** S0A-3, S0B-3

**Reference Doc:** docs/GRAPHITE_STACKED_PRS.md; backend/CPQ_IMPLEMENTATION.md

## Implementation Notes

- Added a dedicated GitHub Actions `shared-libraries` job that verifies `backend/libs/common`, `backend/libs/security`, and `backend/libs/rules-engine` before service-stack changes depend on them.
- The job runs `mvn -B -U verify --no-transfer-progress -pl libs/common,libs/security,libs/rules-engine -am` from `backend/`, with `SPRING_PROFILES_ACTIVE=test`.
- Preserved the existing full backend verification job so broader service regressions still run in CI.
