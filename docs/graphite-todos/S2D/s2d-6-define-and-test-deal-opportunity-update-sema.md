# S2D-6: Define and test deal/opportunity update semantics from QuoteAccepted without moving accepted commercial ownership into CRM.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S2D`

**Service/Module:** backend/services/crm-service

**Actionable Todo:** Define and test deal/opportunity update semantics from QuoteAccepted without moving accepted commercial ownership into CRM.

**Stack Dependency:** S2D-1

**Reference Doc:** backend/services/crm-service/DDD_SPEC.md; docs/CRM_CUTOVER_CHECKLIST.md

## Implementation Notes

- Quote acceptance updates CRM opportunity state only: a matching deal is marked `WON` and `wonAt` is set from the acceptance timestamp when provided.
- CRM does not copy accepted quote `totalAmount`, `arr`, `tcv`, `acv`, or `currency` values onto the deal during quote acceptance; accepted commercial ownership remains in quote/contract services.
- Tests cover preserving existing CRM amount/currency while applying the won-state transition.
