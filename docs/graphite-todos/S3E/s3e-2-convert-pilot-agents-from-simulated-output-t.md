# S3E-2: Convert pilot agents from simulated output to model-backed output using specific agent specs selected in the PR, starting with pricing agents such as customer-segment-discount, volume-discount, and minimum-margin-enforcement.

**Tracking status:** Implemented in this PR for the selected pilot pricing agents.

**Stack lane:** `S3E`

**Service/Module:** backend/services/ai-service

**Actionable Todo:** Convert pilot agents from simulated output to model-backed output using specific agent specs selected in the PR, starting with pricing agents such as customer-segment-discount, volume-discount, and minimum-margin-enforcement.

**Stack Dependency:** S3E-1

**Reference Doc:** docs/phase0/phase0-ai-stack-decision.md; agents/customer-segment-discount.md; agents/volume-discount.md; agents/minimum-margin-enforcement.md

## Implementation Notes

- Converted `cpq-agent-007` (volume-discount), `cpq-agent-008` (customer-segment-discount), and `cpq-agent-011` (minimum-margin-enforcement) to the shared internal LLM adapter path introduced in S3E-1.
- Agent-specific instructions are derived from the referenced pricing agent PRDs and keep pricing-service ownership boundaries intact.
- Existing ai-service test compilation is still blocked by unrelated stale constructor expectations in non-pilot agent tests.
