# Track 05 — Frontend CPQ UI Scaffold

**Agent:** `fe-cpq` (forge scout)  
**Mode:** plan / discovery only (no application source edits)  
**Date:** 2026-08-30  
**Primary sources:** `frontend/` tree, `frontend/README.md`, `frontend/PRICING_IA_BACKLOG.md`, CRM libs vs backend CPQ services

---

## 1. Track name + scope

| Field | Value |
|-------|--------|
| **Track** | Frontend CPQ UI scaffold (`05-fe-cpq-ui`) |
| **Goal** | Establish Nx library layout for **catalog / quote / pricing**, maximize **shared UI reuse**, and define a **first vertical-slice PR sequence** that lands an end-to-end CPQ path in the existing `apps/crm` shell |
| **In scope** | `libs/cpq/**` scaffolding plan; TS models for product/quote/pricing; data-access services; presentational pricing UI; feature modules (catalog browser, quote list/builder, pricing operate/configure); shell routes + sidebar IA; proxy/gateway wiring notes for local serve |
| **Out of scope (this wave)** | Full `PRICING_IA_BACKLOG.md` PRs 9–14 (margins dashboard, discount DRL helpers, SaaS tiers, approval inbox, deep deal-tab quote pricing); configuration-engine wizard UI; document PDF preview; NgRx migration; new Angular apps (`customer-portal`, `admin-console`) |
| **Aligns with** | Orchestrator priority #3 in `docs/forge/00-orchestrator-status.md`; backlog conventions in `frontend/PRICING_IA_BACKLOG.md`; Phase 4 FE features in `MODERNIZATION_PLAN.md` (catalog browser, quote builder, rule UI) |

### Recommended Nx layout (target)

Mirror CRM (`libs/crm/<feature>`) with a **shared pricing data-access** layer (many consumers) plus **catalog** and **quotes** feature libs:

```
frontend/
  apps/crm/                          # existing shell — add /catalog, /quotes, /pricing/**
  libs/
    shared/
      models/                        # + product.model.ts, quote.model.ts, pricing.model.ts
      data-access/                   # reuse BaseCrudService, interceptors (no fork)
      ui/                            # reuse DataTable, PageHeader, badges, pipes (first real consumers)
      utils/
    crm/…                            # DO NOT remodel for CPQ
    cpq/                             # NEW — currently ABSENT
      catalog/                       # @frontend/cpq-catalog — product list/detail/form
      quotes/                        # @frontend/cpq-quotes — quote list/detail/lifecycle
      quote-builder/                 # @frontend/cpq-quote-builder — line items + price panel (later)
      pricing-data-access/           # @frontend/cpq-pricing-data-access — shared HTTP services
      pricing-ui/                    # @frontend/cpq-pricing-ui — waterfall, margin gauge, badges
      pricing-customers/             # @frontend/cpq-pricing-customers
      price-books/                   # @frontend/cpq-price-books
      rule-sets/                     # @frontend/cpq-rule-sets
      pricing-simulator/             # @frontend/cpq-pricing-simulator — critical-path UX
      pricing-audit/                 # @frontend/cpq-pricing-audit (wave 2+)
```

**Path aliases** (add to `frontend/tsconfig.base.json`): `@frontend/cpq-*` → `libs/cpq/<name>/src/index.ts` (exact list in backlog §PR 13, plus `@frontend/cpq-catalog`, `@frontend/cpq-quotes`).

**Nx tags (proposed):** `scope:cpq`, `type:feature` | `type:data-access` | `type:ui`, `domain:catalog|quote|pricing`.

---

## 2. Current state (what exists)

### Frontend workspace

| Layer | Exists | Evidence |
|-------|--------|----------|
| Nx app | Yes — `apps/crm` | `npx nx serve crm` / `npx nx build crm` documented in `frontend/README.md` |
| Shared libs | Yes — models, data-access, ui, utils | `frontend/libs/shared/{models,data-access,ui,utils}/` |
| CRM feature libs | Yes — contacts, organizations, leads, deals, dashboard | `frontend/libs/crm/*/`; aliases in `tsconfig.base.json` |
| CPQ libs | **No** | `test -d frontend/libs/cpq` → **cpq ABSENT**; only `libs/crm` + `libs/shared` under `frontend/libs/` |
| Pricing/catalog/quote routes | **No** | `app.routes.ts` only: dashboard, organizations, contacts, leads, deals |
| Sidebar CPQ section | **No** | `sidebar.component.ts` — CRM links only; title "Open CRM" |
| Pricing models | **No** | `shared/models/src/index.ts` exports org/contact/lead/deal only |
| Dev proxy for CPQ APIs | **No** | `proxy.conf.json` → `http://localhost:8081` (CRM only) |
| `node_modules` / Nx runtime | Missing in this workspace snapshot | `npx nx show projects` → *Could not find Nx modules* (need `npm install` before generate) |

### CRM maturity (pattern to copy)

| Pattern | CRM reality | Path / note |
|---------|-------------|-------------|
| Standalone + OnPush feature components | Yes | e.g. `organization-list.component.ts` |
| Lazy `Route[]` from lib | Yes | `DEAL_ROUTES`, `CONTACT_ROUTES`, … wired in `app.routes.ts` |
| Signal-based services | Yes | `DealService`, `ContactService` — `signal` / `computed`, `HttpClient`, `providedIn: 'root'` |
| Models mirror Java DTOs | Yes | JSDoc `Mirrors: com.opencrm.crm…` in `deal.model.ts` etc. |
| ApiResponse unwrap | Yes | `apiResponseInterceptor` in `app.config.ts` |
| Vitest per lib | Yes | `project.json` targets + `*.spec.ts` (~19 specs across shared/crm) |
| Source volume | ~64 non-spec TS files under `libs/crm` + `libs/shared` | `find … \| wc -l` |

### Shared platform that CPQ should reuse

| Asset | Alias | Status |
|-------|-------|--------|
| `BaseCrudService` | `@frontend/shared-data-access` | Implemented + documented; **no CRM feature `extends` it yet** (CRM uses hand-rolled HttpClient services) |
| Interceptors (apiResponse, error, loading) | same | Wired in `app.config.ts` |
| `DataTableComponent`, `PageHeaderComponent`, `EmptyStateComponent`, `StatusBadgeComponent`, `SearchInputComponent`, `ConfirmDialogComponent` | `@frontend/shared-ui` | Built + exported; **CRM lists do not import them** (inline MatTable + local empty states) |
| `CurrencyFormatPipe`, `DateAgoPipe` | `@frontend/shared-ui` | Ready for pricing amounts / audit timestamps |
| Form validators / query helpers | `@frontend/shared-utils` | Available |

### Backend CPQ surfaces (FE must call via gateway)

| Domain | Service | Base path | Default port (gateway) |
|--------|---------|-----------|-------------------------|
| Catalog | `product-service` | `/api/v1/products` (+ bundles/options/attributes/configuration) | 8083 |
| Quotes | `quote-service` | `/api/v1/quotes` (+ line-items, documents, approve/send/…) | 8084 |
| Pricing | `pricing-service` | `/api/v1/pricing`, `/pricing-customers`, `/rule-sets`, `/rules` | 8082 |
| Price books | `pricebook-service` | `/api/v1/price-books` | 8089 |

Gateway also exposes `/api/v1/cpq/...` rewrite aliases (`application.yml`). Frontend should prefer **canonical** `/api/v1/{resource}` paths (same as CRM), with proxy pointed at **gateway** (not CRM :8081) once CPQ routes are used.

### Existing FE backlog

`frontend/PRICING_IA_BACKLOG.md` is a **14-PR pricing-centric** plan (models → data-access → pricing-ui → customers/price-books/rules/simulator/audit/… → nav → quote-builder). It correctly mirrors CRM conventions (`@frontend/cpq-*`, signals, Material + Tailwind, Vitest) but **under-scopes catalog** (only lightweight `PricingProductDto`) and treats **quote UI as late PR 14**. This forge track expands layout to **catalog + quotes + pricing** and front-loads a thinner vertical slice.

---

## 3. Gaps with evidence (paths / commands)

| Gap | Evidence | Impact |
|-----|----------|--------|
| No `libs/cpq` tree | `ls frontend/libs` → only `crm`, `shared`; `test -d frontend/libs/cpq` → ABSENT | Every CPQ UI PR must start with Nx generate + aliases |
| No CPQ path aliases | `frontend/tsconfig.base.json` paths — CRM + shared only | Imports blocked until aliases land |
| No pricing/product/quote TS models | `frontend/libs/shared/models/src/index.ts` — no `pricing`/`product`/`quote` exports; `rg` for catalog/quote under `frontend/**/*.ts` hits backlog markdown only | Cannot type services or forms |
| No CPQ routes / nav | `apps/crm/src/app/app.routes.ts`, `layout/sidebar.component.ts` | Users cannot reach CPQ even if libs exist |
| Proxy cannot reach product/quote/pricing | `frontend/proxy.conf.json` targets `:8081` only; gateway routes product `:8083`, pricing `:8082`, quote `:8084` | Local `nx serve crm` will 404/proxy-miss CPQ APIs |
| Shared UI unused by features | `rg -l DataTableComponent\|PageHeaderComponent…` → only `libs/shared/ui` itself + README | CPQ is the chance to **adopt** shared UI; otherwise duplicate MatTable patterns |
| `BaseCrudService` unused by CRM | `rg -l 'extends BaseCrudService'` → only base + USAGE_EXAMPLE | Backlog assumes extend BaseCrud; either adopt it in CPQ services **or** follow CRM hand-rolled pattern for consistency — decide in PR-02 |
| ProductType enum drift (catalog vs pricing) | `product-service` `ProductType` (e.g. `SAAS_FLAT_RATE`, `HARDWARE`, …) ≠ `pricing-service` `ProductType` (e.g. `SAAS_MAU`, `HYBRID_MIGRATION_RAMP`, …) | Blind copy of backlog enums will break catalog DTOs; need **domain-prefixed** or separate model files |
| Backlog omits first-class catalog/quotes libs | `PRICING_IA_BACKLOG.md` PR 1–13 pricing; PR 14 quote-builder only | Vertical slice needs catalog browse + quote draft before full pricing admin |
| Nx generate blocked until install | `npx nx show projects` → modules missing | Scaffold PRs require `npm ci` / `npm install` in `frontend/` |
| CRM services don’t share Deal↔Quote UI | No quote references in `libs/crm/deals` | Deal “Pricing” tab (backlog PR 14) is cross-cutting — defer |

### Commands used in discovery

```bash
# Tree / absence
find frontend/libs -type d -maxdepth 3
test -d frontend/libs/cpq && echo exists || echo 'cpq ABSENT'

# Counts
find frontend/libs/crm frontend/libs/shared -name '*.ts' ! -name '*.spec.ts' ! -path '*/test-setup.ts' | wc -l   # 64
find frontend/libs/crm frontend/libs/shared -name '*.spec.ts' | wc -l   # 19

# Reuse reality
rg -l 'DataTableComponent|PageHeaderComponent|EmptyStateComponent|StatusBadgeComponent' frontend/libs frontend/apps
rg -l 'extends BaseCrudService' frontend/libs

# Backend API anchors
rg -n '@RequestMapping' backend/services/{product,quote,pricing,pricebook}-service -g '*Controller*.java'
```

---

## 4. Blast radius / risk notes

### Shell / shared touchpoints (any CPQ integration PR)

| Change target | Blast radius | Risk |
|---------------|--------------|------|
| `apps/crm/.../app.routes.ts` | All lazy children under shell | **Medium** — bad import breaks CRM boot |
| `apps/crm/.../sidebar.component.ts` | Global nav | **Medium** — IA change; keep CRM links intact |
| `tsconfig.base.json` paths | Entire FE compile graph | **Low–Medium** — additive aliases preferred |
| `libs/shared/models` new files + `index.ts` exports | All consumers of `@frontend/shared-models` | **Low** if additive; **High** if renaming CRM models |
| `proxy.conf.json` → gateway | Local CRM + CPQ API traffic | **Medium** — wrong target breaks existing CRM serve |
| `libs/shared/ui` API changes | Future CRM + CPQ | **Medium** — prefer consume-as-is; extend columns/inputs carefully |
| `libs/crm/deals` Pricing tab (late) | Deal detail UX | **High** — defer until quote-builder stable |

### Domain / contract risks

1. **Enum / DTO drift:** Catalog `ProductType` ≠ Pricing `ProductType`. Keep `product.model.ts` aligned to `com.opencrm.product.catalog.*` and `pricing.model.ts` to `com.opencrm.pricing.*`; do not merge enums.
2. **ApiResponse shapes:** Product/Quote controllers return `ApiResponse<Page<T>>`; after interceptor, services must handle `SpringPageResponse<T>` like deals/leads (see comments in `api-response.model.ts`).
3. **Identity keys:** Quotes use `customerExternalId` / `dealExternalId` / product `sku` or `externalId` — UI must not assume CRM numeric FKs everywhere.
4. **Gateway vs direct ports:** Production path is gateway; local proxy must not hardcode only CRM.
5. **PRICING_IA_BACKLOG phantom-endpoint lesson:** Prefer endpoints proven in controllers (matrix in backlog §Backend API Coverage); do not invent margin analysis APIs.
6. **Shared UI first consumer:** Adopting `DataTableComponent` in CPQ may surface component gaps (actions column, custom cell templates). Budget small shared-ui fix PRs rather than forking tables inside CPQ.

### Risk posture for wave 1

- **Additive FE libs + route children:** Low–Medium overall.
- **Refactoring CRM to use BaseCrud/DataTable in the same PRs:** Unnecessary blast — **do not**.
- **Depends on sibling tracks:** Gateway/CI (`06`) for reliable local proxy; CPQ contracts (`04`) for DTO stability; quote↔deal E2E (`03`) before deep deal-tab integration.

---

## 5. Proposed PRs (title, files, acceptance, risk)

Sized for reviewable vertical progress. Maps to / supersedes early slices of `PRICING_IA_BACKLOG.md` while adding catalog + quotes.

### PR-FE-CPQ-01 — Scaffold `libs/cpq` projects + path aliases

| | |
|--|--|
| **Title** | `chore(fe-cpq): scaffold Nx cpq libs and tsconfig aliases` |
| **Files** | New empty libs under `frontend/libs/cpq/{catalog,quotes,pricing-data-access,pricing-ui,pricing-simulator}/` (minimal `project.json`, `src/index.ts`, `README.md`); `frontend/tsconfig.base.json` paths; optional `nx.json` tag defaults; `frontend/package.json` script `test:cpq` stub |
| **Acceptance** | `npm install` then `npx nx show projects` lists new `cpq-*` projects; aliases resolve; no runtime UI change; CRM still builds |
| **Risk** | **Low** — additive |
| **Notes** | Defer generating every backlog lib (customers, rule-sets, …) until needed — avoid empty project sprawl. Minimum set above unblocks the vertical slice. |

### PR-FE-CPQ-02 — Domain models (product + quote + pricing)

| | |
|--|--|
| **Title** | `feat(fe-cpq): add product, quote, and pricing shared models` |
| **Files** | `libs/shared/models/src/lib/product.model.ts`, `quote.model.ts`, `pricing.model.ts`; export from `index.ts` |
| **Acceptance** | Enums/DTOs JSDoc-mirrored to Java (`ProductDto`, `QuoteDto`, pricing backlog types); **separate** ProductType enums or namespaced types documenting catalog vs pricing drift; unit tests for type guards/label maps if introduced; `shared-models` tests pass |
| **Risk** | **Low** — additive exports |
| **Maps to** | Backlog PR 1 (expanded) |

### PR-FE-CPQ-03 — Data-access: catalog, quotes, pricing services

| | |
|--|--|
| **Title** | `feat(fe-cpq): add catalog, quote, and pricing HTTP services` |
| **Files** | `libs/cpq/catalog/.../*service.ts` (or thin `catalog-data-access` if preferred); `libs/cpq/quotes/...`; `libs/cpq/pricing-data-access/` (`PricingCustomerService`, `PriceBookService`, `RuleSetService`, `RuleService`, `PricingExecutionService` per backlog PR 2) |
| **Acceptance** | Signal state pattern matches `DealService` **or** explicit `extends BaseCrudService` (document choice in lib README); base paths `/api/v1/products`, `/api/v1/quotes`, `/api/v1/pricing*`; Vitest with `HttpTestingController`; no UI |
| **Risk** | **Low–Medium** — contracts must match live controllers |
| **Maps to** | Backlog PR 2 + catalog/quote services |

### PR-FE-CPQ-04 — Dev proxy via gateway + smoke env notes

| | |
|--|--|
| **Title** | `chore(fe-cpq): point frontend proxy at API gateway for CPQ routes` |
| **Files** | `frontend/proxy.conf.json` (target gateway, typically `:8080` if that is gateway — **confirm against compose/docs before merge**); short note in `frontend/README.md` on required local services |
| **Acceptance** | Documented one-command or compose profile; `/api/v1/products` and `/api/v1/deals` both reachable through same proxy; CRM list pages still load against gateway→crm |
| **Risk** | **Medium** — can break local CRM DX if gateway down; mitigate with README + optional dual config |
| **Depends** | Track `06-gateway-ci` / local gateway health |

### PR-FE-CPQ-05 — Shared UI adoption spike (catalog list)

| | |
|--|--|
| **Title** | `feat(fe-cpq): catalog list/detail using shared-ui primitives` |
| **Files** | `libs/cpq/catalog/` list + detail (+ optional form); `CATALOG_ROUTES`; consume `DataTableComponent`, `PageHeaderComponent`, `EmptyStateComponent`, `StatusBadgeComponent`, `CurrencyFormatPipe` |
| **Acceptance** | `/catalog` lazy route works; empty/loading/error states; filters by category/status if API supports; **no** duplicated inline MatTable for the happy path; Vitest for list component |
| **Risk** | **Medium** — may require small shared-ui extensions (cell templates) |
| **Why first feature** | Catalog is the simplest CRUD vertical and proves shared-ui reuse before complex pricing viz |

### PR-FE-CPQ-06 — Quotes list + draft create (lifecycle stubs)

| | |
|--|--|
| **Title** | `feat(fe-cpq): quotes list and draft create with line-item stub` |
| **Files** | `libs/cpq/quotes/` list/detail/form; wire product picker (autocomplete from catalog service); lifecycle buttons calling submit/approve only if safe (or disable until backend ready) |
| **Acceptance** | `/quotes` lists pages; create DRAFT quote with ≥1 line item; detail shows totals fields from `QuoteDto`; uses shared-ui + status badges |
| **Risk** | **Medium** — quote state machine is rich; keep mutations minimal (create/update draft first) |

### PR-FE-CPQ-07 — Pricing UI kit + simulator vertical slice

| | |
|--|--|
| **Title** | `feat(fe-cpq): pricing waterfall UI and simulator sandbox` |
| **Files** | `libs/cpq/pricing-ui/` (`PricingWaterfallComponent`, `FinancialMetricsCardComponent`, `MarginGaugeComponent`, `ApprovalStateBadgeComponent`, …); `libs/cpq/pricing-simulator/`; routes `/pricing/simulator` |
| **Acceptance** | User builds `PricingContext`, calls `POST /api/v1/pricing/simulate`, sees waterfall + metrics + approval badge; reuses pricing-ui; Vitest for waterfall pure rendering |
| **Risk** | **Medium–High** (UX complexity) but isolated route |
| **Maps to** | Backlog PR 3 + PR 7 (critical path) |

### PR-FE-CPQ-08 — Shell navigation IA (Pricing / Catalog / Quotes)

| | |
|--|--|
| **Title** | `feat(fe-cpq): wire catalog, quotes, and pricing into CRM shell nav` |
| **Files** | `app.routes.ts`, `sidebar.component.ts` (persona groups Operate / Analyze / Configure for Pricing; top-level Catalog + Quotes); optional dashboard KPI placeholders |
| **Acceptance** | Sidebar shows Catalog, Quotes, Pricing group; lazy loads work; CRM entries unchanged; deep links documented |
| **Risk** | **Medium** — shell blast radius; keep PR wiring-only |
| **Maps to** | Backlog PR 13 (partial — only shipped modules) |

### Wave-2 PRs (sequence after vertical slice; do not block wave 1)

| PR | Title (short) | Backlog map | Risk |
|----|---------------|-------------|------|
| PR-FE-CPQ-09 | Pricing customers CRUD | Backlog PR 4 | Low |
| PR-FE-CPQ-10 | Price books + entries | Backlog PR 5 | Medium |
| PR-FE-CPQ-11 | Rule sets + rules editor | Backlog PR 6 | High |
| PR-FE-CPQ-12 | Pricing audit viewer | Backlog PR 8 | Low |
| PR-FE-CPQ-13 | Quote builder + execute pricing panel | Backlog PR 14 (slim) | High |
| PR-FE-CPQ-14 | Deal detail Pricing tab | Backlog PR 14 integration | High — coordinate with `03-quote-deal-e2e` |
| Later | Margins, discount helpers, SaaS tiers, approvals inbox | Backlog PR 9–12 | Medium–High |

---

## 6. Parallelization / dependencies

### Dependency graph (wave 1)

```
PR-01 scaffold
  └── PR-02 models
        ├── PR-03 data-access ──────────────┐
        │     ├── PR-05 catalog UI          │
        │     ├── PR-06 quotes UI           │
        │     └── PR-07 pricing-ui+simulator│
        └── (models only feed UI kits)      │
PR-04 proxy/gateway  ── needed before E2E demo of 05–07
PR-08 shell nav ──── after at least one of 05–07 ships (or stub redirects)
```

### Critical path (first demoable vertical slice)

**PR-01 → PR-02 → PR-03 → PR-04 → PR-05 (catalog) → PR-06 (quote draft) → PR-07 (simulate) → PR-08 (nav)**

Minimum lovable demo: browse product → create draft quote with line → open simulator with same product/customer context → see waterfall.

Alternate critical path (pricing-admin-first, per backlog): **PR-01 → PR-02 → PR-03 → PR-07** then customers/price-books — acceptable if catalog APIs unstable, but weaker CPQ story.

### Parallel tracks after PR-03

| Parallel | Owners can split |
|----------|------------------|
| A | Catalog UI (PR-05) |
| B | Quotes UI (PR-06) |
| C | Pricing-ui + simulator (PR-07) |
| D | Proxy/gateway docs (PR-04) with gateway-ci track |

### Cross-track dependencies

| This track needs | From |
|------------------|------|
| Stable OpenAPI/DTO field names | `04-cpq-contracts` |
| Gateway routes + local compose | `06-gateway-ci` |
| Quote accept → deal WON semantics | `03-quote-deal-e2e` (before PR-FE-CPQ-14) |
| Avoid conflicting shell edits | `02-crm-frontend` — serialize sidebar/routes changes |

### Backlog alignment

Keep `frontend/PRICING_IA_BACKLOG.md` as the **pricing feature encyclopedia**; this forge doc is the **scaffold + vertical-slice sequencer**. When implementing pricing-only PRs 4–12, follow backlog acceptance detail; when conflicting, prefer controller-verified contracts + this PR order.

---

## 7. Explicit DO NOT TOUCH list

| Do not touch | Reason |
|--------------|--------|
| `cpq_beta/` (Frappe legacy) | Migration source only; not FE target |
| `backend/services/**` Java/Flyway in FE-CPQ PRs | Contract changes belong to `04-cpq-contracts` / service owners |
| Rename or rewrite existing `libs/crm/**` feature services to BaseCrud | Out of scope blast; CPQ may adopt BaseCrud without forcing CRM migration |
| Mass-refactor CRM lists onto `DataTableComponent` in the same PRs | Separate cleanup; CPQ should consume shared-ui first |
| `libs/shared/models` CRM model field renames | Breaks CRM FE; additive files only |
| Jenkins / GHA / `docs/ci-contract.yaml` | Owned by `06-gateway-ci` unless a FE project name must be added to a matrix (coordinate) |
| New Nx apps (`customer-portal`, `admin-console`) | Not required for CPQ scaffold in `apps/crm` |
| Configuration-engine full wizard UI | Post wave-1 |
| Kafka / WebSocket approval inbox realtime | Backlog PR 12 — later |
| Auth/Azure AD B2C FE wiring | Not present; don’t invent mock auth in CPQ PRs |
| Changing Angular/Nx major versions | Stay on workspace pins (Angular ~21.1, Nx 22.4.5 per `package.json`) |
| gRPC client usage from browser | Backlog correctly marks gRPC N/A |

---

## Appendix A — CRM vs CPQ maturity scorecard

| Dimension | CRM | CPQ (today) |
|-----------|-----|-------------|
| Nx feature libs | 5 | 0 |
| Shared models | 4 domains | 0 |
| Lazy routes in shell | 5 | 0 |
| Signal HTTP services | Yes | None |
| Shared-ui consumption | Effectively unused | N/A — should be first consumer |
| Local proxy coverage | CRM :8081 | Missing product/quote/pricing |
| Documented PR backlog | Implicit via code | `PRICING_IA_BACKLOG.md` (pricing-heavy) |

## Appendix B — Shared UI reuse checklist (for implementers)

When building CPQ lists/forms, **prefer**:

1. `PageHeaderComponent` for title + primary action  
2. `SearchInputComponent` for filter box  
3. `DataTableComponent` for columnar pages  
4. `EmptyStateComponent` for zero-data  
5. `StatusBadgeComponent` / pricing badges for enums  
6. `CurrencyFormatPipe` for money  
7. `ConfirmDialogComponent` for delete / lifecycle confirms  

Only drop to raw Material if shared-ui lacks a required extension — then open a **small** `shared-ui` PR first.

## Appendix C — Suggested generate commands (post-approval)

```bash
cd frontend && npm ci
npx nx g @nx/angular:library catalog --directory=libs/cpq/catalog --standalone --prefix=cpq --importPath=@frontend/cpq-catalog
# repeat for quotes, pricing-data-access, pricing-ui, pricing-simulator
# then add paths in tsconfig.base.json and tags in project.json
```

*(Exact generator flags should match workspace Nx 22.4 / Angular 21 conventions used when CRM libs were created — inspect an existing `libs/crm/deals/project.json` before generating.)*

---

## Appendix D — Success criteria for track completion (wave 1)

- [ ] `libs/cpq` exists with catalog, quotes, pricing-data-access, pricing-ui, pricing-simulator  
- [ ] Models for product, quote, pricing exported from `@frontend/shared-models`  
- [ ] Proxy (or documented gateway URL) allows CRM + CPQ API calls from `nx serve crm`  
- [ ] Routes `/catalog`, `/quotes`, `/pricing/simulator` lazy-loaded from shell  
- [ ] At least one list page uses `@frontend/shared-ui` primitives  
- [ ] Simulator (or quote calculate) shows a real `PricingResult` waterfall against backend  
- [ ] CRM existing routes/tests remain green  

**Gate:** Implementation starts only after orchestrator synthesizes `docs/forge/07-wave-backlog.md` and human approval of wave 1.
