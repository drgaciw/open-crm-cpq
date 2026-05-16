# S2E-4: Scaffold CPQ feature library boundaries for catalog/configuration/pricing/quote flows.

**Tracking status:** Implemented in impl/s2e-4 (supersedes tracking PR #201).

**Stack lane:** `S2E`

**Service/Module:** frontend/libs/crm/cpq

**Actionable Todo:** Scaffold CPQ feature library boundaries for catalog/configuration/pricing/quote flows.

**Stack Dependency:** S2E-2, S2D-2

**Reference Doc:** MODERNIZATION_PLAN.md; AGENT.md

## Implementation Notes

- Nx libraries: `crm-cpq-catalog`, `crm-cpq-configuration`, `crm-cpq-pricing`, `crm-cpq-quote`.
- Each exports a stable boundary constant for future feature modules.
