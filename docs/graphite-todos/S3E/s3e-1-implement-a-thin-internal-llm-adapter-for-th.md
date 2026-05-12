# S3E-1: Implement a thin internal LLM adapter for the current CpqAgent and AgentRouter seam without upgrading the AI framework in the same PR.

**Tracking status:** Graphite tracking PR only; implementation is not complete in this PR.

**Stack lane:** `S3E`

**Service/Module:** backend/services/ai-service

**Actionable Todo:** Implement a thin internal LLM adapter for the current CpqAgent and AgentRouter seam without upgrading the AI framework in the same PR.

**Stack Dependency:** S1B-4

**Reference Doc:** docs/phase0/phase0-ai-stack-decision.md; backend/services/ai-service/DDD_SPEC.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
