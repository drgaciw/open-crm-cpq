# price-dools harvest landed as flagged pricing-service APIs {#verdict}

`pricing-service` now owns the deterministic price-dools gaps. Conflicts, templates, and test suites sit behind `opencrm.pricing.rule-tools.enabled`. Metrics snapshots sit behind `opencrm.pricing.metrics-history.enabled`. Both flags default to false. Context7, Sequential Thinking, and semantic cache stay on hold.

- Wave 1 analyzes stored DRL and creates DRAFT rules only.[](source:pd1)
- Wave 2 stores MetricsCalculator outputs. It does not fork ARR/TCV/ACV/CLV.[](source:pd2)
- Existing execute, simulate, and rule CRUD stay the system of record.
- AI inside `pricing-service` is still forbidden until a Path B ADR.

@answer scope="price-dools PD0–PD2 harvest · flags off · AI hold · 2026-11-16"
@meta locale=en
@source harvest title="Harvest index" path="docs/plans/00-harvest-index.md"
@source pd1 title="PD1 capability matrix" path="docs/plans/price-dools-capability-matrix-p1.md"
@source pd2 title="PD2 metrics snapshots" path="backend/services/pricing-service/src/main/resources/db/migration/V10__metrics_snapshots.sql"
@source pin title="Source pin cf994fc" path="docs/plans/price-dools-reuse-gap-analysis.md"

## What shipped {#shipped}

@workflow id=pd-flow title="Docs then deterministic waves"
| docs | Phase 0 docs | external |
| w1 | Wave 1 rule tools | backend |
| w2 | Wave 2 history | backend |
| hold | AI hold | security |
| docs > w1 | PD0 |
| w1 > w2 | PD1 green | emphasis |
| docs > hold | blocked | dashed |

@steps id=pd-steps
| docs | Phase 0 docs | Index, gap analysis, matrix, backlog. | pd-flow | docs |
| w1 | Wave 1 rule tools | Conflicts, templates, test suites. | pd-flow | w1 |
| w2 | Wave 2 history | Snapshots and churn-risk lookup. | pd-flow | w2 |
| hold | AI hold | Context7, Sequential, semantic cache stay out. | pd-flow | hold |

## Which path maps to which API {#paths}

| ID | Source | CPQ path | Flag |
| --- | --- | --- | --- |
| PD1-1 | `/api/v1/rule-conflicts` | `GET /api/v1/rules/by-rule-set/{id}/conflicts` | `rule-tools` {good:Landed} |
| PD1-2 | `/api/v1/rule-templates` | `POST /api/v1/rules/templates/{kind}` | `rule-tools` {good:DRAFT} |
| PD1-3 | `/api/v1/rule-tests` | `POST /api/v1/rules/test-suites/run` | `rule-tools` {good:simulate} |
| PD2-1 | metrics history | `POST/GET /api/v1/pricing/metrics-history/**` | `metrics-history` {good:V10} |
| PD2-2 | churn-risk | `GET /api/v1/pricing/churn-risk/{id}` | `metrics-history` {good:snapshot} |
| PD-AI | Context7 / Sequential / cache | none | HOLD {caution:Path B} |

## What stays out {#holds}

@callout id=ai-hold level=warning title="AI hold" text="Do not port Context7, Sequential Thinking, or semantic cache into pricing-service. Revisit only after a Path B ADR."

- [ ] Flags stay false in committed YAML
- [ ] Template apply never activates a rule set
- [ ] No second Drools execute path
- [ ] ITs need Docker; this run used Testcontainers for V9 replay
