# Wave 3 rule experiments landed without MCP {#wave3-done}

Price-Drools experiment APIs now live in `ai-service` as a flagged, DRAFT-only apply path with DEAL_DESK/ADMIN approval — MCP clients were left out on purpose.

- Flyway `V3` persists experiments and variants in the `ai` schema.
- REST surface mirrors Price-Drools under `/api/v1/ai/rules/experiments`.
- Apply never activates a rule set; it only creates a draft via the gateway.
- Flag `opencrm.ai.experiments.enabled` defaults to false.

@answer scope="Wave 3 · ai-service · no MCP"
@meta locale=en

## Lifecycle {#lifecycle}

@workflow id=experiment-flow title="Experiment create to apply"
| create | Create experiment | backend |
| start | Start RUNNING | backend |
| metrics | Record metrics | backend |
| complete | Pick winner? | backend |
| apply | Apply DRAFT | external |
| create > start | |
| start > metrics | |
| metrics > complete | |
| complete > apply | DEAL_DESK | emphasis |

@steps id=experiment-steps
| s1 | Create | Control + challengers (seeds or decimal mutation). | experiment-flow | create |
| s2 | Start / metrics | RUNNING with recorded metrics. | experiment-flow | metrics |
| s3 | Complete | Explicit winner or highest score. | experiment-flow | complete |
| s4 | Apply | Role gate then createDraftRule only. | experiment-flow | apply |

## What shipped vs skipped {#scope}

| Item | Status | Notes |
| --- | --- | --- |
| Schema + JPA | Shipped {good:Done} | `rule_experiment`, `rule_experiment_variant` |
| CRUD + metrics APIs | Shipped {good:Done} | create / start / complete / metrics / list |
| Apply-winner approval | Shipped {good:Done} | DEAL_DESK or ADMIN; DRAFT only |
| RL / ParameterExtractor | Skipped {caution:Out} | Local decimal mutator instead |
| Sequential / Context7 / Puppeteer MCP | Skipped {caution:Out} | Explicitly deferred |
| Temporary KieSession | Still open {caution:Open} | Wave 2 leftover |

## Enablement {#flags}

@callout id=flag-off level=warning title="Default off" text="Set OPENCRM_AI_EXPERIMENTS_ENABLED=true only for local/offline use. Do not enable in prod without a forge wave."
