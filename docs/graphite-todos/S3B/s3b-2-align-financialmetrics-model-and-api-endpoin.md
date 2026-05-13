# S3B-2: Align FinancialMetrics model and API endpoints with ARR, TCV, ACV, CLV, churn score, discount rate, and price multiplier requirements.

**Tracking status:** Implemented in this PR.

**Stack lane:** `S3B`

**Service/Module:** price-drools-service

**Actionable Todo:** Align FinancialMetrics model and API endpoints with ARR, TCV, ACV, CLV, churn score, discount rate, and price multiplier requirements.

**Stack Dependency:** S3B-1

**Reference Doc:** ../pricing-ai-rules-service/TECH_SPEC.md; ../pricing-ai-rules-service/AGENTIC_DEVELOPMENT_PLAN.md

## Implementation Notes

- `FinancialMetrics` now exposes ARR, TCV, ACV, CLV, churn score, discount rate, total discount, total surcharge, price multiplier, and estimated margin percentage.
- Pricing execution and simulation endpoints populate those metrics from pricing results and customer context.
- Focused pricing-service tests cover the metric calculations and execution-service integration.
