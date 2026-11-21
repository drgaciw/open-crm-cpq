# open-crm-cpq owns the platform; Price-Drools still leads niche AI tooling {#verdict}

**open-crm-cpq** is the production CRM/CPQ platform (Drools execute, quotes, CRM UI, gateway). **pricing-ai-rules-service** (`/home/username01/MyCode/pricing-ai-rules-service`) remains a Drools+AI specialist: richer agent catalog, MCP clients, rule pipeline, and RL experiment services. After CPQ Waves 1–3, core AI rule-ops (create / modify / evaluate / optimize / performance / experiments) are **ported into CPQ** behind flags — MCP and most business-domain agents are **not**.

- CPQ wins lifecycle: execute/simulate, rule-set activate, ARR/TCV/ACV waterfall, quotes, CRM, approvals.
- Price-Drools wins depth: 17 `/v1/agents/*` controllers, Sequential/Context7/Puppeteer MCP, prompt-variant pipeline, sports pricing.
- Shared surface now: `/api/v1/ai/workflow/**` and experiment APIs exist in CPQ (`ai-service`); PD keeps in-process RL + richer performance apply/test paths.
- Do not merge PD as a 12th runtime; harvest into `pricing-service` / `ai-service` per `docs/plans/00-harvest-index.md`.

@answer scope="Functional comparison · open-crm-cpq vs MyCode/pricing-ai-rules-service · 2026-11-16"
@meta locale=en
@source harvest title="Harvest index" path="docs/plans/00-harvest-index.md"
@source pd-orch title="PD AIOrchestrationController (MyCode clone)"
@source cpq-wf title="CPQ RuleWorkflowController" path="backend/services/ai-service/src/main/java/com/opencrm/ai/workflow/RuleWorkflowController.java"
@source cpq-exp title="CPQ RuleExperimentController" path="backend/services/ai-service/src/main/java/com/opencrm/ai/experiment/RuleExperimentController.java"

## How the two systems relate {#systems}

@architecture id=platform-split title="Platform vs AI specialist"
| gw | API gateway | backend |
| crm | crm-service | backend |
| quote | quote-service | backend |
| price | pricing-service | backend |
| ai | ai-service | backend |
| ui | Angular CRM | frontend |
| pd | Price-Drools monolith | external |
| pdfe | Next frontend | frontend |
| mcp | MCP Sequential Context7 Puppeteer | cloud |
| gw > crm | CRM APIs |
| gw > quote | Quotes | emphasis |
| gw > price | Execute simulate rules | emphasis |
| gw > ai | Workflow experiments | emphasis |
| ui > gw | /api proxy |
| ai > gw | Tool clients | dashed |
| pd > mcp | Optional MCP | dashed |
| pdfe > pd | Direct |

@steps id=system-roles
| s1 | pricing-service | Only writer of active DRL; execute / simulate / optimize advisory. | platform-split | price |
| s2 | ai-service | Flagged rule workflows + experiments; drafts only. | platform-split | ai |
| s3 | Price-Drools monolith | Self-contained Drools + AI + agents + MCP. | platform-split | pd |

## Capability matrix {#matrix}

| Capability | Price-Drools (MyCode) | open-crm-cpq | Verdict |
| --- | --- | --- | --- |
| Drools execute / batch | `/v1/pricing/evaluate` | `POST /api/v1/pricing/execute` + simulate/bulk | CPQ stronger {good:Platform} |
| Rule-set CRUD / validate / impact | `/v1/rules/**` | `/api/v1/rules/**` + activate/deprecate | CPQ stronger {good:Platform} |
| ARR / TCV / ACV waterfall | FinancialMetricsService | Waterfall + MetricsCalculator | CPQ stronger {good:Landed} |
| Quotes / CRM / approvals / price books | Thin `/api/v1/cpq` stubs | Full microservices + Angular | CPQ only {good:Platform} |
| AI create / modify / evaluate | `AIOrchestrationController` | `RuleWorkflowController` (flag-off) | Parity {good:Ported} |
| AI optimize + performance | RuleOptimization + PerformanceAnalysis | Workflow optimize/performance + `POST /pricing/optimize` | Near parity {good:Ported} |
| Rule experiments | RL `ExperimentService` + designer agent | Persisted `/api/v1/ai/rules/experiments` DRAFT apply | CPQ gated port {good:Wave3} |
| Apply recommendation / temp KieSession | Performance apply + test deploy | Draft-only; no temp session | PD deeper {caution:Gap} |
| Business agents (geo, elasticity, negotiator, …) | 17 `/v1/agents/*` | Shell agents; not PD ports | PD deeper {caution:Gap} |
| MCP Sequential / Context7 / Puppeteer | Present (flagged) | Not ported | PD only {caution:Gap} |
| Rule / prompt pipeline | `/v1/pipeline/**` | Absent | PD only {caution:Gap} |
| Sports pricing niche | `SportsPricingController` | Absent | PD only {caution:Niche} |

## Where AI rule-ops live now {#ai-ops}

@workflow id=ai-ops-flow title="Advisory rule path in CPQ"
| req | Business requirement | external |
| wf | ai-service workflow | backend |
| draft | createDraftRule | backend |
| val | validateRule | backend |
| human | Human activate? | security |
| live | pricing-service activate | backend |
| req > wf | |
| wf > draft | persist? |
| draft > val | |
| val > human | |
| human > live | yes | emphasis |

@steps id=ai-ops-steps
| a1 | Workflow | Agents 038–042 propose DRL under `opencrm.ai.agents.enabled`. | ai-ops-flow | wf |
| a2 | Draft | Gateway `POST /api/v1/rules` as DRAFT only. | ai-ops-flow | draft |
| a3 | Activate | Existing rule-set activate APIs — never from ai-service. | ai-ops-flow | live |

## Remaining gaps worth harvesting {#gaps}

@callout id=holds level=warning title="Holds" text="No AI inside pricing-service beyond advisory optimize. Agents and experiments stay flag-off until a forge wave. Do not import MCP or OpenL without authorization."

- [ ] Temporary KieSession deploy for recommendation tests (PD PerformanceAnalysis `test/{ruleId}/{recommendationId}`)
- [ ] Decide whether any `/v1/agents/*` domain agents map to CPQ products (else leave in PD)
- [ ] MCP clients only if a forge wave authorizes Sequential / Context7 / Puppeteer
- [ ] Prompt-variant / rule pipeline — out of BC-03 Waves 1–3
- [ ] Keep harvest pin and owners in `docs/plans/00-harvest-index.md`
