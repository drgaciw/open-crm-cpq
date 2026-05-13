# S3C-1: Search and remove NumericalParameterValue duplication by migrating to NumericParameterValue.

**Tracking status:** Completed in this PR.

**Stack lane:** `S3C`

**Service/Module:** price-drools-service

**Actionable Todo:** Search and remove NumericalParameterValue duplication by migrating to NumericParameterValue.

**Stack Dependency:** S3A-1

**Reference Doc:** ../pricing-ai-rules-service/backend-simplification-tasks.md

## Implementation Notes

- Repository-wide searches for `NumericalParameterValue`, `NumericParameterValue`, and `NumericParam` only find this tracking card; there is no duplicated implementation type to migrate on the current `main` base.
- The referenced `price-drools-service` module is not present in this repository. The current pricing/rules ownership lives in `backend/services/pricing-service` and `backend/libs/rules-engine`.
- No production code changes were needed because the duplicate type is already absent from the branch.
