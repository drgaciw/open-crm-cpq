# S1B-2: Inventory all backend module test modes and identify which modules require Docker, Redis, or only mocks.

**Tracking status:** Complete in this PR.

**Stack lane:** `S1B`

**Service/Module:** backend/services/*

**Actionable Todo:** Inventory all backend module test modes and identify which modules require Docker, Redis, or only mocks.

**Stack Dependency:** S1B-1

**Reference Doc:** backend/README.md; docs/CRM_TESTING_STRATEGY.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Inventory added in [Backend test mode inventory](../../BACKEND_TEST_MODE_INVENTORY.md).
- Verification: `mvn -pl libs/common,libs/security,libs/rules-engine,services/ai-service,services/approval-routing,services/configuration-engine,services/contract-asset-management,services/document-generation,services/pricing-service,services/product-service test` for mock-only reactor modules, and `mvn -pl services/crm-service verify` when Docker is available for PostgreSQL Testcontainers.
