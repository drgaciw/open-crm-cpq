# Forge wave backlog (synthesized)

**Status:** **HISTORICAL** — Wave 1 plan from early forge. **Do not** treat as current work.  
**Current:** Waves **A–N COMPLETE**; hygiene N′ COMPLETE; **Wave O FORGING** — [docs/ARCHITECTURE.md](../ARCHITECTURE.md), [23](./23-wave-n-architecture.md)–[26](./26-wave-o-architecture.md).  
**Sources (original):** `docs/forge/01-crm-api-parity.md` … `06-gateway-ci.md` + scout JSON.

## Executive summary

Prioritize **CRM cutover hardening** (convert navigation, search unwrap, Lead/Deal List+PageInfo, quote→WON idempotency), **interleave gateway/CI safety** (fast-path gateway unit + blocking drift), then **CPQ contract docs/tests**, then **FE CPQ Nx scaffold**. Stack FE unwrap before BE search-shape unify. Keep convert as **LeadDto + convertedDealId** (FE-only). Defer activities API, S2B-11 validate rewrite (until ADR Accepted), and full CPQ UI vertical slice to later batches.

## Parallel batches (conflict-free)

| Batch | PR IDs | Rationale (disjoint surfaces) |
|-------|--------|-------------------------------|
| **1** | W1-01, W1-04, W1-05, W1-06, W1-07 | FE leads convert ∥ CRM Redis listener ∥ CI scripts ∥ CPQ docs/skills |
| **2** | W1-02, W1-09, W1-10, W1-11 | CRM FE unwrap ∥ Redis IT ∥ gateway tests ∥ proxy.conf |
| **3** | W1-03, W1-12 | BE Lead/Deal search shape ∥ new `libs/cpq` scaffold+models |
| **4** | W1-08 | CRM parity/activities docs after convert + search contracts settle |

## Ordered backlog (wave 1 — max 12)

| ID | Title | Track | Depends | Acceptance (short) |
|----|-------|-------|---------|-------------------|
| **W1-01** | fix(crm-fe): lead convert uses convertedDealId + LeadDto | crm-frontend | — | Navigate to `/deals/{convertedDealId}`; parity lists LeadDto; BE lifecycle tests unchanged |
| **W1-02** | fix(crm-fe): unwrap SpringPage/content for search and org contacts | crm-frontend | W1-01 | Search/typeahead/org contacts populate `T[]`; specs cover Page + List+PageInfo |
| **W1-03** | fix(crm-api): unify Lead/Deal search to List+PageInfo | crm-api-parity | W1-02 | All four search endpoints return array + pagination; Lead/Deal IT assert `$.data` array |
| **W1-04** | fix(crm): quote-accepted idempotency poison on missing deal | quote-deal-e2e | — | Missing deal does not leave `crm:quote-accepted:processed:*`; replay after create applies WON |
| **W1-05** | ci: run gateway-service unit tests on fast backend-unit path | gateway-ci | — | `run-core.sh backend-unit` runs `CpqGatewayRoutesTest`; Docker-free |
| **W1-06** | ci: run contract drift verify in toolchain (blocking, non-AI) | gateway-ci | — | toolchain invokes `verify-drift.py`; no ai-assist on GHA; ADR-003 unchanged |
| **W1-07** | docs(cpq): refresh matrix for pricebook + align agent skill URLs | cpq-contracts | — | Matrix includes `/api/v1/price-books` + :8089; skills stop citing `/pricing/price-books` |
| **W1-08** | docs(crm): refresh parity matrix + encode activities deferral | crm-api-parity | W1-01, W1-03 | Convert/search/list envelopes accurate; `qualifiedAt` noted; activities Phase-2 only |
| **W1-09** | test(crm): Redis Testcontainers IT for QuoteAccepted → WON | quote-deal-e2e | W1-04 | `-Pintegration-tests` Redis E2E; second message no-op; CI green with Docker |
| **W1-10** | test(gateway): agent-contract route coverage + ERP 501 HTTP assertions | gateway-ci | W1-05 | Predicate/stub regressions fail CI; ERP paths return 501 JSON; route-id coverage expanded |
| **W1-11** | chore(fe): point frontend proxy at API gateway :8080 | crm-frontend | — | `/api` → `:8080`; README documents `:8081` override for CRM-only DX |
| **W1-12** | chore(fe-cpq): scaffold Nx cpq libs, shared models, and aliases | fe-cpq-ui | W1-02, W1-11 | `cpq-*` projects + `@frontend/cpq-*` paths; product/quote/pricing models; `nx build crm` green |

## Held for wave 2+ (not in the 12)

- Optional CRM list filters / UNQUALIFIED tab / kanban stage actions / CRM Vitest suite expansion
- Staging smoke write-path (quote accept → deal WON) + bash CRM/ERP smoke
- MockMvc agent-contract smoke (product/pricing/quote); expand `CpqAgentToolClients`
- S2B-11 structured `{valid,violations[]}` validate (**ADR still Proposed**)
- Quote `dealExternalId` hard validation + deprecate unwired `acceptQuote`
- Gateway compose/dev URI alignment + 8086 port decision
- FE CPQ HTTP services + catalog/quotes/pricing vertical slice + shell nav
- Dashboard `DealService.loadPipelineSummary` consolidation

## Do not touch (wave 1)

- `/api/v1/activities/**` (no Spring controller — Phase 2)
- Changing convert API to return `DealDto` (prefer FE fix; breaks IT)
- S2B-11 configuration validate ownership/shape until ADR Accepted
- Wiring legacy `QuoteService.acceptQuote` Kafka-only path
- Renaming/removing `QuoteAcceptedCrmMessage` fields without quote+crm(+ai) rollout
- Mass `BaseCrudService` adoption for CRM/CPQ FE
- Merging catalog vs pricing `ProductType` enums
- document-generation vs ai-service port **8086** without product decision
- Inventing live ERP adapter bodies (keep gateway **501** stubs)
- Adding ai-assist / LLM gateway to GitHub Actions
- Frappe `cpq_beta` big-bang migration / inventing communication microservice
- High-blast `gateway-service` `application.yml` predicate rewrites without paired HTTP tests

## Implementation rules (when approved)

1. Each PR in an isolated git worktree.
2. Run `npx gitnexus analyze` if `.gitnexus` absent; `gitnexus_impact` before symbol edits; stop on HIGH/CRITICAL until confirmed.
3. No push/PR unless explicitly requested.
4. Conventional Commits on feature branches after local verify.
5. Re-run relevant `mvn` / `nx` / `scripts/ci/*` per acceptance.

## Next gate question

Approve **Batches 1–4** (twelve items above) to flip forge agents from plan → implement?
