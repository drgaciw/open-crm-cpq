# S3E-2: Convert pilot agents from simulated output to model-backed output using specific agent specs selected in the PR, starting with pricing agents such as customer-segment-discount, volume-discount, and minimum-margin-enforcement.

**Tracking status:** Graphite tracking PR only; implementation is not complete in this PR.

**Stack lane:** `S3E`

**Service/Module:** backend/services/ai-service

**Actionable Todo:** Convert pilot agents from simulated output to model-backed output using specific agent specs selected in the PR, starting with pricing agents such as customer-segment-discount, volume-discount, and minimum-margin-enforcement.

**Stack Dependency:** S3E-1

**Reference Doc:** docs/phase0/phase0-ai-stack-decision.md; agents/customer-segment-discount.md; agents/volume-discount.md; agents/minimum-margin-enforcement.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
