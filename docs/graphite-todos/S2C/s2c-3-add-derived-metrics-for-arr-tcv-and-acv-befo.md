# S2C-3: Add derived metrics for ARR, TCV, and ACV before porting quote-level additive pricing policies.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S2C`

**Service/Module:** backend/services/pricing-service

**Actionable Todo:** Add derived metrics for ARR, TCV, and ACV before porting quote-level additive pricing policies.

**Stack Dependency:** S2C-2

**Reference Doc:** docs/phase0/phase0-rule-catalog-reconciliation.md; backend/services/pricing-service/DDD_SPEC.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Added a deterministic derived-metrics layer in `backend/services/pricing-service` for ARR, TCV, and ACV.
- Added focused unit coverage for standard multi-year terms, short-term annualization, invalid duration defaults, and empty results.
