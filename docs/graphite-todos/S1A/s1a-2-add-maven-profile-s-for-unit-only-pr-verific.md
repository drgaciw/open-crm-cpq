# S1A-2: Add Maven profile(s) for unit-only PR verification and full Docker-backed integration verification.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S1A`

**Service/Module:** backend

**Actionable Todo:** Add Maven profile(s) for unit-only PR verification and full Docker-backed integration verification.

**Stack Dependency:** S1A-1

**Reference Doc:** docs/CRM_TESTING_STRATEGY.md; MODERNIZATION_PLAN.md

## Implementation Notes

- Added backend Maven profiles:
  - `unit-tests` excludes JUnit `@Tag("integration")` tests and skips Jacoco for Docker-free PR verification.
  - `integration-tests` leaves integration tests enabled for full Docker-backed verification.
- Updated backend CI to run `mvn -B -U verify -Punit-tests --no-transfer-progress`.
- Updated `docs/CRM_TESTING_STRATEGY.md` with the profile commands.
