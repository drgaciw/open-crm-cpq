# S3C-3: Refactor rule/pricing/optimization/AI controllers to rely on GlobalExceptionHandler instead of local broad try/catch blocks.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S3C`

**Service/Module:** price-drools-service

**Actionable Todo:** Refactor rule/pricing/optimization/AI controllers to rely on GlobalExceptionHandler instead of local broad try/catch blocks.

**Stack Dependency:** S3A-1

**Reference Doc:** ../pricing-ai-rules-service/backend-simplification-tasks.md; ../pricing-ai-rules-service/price-drools-service/src/main/java/com/cascade/price/drools/exception/README.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
