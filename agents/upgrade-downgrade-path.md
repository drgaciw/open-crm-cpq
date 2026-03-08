# Agent PRD: cpq-agent-029

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-029                                  |
| **Name**          | Upgrade/Downgrade Path Agent                   |
| **Group**         | Analytics & Intelligence                       |
| **Primary Service** | product-service (8083), pricing-service (8082), quote-service (8080) |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-028 (Renewal Opportunity Detection Agent) |

---

## Purpose

The Upgrade/Downgrade Path Agent recommends subscription-level changes at renewal time or upon explicit request during the contract lifecycle. It analyzes a customer's current subscription, usage patterns, and growth trajectory to determine whether an upgrade, downgrade, or licensing model migration is appropriate. For customers on growth trajectories -- expanding headcount, increasing usage, or entering new business units -- the agent suggests upgrade paths that align product tiers, add-on modules, and capacity levels with projected needs. For contracting customers -- declining usage, reduced headcount, or budget constraints -- it recommends right-sizing options that preserve the relationship while adjusting the subscription to match actual consumption, preventing churn by proactively addressing overprovisioning.

A critical capability of this agent is managing perpetual-to-SaaS migration paths. It implements the `migration-credit.drl` Drools rule (salience 560), which calculates a migration credit equal to 30% of the customer's perpetual license book value (`perpetualBookValue * 0.30`). This credit is applied as a discount on the first year of the SaaS subscription to incentivize the transition. The agent generates a comprehensive Total Cost of Ownership (TCO) analysis comparing the customer's current perpetual licensing costs (including maintenance, support, and infrastructure) against the proposed SaaS subscription over a 3-year horizon, demonstrating the economic case for migration. Feature comparison matrices ensure the customer understands exactly what they gain and lose in the transition. All path recommendations are advisory -- the sales rep or customer success manager must approve and initiate any subscription changes. The agent is downstream of cpq-agent-028, which feeds it renewal context including risk tier, contract value, and customer segment.

---

## Key Skills / Capabilities

- **Upgrade Path Generation**: Analyzes the customer's current product subscriptions via quote line items (`GET /api/v1/quotes/{quoteId}/line-items`) and maps them against higher-tier product options retrieved from `GET /api/v1/products/{id}` and `GET /api/v1/product-bundles/product/{productId}`. Identifies upgrade candidates based on usage growth signals from ERP purchase history (`GET /api/v1/erp/purchase-history/{customerExternalId}`), customer segment trajectory, and feature requests logged in the CRM. Produces ranked upgrade recommendations with per-tier pricing via `POST /api/v1/pricing/simulate` and quantified value-add for each upgrade step.

- **Downgrade Option Analysis**: Identifies cases where customers are overprovisioned relative to their actual usage or stated needs. Compares current subscription levels against ERP utilization data and recent purchase patterns. Recommends right-sized alternatives that reduce the customer's cost while preserving the core value proposition. Calculates the revenue impact of each downgrade option (ARR reduction) and assesses the churn prevention benefit -- a proactive downgrade recommendation that retains the customer is preferable to a lost renewal. Applies pricing via `POST /api/v1/pricing/simulate` for each downgrade scenario.

- **Migration Credit Calculation**: Implements the `migration-credit.drl` logic for perpetual-to-SaaS transitions. Retrieves the customer's perpetual license portfolio from `GET /api/v1/erp/contracts/{customerExternalId}` to determine the perpetual book value. Computes the migration credit as `perpetualBookValue * 0.30` and applies it to the first year of the proposed SaaS subscription. Validates credit amounts against pricing rules from `GET /api/v1/rules/by-rule-set/{ruleSetId}` to ensure compliance with maximum discount thresholds. Generates a detailed credit breakdown showing the calculation basis and application schedule.

- **Feature Comparison**: Builds a side-by-side feature comparison matrix for the customer's current subscription versus each proposed path (upgrade, downgrade, or migration target). Retrieves product feature sets and capability descriptions from `GET /api/v1/products/{id}` and enriches them with bundle composition data from `GET /api/v1/product-bundles/product/{productId}`. Highlights features gained, features lost, and features unchanged for each option. Flags any features that the customer is actively using (based on ERP/usage data) that would be removed in a downgrade scenario.

- **TCO Analysis**: Generates a multi-year Total Cost of Ownership comparison for perpetual-to-SaaS migrations and for subscription tier changes. For perpetual customers, the TCO model includes: current maintenance/support renewal costs, infrastructure hosting costs (on-premise), upgrade cycle costs, and operational overhead -- compared against the SaaS subscription fee (net of migration credit), included support, automatic upgrades, and eliminated infrastructure costs. Applies the migration credit in year 1 and models the 3-year and 5-year cost trajectories. Uses `POST /api/v1/pricing/simulate` to generate accurate SaaS pricing for each scenario.

- **Path Impact Forecasting**: For each recommended path, projects the financial impact on both the customer and the vendor. Computes the change in ARR, TCV, and ACV. For upgrades, calculates the expansion revenue and incremental margin. For downgrades, models the revenue reduction and compares it against the estimated churn probability if no action is taken. For migrations, projects the short-term revenue dip (year 1 with migration credit) against the long-term revenue gain (recurring SaaS ARR in years 2+). Presents the net present value (NPV) of each path over a configurable horizon.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Customer External ID     | String            | From cpq-agent-028 renewal context or direct request |
| Current Quote ID         | String (UUID)     | The active/accepted quote representing current subscription |
| Renewal Context          | JSON object       | From cpq-agent-028: riskTier, churnScore, daysToExpiration, currentARR |
| Customer Segment         | String (Enum)     | From `GET /api/v1/pricing-customers/{id}`        |
| ERP Contract Portfolio   | JSON array        | From `GET /api/v1/erp/contracts/{customerExternalId}` -- includes perpetual licenses |
| ERP Purchase History     | JSON array        | From `GET /api/v1/erp/purchase-history/{customerExternalId}` |
| Perpetual Book Value     | Decimal           | Computed from ERP contract data for migration credit calculation |
| Target Subscription Type | String (Enum)     | Optional -- if the rep wants to explore a specific migration target (e.g., SaaS) |
| TCO Horizon (years)      | Integer           | Configuration parameter, default 3 years         |
| Product Catalog Context  | JSON array        | From `GET /api/v1/products` -- available tiers and alternatives |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Upgrade Path Recommendations  | JSON array of objects (targetProductId, targetTier, additionalFeatures, arrIncrease, pricingDetails, rationale) | Advisory; sales rep or CSM must approve and initiate upgrade quote |
| Downgrade Options             | JSON array of objects (targetProductId, targetTier, removedFeatures, arrReduction, churnRiskMitigation, rationale) | Advisory; rep must confirm downgrade is appropriate and customer-approved |
| Migration Credit Calculation  | JSON object (perpetualBookValue, creditRate: 0.30, creditAmount, applicationSchedule, complianceCheck) | **Requires rep review**; migration credits above a configurable threshold require deal desk approval via cpq-agent-006 |
| Feature Comparison Matrix     | JSON object (currentFeatures, targetFeatures, gained, lost, unchanged, activeUsageFlags) | Informational; attached to recommendation for rep/customer review |
| TCO Analysis Report           | JSON object (currentTCO, proposedTCO, savings, migrationCredit, yearByYear, npv, breakEvenMonth) | Advisory; rep uses for customer presentation. No auto-sharing with customer |

---

## Typical Workflow

1. **Receive Path Analysis Request**: The agent is triggered either by cpq-agent-028 feeding a renewal context (customer approaching renewal with identified growth/contraction signals) or by a sales rep/CSM explicitly requesting a subscription change analysis for a specific customer. The agent receives the customer external ID and the current quote ID representing the active subscription.

2. **Load Current Subscription State**: Agent calls `GET /api/v1/quotes/{id}` and `GET /api/v1/quotes/{quoteId}/line-items` to retrieve the full detail of the customer's current subscription including product tiers, quantities, pricing, ARR, and subscription type (perpetual vs. SaaS). Calls `GET /api/v1/erp/contracts/{customerExternalId}` to obtain the complete contract portfolio, including any perpetual licenses.

3. **Assess Customer Trajectory**: Agent retrieves customer profile via `GET /api/v1/pricing-customers/{id}` and purchase history via `GET /api/v1/erp/purchase-history/{customerExternalId}`. Analyzes trends: increasing purchase volume and expanding product footprint indicate growth trajectory (upgrade candidate); declining engagement and reducing purchase frequency indicate contraction (downgrade candidate); perpetual license portfolio indicates migration candidate.

4. **Generate Path Options**: Based on trajectory assessment, the agent generates relevant path options. For growth trajectories: identifies the next tier up for each product, suggests add-on modules, and recommends capacity expansions. For contraction: identifies right-sized alternatives and calculates the retention value. For migration: computes the migration credit per migration-credit.drl (`perpetualBookValue * 0.30`) and identifies the target SaaS products that provide equivalent or superior functionality.

5. **Build Feature Comparisons**: For each recommended path, the agent retrieves product details via `GET /api/v1/products/{id}` and bundle information via `GET /api/v1/product-bundles/product/{productId}`. Constructs a side-by-side feature matrix highlighting what the customer gains, loses, and retains. Cross-references with ERP usage data to flag any actively-used features that would be removed.

6. **Simulate Pricing for Each Path**: Agent calls `POST /api/v1/pricing/simulate` for each path option with the appropriate parameters: target products, quantities, customer-specific pricing, and any applicable credits (migration credit for perpetual-to-SaaS). Computes ARR, TCV, and ACV for each option. For migration paths, applies the 30% migration credit to year 1 pricing.

7. **Generate TCO Analysis**: For migration paths and significant tier changes, the agent builds a multi-year TCO model comparing the current state against each proposed path. Includes all relevant cost components: subscription fees, maintenance costs, infrastructure savings, migration credits, and projected renewal pricing (with renewal-uplift.drl applied). Computes break-even month and NPV over the configured horizon.

8. **Present Recommendations and Await Decision**: Agent returns the prioritized path recommendations with feature comparisons, pricing details, TCO analysis, and migration credit calculations to the requesting rep or CSM. The rep reviews the analysis, discusses with the customer as appropriate, and initiates the selected path by creating a new quote through the standard quoting workflow.

---

## Edge Cases & Exceptions

- **No Viable Upgrade Path**: When the customer is already on the highest available tier for all products in their subscription, the agent reports that no standard upgrade path exists. Instead, it suggests capacity-based expansions (additional seats, storage, or API call limits), add-on modules not yet in the subscription, or custom enterprise arrangements. Flags the account for product management review if the customer's needs appear to exceed the current product portfolio.

- **Perpetual License with Zero Book Value**: If the ERP contract data shows a perpetual license that has been fully depreciated (book value approaches zero), the migration credit calculation yields a negligible amount. The agent flags this scenario and shifts the migration value proposition from credit-based incentive to feature-based and operational-savings arguments in the TCO analysis. Recommends alternative migration incentives such as waived onboarding fees or extended trial periods.

- **Mixed Perpetual and SaaS Portfolio**: When a customer holds both perpetual licenses and SaaS subscriptions for different products, the agent handles the migration analysis on a per-product basis. Computes migration credits only for the perpetual components and presents a phased migration roadmap that allows the customer to transition products incrementally rather than requiring a single wholesale migration.

- **Downgrade Below Minimum Commitment**: Some subscription tiers have minimum commitment thresholds (e.g., minimum 10 seats). If the recommended downgrade would bring the customer below the minimum, the agent identifies the closest viable tier and presents the gap. If no viable tier exists below the current one, the agent recommends alternative cost reduction strategies such as term extension (lower annual cost) or payment schedule restructuring.

- **Migration Credit Exceeds Discount Threshold**: When the calculated migration credit (perpetualBookValue * 0.30) would result in an effective first-year discount that exceeds the approval thresholds managed by cpq-agent-006, the agent flags the migration for deal desk review. Includes the credit calculation, the resulting effective discount percentage, and the applicable approval tier in its output so the rep can initiate the approval process proactively.

- **Customer Requesting Both Upgrade and Migration**: When a customer wants to simultaneously upgrade their product tier and migrate from perpetual to SaaS, the agent models this as a combined path. The migration credit is computed on the current perpetual book value, and the upgrade pricing is applied to the target SaaS tier. The combined scenario is simulated as a single pricing event via `POST /api/v1/pricing/simulate` to ensure accurate waterfall calculation and discount stacking compliance.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Upgrade recommendation conversion rate | >= 20%        |
| Proactive downgrade churn prevention rate | >= 70% (customers retained who would have churned) |
| Perpetual-to-SaaS migration conversion rate | >= 15% of eligible customers per year |
| TCO analysis accuracy (projected vs. actual year-1 costs) | >= 92% |
| Agent response latency (p95)         | < 4 seconds     |
