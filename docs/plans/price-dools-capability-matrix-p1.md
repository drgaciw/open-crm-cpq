# Track PD1/PD2 capability matrix — `price-dools-service`

**Parent:** [price-dools-reuse-gap-analysis.md](./price-dools-reuse-gap-analysis.md)  
**Source pin:** `cf994fc055ad2b9723040ff1be73796ba3708014` @ `/home/username01/MyCode/price-dools-service`  
**Target owner:** `pricing-service`  
**Flags:** `opencrm.pricing.rule-tools.enabled` (PD1), `opencrm.pricing.metrics-history.enabled` (PD2) — default false

| ID | Source capability | Source path | CPQ landing | Disposition |
|----|-------------------|-------------|-------------|-------------|
| PD1-1 | Rule conflict analysis | `RuleConflictService` + `/api/v1/rule-conflicts` | `GET /api/v1/rules/by-rule-set/{ruleSetId}/conflicts` | **Harvest** — static analysis over stored DRL (name/salience/opposing effects). No Kie AST. |
| PD1-2 | Rule templates | `RuleTemplateService` + `/api/v1/rule-templates` | `POST /api/v1/rules/templates/{kind}` | **Harvest** — generate DRL string, `RuleService.create` DRAFT only. Kinds: `volume-discount`, `commitment`, `risk-adjustment`, `contract-length`. Never activate rule-set. |
| PD1-3 | Rule test suites | `RuleTestController` + `RuleTestRunner` | `POST /api/v1/rules/test-suites/run` | **Harvest** — wrap `PricingExecutionService.simulate`; compare expected vs `FinancialMetrics`. |
| PD2-1 | Metrics snapshots | `FinancialMetricsHistoryController` + snapshot repo | `POST/GET /api/v1/pricing/metrics-history/**` | **Harvest** — Flyway table; reuse `MetricsCalculator` values; no formula fork. |
| PD2-2 | Churn risk lookup | `GET /api/v1/financial-metrics/churn-risk/{customerId}` | `GET /api/v1/pricing/churn-risk/{customerId}` | **Harvest** — last snapshot `churnScore` (or empty). |
| — | Execute / validate | `/api/v1/drools/**` | Existing execute/simulate/validate | **Skip** — already landed |
| — | ARR/TCV/ACV/CLV formulas | `FinancialMetricsCalculator` | `MetricsCalculator` | **Skip** — T1 closed |
| — | Context7 / Sequential / semantic cache | `/ai/rules`, Redis vectors | — | **HOLD** Path B |
| — | Local JWT | `/auth` | Entra gateway | **Skip** |

**Gateway:** no new prefixes. Paths stay under `/api/v1/rules/**` and `/api/v1/pricing/**`.
