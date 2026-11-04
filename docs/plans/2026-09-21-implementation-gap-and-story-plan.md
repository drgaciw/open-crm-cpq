> **Historical snapshot (2026-09-21).** Written against `codex/local-jev-uat-fixes` at `8db28801`, which is 613 commits behind current `main` (`7a38c0e5`). This file records that comparison. It is not the current backlog, security posture, or hold list. Steering is [AGENT.md](../../AGENT.md), [docs/ARCHITECTURE.md](../ARCHITECTURE.md), and [docs/adr/README.md](../adr/README.md). Do not file the §5 stories from this text without re-checking those sources. Gateway dev-profile CSRF was not disabled on this replay: current `main` keeps CSRF on in dev (ADR-016) and issues the `XSRF-TOKEN` cookie.

# Open CRM/CPQ — implementation gap and story plan

**Date:** 2026-09-21  
**Branch observed:** `codex/local-jev-uat-fixes` @ `8db28801`  
**Role:** project comparison of landed code, JEV acceptance, GitHub, Notion, and Linear.  
**This file does not authorize holds.** Forge holds in [docs/ARCHITECTURE.md](../ARCHITECTURE.md) §9 stay held until a wave plan un-holds them.

## 1. Verdict

Forge waves **A–W are complete** in the repo. The product is not an empty modernization. The tracking systems the prompt asked to compare **do not contain this program**.

| System | What it actually contains | Open CRM/CPQ roadmap |
| --- | --- | --- |
| Git (`AGENT.md`, `docs/ARCHITECTURE.md`, `docs/adr/`, `docs/forge/00-orchestrator-status.md`, `docs/plans/`) | Landed vs held vs harvest queues | **Source of truth** |
| GitHub `drgaciw/open-crm-cpq` | Two open issues, both holds: [#238](https://github.com/drgaciw/open-crm-cpq/issues/238) CI runners, [#241](https://github.com/drgaciw/open-crm-cpq/issues/241) agent Kafka | No story board for UAT, auth, or harvest code |
| Linear team `Drgamtd-workspace` | Projects: cmano-clone, Combat Interaction UX, Unity C# Architect, UCA Adoption, Platform Design Assistant | **No CRM/CPQ project, milestone, or issue** |
| Notion | Hub is [Project Aegis](https://app.notion.com/p/3a7f7cb4e4df811d814fc31384674e46) (cmano-clone). Searches for `open-crm-cpq` and `Frappe CRM Angular quote` returned no CRM pages | **No CRM/CPQ roadmap page** |

`docs/ARCHITECTURE.md` §9 already records “Linear/Notion Open CRM: none.” A fresh search on 2026-09-21 confirms that. Do not copy Aegis milestones into this backlog.

## 2. How this was checked

Parallel work, then a single synthesis:

| Lane | Scope | Result used here |
| --- | --- | --- |
| GitNexus | Repo path `C:\Users\dgorn\cpq-crm\open-crm-cpq` (the name is shared by many clones) | Workspace meta: indexed 2026-09-19 at `baf4a27` on `main` — 1957 files, 16946 symbols, 300 processes, **0 embeddings**. A context dump of 2143 files / 18643 symbols matches the `oms-acceptance` clone, so those counts are not this tree. Keyword search was degraded (FTS missing; semantic search empty). |
| JEV | `docs/uat/2026-09-19-JEV-LOCAL-UAT.md` and `docs/uat/COMPREHENSIVE-JEV-PLAYWRIGHT-UAT-PLAN.md` | Bounded local evidence, not platform acceptance |
| Requirements SSOT | `AGENT.md`, `docs/ARCHITECTURE.md`, ADR index, forge orchestrator | Landed / held / target split |
| Harvest plans | `docs/plans/00-harvest-index.md` plus D1 and P1 backlogs | Docs exist; code queues still gated |
| Linear | `list_teams`, `list_projects`, issue search for CRM/CPQ, open-crm, quote/catalog | Aegis-only |
| Notion | Keyword search (AI search is not on this connection) | Aegis-only |
| [Requirements inventory](b243fead-1043-46da-bb5a-59fd072e85b7) | Steering docs, ADRs, PRDs | Confirmed the landed/held split. Added PRD and NgRx contradictions in §4.4 |
| [JEV UAT gap extract](e51d6146-a500-4002-9967-05968859d862) | UAT plan, 2026-09-19 evidence, e2e runners | Confirmed the thin slice. Added UAT-6 and UAT-7 |
| [Landed feature surface](6f5a8f3d-d8b7-461d-9146-bcf1374de793) | Reactor, routes, stubs | All 11 reactor services are real apps. Added the stub and placeholder rows in §4.2 |
| [GitNexus implementation map](39918619-6ef4-4e52-8250-6d060aacaf55) | Path-scoped clusters and the longest processes | Confirmed CRM, catalog, price book, quote, Drools, and configuration flows. Corrected the index counts above. AI classes exist and stay flag-off; no RAG or identity-provider flow |

Index repair (`gitnexus analyze --repair-fts`) is hygiene. It is not a product story.

## 3. Landed (do not re-open as new build)

Waves A–W and hygiene N′ are complete. Wave T was skipped; do not name T1/T2 as forge work. Harvest track names T1/T2 are pricing queues, not Wave T.

| Surface | Landed |
| --- | --- |
| Backend | Spring Boot 4.0.6 / Java 25 reactor: crm, gateway, product, pricing, pricebook, quote, configuration-engine, approval-routing, document-generation, contract-asset, ai-service |
| Frontend | Angular 22 Nx app `frontend/apps/crm`. Signals + HttpClient. Sidebar: dashboard, orgs, contacts, leads, deals, catalog, quotes, approvals, price books, pricing customers, rule sets, bundles, simulator |
| Data | PostgreSQL 16, one database `opencrm`, schema per service. Redis 7 |
| Rules | Drools on pricing-service. Configuration-engine product rules. Queue A Drools harvest closed (`a4c5e80`, GitHub #505–#511) |
| AI runtime | `ai-service` on Azure OpenAI `gpt-4o`. The graph shows agent classes (pricing, orchestration, approval routing) and `InvokeWorkflow` traces. `AgentRouter` is flag-gated off. No Copilot or RAG community. ADR-002 GLM-5 is Accepted and **not** the runtime |
| API boundary | ADR-006 Accepted. Subscription, configuration-session, approval-request, and document-job DTOs exist. Subscriptions have **no** UI |
| Security | ADR-004 Accepted: CSRF on the gateway only. Azure AD B2C **`enabled: false`** |
| JEV baseline (2026-09-19) | Navigation to catalog, organizations, quotes, simulator. Playwright smoke 21/21 including synthetic CRM creates. Product create persisted after a Jackson-default fix. Catalog/quotes failed-load UI no longer looks empty-success. Pricebook outbox wiring and pricing YAML binding fixed |

`MODERNIZATION_PLAN.md` still describes a greenfield brochure (Boot 3.5.9, Angular 21, Wave O “forging”). Treat it as historical target text. `docs/plans/00-harvest-index.md` still says Java 21 and still marks deal-desk and price-book matrices as unwritten, while those files are already in `docs/plans/`. Refresh the index; do not regenerate the matrices.

## 4. Gaps that are real

### 4.1 Acceptance gap (JEV) — not in Linear or Notion

The comprehensive plan is proposed coverage, not a completed run.

| Evidence | Meaning |
| --- | --- |
| Pricing customers, rule sets, and price books return **anonymous 403** | Protected CPQ transactions are unverified. The gateway **dev** profile is permit-all; the 403 is enforced on the domain services. UAT-1 is a test issuer for those services. Production stays fail-closed. No new `permitAll` and no production flag change |
| Jev product **Create** stopped at confidence **0.55** (earlier 0.41) under a **0.90** guard | The form was filled. Submission did not run. This is an agent-action gap, not an app defect, unless a later deterministic test fails |
| Smoke 21/21 | CRM creates and CPQ shell checks. It is not J1 (org → product → quote → line → calculate → reload) |
| Runtime that was up | Gateway, CRM, product, pricing, quote, pricebook. Approval-routing, configuration-engine, document-generation, contract-asset, and ai-service were **not** in that health set |
| Coverage ledger | Plan step 1 (route/action/component/controller ledger + `coverage.csv`) is not delivered |
| Catalog/quotes load failure vs organizations | Organizations expose **Retry**. Catalog exposes **Dismiss**. A 2026-09-19 navigation snapshot showed a 502 banner together with “No products yet” / empty quote CTA. The 503 fixture tests passed later. UAT-6 and UAT-7 confirm the live path |
| `uat-agentic-run.mjs` listPrice minimum | The 2026-09-15 miss is covered by the current dialog `mat-error`. Re-run once. No new story unless that run fails |

### 4.2 Target capabilities with no tracking issue

These are in the 16-month target and in `AGENT.md` phases. They are absent from Linear and Notion. Several are also explicit holds; those get **Held** status, not a sprint.

| Capability | Implementation | Tracking |
| --- | --- | --- |
| Activities API | No schema, controller, or UI | Hold. Do not invent `/api/v1/activities/**` |
| NgRx | Not in the app. Signals + HttpClient | Hold |
| Subscriptions / contract-asset UI | DTO API only. The GitNexus community named Contract is configuration parity tests, not this service | Hold |
| Dual-read flip (`APPROVALS_LEGACY_LOCAL_READ`) | Default true. ADR-005 Proposed | Hold. HIGH risk |
| Full SpEL / OpenL | `OpenLRulesEngine` throws and tells callers to use Drools. Drools `execute(context)` also throws when no rule list is supplied; the rules-list overload runs | Hold. The placeholder is not a started OpenL port |
| Kafka S2B-9 and agent Kafka #241 | Not the current bus | Hold. GitHub #241 already open |
| CI runners #238 | Jobs do not acquire a runner | Hold. GitHub #238 already open |
| Gateway domain validation; delete product `POST /validate` | Route/rewrite only; product validate retained | Hold |
| R2 price-book list-row delete | Header delete exists; list-row delete does not | Hold |
| GLM-5 runtime | Not landed | Hold until a new runtime decision |
| WhatsApp #430 undo | Steering says the change stays on `main`. The Spring and Angular trees have no WhatsApp types; the live client is still `cpq_beta` | Do not add an undo story. A modern WhatsApp port stays in the held communication hub |
| Customer portal and admin console | Not in `frontend/apps` | Future. JEV J6 is the in-app admin analogue; do not assume separate portals |
| Identity service / Azure AD B2C | Gateway JWT config; B2C off | Production identity is future. **Local test identity is a new P0** (below) |
| Elasticsearch | List + `PageInfo` only | Future |
| `communication-service` in the reactor | Gateway routes `/emails`, `/calls`, `/templates` to `lb://communication-service`. That module is not in the reactor. The dev profile drops the communication and AI routes | Future. Do not treat the route as a live inbox |
| ERP adapter | Gateway `/api/v1/erp/**` returns **501**. `agent-prd.md` places `erp-adapter-service` on port **8085**, which approval-routing already owns | Future, and the PRD port is wrong. Do not build it from the PRD |
| AI agents enabled | `opencrm.ai.agents.enabled` defaults **false**, so invoke returns **503**. Runtime model remains Azure `gpt-4o` | UAT-5 records the skip. Enabling agents is a separate decision, not a silent flag flip |
| Azure AKS / Terraform / Helm | Compose + Jenkins | Future |
| Frappe data migration | Legacy tree is reference (`cpq_beta/`) | Future |
| pgvector RAG | Extension commented until needed | Future. Depends on an AI ADR, not a silent enable |

### 4.3 Harvest code that is specified and still gated

Docs for the next reuse slices exist. The harvest index still says “authorized now: docs only” and leaves C3/E3 **un-held**. Do not start the code until an architect un-hold says the docs have merged and the gate is open. They are also absent from Linear.

| ID | Story | Gate |
| --- | --- | --- |
| D1-1 | Approval/quote transition living spec (test-first) | C3 hold |
| D1-2 | Invalid-state guards and error codes | After D1-1 |
| D1-3 | Quote cancel cascades the approval request | After D1-2 |
| P1-1 | Price-book entry effective windows and overlap reject | E3 hold |
| P1-2 | Resolve-batch as-of plus pricing regression | After P1-1 |
| P1-3 | Publish immutability tests | Parallel with P1-1 after un-hold |
| P1-4 | Selective header update | Residual. Not the first slice |
| Queue D | TCV handoff doc only | After C-GATE. Do not reopen Drools T1 |
| Queue F | Price-book P2 coverage, bulk, health, filters, UX | After E-GATE |
| Queue B | Spring AI / Path B inside pricing | Hold. No AI in `pricing-service` |

### 4.4 Doc contradictions to record, not to build

| Source | Conflict | What to do |
| --- | --- | --- |
| `MODERNIZATION_PLAN.md` | Boot 3.5.9, Angular 21, “Wave O forging” | TRK-2. Steering says Boot 4.0.6, Java 25, Angular 22, waves A–W complete |
| `AGENT.md` phase tables | Phase 2 lists NgRx as work | The same file holds NgRx. Held list wins |
| `agent-prd.md` | 37 agents, Kafka agent mesh, `erp-adapter-service` on 8085 | Product vision. Approval-routing already uses 8085. Do not file 37 agent stories |
| `docs/adr/ADR-002` | Accepted GLM-5, and a note that `AGENT.md` still mentions Claude | Runtime stays Azure `gpt-4o` until a new decision |

## 5. Stories to add

Add these to a **new** Linear project and a **new** Notion hub. Do not file them under cmano-clone or Project Aegis.

### 5.1 Add now (ready, not a forge hold)

| ID | Title | Why it is missing | Acceptance |
| --- | --- | --- | --- |
| TRK-1 | Create the Open CRM/CPQ Linear project and Notion hub | Neither system has this roadmap | Project description points at `docs/ARCHITECTURE.md`. Aegis pages are linked as a different product. Holds are labeled Held |
| TRK-2 | Refresh harvest index status | Index still says D1/P1 matrices are unwritten and pins Java 21 | Index matches the files on disk and the Java 25 / Boot 4.0.6 landed line. No production code |
| UAT-1 | Supported local identity for protected CPQ calls | Anonymous 403 blocks pricing, price books, and rule sets | A documented test issuer works through the browser, gateway, and domain services. Production stays fail-closed. No `permitAll`, no B2C default flip, no CSRF move onto domain services |
| UAT-2 | Freeze the JEV coverage ledger | Plan step 1 is not delivered | `coverage.csv` (or equivalent) maps every inventoried route/component to a case or an explicit N/A, including holds |
| UAT-3 | Deterministic journeys J1–J3 | Smoke does not prove quote calculate or approval | J1 org/contact/deal → product → quote → line → calculate → reload. J2 one pricing change with a control quote. J3 one consistent approval decision. Each has an API postcondition |
| UAT-4 | Jev Create-action experiment | Submit stopped at 0.55 under the 0.90 guard | Frozen cases. Compare action wording and observation shape. Do not lower 0.90 to force a pass. Report agent stops separately from app defects |
| UAT-5 | Readiness for the services the 2026-09-19 run did not start | Approval, CE, documents, contract-asset, AI | Each relevant service: migrations, one representative authenticated call, and a recorded skip if a broker or provider is absent. Actuator 200 alone is not readiness. AI invoke stays a recorded 503 while agents are disabled |
| UAT-6 | Retry on catalog and quotes load failure | Organizations already retry. Catalog only dismisses | A failed first list load shows Retry, reloads the same request, and hides empty-state CTAs until a successful empty response |
| UAT-7 | Live 502/503 does not look like an empty catalog or quote list | Navigation snapshot mixed a 502 banner with empty-state copy. Fixture 503 tests already pass | A routed 502 and 503 on the first list fetch shows the error banner only. If the current UI already does this, close the story with that trace |

### 5.2 Add as Held (so they are visible and not rebuilt by accident)

Mirror into Linear with status Backlog and label `held`. Link the existing GitHub issues where they exist. **Do not schedule them.**

- Activities API
- NgRx
- Subscriptions UI
- ADR-005 dual-read flip
- Full SpEL / OpenL
- Kafka S2B-9
- GitHub #238 CI runners
- GitHub #241 agent Kafka
- Gateway domain validation
- Delete product-service validate
- R2 price-book list-row delete
- GLM-5 as runtime
- Customer portal
- Admin console
- Elasticsearch
- Communication hub (Twilio / email / WhatsApp) beyond current stubs
- Production identity service
- AKS / Terraform / Helm
- Frappe migration
- pgvector RAG
- Harvest D1-1, D1-2, D1-3, P1-1, P1-2, P1-3 until C3/E3 are explicitly un-held

### 5.3 Do not add

- A story to undo WhatsApp #430
- Thirty-seven agents, or an ERP service on port 8085, taken from `agent-prd.md`
- Forge Wave T / T1 / T2
- Re-opening Drools Queue A (T1-1…T1-5)
- Any issue under the Aegis Linear projects
- A duplicate of #238 or #241 on GitHub. Linear links are enough

## 6. Recommended sequence

```text
TRK-1  Linear project + Notion hub
TRK-2  Harvest index refresh (docs)
UAT-1  Local test identity          ← blocks protected CPQ proof
UAT-2  Coverage ledger              ← can run beside UAT-1
UAT-3  Deterministic J1–J3          ← after UAT-1
UAT-5  Remaining service readiness  ← beside UAT-3 where auth is shared
UAT-6  Catalog/quotes Retry         ← independent of auth
UAT-7  Live 502/503 empty-state     ← close immediately if the trace is already clean
UAT-4  Jev experiment               ← after deterministic oracles exist
```

Harvest C3/E3 stays behind an architect un-hold. UAT must not flip dual-read, un-hold R2, or delete product validate in order to go green.

## 7. Notion and Linear shape (when TRK-1 is approved)

**Linear project:** Open CRM/CPQ. Team: `Drgamtd-workspace` only if a second team is not wanted. Milestones:

1. Acceptance unblock (UAT-1…UAT-7)
2. Held target (the §5.2 list, progress not a delivery signal)
3. Harvest code (empty until C3/E3 un-hold; then D1-1→D1-3 and P1-1→P1-3)

**Notion:** one private hub, not a child of Project Aegis. Pages: Landed vs held (copy of architecture §2 and §9), JEV ledger, Harvest gates. Link Linear issues. Do not paste `MODERNIZATION_PLAN.md` week-by-week as current work.

## 8. Risks

- Filing Aegis-shaped stories into this repo, or CRM stories into Aegis, will split the backlog again.
- Treating Jev’s confidence stop as a product bug will produce the wrong fix.
- The gateway dev profile is already permit-all. Clearing the domain 403 by widening that bypass would fail the UAT plan’s own P0 rule.
- Filing the 37-agent PRD, or an ERP service on 8085, would collide with approval-routing and with the AI hold.
- The workspace GitNexus index has no embeddings, is one commit behind, and its longest UI clusters are the Vue beta. A missing Angular or portal flow in that window is not proof the Angular screens are absent. Use `docs/ARCHITECTURE.md` and the route map for the UI.
- Harvest index Java 21 text disagrees with landed Java 25. Implementers should follow `docs/ARCHITECTURE.md` until the index is corrected.
