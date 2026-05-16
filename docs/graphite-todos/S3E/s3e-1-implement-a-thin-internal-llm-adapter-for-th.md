# S3E-1: Implement a thin internal LLM adapter for the current CpqAgent and AgentRouter seam without upgrading the AI framework in the same PR.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S3E`

**Service/Module:** backend/services/ai-service

**Actionable Todo:** Implement a thin internal LLM adapter for the current CpqAgent and AgentRouter seam without upgrading the AI framework in the same PR.

**Stack Dependency:** S1B-4

**Reference Doc:** docs/phase0/phase0-ai-stack-decision.md; backend/services/ai-service/DDD_SPEC.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Added an internal `LlmAdapter` seam for CPQ agent LLM calls.
- Added a Spring AI 0.8.1-backed adapter implementation without upgrading the AI framework.
- Moved `AbstractCpqAgent` off the newer Spring AI `chat.client.ChatClient` API so the current ai-service compile path uses the pinned framework version.
