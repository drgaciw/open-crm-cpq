# S2E-3: Add dashboard and lead lifecycle UI test coverage for gateway-backed CRM flows before broader CPQ UI work.

**Tracking status:** Implemented in impl/s2e-3 (supersedes tracking PR #200).

**Stack lane:** `S2E`

**Service/Module:** frontend/libs/crm/*

**Actionable Todo:** Add dashboard and lead lifecycle UI test coverage for gateway-backed CRM flows before broader CPQ UI work.

**Stack Dependency:** S0B-3, S2E-1

**Reference Doc:** MODERNIZATION_PLAN.md; docs/CRM_CUTOVER_CHECKLIST.md

## Implementation Notes

- `DashboardComponent` spec asserts `/api/v1/deals/*` and `/api/v1/leads/*` gateway paths.
- `LeadService` spec covers list, stats, qualify, and convert command paths.
