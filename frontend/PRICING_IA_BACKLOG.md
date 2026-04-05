# Pricing Service — Angular Information Architecture PR Backlog

> Backlog of PRs to build out the Angular frontend for the CPQ Pricing Service.
> Ordered by dependency — each PR builds on prior work.
> Aligns with the Nx monorepo conventions (`@frontend/*`) and signal-based state patterns
> established by the existing CRM modules (deals, leads, contacts, organizations).

## Changes from v1

- **Fixed phantom endpoints** — Removed references to `/api/v1/erp/costs/` and `/api/v1/pricing/margin-analysis/` (not in backend). Margin analysis is derived client-side from `PricingResult.financialMetrics`.
- **Added pricing customers UI** — Backend has full CRUD at `/api/v1/pricing-customers` with segment/region/industry/churnScore; the old backlog omitted it.
- **Added audit trail viewer** — Backend persists `PricingAuditLog` with input/output snapshots; the old backlog had no UI for it.
- **Split discount management** — Old PR 8 was XL (4 sub-domains). Now split into volume/segment discounts (PR 9) and promotional/deal-specific discounts (PR 10).
- **Models match actual backend DTOs** — Field names and types now mirror the Java entities discovered in the backend exploration.
- **Incremental navigation** — Each feature PR adds its own route and sidebar entry rather than deferring all wiring to a late PR.
- **Persona-driven nav grouping** — Sidebar organizes features by user workflow (Operate, Analyze, Configure) rather than flat list.
- **Added bulk simulation** — Backend supports `POST /api/v1/pricing/simulate/bulk`; now included in the simulator PR.

---

## PR 1: Pricing domain models and shared types

**Library:** `libs/shared/models/src/lib/pricing.model.ts`
**Alias:** Extends `@frontend/shared-models`

TypeScript interfaces and enums mirroring the backend pricing domain exactly.

### Enums (mirror backend Java enums)

- **`ProductType`**: `SAAS_SEAT_NAMED`, `SAAS_SEAT_CONCURRENT`, `SAAS_MAU`, `SAAS_USAGE`, `SAAS_CREDITS`, `SAAS_TIERED`, `SAAS_BASE_OVERAGE`, `SAAS_COMMITTED`, `SAAS_OUTCOME`, `ONPREM_PERPETUAL`, `ONPREM_CORE`, `ONPREM_SERVER`, `ONPREM_CAPACITY`, `ONPREM_SITE`, `ONPREM_MODULE`, `ONPREM_MIGRATION`, `ONPREM_SUPPORT`, `HYBRID_BUNDLE`, `HYBRID_BRIDGE`, `HYBRID_MIGRATION_RAMP`
- **`ProductCategory`**: `SUBSCRIPTION`, `LICENSE`, `SUPPORT`, `SERVICES`, `ADD_ON`
- **`CustomerSegment`**: `STARTUP`, `SMB`, `MID_MARKET`, `ENTERPRISE`, `STRATEGIC`
- **`CustomerRegion`**: `NA`, `EMEA`, `APAC`, `LATAM`
- **`RuleType`**: `PRICING`, `CONFIGURATION`, `APPROVAL`, `RENEWAL`, `RISK`
- **`RuleStatus`**: `DRAFT`, `ACTIVE`, `DISABLED`
- **`RuleSetStatus`**: `DRAFT`, `ACTIVE`, `DEPRECATED`, `ARCHIVED`
- **`SubscriptionType`**: `NEW`, `AMENDMENT`, `RENEWAL`
- **`WaterfallStepType`**: `LIST_PRICE`, `BASE_PRICE`, `DISCOUNT`, `SURCHARGE`, `PARTNER_ADJUSTMENT`, `PRORATION`, `FX_CONVERSION`, `TAX`, `NET_PRICE`, `APPROVAL`
- **`ApprovalState`**: `COMPLIANT`, `DEAL_DESK_REQUIRED`, `CFO_REQUIRED`, `HARD_BLOCK`
- **`RulesEngineType`**: `DROOLS`, `OPENL`

### Interfaces — Customer domain

```typescript
/** Mirrors: com.opencrm.pricing.customer.CustomerDto */
interface PricingCustomerDto {
  id: number;
  uuid: string;
  externalId: string;
  name: string;
  type: string; // default: DIRECT
  segment: CustomerSegment;
  region: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

interface PricingCustomerCreateRequest {
  externalId: string;
  name: string;
  type?: string;
  segment: CustomerSegment;
  region: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: Record<string, string>;
}

interface PricingCustomerUpdateRequest {
  name?: string;
  segment?: CustomerSegment;
  region?: CustomerRegion;
  industry?: string;
  churnScore?: number;
  annualRevenue?: number;
  metadata?: Record<string, string>;
}
```

### Interfaces — Price Book domain

```typescript
/** Mirrors: com.opencrm.pricing.pricebook.PriceBookDto */
interface PriceBookDto {
  id: number;
  uuid: string;
  name: string;
  region?: string;
  currency: string; // default: USD
  isDefault: boolean;
  effectiveDate: string;
  expirationDate?: string;
  createdAt: string;
  updatedAt: string;
}

interface PriceBookCreateRequest {
  name: string;
  region?: string;
  currency?: string;
  isDefault?: boolean;
  effectiveDate: string;
  expirationDate?: string;
}

interface PriceBookUpdateRequest {
  name?: string;
  region?: string;
  currency?: string;
  isDefault?: boolean;
  effectiveDate?: string;
  expirationDate?: string;
}

/** Mirrors: com.opencrm.pricing.pricebook.PriceBookEntryDto */
interface PriceBookEntryDto {
  id: number;
  uuid: string;
  priceBookId: number;
  productExternalId: string;
  listPrice: number;
  floorPrice?: number;
  currency: string;
  createdAt: string;
  updatedAt: string;
}

interface PriceBookEntryCreateRequest {
  productExternalId: string;
  listPrice: number;
  floorPrice?: number;
  currency?: string;
}

interface PriceBookEntryUpdateRequest {
  listPrice?: number;
  floorPrice?: number;
  currency?: string;
}
```

### Interfaces — Rule domain

```typescript
/** Mirrors: com.opencrm.pricing.ruleset.RuleSetDto */
interface RuleSetDto {
  id: number;
  uuid: string;
  name: string;
  description?: string;
  status: RuleSetStatus;
  ruleSetVersion?: string;
  effectiveDate: string;
  expirationDate?: string;
  engineType: RulesEngineType; // default: DROOLS
  createdAt: string;
  updatedAt: string;
}

interface RuleSetCreateRequest {
  name: string;
  description?: string;
  effectiveDate: string;
  expirationDate?: string;
  engineType?: RulesEngineType;
}

interface RuleSetUpdateRequest {
  name?: string;
  description?: string;
  effectiveDate?: string;
  expirationDate?: string;
}

/** Mirrors: com.opencrm.pricing.rule.RuleDto */
interface RuleDto {
  id: number;
  uuid: string;
  ruleSetId: number;
  name: string;
  description?: string;
  ruleType: RuleType;
  priority: number;
  status: RuleStatus;
  content?: string; // DRL content
  conditionGroup?: object;
  actionSet?: object;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

interface RuleCreateRequest {
  ruleSetId: number;
  name: string;
  description?: string;
  ruleType: RuleType;
  priority: number;
  content?: string;
  conditionGroup?: object;
  actionSet?: object;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: Record<string, string>;
}

interface RuleUpdateRequest {
  name?: string;
  description?: string;
  priority?: number;
  status?: RuleStatus;
  content?: string;
  conditionGroup?: object;
  actionSet?: object;
  conflictPolicy?: string;
  explanationCode?: string;
  metadata?: Record<string, string>;
}

interface RuleImpactDto {
  ruleId: number;
  affectedProducts: number;
  affectedCustomers: number;
  estimatedRevenueImpact: number;
}
```

### Interfaces — Pricing Execution domain

```typescript
/** Mirrors: com.opencrm.pricing.execution.PricingContext */
interface PricingContext {
  customerExternalId: string;
  quoteExternalId?: string;
  lineItems: PricingLineRequest[];
  region?: string;
  currency?: string;
  durationInMonths?: number;
  subscriptionType?: SubscriptionType;
  metadata?: Record<string, string>;
}

interface PricingLineRequest {
  productExternalId: string;
  lineItemType?: string;
  quantity: number;
  listPriceOverride?: number;
  metadata?: Record<string, string>;
}

/** Mirrors: com.opencrm.pricing.execution.PricingResult */
interface PricingResult {
  success: boolean;
  lineResults: PricingLineResult[];
  waterfall?: PricingWaterfall;
  financialMetrics?: FinancialMetrics;
  explanationCodes: string[];
  approvalState?: ApprovalState;
  approvalTier?: string;
  executionTimeMs: number;
  errorMessage?: string;
}

interface PricingLineResult {
  productExternalId: string;
  lineItemType?: string;
  quantity: number;
  listPrice: number;
  unitPrice: number;
  discountRate: number;
  discountAmount: number;
  surchargeAmount: number;
  finalPrice: number;
  explanationCodes: string[];
  metadata?: Record<string, string>;
}

/** Mirrors: com.opencrm.pricing.waterfall.PricingWaterfall */
interface PricingWaterfall {
  listPrice: number;
  basePrice: number;
  netPrice: number;
  steps: PricingWaterfallStep[];
  approvalState?: ApprovalState;
  explanationCodes: string[];
}

interface PricingWaterfallStep {
  stepType: WaterfallStepType;
  description: string;
  amount: number;
  runningTotal: number;
  ruleId?: number;
  ruleName?: string;
}

/** Mirrors: com.opencrm.pricing.execution.FinancialMetrics */
interface FinancialMetrics {
  arr: number; // Annual Recurring Revenue
  tcv: number; // Total Contract Value
  acv: number; // Annual Contract Value
  clv: number; // Customer Lifetime Value
  totalDiscount: number;
  totalSurcharge: number;
  estimatedMarginPct: number;
}

/** Bulk simulation types */
interface BulkPricingSimulationRequest {
  contexts: PricingContext[];
}

interface BulkPricingSimulationResponse {
  results: PricingResult[];
}
```

### Interfaces — Audit domain

```typescript
/** Mirrors: com.opencrm.pricing.audit.PricingAuditLog */
interface PricingAuditLogDto {
  id: number;
  uuid: string;
  quoteExternalId: string;
  customerExternalId: string;
  ruleSetId: number;
  inputSnapshot: string; // JSON string
  outputSnapshot: string; // JSON string
  waterfallTrace?: string;
  rulesFired?: string;
  executionTimeMs: number;
  executedAt: string;
  executedBy?: string;
}
```

### Interfaces — Product domain

```typescript
/** Mirrors: com.opencrm.pricing.product.PricingProduct (lightweight ref) */
interface PricingProductDto {
  id: number;
  uuid: string;
  externalId: string;
  name: string;
  productType: ProductType;
  category: ProductCategory;
  listPrice: number;
  currency: string;
  metadata?: Record<string, string>;
}
```

**Size:** S — Models only, no runtime code.

---

## PR 2: Pricing data-access library

**Library:** `libs/cpq/pricing-data-access/`
**Alias:** `@frontend/cpq-pricing-data-access`

Angular service layer for all pricing API communication. Each service extends `BaseCrudService` where applicable or uses raw `HttpClient` for non-CRUD operations.

### Services

- **`PricingCustomerService`** — extends `BaseCrudService<PricingCustomerDto, PricingCustomerCreateRequest, PricingCustomerUpdateRequest>`
  - `basePath = '/api/v1/pricing-customers'`
  - Additional: `listBySegment(segment: CustomerSegment)`
  - Maps to backend: `CustomerController`

- **`PriceBookService`** — extends `BaseCrudService<PriceBookDto, PriceBookCreateRequest, PriceBookUpdateRequest>`
  - `basePath = '/api/v1/price-books'`
  - Additional: `listByRegion(region: string)`, `getEntries(bookId)`, `createEntry(bookId, req)`, `updateEntry(bookId, entryId, req)`, `deleteEntry(bookId, entryId)`
  - Maps to backend: `PriceBookController`

- **`RuleSetService`** — extends `BaseCrudService<RuleSetDto, RuleSetCreateRequest, RuleSetUpdateRequest>`
  - `basePath = '/api/v1/rule-sets'`
  - Additional: `activate(id)` → `POST /api/v1/rule-sets/:id/activate`, `deprecate(id)` → `POST /api/v1/rule-sets/:id/deprecate`, `listByStatus(status: RuleSetStatus)`
  - Maps to backend: `RuleSetController`

- **`RuleService`** — extends `BaseCrudService<RuleDto, RuleCreateRequest, RuleUpdateRequest>`
  - `basePath = '/api/v1/rules'`
  - Additional: `listByRuleSet(ruleSetId)` → `GET /api/v1/rules/by-rule-set/:ruleSetId`, `validateSyntax(id)` → `POST /api/v1/rules/:id/validate`, `analyzeImpact(id)` → `GET /api/v1/rules/:id/impact`
  - Maps to backend: `RuleController`

- **`PricingExecutionService`** — standalone (no BaseCrudService)
  - `execute(ctx: PricingContext)` → `POST /api/v1/pricing/execute`
  - `simulate(ctx: PricingContext)` → `POST /api/v1/pricing/simulate`
  - `simulateBulk(req: BulkPricingSimulationRequest)` → `POST /api/v1/pricing/simulate/bulk`
  - `getAuditTrail(quoteExternalId: string)` → `GET /api/v1/pricing/audit/:quoteExternalId`
  - Signal state: `lastResult`, `simulationResults`, `loading`, `error`
  - Maps to backend: `PricingExecutionController`

All services use signal-based state (`signal()`, `computed()`) consistent with existing `DealService` pattern.

**Size:** M — Multiple services, but follows established `BaseCrudService` patterns.

---

## PR 3: Pricing shared UI components

**Library:** `libs/cpq/pricing-ui/`
**Alias:** `@frontend/cpq-pricing-ui`

Reusable presentational components specific to pricing visualization.

- **`PricingWaterfallComponent`** — Stepped bar chart visualizing `PricingWaterfall` (listPrice through each `PricingWaterfallStep` to netPrice). Each step shows amount, running total, and rule attribution. Uses Material card with inline SVG bars.

- **`MarginGaugeComponent`** — Circular gauge rendering `FinancialMetrics.estimatedMarginPct` with color zones:
  - Green: >= 40%
  - Yellow: 35-40%
  - Orange: 20-35%
  - Red: < 20%
  - Shows approval state badge when below threshold

- **`FinancialMetricsCardComponent`** — Card grid showing ARR, TCV, ACV, CLV from `FinancialMetrics`. Uses `CurrencyFormatPipe` from shared-ui.

- **`ApprovalStateBadgeComponent`** — Badge for `ApprovalState` values with severity colors:
  - COMPLIANT → green
  - DEAL_DESK_REQUIRED → orange
  - CFO_REQUIRED → red
  - HARD_BLOCK → dark red

- **`RuleStatusBadgeComponent`** — Badge for `RuleStatus` and `RuleSetStatus` lifecycle states

- **`CustomerSegmentBadgeComponent`** — Badge for `CustomerSegment` with distinct colors per tier

- **`ProductTypeBadgeComponent`** — Chip displaying `ProductType` grouped by SaaS/On-Prem/Hybrid with icon

**Size:** M — Focused presentational components with Material + Tailwind styling.

---

## PR 4: Pricing customers management module

**Library:** `libs/cpq/pricing-customers/`
**Alias:** `@frontend/cpq-pricing-customers`
**Route:** `/pricing/customers`

CRUD for managing pricing-specific customer references. These are lightweight records linking to CRM organizations with pricing attributes (segment, region, churn score, annual revenue).

- **`PricingCustomerListComponent`** — Table with filters by segment and region; columns: externalId, name, segment (badge), region (badge), industry, annualRevenue (currency), churnScore, updatedAt. Uses `DataTableComponent`.

- **`PricingCustomerDetailComponent`** — Header card with customer attributes + metadata key-value display. Action buttons: Edit, Delete.

- **`PricingCustomerFormComponent`** — Create/edit form:
  - externalId (required, unique)
  - name (required)
  - segment (dropdown: CustomerSegment)
  - region (dropdown: CustomerRegion)
  - industry (text)
  - annualRevenue (currency input)
  - churnScore (0-100 slider or number input)
  - metadata (key-value pair editor)

- **Routes:** `PRICING_CUSTOMER_ROUTES` (list → detail → form)

**Size:** S — Standard CRUD following the organizations module pattern.

---

## PR 5: Price Books management module

**Library:** `libs/cpq/price-books/`
**Alias:** `@frontend/cpq-price-books`
**Route:** `/pricing/price-books`

Full CRUD for managing price books and their entries.

- **`PriceBookListComponent`** — Table with filters by region and default status; columns: name, region, currency, isDefault (chip), effectiveDate, expirationDate, updatedAt. Uses `DataTableComponent`.

- **`PriceBookDetailComponent`** — Price book header card + entries table below. Entries table columns: productExternalId, listPrice (currency), floorPrice (currency), currency. Inline "Add Entry" button. Shows effective date range prominently.

- **`PriceBookFormComponent`** — Create/edit form: name, region (optional), currency (default: USD), isDefault (toggle), effectiveDate (datepicker), expirationDate (datepicker).

- **`PriceBookEntryFormComponent`** — Add/edit entry dialog: product selector (autocomplete by externalId), listPrice, floorPrice, currency. Validates floorPrice <= listPrice.

- **Routes:** `PRICE_BOOK_ROUTES` (list → detail → form)

**Size:** M — Standard CRUD with nested entries sub-resource.

---

## PR 6: Rule Sets management module

**Library:** `libs/cpq/rule-sets/`
**Alias:** `@frontend/cpq-rule-sets`
**Route:** `/pricing/rules`

Manage versioned pricing rule sets and individual rules within them.

- **`RuleSetListComponent`** — Table with status filter (DRAFT, ACTIVE, DEPRECATED, ARCHIVED); columns: name, ruleSetVersion, status (badge), engineType, effectiveDate, expirationDate, updatedAt. Uses `DataTableComponent`.

- **`RuleSetDetailComponent`** — Header card with lifecycle actions:
  - DRAFT → **Activate** button (calls `POST /api/v1/rule-sets/:id/activate`)
  - ACTIVE → **Deprecate** button (calls `POST /api/v1/rule-sets/:id/deprecate`)
  - Shows child rules table sorted by priority descending
  - "Add Rule" button (disabled when status is ACTIVE — backend enforces immutability)

- **`RuleSetFormComponent`** — Create/edit: name, description, effectiveDate (datepicker), expirationDate (datepicker), engineType (dropdown: DROOLS, OPENL).

- **`RuleListComponent`** — Rules within a set; columns: name, ruleType (badge), priority, status (badge), explanationCode. Sorted by priority descending.

- **`RuleFormComponent`** — Create/edit rule:
  - name (required)
  - ruleType (dropdown: PRICING, CONFIGURATION, APPROVAL, RENEWAL, RISK)
  - priority (number, with guidance: 900-1000 guardrails, 700-899 validation, 500-699 line pricing, 300-499 quote adjustments, 200-299 approval, 100-199 telemetry)
  - status (dropdown: DRAFT, ACTIVE, DISABLED)
  - content (code editor for DRL content)
  - explanationCode (text)
  - metadata (key-value editor)

- **`RuleSalienceVisualizerComponent`** — Visual priority ladder showing rule execution order. Groups rules into named bands (guardrails 900-1000, validation 700-899, etc.) with rules positioned on a vertical scale.

- **`RuleValidationComponent`** — Inline panel showing result of `POST /api/v1/rules/:id/validate` (DRL syntax check). Displays success or error details.

- **Routes:** `RULE_SET_ROUTES` (list → detail with nested rules)

**Size:** L — Complex domain with rule editing, versioning, lifecycle transitions, and visualization.

---

## PR 7: Pricing simulator module

**Library:** `libs/cpq/pricing-simulator/`
**Alias:** `@frontend/cpq-pricing-simulator`
**Route:** `/pricing/simulator`

Interactive pricing sandbox for what-if analysis using `POST /api/v1/pricing/simulate`.

- **`SimulatorComponent`** (container) — Split-pane layout: input panel left, result panel right.

- **`SimulatorInputPanelComponent`** — Form building a `PricingContext`:
  - Customer selector (autocomplete from `PricingCustomerService`)
  - Line items (dynamic form array):
    - productExternalId (autocomplete)
    - quantity (number)
    - listPriceOverride (optional number)
  - region, currency (dropdowns)
  - durationInMonths (number)
  - subscriptionType (dropdown: NEW, AMENDMENT, RENEWAL)
  - "Simulate" button → calls `PricingExecutionService.simulate()`

- **`SimulatorResultPanelComponent`** — Displays `PricingResult`:
  - Success/error banner
  - Pricing waterfall (reuses `PricingWaterfallComponent`)
  - Financial metrics cards (reuses `FinancialMetricsCardComponent`)
  - Margin gauge (reuses `MarginGaugeComponent`)
  - Approval prediction (reuses `ApprovalStateBadgeComponent`)
  - Line results table: product, quantity, listPrice, unitPrice, discountRate, finalPrice
  - Execution time display

- **`SimulatorComparisonComponent`** — Side-by-side comparison of up to 3 saved simulation results. Highlights deltas between scenarios.

- **`BulkSimulatorComponent`** — Upload/paste multiple `PricingContext` objects, submit via `POST /api/v1/pricing/simulate/bulk`, display tabular results with export.

- **`SimulatorHistoryComponent`** — In-memory list of recent simulations in the current session with quick replay.

**Size:** L — Core pricing UX; combines multiple visualization components.

---

## PR 8: Pricing audit trail viewer

**Library:** `libs/cpq/pricing-audit/`
**Alias:** `@frontend/cpq-pricing-audit`
**Route:** `/pricing/audit`

Read-only viewer for the immutable pricing audit log persisted by the backend.

- **`AuditTrailSearchComponent`** — Search form: quoteExternalId (text input) → calls `GET /api/v1/pricing/audit/:quoteExternalId`. Results displayed below.

- **`AuditTrailListComponent`** — Table of `PricingAuditLogDto` entries for a quote; columns: executedAt, customerExternalId, ruleSetId, rulesFired count, executionTimeMs, executedBy. Sorted by executedAt descending.

- **`AuditTrailDetailComponent`** — Expandable row or detail panel showing:
  - Input snapshot (parsed JSON, displayed as formatted `PricingContext`)
  - Output snapshot (parsed JSON, displayed as formatted `PricingResult`)
  - Waterfall trace
  - Rules fired list
  - Execution metadata

- **Routes:** `PRICING_AUDIT_ROUTES` (search → list → detail)

**Size:** S — Read-only views, no mutations.

---

## PR 9: Margin & profitability dashboard

**Library:** `libs/cpq/margin-dashboard/`
**Alias:** `@frontend/cpq-margin-dashboard`
**Route:** `/pricing/margins`

Dashboard for monitoring margin health. Derived client-side from `PricingResult.financialMetrics` and `PricingAuditLog` data — no separate backend margin endpoint needed.

- **`MarginDashboardComponent`** (container) — KPI cards row + detail panels below.

- **`MarginKpiCardsComponent`** — Summary cards:
  - Average Margin % (from recent audit logs)
  - Deals Below Threshold (margin < 20%)
  - Pending Approvals count
  - Total Discount Volume

- **`MarginDistributionChartComponent`** — Histogram of deal margins across recent executions; highlights guardrail zones (<20%, 20-35%, 35-40%, >40%).

- **`MarginAlertsTableComponent`** — Table of executions where `estimatedMarginPct < threshold`; columns: quoteExternalId, customerExternalId, margin%, approvalState, executedAt. Links to audit detail.

- **`MarginTrendChartComponent`** — Time-series of average margin % over configurable date ranges.

- Filters: date range, customer segment, region

**Size:** M — Dashboard using existing audit data, no new backend endpoints.

---

## PR 10: Discount rule authoring helpers

**Library:** `libs/cpq/discount-rules/`
**Alias:** `@frontend/cpq-discount-rules`
**Route:** `/pricing/rules` (extends Rule Sets module)

Specialized UI for authoring discount-related rules within the Rule Sets module. These are opinionated form components that generate DRL `content` for common discount patterns.

- **`VolumeDiscountRuleFormComponent`** — Guided form for creating volume discount rules:
  - Tier table: minQty, maxQty, discountRate
  - Toggle: all-units vs graduated
  - Generates DRL content targeting `QuoteLineItemFact.quantity`

- **`SegmentDiscountRuleFormComponent`** — Matrix editor: customer segments (rows) x product categories (columns) → discount rates. Generates DRL content targeting `CustomerFact.segment`.

- **`PromotionalDiscountRuleFormComponent`** — Campaign-style form: name, discountRate, eligible products (multi-select), start/end dates, stacking policy. Generates time-bounded DRL conditions.

- **`DiscountStackPreviewComponent`** — Given a `PricingResult`, shows the combined effect of all discount-type waterfall steps as a stacked bar. Reusable in simulator and quote builder.

These components plug into the `RuleFormComponent` from PR 6 as alternative "template" modes alongside raw DRL editing.

**Size:** M — Form generators, no new backend endpoints (rules are persisted through existing Rule API).

---

## PR 11: SaaS tier configuration module

**Library:** `libs/cpq/saas-tiers/`
**Alias:** `@frontend/cpq-saas-tiers`
**Route:** `/pricing/saas-tiers`

Manage SaaS subscription tier definitions. Tiers are composed from `PricingProduct` records and `PriceBookEntry` pricing.

- **`TierListComponent`** — Table of tiers: name, basePrice, seatModel (ProductType badge), billingFrequencies, featureCount.

- **`TierDetailComponent`** — Tier header + feature gating matrix + pricing breakdown by billing frequency (monthly/annual/multi-year with discount %).

- **`TierFormComponent`** — Create/edit: name, seatModel (dropdown of SAAS\_\* ProductTypes), basePrice per frequency, included features, usage limits.

- **`FeatureGatingMatrixComponent`** — Matrix view: features (rows) x tiers (columns) with checkmarks/limits showing entitlements per tier.

- **`TierComparisonComponent`** — Side-by-side tier comparison card layout for internal review.

**Size:** M — Focused on subscription tier structure.

---

## PR 12: Approval workflow UI

**Library:** `libs/cpq/approvals/`
**Alias:** `@frontend/cpq-approvals`
**Route:** `/pricing/approvals`

UI for the discount approval pipeline. Triggered when `PricingResult.approvalState` is not `COMPLIANT` (backend threshold: discount > 20% of base price publishes `DiscountApprovalRequiredEvent` via Kafka).

- **`ApprovalQueueComponent`** — Inbox-style list of pending approvals; columns: quote name, customer, requested discount, current margin %, approval tier (DEAL_DESK/CFO), requestor, age. Polling or WebSocket refresh.

- **`ApprovalDetailComponent`** — Full context view:
  - Quote summary with line items and pricing waterfall (reuses `PricingWaterfallComponent`)
  - Margin analysis (reuses `MarginGaugeComponent`)
  - Financial metrics (reuses `FinancialMetricsCardComponent`)
  - Rule that triggered the approval (link to rule set detail)
  - Approval history / audit trail (links to audit viewer)
  - Actions: Approve, Reject, Request Changes, Escalate

- **`ApprovalHistoryComponent`** — Completed approvals with outcome, decision time, approver.

- **`ExceptionRequestFormComponent`** — Form for requesting below-threshold exceptions with business justification text.

**Size:** L — Critical workflow with rich context display.

---

## PR 13: Navigation, routing, and shell integration

**Library:** Updates to `apps/crm/`
**Route:** `/pricing/**`

Wire all pricing modules into the main application with persona-driven sidebar grouping.

### Sidebar IA

Add a **"Pricing"** section to `SidebarComponent` with collapsible sub-groups:

```
Pricing
├── Operate
│   ├── Simulator          /pricing/simulator
│   ├── Approvals          /pricing/approvals
│   └── Audit Trail        /pricing/audit
├── Analyze
│   └── Margins            /pricing/margins
└── Configure
    ├── Price Books         /pricing/price-books
    ├── Rules               /pricing/rules
    ├── SaaS Tiers          /pricing/saas-tiers
    └── Customers           /pricing/customers
```

### Routing updates

Add lazy-loaded routes to `app.routes.ts`:

```typescript
{
  path: 'pricing',
  children: [
    { path: '', redirectTo: 'simulator', pathMatch: 'full' },
    { path: 'simulator', loadChildren: () => import('@frontend/cpq-pricing-simulator').then(m => m.SIMULATOR_ROUTES) },
    { path: 'price-books', loadChildren: () => import('@frontend/cpq-price-books').then(m => m.PRICE_BOOK_ROUTES) },
    { path: 'rules', loadChildren: () => import('@frontend/cpq-rule-sets').then(m => m.RULE_SET_ROUTES) },
    { path: 'customers', loadChildren: () => import('@frontend/cpq-pricing-customers').then(m => m.PRICING_CUSTOMER_ROUTES) },
    { path: 'margins', loadChildren: () => import('@frontend/cpq-margin-dashboard').then(m => m.MARGIN_ROUTES) },
    { path: 'approvals', loadChildren: () => import('@frontend/cpq-approvals').then(m => m.APPROVAL_ROUTES) },
    { path: 'audit', loadChildren: () => import('@frontend/cpq-pricing-audit').then(m => m.PRICING_AUDIT_ROUTES) },
    { path: 'saas-tiers', loadChildren: () => import('@frontend/cpq-saas-tiers').then(m => m.SAAS_TIER_ROUTES) },
  ]
}
```

### Path aliases

Add to `tsconfig.base.json`:

```
@frontend/cpq-pricing-data-access → libs/cpq/pricing-data-access/src/index.ts
@frontend/cpq-pricing-ui          → libs/cpq/pricing-ui/src/index.ts
@frontend/cpq-pricing-customers   → libs/cpq/pricing-customers/src/index.ts
@frontend/cpq-price-books         → libs/cpq/price-books/src/index.ts
@frontend/cpq-rule-sets           → libs/cpq/rule-sets/src/index.ts
@frontend/cpq-pricing-simulator   → libs/cpq/pricing-simulator/src/index.ts
@frontend/cpq-pricing-audit       → libs/cpq/pricing-audit/src/index.ts
@frontend/cpq-margin-dashboard    → libs/cpq/margin-dashboard/src/index.ts
@frontend/cpq-discount-rules      → libs/cpq/discount-rules/src/index.ts
@frontend/cpq-saas-tiers          → libs/cpq/saas-tiers/src/index.ts
@frontend/cpq-approvals           → libs/cpq/approvals/src/index.ts
@frontend/cpq-quote-builder       → libs/cpq/quote-builder/src/index.ts
```

### Dashboard integration

Add pricing KPI cards to the existing Dashboard: total simulations run, average margin %, pending approvals count.

### Breadcrumbs

Add `PricingBreadcrumbComponent` for nested pricing routes (e.g., Pricing > Rules > Rule Set Detail > Rule Edit).

**Size:** M — Integration/wiring PR, touches several existing files.

---

## PR 14: Quote builder pricing integration

**Library:** `libs/cpq/quote-builder/`
**Alias:** `@frontend/cpq-quote-builder`
**Route:** `/quotes/:id/pricing` (or embedded in deal detail)

Integrate pricing into the deal/quote workflow.

- **`QuotePricingPanelComponent`** — Embeddable panel showing live pricing for a quote:
  - Line items with unit price, quantity, discounts, line total
  - Quote-level pricing waterfall
  - Margin summary with approval state indicator
  - "Recalculate" button triggering `POST /api/v1/pricing/execute`

- **`LineItemPricingComponent`** — Per-line detail: pricing model applied, discount breakdown, floor/ceiling indicators

- **`QuotePricingComparisonComponent`** — Compare pricing across quote versions using audit trail

- Integration point with existing `DealDetailComponent` — Add "Pricing" tab

- Integration point with `ApprovalDetailComponent` — Deep link from approval queue to quote pricing

**Size:** L — Cross-cutting integration touching deals and pricing domains.

---

## Dependency Graph

```
PR 1  (models)
 ├── PR 2  (data-access)          depends on: PR 1
 │    ├── PR 4  (customers)       depends on: PR 2
 │    ├── PR 5  (price-books)     depends on: PR 2
 │    ├── PR 6  (rule-sets)       depends on: PR 2
 │    ├── PR 7  (simulator)       depends on: PR 2, PR 3
 │    ├── PR 8  (audit)           depends on: PR 2
 │    ├── PR 9  (margins)         depends on: PR 2, PR 3, PR 8
 │    ├── PR 10 (discount-rules)  depends on: PR 2, PR 6
 │    ├── PR 11 (saas-tiers)      depends on: PR 2
 │    ├── PR 12 (approvals)       depends on: PR 2, PR 3
 │    └── PR 14 (quote-builder)   depends on: PR 2, PR 3, PR 12
 └── PR 3  (pricing-ui)           depends on: PR 1
      └── PR 13 (nav/routing)     depends on: PR 4–12
```

## Critical Path

**PR 1 → PR 2 → PR 3 → PR 7 (simulator)** — gives the team an end-to-end pricing UX to validate against the backend.

**Parallel track after PR 2:** PR 4 (customers), PR 5 (price-books), PR 6 (rule-sets), PR 8 (audit) can all be built concurrently.

**Late integration:** PR 13 (navigation) and PR 14 (quote builder) come last since they wire everything together.

---

## Backend API Coverage Matrix

| Backend Endpoint                             | Frontend PR                             |
| -------------------------------------------- | --------------------------------------- |
| `POST /api/v1/pricing/execute`               | PR 7 (simulator), PR 14 (quote-builder) |
| `POST /api/v1/pricing/simulate`              | PR 7 (simulator)                        |
| `POST /api/v1/pricing/simulate/bulk`         | PR 7 (simulator)                        |
| `GET /api/v1/pricing/audit/:quoteExternalId` | PR 8 (audit)                            |
| `CRUD /api/v1/pricing-customers`             | PR 4 (customers)                        |
| `CRUD /api/v1/price-books`                   | PR 5 (price-books)                      |
| `CRUD /api/v1/price-books/:id/entries`       | PR 5 (price-books)                      |
| `CRUD /api/v1/rule-sets`                     | PR 6 (rule-sets)                        |
| `POST /api/v1/rule-sets/:id/activate`        | PR 6 (rule-sets)                        |
| `POST /api/v1/rule-sets/:id/deprecate`       | PR 6 (rule-sets)                        |
| `CRUD /api/v1/rules`                         | PR 6 (rule-sets)                        |
| `GET /api/v1/rules/by-rule-set/:ruleSetId`   | PR 6 (rule-sets)                        |
| `POST /api/v1/rules/:id/validate`            | PR 6 (rule-sets)                        |
| `GET /api/v1/rules/:id/impact`               | PR 6 (rule-sets)                        |
| gRPC `calculatePrice`                        | N/A (server-to-server)                  |
| gRPC `calculateQuoteTotal`                   | N/A (server-to-server)                  |

---

## Conventions

All PRs follow the patterns established by the existing CRM modules:

| Concern          | Convention                                                                                                                  |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------- |
| State management | Angular signals (`signal()`, `computed()`, `effect()`)                                                                      |
| HTTP layer       | `HttpClient` + `BaseCrudService` from `@frontend/shared-data-access`                                                        |
| Components       | Standalone, `ChangeDetectionStrategy.OnPush`                                                                                |
| Styling          | Angular Material 21 + Tailwind CSS utility classes                                                                          |
| Testing          | Vitest with Angular testing utilities                                                                                       |
| Routing          | Lazy-loaded `Route[]` arrays exported from each library                                                                     |
| Naming           | `@frontend/cpq-*` for CPQ libraries vs `@frontend/crm-*` for CRM                                                            |
| Nx structure     | `libs/cpq/<feature>/` mirroring `libs/crm/<feature>/`                                                                       |
| Models           | JSDoc `/** Mirrors: com.opencrm.pricing.<class> */` on every DTO interface                                                  |
| Shared UI        | Reuse `DataTableComponent`, `PageHeaderComponent`, `StatusBadgeComponent`, `EmptyStateComponent` from `@frontend/shared-ui` |
