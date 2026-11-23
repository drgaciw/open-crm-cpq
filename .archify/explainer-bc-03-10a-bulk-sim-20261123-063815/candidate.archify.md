# Multi-context pricing simulation is a UI wire-up, not a new API {#bc-0310a}

Ship **BC-03-10A** by adding a Bulk tab that pastes JSON contexts into existing `POST /api/v1/pricing/simulate/bulk`; leave single-context `execute` alone and defer comparison to BC-03-10B.

- Backend `simulateBulk` and FE `PricingExecutionService.simulateBulk` already land; the simulator page never calls them.
- Locked decisions: JSON paste/upload, ≥2 contexts, partial failures visible, comparison deferred.
- Risk is low: backend impact LOW; FE method has no UI callers yet.

@answer scope="BC-03-10A · Pricing Simulator · open-crm-cpq · Angular + Spring"
@meta locale=en

## What already exists vs what we build? {#gap}

| Layer | Landed | This story |
| --- | --- | --- |
| Backend bulk API | `simulateBulk` on pricing-service {good:Done} | No change |
| FE models + data-access | `BulkPricingSimulationRequest` + `simulateBulk()` {good:Done} | Call from UI |
| Simulator page | Single form → `execute` {caution:Single only} | Bulk tab + JSON |
| Side-by-side deltas | Backlog sketch only | Defer → BC-03-10B {caution:Out} |

## How does a bulk run move? {#flow}

@sequence id=bulk-sim-flow title="Bulk simulate request path"
| actor admin | Pricing admin | external |
| actor ui | BulkSimulatorComponent | frontend |
| actor svc | PricingExecutionService (FE) | frontend |
| actor gw | API gateway :8080 | backend |
| actor pricing | pricing-service :8082 | backend |
| admin | ui | Paste ≥2 PricingContext JSON (client validates) |
| ui | svc | simulateBulk({ contexts }) |
| svc | gw | POST /api/v1/pricing/simulate/bulk |
| gw | pricing | Forward (simulate each, no audit) |
| pricing | svc | BulkPricingSimulationResponse | return |
| svc | ui | results[] + counts | return |
| ui | admin | Summary chips + DataTable rows | return |

@steps id=bulk-sim-steps
| paste | Paste ≥2 PricingContext JSON (client validates) | Admin pastes array or `{ "contexts": [...] }`; UI blocks bad JSON or fewer than two. | bulk-sim-flow | admin |
| post | POST /api/v1/pricing/simulate/bulk | One request; gateway routes to pricing-service. | bulk-sim-flow | gw |
| forward | Forward (simulate each, no audit) | Service runs each context without persisting audit. | bulk-sim-flow | pricing |
| table | Summary chips + DataTable rows | Show success/fail per row; keep partial failures. | bulk-sim-flow | ui |

## Implementation checklist {#checklist}

- [ ] Add `BulkSimulatorComponent` under `libs/cpq/pricing-simulator`
- [ ] Mode switch on `SimulatorPageComponent` (Single vs Bulk)
- [ ] Vitest: invalid JSON, `<2` contexts, one `simulateBulk` call, partial-fail table
- [ ] UAT: paste ≥2 seeded contexts; one bad row still shows siblings OK
- [ ] Re-run GitNexus `impact` before editing symbols
- [ ] Do not change proxy (`:8080`) or Jev/dual-read holds
