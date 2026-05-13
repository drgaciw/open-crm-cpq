# S2C-2: Introduce a PriceCalculation audit concept or harden PricingAuditLog to persist input snapshots, rule set version, and pricebook version.

**Tracking status:** Implemented in this PR by hardening `PricingAuditLog` to persist calculation snapshots plus rule set and price book version context.

**Stack lane:** `S2C`

**Service/Module:** backend/services/pricing-service

**Actionable Todo:** Introduce a PriceCalculation audit concept or harden PricingAuditLog to persist input snapshots, rule set version, and pricebook version.

**Stack Dependency:** S2A-3, S2C-1

**Reference Doc:** backend/services/pricing-service/DDD_SPEC.md; docs/phase0/phase0-rule-catalog-reconciliation.md

## Implementation Notes

- Keep the implementation PR focused on this single backlog task.
- Preserve the service ownership boundaries documented in the referenced DDD specs.
- Update this tracking card or close the PR when the real implementation PR supersedes it.
