# Pricing Service — Angular Information Architecture PR Backlog

> Backlog of PRs to build out the Angular frontend for the CPQ Pricing Service.
> Ordered by dependency — each PR builds on prior work.
> Aligns with the Nx monorepo conventions (`@frontend/*`) and signal-based state patterns
> established by the existing CRM modules (deals, leads, contacts, organizations).

---

## PR 1: Pricing domain models and shared types

**Library:** `libs/shared/models/src/lib/pricing.model.ts`
**Alias:** Extends `@frontend/shared-models`

Create TypeScript interfaces and enums mirroring the backend pricing domain:

- **Enums:** `PricingModel` (NAMED_SEAT, CONCURRENT_SEAT, MAU, USAGE_METERED, PREPAID_CREDITS, TIERED_FEATURE, BASE_PLUS_OVERAGE, COMMITTED_SPEND, OUTCOME_BASED, PERPETUAL_MAINTENANCE, CORE_SOCKET, SERVER_VM, CAPACITY_BASED, SITE_UNLIMITED, MODULE_ADDON, MIGRATION_UPGRADE, SUPPORT_TIER, HYBRID_SAAS_ONPREM, BRIDGE_CONTRACT, CLOUD_MIGRATION_RAMP)
- **Enums:** `RuleType`, `RuleStatus`, `RuleSetStatus`, `ApprovalState`, `DiscountType`, `BillingFrequency`, `ProductType`, `BundleType`, `CustomerSegment`
- **Interfaces:** `PricingCustomerDto`, `ProductDto`, `ProductBundleDto`, `QuoteDto`, `QuoteLineItemDto`, `RuleSetDto`, `RuleDto`
- **Interfaces:** `PricingWaterfallDto` (listPrice → basePrice → discounts[] → surcharges[] → partnerAdj → prorationAdj → fxRate → tax → netPrice)
- **Interfaces:** `PricingExecuteRequest`, `PricingExecuteResponse`, `PricingSimulateRequest`, `PricingSimulateResponse`
- **Interfaces:** `MarginAnalysisDto` (grossMargin, netMargin, costBasis, marginThreshold, approvalRequired)
- **Interfaces:** `DiscountStackDto` (volume, segment, promotional, dealSpecific, combined)
- **Interfaces:** `PriceBookDto`, `PriceBookEntryDto`
- **Financial metrics:** `ARR`, `TCV`, `ACV`, `CLV` computed type helpers

**Size:** S — Models only, no runtime code.

---

## PR 2: Pricing data-access library

**Library:** `libs/cpq/pricing-data-access/`
**Alias:** `@frontend/cpq-pricing-data-access`

Create the Angular service layer for all pricing API communication:

- `PricingService` — execute pricing, simulate pricing, get waterfall breakdown
  - `POST /api/v1/pricing/execute`
  - `POST /api/v1/pricing/simulate`
- `PriceBookService` — CRUD for price books and entries
  - `GET/POST/PUT/DELETE /api/v1/price-books`
  - `GET /api/v1/price-books/:id/entries`
- `RuleSetService` — CRUD for rule sets and rules
  - `GET/POST/PUT /api/v1/rule-sets`
  - `GET /api/v1/rules/by-rule-set/:id`
  - `POST /api/v1/rule-sets/:id/activate`
  - `POST /api/v1/rule-sets/:id/archive`
- `MarginService` — margin analysis and profitability alerts
  - `GET /api/v1/pricing/margin-analysis/:quoteId`
  - `GET /api/v1/pricing/profitability-alerts`
- `ErpCostService` — ERP cost data proxy
  - `GET /api/v1/erp/costs/:productExternalId`
  - `GET /api/v1/erp/costs/bulk`
  - `GET /api/v1/erp/fx-rates/:base/:target`
- Signal-based state (consistent with existing `DealService` pattern)
- Reuse `BaseCrudService` from `@frontend/shared-data-access` where applicable

**Size:** M — Multiple services, but follows established patterns.

---

## PR 3: Pricing shared UI components

**Library:** `libs/cpq/pricing-ui/`
**Alias:** `@frontend/cpq-pricing-ui`

Reusable presentational components specific to pricing:

- `PricingWaterfallComponent` — Visualize the pricing waterfall (listPrice → netPrice) as a stepped bar/waterfall chart showing each adjustment layer
- `MarginGaugeComponent` — Circular gauge showing gross margin % with color zones (green ≥40%, yellow 35–40%, orange 20–35%, red <20%)
- `DiscountStackComponent` — Stacked horizontal bar showing volume + segment + promo + deal-specific discount contributions
- `PricingModelBadgeComponent` — Chip/badge displaying the pricing model type with icon
- `CurrencyDisplayComponent` — Enhanced currency display with FX rate tooltip and original/converted values
- `ApprovalStateBadgeComponent` — Badge for approval states (COMPLIANT, DEAL_DESK_REQUIRED, CFO_REQUIRED, HARD_BLOCK) with severity colors
- `RuleStatusBadgeComponent` — Badge for rule lifecycle states (DRAFT, ACTIVE, ARCHIVED, EXPIRED)

**Size:** M — Focused presentational components with Material/Tailwind styling.

---

## PR 4: Price Books management module

**Library:** `libs/cpq/price-books/`
**Alias:** `@frontend/cpq-price-books`
**Route:** `/pricing/price-books`

Full CRUD for managing price books (the resolution chain: customer-specific → region+currency → global default):

- `PriceBookListComponent` — Table with filters by status, currency, effective date range; columns: name, currency, status, effectiveDate, expirationDate, entryCount
- `PriceBookDetailComponent` — Price book header + entries table with inline editing; shows resolution priority
- `PriceBookFormComponent` — Create/edit form with fields: name, description, currency, effectiveDate, expirationDate, isDefault, region, customerSegment
- `PriceBookEntryFormComponent` — Add/edit entries: product selector, listPrice, floorPrice, ceilingPrice, currency
- Routes: `PRICE_BOOK_ROUTES` (list → detail → form)

**Size:** M — Standard CRUD following the deals module pattern.

---

## PR 5: Rule Sets management module

**Library:** `libs/cpq/rule-sets/`
**Alias:** `@frontend/cpq-rule-sets`
**Route:** `/pricing/rules`

Manage versioned pricing rule sets and individual rules:

- `RuleSetListComponent` — Table with version history, status filters (DRAFT, ACTIVE, ARCHIVED); columns: name, version, status, ruleCount, effectiveDate, lastModified
- `RuleSetDetailComponent` — Header with lifecycle actions (Activate, Archive, Clone) + child rules table sorted by salience
- `RuleSetFormComponent` — Create/edit with fields: name, description, effectiveDate, expirationDate
- `RuleListComponent` — Rules within a set; columns: name, ruleType, salience/priority, status, conditions summary
- `RuleFormComponent` — Create/edit rule: name, ruleType (dropdown), priority/salience, conditions (JSON/structured editor), actions, dependencies
- `RuleSalienceVisualizerComponent` — Visual priority ladder showing rule execution order (900–1000 guardrails → 700–899 validation → 500–699 line pricing → 300–499 quote adjustments → 200–299 approval → 100–199 telemetry)
- Routes: `RULE_SET_ROUTES` (list → detail → rules)

**Size:** L — Complex domain with rule editing, versioning, and visualization.

---

## PR 6: Pricing simulator module

**Library:** `libs/cpq/pricing-simulator/`
**Alias:** `@frontend/cpq-pricing-simulator`
**Route:** `/pricing/simulator`

Interactive pricing sandbox for what-if analysis:

- `SimulatorComponent` (container) — Orchestrates the simulation workflow
- `SimulatorInputPanelComponent` — Form to configure simulation parameters:
  - Customer selector (segment, region, industry)
  - Product/bundle selector
  - Quantity, term (months), billing frequency
  - Override toggles: force pricing model, force price book, apply/skip specific discount types
- `SimulatorResultPanelComponent` — Displays simulation output:
  - Pricing waterfall visualization (reuses `PricingWaterfallComponent`)
  - Discount breakdown (reuses `DiscountStackComponent`)
  - Margin analysis (reuses `MarginGaugeComponent`)
  - Financial metrics: ARR, TCV, ACV, CLV
  - Approval prediction (will this trigger DEAL_DESK / CFO review?)
- `SimulatorComparisonComponent` — Side-by-side comparison of up to 3 scenarios
- `SimulatorHistoryComponent` — List of recent simulations with quick replay

**Size:** L — Core pricing UX; combines multiple visualization components.

---

## PR 7: Margin & profitability dashboard

**Library:** `libs/cpq/margin-dashboard/`
**Alias:** `@frontend/cpq-margin-dashboard`
**Route:** `/pricing/margins`

Real-time margin monitoring aligned with Agent 012 (Profitability Alert):

- `MarginDashboardComponent` (container) — Main layout with KPI cards + detail panels
- `MarginKpiCardsComponent` — Summary cards: Average Margin %, Deals Below Threshold, Pending Approvals, Revenue at Risk
- `MarginDistributionChartComponent` — Histogram of deal margins across the pipeline; highlights guardrail zones (<20%, 20–35%, 35–40%, >40%)
- `MarginAlertsTableComponent` — Active profitability alerts: deal name, current margin %, trend (improving/declining), alert severity (CAUTION/WARNING), suggested action
- `MarginTrendChartComponent` — Time-series chart showing margin trends by segment/product/rep over configurable time ranges
- `MarginErosionComponent` — Attribution breakdown showing which discount layers are eroding margin (volume, segment, promo, deal-specific)
- Filters: date range, customer segment, product family, sales rep

**Size:** L — Analytics-heavy with multiple chart components.

---

## PR 8: Discount management module

**Library:** `libs/cpq/discounts/`
**Alias:** `@frontend/cpq-discounts`
**Route:** `/pricing/discounts`

Manage the four discount types (Agents 007–010):

- `DiscountListComponent` — Tabbed view: Volume | Segment | Promotional | Deal-Specific
- **Volume Discounts tab:**
  - `VolumeDiscountScheduleComponent` — Table of tier breakpoints: minQty, maxQty, discountRate; toggle all-units vs graduated
  - `VolumeDiscountFormComponent` — Create/edit schedules: name, product scope, term eligibility (min months), tiers
- **Segment Discounts tab:**
  - `SegmentDiscountListComponent` — Discount rates by customer segment + product family matrix
  - `SegmentDiscountFormComponent` — Edit segment-product discount mappings
- **Promotional Campaigns tab:**
  - `PromotionalCampaignListComponent` — Active/scheduled/expired campaigns; columns: name, discountRate, startDate, endDate, redemptionCount
  - `PromotionalCampaignFormComponent` — Create/edit: name, discount type (% or fixed), eligible products, date range, stacking rules
- **Deal-Specific tab:**
  - `DealDiscountListComponent` — Rep-negotiated discounts tied to deals; shows margin impact
  - `DealDiscountFormComponent` — Ad-hoc discount with margin gate preview
- Shared: `DiscountStackPreviewComponent` — Shows combined effect of all active discount types for a given product/customer

**Size:** XL — Four sub-domains, each with list + form views.

---

## PR 9: SaaS tier configuration module

**Library:** `libs/cpq/saas-tiers/`
**Alias:** `@frontend/cpq-saas-tiers`
**Route:** `/pricing/saas-tiers`

Manage SaaS tier definitions aligned with Agent 019:

- `TierListComponent` — Table of tiers: Standard, Professional, Enterprise; columns: name, basePrice, seatModel, billingFrequencies, featureCount
- `TierDetailComponent` — Tier header + feature gating matrix + pricing breakdown by billing frequency (monthly/annual/multi-year with discount %)
- `TierFormComponent` — Create/edit: name, seatModel (NAMED/CONCURRENT/FLAT), basePrice per frequency, included features, usage limits
- `FeatureGatingMatrixComponent` — Matrix view: features (rows) × tiers (columns) with checkmarks/limits showing entitlements per tier
- `TierComparisonComponent` — Side-by-side tier comparison card layout (similar to typical SaaS pricing pages) for internal review
- `BillingFrequencyConfigComponent` — Configure frequency discounts (e.g., 15% annual, 20% multi-year)

**Size:** M — Focused on subscription tier structure.

---

## PR 10: Approval workflow UI

**Library:** `libs/cpq/approvals/`
**Alias:** `@frontend/cpq-approvals`
**Route:** `/pricing/approvals`

UI for the approval pipeline (Agents 011, 024, 025, 037):

- `ApprovalQueueComponent` — Inbox-style list of pending approvals for current user/role; columns: quote name, customer, requestedDiscount, currentMargin, approvalTier (DEAL_DESK/CFO), requestor, age
- `ApprovalDetailComponent` — Full context view:
  - Quote summary with line items and pricing waterfall
  - Margin analysis (reuses `MarginGaugeComponent`)
  - Discount breakdown (reuses `DiscountStackComponent`)
  - Rule that triggered the approval (link to rule set)
  - Approval history / audit trail
  - Actions: Approve, Reject, Request Changes, Escalate
- `ApprovalHistoryComponent` — Completed approvals with outcome, decision time, approver
- `ExceptionRequestFormComponent` — Form for requesting below-cost or below-threshold exceptions with business justification
- `ApprovalRoutingConfigComponent` — Configure approval tiers: margin threshold → required approver role mapping

**Size:** L — Critical workflow with rich context display.

---

## PR 11: Pricing navigation, routing, and shell integration

**Library:** Updates to `apps/crm/`
**Route:** `/pricing/**`

Wire all pricing modules into the main application:

- Add "Pricing" section to `SidebarComponent` with collapsible sub-navigation:
  - Simulator (`/pricing/simulator`)
  - Price Books (`/pricing/price-books`)
  - Discounts (`/pricing/discounts`)
  - SaaS Tiers (`/pricing/saas-tiers`)
  - Rules (`/pricing/rules`)
  - Margins (`/pricing/margins`)
  - Approvals (`/pricing/approvals`)
- Add lazy-loaded routes to `app.routes.ts` for all pricing modules
- Add path aliases to `tsconfig.base.json` for all new `@frontend/cpq-*` libraries
- Add pricing KPI cards to the existing Dashboard (total ARR, avg margin, pending approvals count)
- Add "Pricing" breadcrumb trail component for nested pricing routes

**Size:** M — Integration/wiring PR, touches several existing files.

---

## PR 12: Quote builder pricing integration

**Library:** `libs/cpq/quote-builder/`
**Alias:** `@frontend/cpq-quote-builder`
**Route:** `/quotes/:id/pricing` (or embedded in deal detail)

Integrate pricing into the deal/quote workflow:

- `QuotePricingPanelComponent` — Embeddable panel showing live pricing for a quote:
  - Line items with unit price, quantity, discounts, line total
  - Quote-level pricing waterfall
  - Margin summary with approval state indicator
  - "Recalculate" button triggering `POST /api/v1/pricing/execute`
- `LineItemPricingComponent` — Per-line detail: pricing model applied, discount stack, floor/ceiling indicators
- `QuotePricingComparisonComponent` — Compare pricing across quote versions/revisions
- Integration point with existing `DealDetailComponent` — Add "Pricing" tab
- Integration point with `ApprovalDetailComponent` — Deep link from approval queue to quote pricing

**Size:** L — Cross-cutting integration touching deals and pricing domains.

---

## Dependency Graph

```
PR 1  (models)
 ├── PR 2  (data-access)       ── depends on PR 1
 │    ├── PR 4  (price-books)  ── depends on PR 2
 │    ├── PR 5  (rule-sets)    ── depends on PR 2
 │    ├── PR 6  (simulator)    ── depends on PR 2, PR 3
 │    ├── PR 7  (margins)      ── depends on PR 2, PR 3
 │    ├── PR 8  (discounts)    ── depends on PR 2, PR 3
 │    ├── PR 9  (saas-tiers)   ── depends on PR 2
 │    ├── PR 10 (approvals)    ── depends on PR 2, PR 3
 │    └── PR 12 (quote-builder)── depends on PR 2, PR 3
 └── PR 3  (pricing-ui)        ── depends on PR 1
      └── PR 11 (nav/routing)  ── depends on PR 4–10
```

**Critical path:** PR 1 → PR 2 → PR 3 → PR 6 (simulator) — this gives the team an end-to-end pricing UX to validate against the backend.

---

## Conventions

All PRs follow the patterns established by the existing CRM modules:

| Concern | Convention |
|---------|-----------|
| State management | Angular signals (`signal()`, `computed()`, `effect()`) |
| HTTP layer | `HttpClient` + `@frontend/shared-data-access` interceptors |
| Components | Standalone, `ChangeDetectionStrategy.OnPush` |
| Styling | Angular Material 21 + Tailwind CSS utility classes |
| Testing | Vitest with Angular testing utilities |
| Routing | Lazy-loaded `Route[]` arrays exported from each library |
| Naming | `@frontend/cpq-*` for CPQ libraries vs `@frontend/crm-*` for CRM |
| Nx structure | `libs/cpq/<feature>/` mirroring `libs/crm/<feature>/` |
