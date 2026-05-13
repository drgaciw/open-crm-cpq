# S3A-2: Disable Spring Cloud Config requirement for tests or add test config import defaults.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S3A`

**Service/Module:** pricing-ai-rules-service/price-drools-service

**Actionable Todo:** Disable Spring Cloud Config requirement for tests or add test config import defaults.

**Stack Dependency:** S3A-1

**Reference Doc:** ../pricing-ai-rules-service/BUILD_SUMMARY.md; ../pricing-ai-rules-service/AGENTS.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.

## Implementation

- Added test-scope Spring Cloud Config defaults for `pricing-service` so context-loading tests do not require a Config Server import.
