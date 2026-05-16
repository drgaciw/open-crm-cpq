# S3E-4: Batch remaining agents by domain package, one Graphite PR per package group with matching tests.

**Tracking status:** Implemented in impl/s3e-4-pricing-batch-no-aggregate-ownership (pricing domain batch + ownership boundary tests).

**Stack lane:** `S3E`

**Service/Module:** backend/services/ai-service

**Actionable Todo:** Batch remaining agents by domain package, one Graphite PR per package group with matching tests.

**Stack Dependency:** S3E-3

**Reference Doc:** docs/backlog/ARCHITECTURE_REVIEW_IMPLEMENTATION_BACKLOG.md; AGENT.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
- This PR batches the remaining `backend/services/ai-service` pricing agents onto the shared model-backed `AbstractCpqAgent` path with package-level tests.
