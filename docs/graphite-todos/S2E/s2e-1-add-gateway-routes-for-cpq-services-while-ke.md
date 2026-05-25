# S2E-1: Add gateway routes for CPQ services while keeping commands delegated to aggregate-owning services.

**Tracking status:** Implemented in impl/s2e-1 (supersedes tracking PR #198).

**Stack lane:** `S2E`

**Service/Module:** backend/services/gateway-service

**Actionable Todo:** Add gateway routes for CPQ services while keeping commands delegated to aggregate-owning services.

**Stack Dependency:** S1A-4, S1B-4

**Reference Doc:** backend/services/gateway-service/DDD_SPEC.md; MODERNIZATION_PLAN.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Added CPQ-prefixed gateway aliases that rewrite to the aggregate-owning service paths for pricing, price books, products/configuration, quotes, approvals, documents, and subscriptions.
