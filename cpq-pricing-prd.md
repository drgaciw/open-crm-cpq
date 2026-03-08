# Price-Drools Service PRD

## 1. Document Control

| Version | Date       | Author              | Description of Changes |
|---------|------------|---------------------|------------------------|
| 1.0     | 2025-05-15 | AI Team             | Initial version |
| 1.1     | 2025-05-15 | AI Team             | Added financial metrics rules |
| 1.2     | 2025-05-15 | AI Team             | Added AI-powered rule optimization |
| 2.0     | 2026-02-07 | Product + Platform  | Full rewrite: canonical structure, expanded pricing models, CPQ lifecycle, AI business-process agents, current vs target APIs |

## 2. Executive Summary

Price-Drools Service is the centralized pricing decision engine for enterprise software businesses that sell SaaS, on-premise,
and hybrid offerings. The service uses Drools for deterministic policy execution and integrates with AI components for
rule creation, analysis, testing, and optimization. This PRD defines current implementation behavior and target-state
capabilities required to support OpenCRM CPQ workflows, stronger pricing governance, and faster monetization changes.

## Table of Contents

1. [Document Control](#1-document-control)
2. [Executive Summary](#2-executive-summary)
3. [Product Scope and Goals](#3-product-scope-and-goals)
4. [Pricing Domain Model](#4-pricing-domain-model)
5. [Pricing Models (SaaS, On-Prem, Hybrid)](#5-pricing-models-saas-on-prem-hybrid)
6. [Rule Framework (Conditions, Actions, Scopes, Conflicts)](#6-rule-framework-conditions-actions-scopes-conflicts)
7. [CPQ Lifecycle Interactions (OpenCRM-style)](#7-cpq-lifecycle-interactions-opencrm-style)
8. [AI Agent Operating Model](#8-ai-agent-operating-model)
9. [Technical Architecture and Integrations](#9-technical-architecture-and-integrations)
10. [API and Interface Contracts (Current + Target)](#10-api-and-interface-contracts-current--target)
11. [Non-Functional Requirements](#11-non-functional-requirements)
12. [Success Metrics](#12-success-metrics)
13. [Rollout Phases](#13-rollout-phases)
14. [Glossary](#14-glossary)

## 3. Product Scope and Goals

### 3.1 Problem Statement

Enterprise software pricing is fragmented across CRM, CPQ, ERP, and ad hoc scripts. This creates inconsistent discounts,
approval bottlenecks, low explainability, and delayed go-to-market changes. Price-Drools Service provides a single policy
layer for quote-time price decisions with auditability and controlled AI assistance.

### 3.2 In Scope

- Centralized rule-based pricing for SaaS, on-prem, and hybrid products.
- Quote-level and line-level pricing decisions, including discounts, surcharges, floors, and commitments.
- OpenCRM-style CPQ lifecycle support: configuration, waterfall pricing, approvals, amendments, renewals.
- Rule management APIs and operational metrics.
- AI-assisted business process agents with human approval for commercial-impacting actions.
- Current-state and target-state API contracts for staged delivery.

### 3.3 Out of Scope

- Invoice generation and billing execution.
- Revenue recognition accounting.
- Contract document generation engine replacement.
- Payment orchestration and collections.
- End-customer checkout UX.

### 3.4 Primary Users

- Sales Representatives: configure quotes and request pricing.
- Deal Desk: evaluate exception pricing and approve or reject.
- Pricing Operations: author and maintain pricing policies.
- Revenue Operations: monitor discount leakage and process health.
- Finance: enforce margin and revenue guardrails.
- Engineering: integrate external systems and maintain service reliability.

### 3.5 Product Goals

- Reduce time to publish pricing changes without redeploying business apps.
- Increase pricing consistency and explainability across channels.
- Enforce margin and discount guardrails at quote time.
- Support complex enterprise contract lifecycle events.
- Improve win rate and renewal outcomes with controlled AI recommendations.

### 3.6 Assumptions and Defaults

- Market focus: enterprise hybrid sellers (SaaS + on-prem complexity).
- Scope boundary: document both current implementation and target capabilities.
- CPQ style: OpenCRM interaction framing.
- AI autonomy default: human approval first for commercial-impacting actions.
- Example depth: detailed rule catalog with concrete condition-action-output scenarios.
- API policy: canonical public paths are `/api/v1/*`.
- Rewrite strategy: full structural rewrite, single authoritative PRD.

## 4. Pricing Domain Model

### 4.1 Core Business Entities

| Entity | Purpose | Key Fields (Representative) |
|-------|---------|------------------------------|
| `Customer` | Customer identity and segmentation | `externalId`, `type`, `segment`, `region`, `industry`, `churnScore` |
| `Product` | Sellable SKU metadata | `externalId`, `productType`, `category`, `listPrice`, `currency` |
| `ProductBundle` | Group of products/options with constraints | `bundleType`, bundle items, option metadata |
| `Quote` | Commercial transaction header | `subscriptionType`, `durationInMonths`, `basePrice`, `finalPrice`, `ARR`, `TCV`, `ACV`, `CLV`, `metadata` |
| `QuoteLineItem` | Line-level product or bundle pricing | `quantity`, `listPrice`, `unitPrice`, `discountRate`, `finalPrice`, `lineItemType`, `metadata` |
| `RuleSet` | Versioned group of rules | `name`, `version`, `status`, `effectiveDate`, `expirationDate` |
| `Rule` | Atomic pricing policy | `ruleType`, `priority`, `status`, `content`, dependencies |

### 4.2 Financial Metrics

| Metric | Definition | Formula | Typical Usage |
|-------|------------|---------|---------------|
| ARR | Annual recurring revenue | `monthlyPrice * 12` | Volume discount tiers, account segmentation |
| TCV | Total contract value | `monthlyPrice * durationInMonths` | Premium pricing tiers, approval thresholds |
| ACV | Annual contract value | `(monthlyPrice * durationInMonths) / 12` | Minimum commitments, enterprise floors |
| CLV | Customer lifetime value | `monthlyPrice * expectedDuration` | Retention and risk-adjusted pricing |

### 4.3 Canonical Pricing Waterfall Fields

The pricing waterfall should be represented in quote/line metadata for explainability:

- `listPrice`
- `basePrice`
- `discountsApplied[]`
- `surchargesApplied[]`
- `partnerAdjustment`
- `prorationAdjustment`
- `currencyConversionRate`
- `taxAdjustment`
- `netPrice`
- `approvalState`
- `explanationCodes[]`

## 5. Pricing Models (SaaS, On-Prem, Hybrid)

### 5.1 SaaS Pricing Models

| Model | Commercial Intent | Primary Conditions | Typical Actions | Guardrails | Example Quote Scenario |
|------|--------------------|--------------------|-----------------|------------|------------------------|
| Named Seat Subscription | Predictable recurring revenue | `lineItemType == SAAS_SEAT_NAMED`, quantity, term | Set per-seat unit price, apply volume discount | Min price per seat, max discount by segment | 800 named seats for 36 months with enterprise discount cap |
| Concurrent Seat Subscription | Lower entry with shared usage | concurrent seat count, peak usage ratio | Apply concurrent multiplier over named seat baseline | Minimum named-to-concurrent ratio | 300 concurrent seats for support portal with concurrency factor 1.6 |
| Active User Pricing | Align value to monthly active adoption | MAU bands, customer segment | Apply MAU tier rate | MAU floor to avoid underbilling | 120k MAU analytics package with graduated tiers |
| Usage/Metered Pricing | Monetize consumption | billable events, API calls, storage GB | Compute billable units and overage | Overage cap and hard spend alerts | 50M API calls with overage after included 10M |
| Prepaid Credits | Improve cash flow via pre-commit | credit wallet balance, expiration, drawdown velocity | Deduct credits before overage | Credit expiry policy, negative balance protection | Customer prepays 500k credits and consumes against model inference |
| Tiered Feature Packaging | Segment willingness to pay | selected edition (Standard/Pro/Enterprise) | Restrict entitlements and apply package price | Upgrade-only constraints for gated features | Pro package with add-on for advanced governance |
| Base Subscription + Overage | Stable baseline with growth upside | base commit units plus actual usage | Price base fee, compute overage per unit | Hard cap on overage multiplier | 10k events included; extra events charged at overage rate |
| Committed Spend with True-up | Secure annual commit while allowing burst | annual spend commitment, quarterly true-up | Apply commit discount, reconcile deficit at true-up | True-up floor and credit carry rules | 2M annual commit with quarter-end reconciliation |
| Outcome/Value-Based Pricing | Price against delivered business outcome | KPI achievement bands, verified outcomes | Apply outcome multiplier or rebate | Outcome audit requirements and max rebate | Fraud prevention fee tied to blocked fraud dollars |
| Regional Price Books + FX | Protect margin by geography | region, local currency, effective date | Select regional price book, convert currency | FX staleness threshold, country legal rules | EMEA quote in EUR with quarterly FX refresh |

### 5.2 On-Prem Pricing Models

| Model | Commercial Intent | Primary Conditions | Typical Actions | Guardrails | Example Quote Scenario |
|------|--------------------|--------------------|-----------------|------------|------------------------|
| Perpetual + Annual Maintenance | Upfront license plus recurring support | perpetual license flag, support tier | Price perpetual license and annual maintenance percent | Maintenance floor and reinstatement policy | Perpetual security suite plus 22% annual support |
| Core/Socket Licensing | Align to compute footprint | core count, socket count, virtualization policy | Calculate capacity license quantity | Minimum core pack, audit clause flags | 96 cores licensed with 16-core pack minimum |
| Server/VM/Container Licensing | Flex deployment topology | deployment type, host count, VM density | Select server or VM unit pricing | VM overcommit guardrails | Mixed bare-metal + VM footprint across regions |
| Capacity-Based (TB/Data Volume) | Price by managed data scale | TB under management, retention tier | Apply capacity tiers and archive discounts | Capacity measurement source validation | 2.5 PB observability deployment with archive tier |
| Site/Enterprise Unlimited License | Simplify large global procurement | legal entity count, site count | Apply enterprise license fee | Strict geographic and affiliate usage limits | Global enterprise agreement across 18 sites |
| Module-Based Add-ons | Monetize specialized capabilities | selected modules, dependencies | Add module surcharge and dependency validation | Module compatibility matrix | Base platform + AI analytics + compliance module |
| Migration/Upgrade Licensing | Encourage version upgrades | previous entitlement, migration window | Apply migration credit or upgrade discount | Time-boxed migration credits | Legacy v8 upgrade to v10 with 30% migration credit |
| Support Tier Pricing (Standard/Premium/24x7) | Differentiate service value | SLA tier, response commitment | Set support multiplier | SLA entitlement and staffing limits | 24x7 premium support on mission-critical deployment |

### 5.3 Hybrid Pricing Models

| Model | Commercial Intent | Primary Conditions | Typical Actions | Guardrails | Example Quote Scenario |
|------|--------------------|--------------------|-----------------|------------|------------------------|
| On-Prem Core + SaaS Managed Add-on | Blend deployment flexibility with managed services | on-prem license present + SaaS add-on selected | Price each component and apply bundle adjustment | Prevent incompatible mix of deployment rights | On-prem SIEM plus SaaS threat intel feed |
| Bridge Contract with Perpetual Conversion Credits | Transition customers to subscription without revenue shock | existing perpetual entitlement, migration plan | Apply conversion credits toward SaaS subscription | Credit expiration and one-time-use rules | Convert perpetual estate to 3-year SaaS commitment |
| Cloud Migration Ramp + Dual Entitlement Window | De-risk migration program | ramp schedule, dual entitlement period | Apply phased ramp pricing and proration | Max dual-entitlement window and margin floor | 12-month migration with declining on-prem charge and rising SaaS charge |
## 6. Rule Framework (Conditions, Actions, Scopes, Conflicts)

### 6.1 Condition Matrix

| Condition Domain | Condition Examples | Typical Source Fields | Notes |
|------------------|-------------------|-----------------------|-------|
| Customer | Segment, region, industry, strategic account, churn risk | `Customer.segment`, `Customer.region`, `Customer.industry`, `Customer.churnScore`, account tags | Drives price sensitivity and guardrails |
| Product | Family, edition, bundle membership, entitlement flags | `Product.productType`, `Product.category`, bundle metadata | Enforces compatibility and packaging rules |
| Commercial | Quantity, term, payment terms, currency, partner channel | `QuoteLineItem.quantity`, `Quote.durationInMonths`, payment metadata | Core pricing and discount logic |
| Lifecycle | New sale, amendment, renewal, uplift cycle, co-term date | Quote metadata (`dealType`, `coTermDate`, `renewalWindow`) | Enables lifecycle-aware rules |
| Risk/Compliance | Margin floor, discount cap, export controls, geo restrictions | Margin calculations, policy metadata | Enforces legal and profitability constraints |

### 6.2 Action Matrix

| Action Type | Action Examples | Target Fields | Typical Use |
|-------------|-----------------|---------------|-------------|
| Price Setting | Set list/base/unit price | `QuoteLineItem.listPrice`, `Quote.basePrice` | Initialize pricing from price book and model |
| Discounts/Surcharges | Apply discount, surcharge, uplift | `discountRate`, adjustment arrays in metadata | Commercial negotiation and risk pricing |
| Guardrail Enforcement | Enforce floor/ceiling/margin thresholds | `Quote.finalPrice`, `approvalState` | Prevent non-compliant deals |
| Lifecycle Adjustments | Proration, co-term alignment, true-up calculations | metadata waterfall fields | Amendments and renewals |
| Configuration Actions | Attach/remove bundle options | line item collection, metadata | Guided selling and compatibility |
| Approval Routing | Route to tier and set reason | `approvalState`, `approvalTier`, reason codes | Governance and auditability |
| Explainability | Emit guidance and reason codes | `explanationCodes[]`, `metadata` | Sales transparency and audit |
| Telemetry | Emit audit and metrics tags | event payloads, metrics dimensions | Monitoring and optimization |

### 6.3 Rule Scopes and Priority Model

| Scope | Purpose | Priority Band (High to Low) |
|------|---------|------------------------------|
| Policy Guardrails | Hard legal/profit constraints | `900-1000` |
| Configuration Validation | Compatibility/dependency/exclusion checks | `700-899` |
| Line Pricing | Product- and model-specific pricing | `500-699` |
| Quote-Level Adjustments | Aggregate discounts, commitments, partner adjustments | `300-499` |
| Approval and Workflow | Routing and exception handling | `200-299` |
| Explainability and Telemetry | Reason codes and audit output | `100-199` |

### 6.4 Conflict Resolution and Fail-safe Policy

1. Hard constraints always execute before pricing adjustments.
2. Within the same priority, deterministic tie-break order is:
   - highest `priority`,
   - then highest absolute commercial impact,
   - then lexical `ruleId`/`ruleName`.
3. Discount stacking is controlled by explicit stacking policy flags.
4. Missing mandatory data must not produce silent pricing:
   - set quote status to `NEEDS_REVIEW`,
   - attach explanation code,
   - route approval.
5. Rule execution must remain deterministic for identical inputs.

### 6.5 Canonical Rule Metadata Type (PRD Contract)

```json
{
  "ruleCategory": "PRICING|CONFIGURATION|APPROVAL|RENEWAL|RISK",
  "conditionGroup": ["CUSTOMER_SEGMENT", "TERM_LENGTH", "REGION"],
  "actionSet": ["APPLY_DISCOUNT", "ENFORCE_MARGIN_FLOOR", "ROUTE_APPROVAL"],
  "priority": 650,
  "conflictPolicy": "HIGHEST_PRIORITY_THEN_IMPACT",
  "approvalPolicy": "HUMAN_REQUIRED_IF_MARGIN_LT_FLOOR",
  "explanationCode": "DISC_SEGMENT_ENTERPRISE_TIER2"
}
```

### 6.6 DRL-style Rule Examples by Major Pattern

#### Example A: Named Seat Volume Discount (SaaS)

```drools
rule "Named Seat Volume Discount"
salience 620
when
    $qli: QuoteLineItem(lineItemType == "SAAS_SEAT_NAMED", $qty: quantity, $list: listPrice)
    $quote: Quote(this == $qli.quote, durationInMonths >= 12)
then
    java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
    if ($qty >= 1000) discount = new java.math.BigDecimal("0.18");
    else if ($qty >= 500) discount = new java.math.BigDecimal("0.12");
    else if ($qty >= 100) discount = new java.math.BigDecimal("0.07");

    $qli.setDiscountRate(discount);
    $qli.setFinalPrice($list.multiply(java.math.BigDecimal.ONE.subtract(discount)));
end
```

#### Example B: Usage Overage with Included Units (SaaS)

```drools
rule "Usage Overage Charge"
salience 610
when
    $quote: Quote($meta: metadata)
    eval(getInt($meta, "includedUnits") < getInt($meta, "actualUnits"))
then
    int includedUnits = getInt($meta, "includedUnits");
    int actualUnits = getInt($meta, "actualUnits");
    int overageUnits = actualUnits - includedUnits;
    java.math.BigDecimal overageRate = getDecimal($meta, "overageRate");
    java.math.BigDecimal overageAmount = overageRate.multiply(java.math.BigDecimal.valueOf(overageUnits));
    setDecimal($meta, "overageAmount", overageAmount);
    setString($meta, "explanationCode", "USAGE_OVERAGE_APPLIED");
end
```

#### Example C: Perpetual License + Annual Maintenance (On-Prem)

```drools
rule "Perpetual Maintenance Price"
salience 580
when
    $qli: QuoteLineItem(lineItemType == "ONPREM_PERPETUAL", $licensePrice: listPrice)
then
    java.math.BigDecimal maintenancePct = new java.math.BigDecimal("0.22");
    java.math.BigDecimal maintenanceAmount = $licensePrice.multiply(maintenancePct);
    setDecimal($qli.metadata, "maintenanceAmount", maintenanceAmount);
    setString($qli.metadata, "maintenanceTier", "STANDARD");
end
```

#### Example D: Core Licensing Minimum Pack (On-Prem)

```drools
rule "Core License Pack Floor"
salience 590
when
    $qli: QuoteLineItem(lineItemType == "ONPREM_CORE", $meta: metadata)
    eval(getInt($meta, "coreCount") > 0)
then
    int coreCount = getInt($meta, "coreCount");
    int minPack = 16;
    int billableCores = ((coreCount + minPack - 1) / minPack) * minPack;
    setInt($meta, "billableCores", billableCores);
    setString($meta, "explanationCode", "CORE_PACK_ROUND_UP");
end
```

#### Example E: Migration Credit to SaaS (Hybrid)

```drools
rule "Perpetual to SaaS Migration Credit"
salience 560
when
    $quote: Quote($meta: metadata)
    eval(getBoolean($meta, "hasPerpetualEntitlement") && getBoolean($meta, "migrationProgramActive"))
then
    java.math.BigDecimal credit = getDecimal($meta, "perpetualBookValue")
        .multiply(new java.math.BigDecimal("0.30"));
    setDecimal($meta, "migrationCredit", credit);
    setString($meta, "explanationCode", "MIGRATION_CREDIT_30");
end
```

#### Example F: Approval Routing on Margin Exception

```drools
rule "Route Approval for Margin Exception"
salience 930
when
    $quote: Quote($meta: metadata, finalPrice != null, basePrice != null)
    eval(getDecimal($meta, "estimatedMarginPct").compareTo(new java.math.BigDecimal("0.35")) < 0)
then
    setString($meta, "approvalState", "PENDING");
    setString($meta, "approvalTier", "DEAL_DESK");
    setString($meta, "explanationCode", "MARGIN_BELOW_FLOOR");
end
```

#### Example G: Mid-term Amendment Proration and Co-term

```drools
rule "Amendment Proration"
salience 540
when
    $quote: Quote($meta: metadata)
    eval("AMENDMENT".equals(getString($meta, "dealType")))
then
    int remainingDays = getInt($meta, "remainingTermDays");
    int fullTermDays = getInt($meta, "fullTermDays");
    java.math.BigDecimal deltaAmount = getDecimal($meta, "amendmentDeltaAmount");
    java.math.BigDecimal prorated = deltaAmount
        .multiply(java.math.BigDecimal.valueOf(remainingDays))
        .divide(java.math.BigDecimal.valueOf(fullTermDays), 4, java.math.RoundingMode.HALF_UP);
    setDecimal($meta, "prorationAdjustment", prorated);
    setString($meta, "coTermDate", getString($meta, "masterContractCoTermDate"));
end
```

#### Example H: Renewal Uplift with Retention Guardrail

```drools
rule "Renewal Uplift and Retention Offer"
salience 530
when
    $quote: Quote($meta: metadata, customer != null, $cust: customer)
    eval("RENEWAL".equals(getString($meta, "dealType")))
then
    java.math.BigDecimal baseUplift = new java.math.BigDecimal("0.08");
    if ($cust.getChurnScore() != null && $cust.getChurnScore() > 70) {
        baseUplift = new java.math.BigDecimal("0.03");
        setString($meta, "retentionOffer", "ENABLE_12M_PRICE_PROTECTION");
    }
    setDecimal($meta, "renewalUpliftPct", baseUplift);
    setString($meta, "explanationCode", "RENEWAL_UPLIFT_POLICY");
end
```

## 7. CPQ Lifecycle Interactions (OpenCRM-style)

### 7.1 End-to-End CPQ Flow

The CPQ lifecycle follows an OpenCRM interaction model adapted for API-first operation. Each stage maps to concrete service endpoints and state transitions.

```
[CRM Deal Created] ──► [Configure Products] ──► [Build Quote] ──► [Price Quote]
                                                                        │
  [Contract Execution] ◄── [Customer Accepts] ◄── [Send to Customer] ◄──┘
        │                                                │
        ├── [Amendment]  ──► re-enter CPQ flow           ├── [Expire] (timeout)
        └── [Renewal]    ──► re-enter CPQ flow           └── [Reject] ──► revise
```

### 7.2 Stage-to-API Mapping

| CPQ Stage | Service | Primary Endpoints | State Transition |
|-----------|---------|-------------------|------------------|
| Deal Creation | CRM | `POST /api/v1/deals` | Deal enters pipeline |
| Product Configuration | Product | `POST /api/v1/configuration/validate`, `GET /api/v1/products`, `GET /api/v1/product-bundles/{id}` | Validate product compatibility |
| Quote Creation | Quote | `POST /api/v1/quotes`, `POST /api/v1/quotes/{id}/line-items` | Quote enters DRAFT |
| Pricing Execution | Pricing | `POST /api/v1/pricing/execute` | Waterfall applied, financials computed |
| Internal Review | Quote | `POST /api/v1/quotes/{id}/submit` | DRAFT → IN_REVIEW |
| Approval Workflow | Quote | `POST /api/v1/quotes/{id}/approve`, `POST /api/v1/quotes/{id}/reject` | IN_REVIEW → APPROVED or REJECTED |
| Multi-tier Approval | Quote | `POST /api/v1/quotes/{quoteId}/approvals`, `POST /api/v1/quotes/approvals/{id}/approve` | PENDING_APPROVAL → APPROVED |
| Customer Delivery | Quote | `POST /api/v1/quotes/{id}/send` | APPROVED → SENT |
| Customer Acceptance | Quote | `POST /api/v1/quotes/{id}/accept` | SENT → ACCEPTED, publishes event |
| CRM Update | CRM (event) | Redis `quote:accepted` → DealService | Deal advances to WON stage |
| Document Generation | Quote | `POST /api/v1/quotes/{id}/documents/generate` | PDF proposal created |
| Amendment | Quote | `POST /api/v1/quotes/{id}/clone` + new line items | New DRAFT from existing quote |
| Renewal | Quote | `POST /api/v1/quotes/{id}/clone` with renewal pricing | New DRAFT with uplift rules applied |

### 7.3 Quote Lifecycle State Machine

```
                    ┌─────────────┐
                    │    DRAFT    │◄──────────────────────────┐
                    └──────┬──────┘                           │
                           │ submit                           │ (revise)
                    ┌──────▼──────┐                           │
                    │  IN_REVIEW  │                           │
                    └──────┬──────┘                           │
                           │ approve / needs-approval         │
                    ┌──────▼──────────┐     reject     ┌─────┴─────┐
                    │PENDING_APPROVAL │────────────────►│ REJECTED  │
                    └──────┬──────────┘                └───────────┘
                           │ approve
                    ┌──────▼──────┐
                    │  APPROVED   │
                    └──────┬──────┘
                           │ send
                    ┌──────▼──────┐
                    │    SENT     │
                    └──┬─────┬───┘
                       │     │ timeout
              accept   │     │
                ┌──────▼┐  ┌─▼──────┐
                │ACCEPTED│  │EXPIRED │
                └────────┘  └────────┘

  Any state (except ACCEPTED) ──► CANCELLED
```

### 7.4 Amendment and Renewal Flows

**Amendment Flow**: When a customer modifies an active contract mid-term, the amendment proration rule (`amendment-proration.drl`) calculates the prorated delta based on remaining term days vs full term days. The co-term date is aligned to the master contract end date.

**Renewal Flow**: At renewal, the system applies `renewal-uplift.drl` which adjusts the uplift percentage based on customer churn score. High-risk customers (churn score > 70) receive a reduced uplift (3% vs standard 8%) and a retention offer (12-month price protection).

### 7.5 Approval Tiers

| Tier | Trigger Condition | Approver Role |
|------|-------------------|---------------|
| DEAL_DESK | Margin below 35% floor (`approval-margin-exception.drl`) | Deal Desk Analyst |
| SALES_VP | Discount exceeds 25% or TCV > threshold | Sales VP |
| CFO | Strategic pricing exception or margin below 20% | CFO / Finance |

---

## 8. AI Agent Operating Model

### 8.1 Agent Architecture

AI agents operate as advisory and optimization layers on top of the deterministic rule engine. All agents follow a human-in-the-loop model for commercial-impacting actions.

| Agent | Purpose | Input | Output | Autonomy Level |
|-------|---------|-------|--------|----------------|
| Rule Optimization Agent | Analyze rule performance and suggest improvements | Pricing audit logs, win/loss data | Rule modification proposals | Advisory (human approval required) |
| Deal Desk Copilot | Assist deal desk with pricing guidance | Quote context, customer history, margin targets | Recommended pricing, risk assessment | Advisory (human decision) |
| Pricing Anomaly Detector | Identify unusual pricing patterns | Audit trail time series | Anomaly alerts with explanation | Automated alerting, manual investigation |
| Renewal Intelligence Agent | Predict renewal risk and recommend strategy | Customer usage data, churn scores, contract history | Renewal playbook recommendations | Advisory (sales rep decision) |

### 8.2 Rule Optimization Agent

**Objective**: Continuously improve pricing rule effectiveness by analyzing outcomes.

**Process**:
1. Ingest pricing audit logs from `GET /api/v1/pricing/audit/{quoteExternalId}`
2. Correlate with deal outcomes (won/lost) from CRM deal pipeline data
3. Identify rules that correlate with lost deals or margin erosion
4. Generate proposed DRL modifications with impact analysis
5. Submit proposals for human review via approval workflow
6. Validated changes published through `POST /api/v1/rule-sets` → `POST /api/v1/rule-sets/{id}/activate`

### 8.3 Deal Desk Copilot

**Objective**: Provide real-time pricing guidance during quote construction.

**Capabilities**:
- Simulate pricing scenarios via `POST /api/v1/pricing/simulate` without persisting audit records
- Compare current quote against historical win-rate data for similar deal profiles
- Suggest discount levels that maximize probability of close while maintaining margin
- Flag waterfall anomalies (e.g., unusual discount stacking, missing surcharges)
- Generate natural-language explanations of pricing decisions from explanation codes

### 8.4 Safety and Governance

- All AI-generated rule modifications require human approval before activation
- Simulation mode (`/pricing/simulate`) is the primary AI interaction point; no persistent side effects
- Audit logs record when AI-recommended pricing was accepted or overridden
- AI agents cannot directly modify active rule sets or approve quotes

---

## 9. Technical Architecture and Integrations

### 9.1 Service Topology

```
┌─────────────────────────────────────────────────────────┐
│                   Gateway Service (8080)                 │
│              Spring Cloud Gateway + Resilience4j         │
└───────┬──────────┬──────────┬──────────┬────────────────┘
        │          │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼─────┐
   │  CRM   │ │Pricing │ │Product │ │ Quote  │
   │ :8081  │ │ :8082  │ │ :8083  │ │ :8084  │
   └───┬────┘ └───┬────┘ └───┬────┘ └──┬─────┘
       │          │          │         │
       └──────────┴──────────┴─────────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
    ┌────▼────┐  ┌────▼────┐  ┌───▼───┐
    │PostgreSQL│  │  Redis  │  │Future │
    │ (schemas)│  │Pub/Sub +│  │Kafka  │
    │          │  │ Cache   │  │       │
    └─────────┘  └─────────┘  └───────┘
```

### 9.2 Database Architecture

Single PostgreSQL instance with schema-per-service isolation:

| Service | Schema | Port | Key Tables |
|---------|--------|------|------------|
| CRM | `crm` | 8081 | leads, contacts, deals, organizations |
| Pricing | `pricing` | 8082 | customers, pricing_products, rule_sets, rules, price_books, price_book_entries, pricing_audit_log |
| Product | `product` | 8083 | products, product_bundles, product_bundle_items, product_option_groups, product_options, product_attributes, product_attribute_values, configuration_rules |
| Quote | `quote` | 8084 | quotes, quote_line_items, approval_requests, quote_documents, quote_status_history |

All tables use a common base structure: `id` (BIGSERIAL PK), `uuid` (unique), `created_at`, `updated_at`, `created_by`, `updated_by`, `version` (optimistic locking), `deleted` (soft delete).

### 9.3 Inter-Service Communication

| Pattern | Usage | Implementation |
|---------|-------|----------------|
| Synchronous REST | Quote → Pricing (execute pricing), Quote → Product (validate config, fetch products) | Spring `RestClient` with configurable base URLs |
| Async Event (Redis Pub/Sub) | Quote → CRM (quote accepted event) | `StringRedisTemplate` publish, `RedisMessageListenerContainer` subscribe on `quote:accepted` channel |
| API Gateway Routing | External clients → internal services | Spring Cloud Gateway with path-based predicates |
| Circuit Breaking | Fault tolerance for inter-service calls | Resilience4j circuit breakers per target service |

### 9.4 Shared Libraries

| Library | Package | Purpose |
|---------|---------|---------|
| `common` | `com.opencrm.common` | BaseEntity, ApiResponse, exceptions (ResourceNotFoundException, BusinessException), global exception handling patterns |
| `rules-engine` | `com.opencrm.rules` | Drools engine abstraction, fact model classes (QuoteFact, CustomerFact, ProductFact), rule compilation and caching |

### 9.5 Infrastructure Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| Database | PostgreSQL 16 | Primary data store, schema-per-service |
| Cache / Events | Redis 7 | Response caching (TTL 600s), Pub/Sub event bus |
| API Gateway | Spring Cloud Gateway | Routing, rate limiting, circuit breaking |
| Migrations | Flyway | Schema versioning per service |
| PDF Generation | OpenHTMLtoPDF + Thymeleaf | Quote document rendering |
| Rules Engine | Drools 8.44.0 | Deterministic pricing rule execution |
| Containerization | Docker + Docker Compose | Local development and deployment |

---

## 10. API and Interface Contracts (Current + Target)

### 10.1 Current State: Implemented Endpoints

**Total: 107 REST endpoints across 4 services**

#### Pricing Service (20 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/pricing-customers` | Create pricing customer |
| GET | `/api/v1/pricing-customers` | List customers (filter: segment) |
| GET | `/api/v1/pricing-customers/{id}` | Get customer by ID |
| PUT | `/api/v1/pricing-customers/{id}` | Update customer |
| DELETE | `/api/v1/pricing-customers/{id}` | Soft delete customer |
| POST | `/api/v1/rule-sets` | Create rule set |
| GET | `/api/v1/rule-sets` | List rule sets (filter: status) |
| GET | `/api/v1/rule-sets/{id}` | Get rule set by ID |
| PUT | `/api/v1/rule-sets/{id}` | Update rule set |
| DELETE | `/api/v1/rule-sets/{id}` | Soft delete rule set |
| POST | `/api/v1/rule-sets/{id}/activate` | DRAFT → ACTIVE |
| POST | `/api/v1/rule-sets/{id}/deprecate` | ACTIVE → DEPRECATED |
| POST | `/api/v1/rules` | Create rule within rule set |
| GET | `/api/v1/rules/by-rule-set/{ruleSetId}` | List rules by rule set |
| GET | `/api/v1/rules/{id}` | Get rule by ID |
| PUT | `/api/v1/rules/{id}` | Update rule |
| DELETE | `/api/v1/rules/{id}` | Soft delete rule |
| POST | `/api/v1/rules/{id}/validate` | Validate DRL syntax |
| POST | `/api/v1/pricing/execute` | Execute pricing with audit trail |
| POST | `/api/v1/pricing/simulate` | Simulate pricing (no persistence) |

#### Product Service (28 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/products` | Create product |
| GET | `/api/v1/products` | List products (filters: type, category, status) |
| GET | `/api/v1/products/search` | Search by name/SKU/description |
| GET | `/api/v1/products/{id}` | Get product by ID |
| GET | `/api/v1/products/sku/{sku}` | Get product by SKU |
| PUT | `/api/v1/products/{id}` | Update product |
| POST | `/api/v1/products/{id}/activate` | Activate product |
| POST | `/api/v1/products/{id}/deprecate` | Deprecate product |
| DELETE | `/api/v1/products/{id}` | Soft delete product |
| POST | `/api/v1/product-bundles` | Create bundle |
| GET | `/api/v1/product-bundles` | List bundles |
| GET | `/api/v1/product-bundles/{id}` | Get bundle by ID |
| GET | `/api/v1/product-bundles/product/{productId}` | Bundles by product |
| POST | `/api/v1/product-bundles/{id}/items` | Add bundle item |
| DELETE | `/api/v1/product-bundles/{bundleId}/items/{itemId}` | Remove bundle item |
| DELETE | `/api/v1/product-bundles/{id}` | Delete bundle |
| POST | `/api/v1/product-options/groups` | Create option group |
| GET | `/api/v1/product-options/groups/{id}` | Get option group |
| GET | `/api/v1/product-options/groups/product/{productId}` | Option groups by product |
| POST | `/api/v1/product-options/groups/{groupId}/options` | Add option |
| DELETE | `/api/v1/product-options/groups/{id}` | Delete option group |
| POST | `/api/v1/product-attributes` | Create attribute definition |
| GET | `/api/v1/product-attributes` | List attribute definitions |
| GET | `/api/v1/product-attributes/{id}` | Get attribute |
| DELETE | `/api/v1/product-attributes/{id}` | Delete attribute |
| POST | `/api/v1/product-attributes/products/{productId}/values` | Set attribute value |
| GET | `/api/v1/product-attributes/products/{productId}/values` | Get product attribute values |
| POST | `/api/v1/configuration/rules` | Create configuration rule |
| GET | `/api/v1/configuration/rules` | List configuration rules |
| GET | `/api/v1/configuration/rules/{id}` | Get configuration rule |
| GET | `/api/v1/configuration/rules/product/{productId}` | Rules by source product |
| POST | `/api/v1/configuration/validate` | Validate product configuration |
| DELETE | `/api/v1/configuration/rules/{id}` | Delete configuration rule |

#### Quote Service (26 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/quotes` | Create quote |
| GET | `/api/v1/quotes` | List quotes (filters: status, dealType, customerExternalId) |
| GET | `/api/v1/quotes/{id}` | Get quote by ID |
| GET | `/api/v1/quotes/number/{quoteNumber}` | Get quote by number |
| PUT | `/api/v1/quotes/{id}` | Update quote (DRAFT/REJECTED only) |
| POST | `/api/v1/quotes/{id}/submit` | DRAFT → IN_REVIEW |
| POST | `/api/v1/quotes/{id}/approve` | Approve quote |
| POST | `/api/v1/quotes/{id}/reject` | Reject quote |
| POST | `/api/v1/quotes/{id}/send` | APPROVED → SENT |
| POST | `/api/v1/quotes/{id}/accept` | SENT → ACCEPTED (publishes event) |
| POST | `/api/v1/quotes/{id}/expire` | SENT → EXPIRED |
| POST | `/api/v1/quotes/{id}/cancel` | Cancel quote |
| POST | `/api/v1/quotes/{id}/clone` | Clone quote as new DRAFT |
| DELETE | `/api/v1/quotes/{id}` | Soft delete quote |
| POST | `/api/v1/quotes/{quoteId}/line-items` | Add line item |
| GET | `/api/v1/quotes/{quoteId}/line-items` | List line items |
| PUT | `/api/v1/quotes/{quoteId}/line-items/{lineItemId}` | Update line item |
| DELETE | `/api/v1/quotes/{quoteId}/line-items/{lineItemId}` | Remove line item |
| POST | `/api/v1/quotes/{quoteId}/approvals` | Submit for multi-tier approval |
| GET | `/api/v1/quotes/{quoteId}/approvals` | Approval history |
| GET | `/api/v1/quotes/approvals/pending` | All pending approvals |
| POST | `/api/v1/quotes/approvals/{approvalId}/approve` | Approve approval request |
| POST | `/api/v1/quotes/approvals/{approvalId}/reject` | Reject approval request |
| POST | `/api/v1/quotes/{quoteId}/documents/generate` | Generate PDF document |
| GET | `/api/v1/quotes/{quoteId}/documents` | List documents |
| GET | `/api/v1/quotes/{quoteId}/documents/{documentId}/download` | Download document |

#### CRM Service (33 endpoints)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/leads` | Create lead |
| GET | `/api/v1/leads` | List leads (filters: status, priority) |
| GET | `/api/v1/leads/{id}` | Get lead by ID |
| PUT | `/api/v1/leads/{id}` | Update lead |
| DELETE | `/api/v1/leads/{id}` | Soft delete lead |
| GET | `/api/v1/leads/search` | Search leads by title |
| POST | `/api/v1/leads/{id}/contact` | Mark as contacted |
| POST | `/api/v1/leads/{id}/qualify` | Qualify lead |
| POST | `/api/v1/leads/{id}/convert` | Convert lead to deal |
| POST | `/api/v1/leads/{id}/lose` | Mark lead as lost |
| GET | `/api/v1/leads/stats` | Lead statistics by status |
| POST | `/api/v1/contacts` | Create contact |
| GET | `/api/v1/contacts` | List contacts |
| GET | `/api/v1/contacts/{id}` | Get contact by ID |
| PUT | `/api/v1/contacts/{id}` | Update contact |
| DELETE | `/api/v1/contacts/{id}` | Soft delete contact |
| GET | `/api/v1/contacts/search` | Search contacts |
| POST | `/api/v1/deals` | Create deal |
| GET | `/api/v1/deals` | List deals (filter: stage) |
| GET | `/api/v1/deals/{id}` | Get deal by ID |
| PUT | `/api/v1/deals/{id}` | Update deal |
| DELETE | `/api/v1/deals/{id}` | Soft delete deal |
| GET | `/api/v1/deals/search` | Search deals |
| POST | `/api/v1/deals/{id}/advance` | Advance deal stage |
| POST | `/api/v1/deals/{id}/win` | Mark deal as won |
| POST | `/api/v1/deals/{id}/lose` | Mark deal as lost |
| GET | `/api/v1/deals/pipeline` | Pipeline summary |
| POST | `/api/v1/organizations` | Create organization |
| GET | `/api/v1/organizations` | List organizations |
| GET | `/api/v1/organizations/{id}` | Get organization by ID |
| PUT | `/api/v1/organizations/{id}` | Update organization |
| DELETE | `/api/v1/organizations/{id}` | Soft delete organization |
| GET | `/api/v1/organizations/search` | Search organizations |

### 10.2 Target State: Planned Enhancements

| Capability | Endpoints (Planned) | Status |
|------------|---------------------|--------|
| Price Book CRUD | `GET/POST/PUT/DELETE /api/v1/price-books`, `/api/v1/price-books/{id}/entries` | Entities exist, controllers pending |
| Pricing audit query | `GET /api/v1/pricing/audit/{quoteExternalId}` | Controller exists, service pending full implementation |
| Quote calculate (calls pricing) | `POST /api/v1/quotes/{id}/calculate` | Client exists, integration pending |
| Bulk pricing simulation | `POST /api/v1/pricing/simulate/bulk` | Not started |
| Rule impact analysis | `GET /api/v1/rules/{id}/impact` | Not started |
| Webhook notifications | `POST /api/v1/webhooks` | Not started |
| Usage event ingestion | `POST /api/v1/pricing/usage-events` | Not started |
| Contract management | `/api/v1/contracts/**` | Not started |

### 10.3 Standard Response Format

All endpoints return a consistent `ApiResponse<T>` wrapper:

```json
{
  "success": true,
  "data": { ... },
  "message": null,
  "error": null,
  "pageInfo": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

Error responses:

```json
{
  "success": false,
  "data": null,
  "message": "Quote can only be edited in DRAFT or REJECTED status. Current: APPROVED",
  "error": "QUOTE_NOT_EDITABLE"
}
```

---

## 11. Non-Functional Requirements

### 11.1 Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Pricing execution latency (p50) | < 100ms | From API request receipt to response |
| Pricing execution latency (p95) | < 200ms | Including rule compilation cache hit |
| Pricing execution latency (p99) | < 500ms | Including rule cache miss and recompilation |
| Quote CRUD operations | < 50ms p95 | Standard database operations |
| Product configuration validation | < 100ms p95 | REQUIRES/EXCLUDES rule evaluation |
| PDF document generation | < 3s | Including template rendering and PDF conversion |
| Concurrent pricing requests | 200 req/s sustained | Per pricing-service instance |

### 11.2 Availability and Reliability

| Requirement | Target |
|-------------|--------|
| Service availability | 99.9% (8.76h downtime/year) |
| Database availability | 99.95% |
| Recovery Time Objective (RTO) | < 15 minutes |
| Recovery Point Objective (RPO) | < 1 minute (with WAL replication) |
| Circuit breaker threshold | 50% failure rate triggers open state |
| Circuit breaker recovery | 30s half-open probe interval |

### 11.3 Scalability

| Dimension | Current Target | Scale Strategy |
|-----------|----------------|----------------|
| Rules per rule set | Up to 500 | Drools KieSession compiled and cached |
| Active rule sets | Up to 50 | Redis-cached compiled rule binaries |
| Quotes per day | 10,000 | Horizontal service scaling |
| Line items per quote | Up to 200 | Batch processing in pricing execution |
| Concurrent users | 500 | Connection pooling (HikariCP, max 20/service) |
| Audit log retention | 7 years | Partitioned by year, archival to cold storage |

### 11.4 Security

| Control | Implementation |
|---------|----------------|
| Authentication | Stateless (JWT-ready, currently permitAll for MVP) |
| Authorization | Role-based (ADMIN, DEAL_DESK, SALES_REP) - target state |
| Transport | HTTPS enforced at gateway level |
| Data at rest | PostgreSQL encryption + schema isolation |
| Audit trail | Immutable pricing_audit_log with input/output snapshots |
| Soft delete | All entities support logical deletion, no physical removes |
| Optimistic locking | Version field on all entities prevents concurrent update conflicts |
| Input validation | Jakarta Bean Validation on all request DTOs |

### 11.5 Observability

| Capability | Implementation |
|------------|----------------|
| Health checks | Spring Actuator `/actuator/health` per service |
| Metrics | Micrometer + Prometheus (`/actuator/prometheus`) |
| Logging | Structured (SLF4J/Logback), DEBUG for `com.opencrm` |
| Tracing | Correlation IDs via gateway (target: OpenTelemetry) |
| API documentation | SpringDoc OpenAPI at `/swagger-ui.html` per service |

---

## 12. Success Metrics

### 12.1 Pricing Consistency

| Metric | Definition | Target | Baseline |
|--------|------------|--------|----------|
| Pricing consistency rate | % of quotes where rule-based price matches expected waterfall | > 99.5% | Manual: ~85% |
| Discount policy compliance | % of discounts within approved guardrails | > 98% | ~70% |
| Approval accuracy | % of approval routes matching policy intent | > 95% | ~60% |

### 12.2 Operational Efficiency

| Metric | Definition | Target | Baseline |
|--------|------------|--------|----------|
| Quote cycle time | Time from DRAFT to SENT | < 4 hours median | 2-3 days |
| Rule publication time | Time to author and activate a pricing rule change | < 1 hour | 1-2 weeks (code deploy) |
| Approval turnaround | Time from PENDING_APPROVAL to decision | < 2 hours median | 1 day |

### 12.3 Revenue Impact

| Metric | Definition | Target |
|--------|------------|--------|
| Discount leakage reduction | Reduction in unapproved or excessive discounts | 30% reduction in year 1 |
| Margin floor compliance | % of deals above 35% margin floor | > 95% |
| Renewal rate improvement | Improvement in renewal conversion | +5% through AI-assisted retention pricing |
| Amendment accuracy | % of amendments with correct proration | > 99% |

### 12.4 System Health

| Metric | Definition | Target |
|--------|------------|--------|
| API error rate | 5xx responses / total responses | < 0.1% |
| Rule execution success rate | Successful pricing executions / total attempts | > 99.9% |
| Cache hit ratio | Compiled rule cache hits / total rule lookups | > 90% |
| Event delivery rate | Quote accepted events successfully processed | > 99.5% |

---

## 13. Rollout Phases

### Phase 0: Foundation (Completed)

**Deliverables**:
- Database schema initialization (4 schemas: crm, pricing, product, quote)
- Parent POM with module declarations and dependency management
- `rules-engine` shared library (Drools abstraction, fact models, caching)
- Docker Compose updated with all service containers
- Removed legacy `cpq-service` stub

### Phase 1: Pricing Service (Completed)

**Deliverables**:
- Full pricing-service module with 20 REST endpoints
- Customer, RuleSet, Rule, PriceBook, PricingExecution domains
- Pricing waterfall execution engine with audit logging
- 8 default DRL rule files (Examples A-H from PRD section 6.6)
- Database seed migration for default rules
- Drools-based rule compilation and validation

### Phase 2: Product Service (Completed)

**Deliverables**:
- Full product-service module with 28+ REST endpoints
- Product catalog with search, bundles, options, attributes
- Configuration rule engine (REQUIRES/EXCLUDES/RECOMMENDS/DEFAULT/VALIDATES)
- Product lifecycle management (DRAFT → ACTIVE → DEPRECATED → END_OF_LIFE)

### Phase 3: Quote Service (Completed)

**Deliverables**:
- Full quote-service module with 26 REST endpoints
- Quote lifecycle state machine with 9 states and guarded transitions
- Line item management with automatic total recalculation
- Multi-tier approval workflow (DEAL_DESK, SALES_VP, CFO)
- PDF document generation (OpenHTMLtoPDF + Thymeleaf)
- Inter-service clients for pricing and product services
- Quote number generation (Q-YYYYMMDD-XXXX format)

### Phase 4: Integration (Completed)

**Deliverables**:
- Gateway route configuration for all 4 services with circuit breakers
- Redis Pub/Sub event bridge (quote-service → crm-service)
- `QuoteAcceptedEvent` triggers CRM deal stage update to WON
- RestClient configuration for sync inter-service calls

### Phase 5: PRD and Documentation (Current)

**Deliverables**:
- Complete PRD with sections 7-14 (CPQ lifecycle, AI agents, architecture, API contracts, NFRs, metrics, rollout, glossary)

### Phase 6: Testing and Hardening (Planned)

**Deliverables**:
- Unit tests for all service layers
- Integration tests with TestContainers
- End-to-end flow test: Deal → Product Config → Quote → Price → Approve → Accept → CRM Update
- Performance benchmarking of pricing execution
- Security review and JWT integration

### Phase 7: Advanced Features (Planned)

**Deliverables**:
- OpenL Tablets spreadsheet-based rule support in rules-engine
- Kafka migration for event bus (replacing Redis Pub/Sub)
- Contract management service
- Usage event ingestion pipeline
- AI agent integration (Rule Optimization Agent, Deal Desk Copilot)
- Webhook notification system
- Multi-currency support with FX rate management

---

## 14. Glossary

| Term | Definition |
|------|------------|
| **ACV** | Annual Contract Value. Total contract value divided by contract years. |
| **Amendment** | Mid-term contract modification that triggers proration and co-term alignment. |
| **ARR** | Annual Recurring Revenue. Monthly recurring price multiplied by 12. |
| **CLV** | Customer Lifetime Value. Expected total revenue over the customer relationship. |
| **Co-term** | Aligning an amendment or add-on end date to the master contract end date. |
| **CPQ** | Configure, Price, Quote. The end-to-end process of building and pricing commercial proposals. |
| **Deal Desk** | Specialized team that reviews and approves non-standard pricing. |
| **DMN** | Decision Model and Notation. OMG standard for modeling business decisions (future support). |
| **DRL** | Drools Rule Language. Text-based format for expressing business rules in the Drools engine. |
| **Drools** | Open-source business rules engine (Red Hat). Used for deterministic pricing rule execution. |
| **Flyway** | Database migration tool. Manages schema versioning per service. |
| **KieSession** | Drools runtime session for rule execution against a set of facts. |
| **Line Item** | Individual product or SKU entry within a quote, carrying its own pricing waterfall. |
| **MAU** | Monthly Active Users. Usage metric for consumption-based pricing models. |
| **MVP** | Minimum Viable Product. Current implementation scope. |
| **NFR** | Non-Functional Requirement. Quality attributes like performance, reliability, security. |
| **OpenHTMLtoPDF** | Java library for rendering HTML/CSS to PDF documents. Used for quote proposals. |
| **OpenL Tablets** | Open-source business rules engine using Excel spreadsheets. Planned for future integration. |
| **Price Book** | Regional or segment-specific catalog of list prices and floor prices per product. |
| **Proration** | Proportional pricing adjustment based on remaining contract term. |
| **Pub/Sub** | Publish-Subscribe messaging pattern. Used via Redis for inter-service events. |
| **Quote** | Commercial proposal containing line items, pricing, terms, and approval status. |
| **Renewal** | Contract extension at end of term, typically with uplift pricing adjustment. |
| **RestClient** | Spring Framework 6.1+ declarative HTTP client for synchronous inter-service calls. |
| **Rule Set** | Versioned collection of pricing rules with lifecycle (DRAFT → ACTIVE → DEPRECATED). |
| **Salience** | Drools priority mechanism. Higher salience rules execute first within a session. |
| **Soft Delete** | Logical deletion by setting `deleted=true` rather than physical row removal. |
| **TCV** | Total Contract Value. Total revenue over the full contract duration. |
| **Thymeleaf** | Java template engine. Used for rendering quote PDF templates. |
| **Waterfall** | Sequential chain of pricing adjustments from list price to final price with full auditability. |
