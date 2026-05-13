# S3A-4: Verify Drools 10.1 compatibility with Jakarta/Spring Boot 4 test slices before enabling broader upgrade work.

**Tracking status:** Implemented in this PR with a targeted Drools 10.1 compatibility profile and Spring Boot context evidence test.

**Stack lane:** `S3A`

**Service/Module:** pricing-ai-rules-service/price-drools-service

**Actionable Todo:** Verify Drools 10.1 compatibility with Jakarta/Spring Boot 4 test slices before enabling broader upgrade work.

**Stack Dependency:** S3A-3

**Reference Doc:** ../pricing-ai-rules-service/dependency-compatibility-report.md; ../pricing-ai-rules-service/AGENTS.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Added Maven profile `drools-10.1-compatibility` to run the rules-engine module against Drools 10.1.0 without changing the default runtime version.
- Added `Drools101JakartaSpringBootCompatibilityTest` to load the rules engine auto-configuration in a Spring Boot test context and compile/execute the pricing service `approval-margin-exception.drl` rule.
- The referenced `backend/services/pricing-ai-rules-service` path does not exist in this repository; evidence is scoped to the existing `backend/libs/rules-engine` and `backend/services/pricing-service` modules.
