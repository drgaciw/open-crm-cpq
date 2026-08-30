---
status: done
id: f2-ce-evaluate
---

# f2-ce-evaluate

## Summary

Added `POST /internal/v1/configurations/evaluate` on configuration-engine with thin REQUIRES/EXCLUDES `StatelessConfigurationEvaluator` (sync REST rules from product-service). Session `ConstraintSolver` placeholder left unchanged.

## Acceptance

- [x] Internal evaluate endpoint
- [x] Returns `ConfigurationValidationResult`
- [x] ConstraintSolver untouched
