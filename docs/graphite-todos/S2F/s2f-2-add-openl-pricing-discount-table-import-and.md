# S2F-2: Add OpenL pricing/discount table import and validation scaffolding for business-user maintained pricing rules.

**Tracking status:** Implemented in this PR as import/validation scaffolding.

**Stack lane:** `S2F`

**Service/Module:** backend/services/pricing-service

**Actionable Todo:** Add OpenL pricing/discount table import and validation scaffolding for business-user maintained pricing rules.

**Stack Dependency:** S2F-1, S2C-2

**Reference Doc:** MODERNIZATION_PLAN.md; backend/services/pricing-service/DDD_SPEC.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Adds a pricing-service OpenL pricing table validation endpoint for business-user maintained CSV table drafts.
- Validates required pricing/discount columns, numeric ranges, duplicate rule names, and rule set existence before import promotion.
