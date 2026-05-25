# Agent PRD: cpq-agent-027

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-027                                  |
| **Name**          | Margin Optimization Agent                      |
| **Group**         | Analytics & Intelligence                       |
| **Primary Service** | pricing-service (8082), quote-service (8080)  |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-006 (Approval Workflow Agent), cpq-agent-011 (Deal Desk Analyst Agent) |

---

## Purpose

The Margin Optimization Agent analyzes existing quotes at the line-item level to identify opportunities for improving gross margin without jeopardizing competitive positioning or deal closure probability. It dissects the pricing waterfall -- from list price through each discount tier, volume adjustment, customer-specific override, and promotional concession -- to pinpoint where margin erosion is occurring and which adjustments would yield the greatest margin recovery. The agent retrieves real-time cost data from the ERP integration via `GET /api/v1/erp/costs/{productExternalId}` to compute true margin profiles for each line item, ensuring that its recommendations are grounded in actual cost-of-goods rather than list-price-based approximations.

Every suggestion produced by this agent requires explicit sales representative approval before any quote modification occurs. The agent never autonomously changes pricing, discounts, or product selections. Instead, it presents a prioritized set of margin improvement levers -- such as reducing a discretionary discount from 22% to 18% (recovering $4,200 in margin), substituting a lower-cost product variant that meets the same requirements, adjusting quantity breakpoints to align with more favorable pricing tiers, or restructuring a multi-year commitment to capture upfront payment benefits. Each suggestion includes the projected margin impact, the estimated effect on win probability (leveraging data from cpq-agent-026 if available), and a risk assessment indicating whether the change might trigger re-approval via the cpq-agent-006 approval workflow. The agent's dependency on cpq-agent-011 (Deal Desk Analyst) ensures its recommendations align with deal desk policies and negotiation guardrails.

---

## Key Skills / Capabilities

- **Waterfall Analysis**: Parses the `pricingWaterfallJson` field from the quote entity (`GET /api/v1/quotes/{id}`) to decompose the full pricing cascade for each line item. Identifies each discount layer -- volume, promotional, customer-specific, discretionary rep discount, and approval-based exception -- and computes the cumulative margin impact at every step. Highlights the layers with the highest margin erosion and flags discount stacking that may violate pricing policy rules retrieved from `GET /api/v1/rules/by-rule-set/{ruleSetId}`.

- **Line-Level Optimization**: Evaluates each line item individually by cross-referencing the quoted price against ERP cost data (`GET /api/v1/erp/costs/{productExternalId}`) and historical pricing for comparable deals. Produces per-line-item optimization suggestions ranked by margin recovery potential. Considers interdependencies between line items (e.g., a discount on a platform product that drives adoption of higher-margin add-on services) to avoid sub-optimizations that hurt overall deal economics.

- **Product Substitution Suggestion**: Identifies alternative products that fulfill the same functional requirements at a lower cost or higher margin. Queries `GET /api/v1/products/{id}` for product attributes and `GET /api/v1/product-bundles/product/{productId}` for bundle alternatives. Compares feature parity, licensing model (perpetual vs. subscription), and support tier to ensure substitutions do not degrade the customer's solution. Calculates the margin delta for each substitution option.

- **Discount Reduction Recommendation**: Analyzes discretionary discount levels applied by the sales rep and benchmarks them against the historical discount distribution for the customer's segment and deal type. Recommends specific discount reductions with quantified margin recovery, calibrated to stay within the range that historically maintains comparable win rates. References pricing rules from `GET /api/v1/rules/by-rule-set/{ruleSetId}` to identify discounts that approach or breach approval thresholds managed by cpq-agent-006.

- **Scenario Comparison**: Generates multiple "what-if" pricing scenarios using `POST /api/v1/pricing/simulate` with adjusted parameters (discount levels, product substitutions, term changes, quantity modifications). Presents a side-by-side comparison table showing the ARR, TCV, ACV, gross margin percentage, and gross margin dollars for each scenario. Highlights the Pareto-optimal scenarios that maximize margin while minimizing win-probability risk.

- **Approval Impact Assessment**: For each recommended change, evaluates whether implementing it would alter the quote's position relative to approval thresholds managed by cpq-agent-006. Flags recommendations that would move the quote from "auto-approved" to "requires approval" (a potential deal velocity risk) or, conversely, from "requires approval" to "auto-approved" (a positive side effect). This ensures the sales rep understands the full workflow implications of each margin optimization.

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Quote ID                 | String (UUID)     | Quote detail view / optimization request trigger |
| Customer External ID     | String            | From quote record (`GET /api/v1/quotes/{id}`)   |
| Deal External ID         | String            | From quote record, linked to CRM deal            |
| Line Items               | JSON array        | From `GET /api/v1/quotes/{quoteId}/line-items`   |
| Pricing Waterfall        | JSON object       | From `pricingWaterfallJson` field on quote entity |
| ERP Cost Data            | JSON object       | From `GET /api/v1/erp/costs/{productExternalId}` per line item |
| Customer Segment         | String (Enum)     | From `GET /api/v1/pricing-customers/{id}`        |
| Target Margin Threshold  | Decimal (%)       | Configuration parameter, varies by segment (e.g., 65% for ENTERPRISE) |
| Pricing Rule Set ID      | String (UUID)     | From pricing service configuration               |
| Win Probability (optional) | Integer (0-100) | From cpq-agent-026 if available                  |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Line-Item Margin Analysis     | JSON array of objects (lineItemId, product, listPrice, quotedPrice, costBasis, marginPct, marginDollars, waterfallBreakdown) | Informational; displayed as margin heatmap on quote view |
| Optimization Suggestions      | JSON array of objects (suggestionId, type, lineItemId, currentValue, suggestedValue, marginImpact, winProbImpact, approvalImpact, risk, priority) | **All suggestions require explicit sales rep approval before implementation**. Rep must confirm each suggestion individually |
| Scenario Comparison Table     | JSON array of objects (scenarioId, description, arr, tcv, acv, marginPct, marginDollars, winProbEstimate) | Advisory; rep selects preferred scenario or requests additional simulations |
| Substitution Options          | JSON array of objects (originalProductId, alternativeProductId, featureComparison, marginDelta, riskAssessment) | Rep must approve any product substitution; no auto-replacement |
| Aggregate Margin Summary      | JSON object (quoteMarginPct, quoteMarginDollars, marginVsTarget, topErosionFactors) | Dashboard metric; no action required |

---

## Typical Workflow

1. **Receive Optimization Request**: Sales rep or deal desk analyst triggers a margin analysis from the quote detail view. The agent receives the quote ID and retrieves the full quote record via `GET /api/v1/quotes/{id}`, including customer external ID, deal external ID, deal type, subscription type, and pricing waterfall.

2. **Load Line Items and Cost Data**: Agent calls `GET /api/v1/quotes/{quoteId}/line-items` to retrieve all line items with their pricing details. For each line item, calls `GET /api/v1/erp/costs/{productExternalId}` to obtain current cost-of-goods data. Computes true gross margin for each line item and for the quote in aggregate.

3. **Analyze Pricing Waterfall**: Agent parses the `pricingWaterfallJson` to decompose every discount layer applied to each line item. Identifies the highest-impact discount layers, flags discount stacking violations, and benchmarks each discount against the historical distribution for the customer segment retrieved via `GET /api/v1/pricing-customers/{id}`.

4. **Identify Optimization Opportunities**: Agent evaluates five optimization lever categories: (a) discretionary discount reduction, (b) product substitution, (c) quantity/tier adjustment, (d) term restructuring, and (e) bundle repackaging. For each applicable lever on each line item, computes the projected margin improvement and assesses the risk to deal closure.

5. **Simulate Pricing Scenarios**: For the top optimization opportunities, the agent calls `POST /api/v1/pricing/simulate` with adjusted parameters to produce fully calculated alternative pricing. Generates 2--4 scenarios ranging from conservative (small margin improvement, minimal win-probability risk) to aggressive (maximum margin recovery, higher win-probability risk).

6. **Assess Approval Workflow Impact**: For each scenario, the agent evaluates whether the pricing changes would cross approval thresholds defined in the rule sets (`GET /api/v1/rules/by-rule-set/{ruleSetId}`) managed by cpq-agent-006. Annotates each suggestion with its approval impact: "remains auto-approved," "triggers manager approval," or "requires deal desk review."

7. **Present Prioritized Recommendations**: Agent returns the complete margin analysis, prioritized suggestions (ranked by margin impact adjusted for win-probability risk), scenario comparison table, and substitution options to the sales rep. Each suggestion is clearly labeled with its margin recovery, risk level, and approval implications.

8. **Await Rep Decision and Log**: Sales rep reviews suggestions, approves selected optimizations, and rejects or defers others. Approved changes are routed to the appropriate pricing update workflow. The agent logs all suggestions, decisions, and outcomes for model refinement and reporting on optimization effectiveness.

---

## Edge Cases & Exceptions

- **Quote Below Minimum Margin Floor**: When the overall quote margin is below the organization's minimum margin floor (e.g., < 40% gross margin for ENTERPRISE segment), the agent elevates the severity of its recommendations and flags the quote for mandatory deal desk review via cpq-agent-011. Includes a specific "path to minimum margin" analysis showing the minimum set of changes required to reach the floor.

- **ERP Cost Data Unavailable**: If `GET /api/v1/erp/costs/{productExternalId}` returns 404 or stale data for one or more line items, the agent falls back to catalog-based estimated margins using list price minus standard cost from the product record. Flags affected line items with a data quality warning and notes that true margin may differ. Recommends the rep verify cost data with finance before implementing aggressive margin optimization.

- **Interdependent Line Items**: When line items have pricing dependencies (e.g., platform license required for add-on modules, or volume-based pricing across a product family), the agent models these relationships before suggesting changes. Removing or substituting one product may invalidate the pricing basis for dependent items. The agent groups interdependent items and presents optimization suggestions at the group level rather than individually.

- **Competitive Bid Situations**: When the deal type is NEW_BUSINESS and the deal record indicates a competitive situation (multiple vendors in evaluation), the agent adjusts its risk calibration to be more conservative. Discount reduction recommendations are tempered, and the agent prioritizes value-additive strategies (bundling, term incentives) over price increases. Flags this context explicitly in the output.

- **Recently Approved Exceptions**: If the quote has already received approval exceptions from cpq-agent-006 (e.g., VP-approved deep discount for strategic account), the agent respects these exceptions and does not suggest reversing them. It focuses optimization efforts on non-exception line items and notes the locked-in exceptions in its analysis.

- **Multi-Currency Quotes**: When the quote includes line items priced in different currencies, the agent normalizes all margin calculations to the quote's base currency using current exchange rates. Flags any line items where currency conversion introduces margin uncertainty exceeding 2% and recommends currency-specific pricing review.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Average margin improvement per optimized quote | >= 3 percentage points |
| Suggestion acceptance rate (rep approves suggestion) | >= 30% |
| Quotes analyzed per month            | >= 500          |
| Win-rate maintenance (optimized quotes vs. baseline) | No degradation (>= -1 pp) |
| Agent response latency (p95)         | < 5 seconds     |
