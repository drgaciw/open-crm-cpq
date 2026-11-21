# open-crm-cpq is the platform; price-dools-service is a Drools+AI metrics sidecar {#verdict}

**open-crm-cpq** (this workspace) is a multi-service CRM/CPQ platform. **price-dools-service** (`/home/username01/MyCode/price-dools-service`, artifact `price-rules-ai-drools`) is a single Spring Boot 3.4 / Drools 8.44 microservice focused on rule deploy/execute, ARR·TCV·ACV·CLV·churn, templates/conflicts/tests, and AI rule create/improve with Sequential Thinking + Context7 + semantic cache. It does **not** replace CPQ's quotes, CRM, gateway, or Angular UI.

- CPQ wins product surface: quotes, CRM, price books, approvals, gateway, Angular, waterfall pricing, flagged AI workflows/experiments.
- price-dools wins niche rule-ops tooling: conflict analysis, template factories, rule-test suites, metrics history/snapshots, semantic routing cache, Context7 doc enhancement.
- Overlap: Drools execute/validate, financial ARR/TCV/ACV, AI create/improve rules — different APIs and maturity; do not dual-run both as system of record.
- Harvest selectively into `pricing-service` / `ai-service`; do not merge the monolith wholesale.

@answer scope="Functional comparison · open-crm-cpq vs MyCode/price-dools-service · 2026-11-16"
@meta locale=en
@source harvest title="Harvest index" path="docs/plans/00-harvest-index.md"
@source cpq-price title="CPQ PricingExecutionController" path="backend/services/pricing-service/src/main/java/com/opencrm/pricing/execution/PricingExecutionController.java"
@source cpq-ai title="CPQ RuleWorkflowController" path="backend/services/ai-service/src/main/java/com/opencrm/ai/workflow/RuleWorkflowController.java"
@source pdools title="price-dools README (local clone)"

## System shapes {#systems}

@architecture id=shape-compare title="Platform vs single Drools service"
| ui | Angular CRM | frontend |
| gw | gateway-service | backend |
| crm | crm-service | backend |
| quote | quote-service | backend |
| pb | pricebook-service | backend |
| price | pricing-service | backend |
| ai | ai-service | backend |
| pd | price-dools monolith | external |
| redis | Redis semantic cache | database |
| pg | PostgreSQL | database |
| ui > gw | /api |
| gw > crm | |
| gw > quote | | emphasis |
| gw > price | execute simulate | emphasis |
| gw > ai | workflow experiments | dashed |
| price > pg | Flyway | 
| ai > gw | tool clients | dashed |
| pd > pg | Liquibase | 
| pd > redis | embeddings | dashed |

@steps id=shape-steps
| s1 | open-crm-cpq | Many services behind gateway; pricing owns active DRL. | shape-compare | price |
| s2 | price-dools | One jar: Drools + metrics + AI + local JWT. | shape-compare | pd |

## Capability matrix {#matrix}

| Capability | price-dools-service | open-crm-cpq | Verdict |
| --- | --- | --- | --- |
| Runtime shape | Monolith Boot 3.4 / Java 21 / Drools 8.44 | 11+ services Boot 4 / Java 25 / Drools in pricing | Different eras {caution:Stack} |
| Drools deploy / execute / batch | `/api/v1/drools/**` | `/api/v1/pricing/execute` + rules CRUD | Overlap {good:Both} |
| Rule validate / reload / cache metrics | Yes | Validate + impact; no identical cache-metrics API | Near overlap |
| ARR / TCV / ACV | `/api/v1/financial-metrics` | Waterfall MetricsCalculator | Overlap {good:Both} |
| CLV / churn risk / metrics history | Dedicated history + snapshots | Not as rich customer history APIs | price-dools {caution:Gap in CPQ} |
| Pricing strategies (volume / value / risk) | Strategy factory + apply-strategy | Waterfall + rule content | Different model |
| Rule templates | volume / commitment / risk / contract-length | No template factory API | price-dools {caution:Harvest?} |
| Rule conflict analysis | `/api/v1/rule-conflicts` | Absent | price-dools {caution:Gap} |
| Rule test suites | `/api/v1/rule-tests` | Unit/IT + e2e; no same API | price-dools {caution:Gap} |
| AI create / improve / analyze | `/ai/rules` + Sequential Thinking | `/api/v1/ai/workflow/**` (flag-off) | Overlap {good:Both} |
| Context7 documentation enhance | Built-in | Not ported | price-dools {caution:MCP-ish} |
| Semantic cache / AI routing | Redis vector embeddings | Selection/OpenRouter path; different | Different AI stacks |
| Rule experiments A/B | Absent | `/api/v1/ai/rules/experiments` | CPQ {good:Wave3} |
| Quotes / CRM / approvals / price books / UI | Absent | Full stack | CPQ only {good:Platform} |
| Auth | Local JWT login/signup | Entra JWT via gateway | CPQ {good:Enterprise} |

## What each should own {#ownership}

@workflow id=own-flow title="Recommended ownership"
| need | Business need? | external |
| crmq | Quotes CRM UI? | backend |
| cpq | Use open-crm-cpq | backend |
| niche | Conflict templates history? | backend |
| harvest | Port algorithm to pricing/ai | backend |
| need > crmq | |
| crmq > cpq | yes | emphasis |
| crmq > niche | no |
| niche > harvest | selectively |

@steps id=own-steps
| o1 | Platform need | Always CPQ path owners. | own-flow | cpq |
| o2 | Niche algorithm | Consider harvest; keep auth and deploy model on CPQ. | own-flow | harvest |

## Suggested harvest targets {#harvest}

@callout id=no-merge level=warning title="Do not merge wholesale" text="price-dools uses Boot 3.4, local JWT, and Liquibase. Port algorithms only into pricing-service / ai-service under harvest gates — no second Drools system of record."

- [ ] Rule conflict detection → optional `pricing-service` analysis API
- [ ] Template factories (volume/commitment/risk) → draft helpers or rule-set seeds
- [ ] Metrics history / churn snapshot patterns → only if product asks for customer financial timelines
- [ ] Sequential/Context7 patterns → only if forge authorizes MCP-style clients (same hold as PD MCP)
- [ ] Skip local JWT, signup, and standalone Next-less UI assumptions
