# Agent PRD: cpq-agent-006

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-006                                  |
| **Name**          | Markup & Cost Integration Agent                |
| **Group**         | Pricing - Base                                 |
| **Primary Service** | pricing-service (8082), erp-adapter (8085)    |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent), cpq-agent-005 (Pricing Method Agent) |

---

## Purpose

The Markup & Cost Integration Agent serves as the primary bridge between the CPQ pricing engine and the enterprise ERP system for all cost-related data. While cpq-agent-004 and cpq-agent-005 establish what the customer will pay (list prices and method-adjusted prices), this agent establishes what the product costs the organization to deliver. It pulls cost of goods sold (COGS), landed costs, component cost breakdowns, freight estimates, and vendor rebate schedules from the ERP system via the ERP adapter service. Using this cost data, the agent calculates markup amounts, gross margin at both the line item and aggregate quote level, and provides the foundational cost intelligence that downstream agents depend on -- particularly cpq-agent-011 (Margin Enforcement) for minimum margin guardrails and cpq-agent-012 (Profitability Alerts) for real-time deal profitability visibility.

The agent operates at a Semi-autonomous level. It automatically retrieves cost data, calculates markups and margins, and updates line item cost metadata without requiring rep approval. However, it surfaces margin analysis results as advisory information and requires human confirmation before applying cost-based price adjustments (e.g., when cost data reveals that the current price yields a margin below the product family's target). The agent handles the inherent complexity of ERP cost data: multi-level BOMs (bill of materials) with component costs that may be denominated in different currencies, freight costs that vary by shipping destination and carrier, vendor rebates that are conditional on volume commitments, and landed costs that include duties, tariffs, and import fees. It normalizes all cost data into the quote's target currency and provides drill-down decomposition so that deal desk analysts can understand exactly what drives the cost basis of each line item.

---

## Key Skills / Capabilities

- **Real-Time Cost Retrieval**: Retrieves current cost data from the ERP system for individual products via `GET /api/v1/erp/costs/{productExternalId}` and in bulk via `GET /api/v1/erp/costs/bulk`. Cost data includes COGS, landed cost, component cost breakdown (multi-level BOM), freight estimates, and duty/tariff amounts. Handles ERP timeouts with configurable retry logic (3 retries, exponential backoff) and caches cost data with a configurable TTL (default: 15 minutes) to minimize ERP load.

- **Markup Calculation**: Calculates the markup for each line item as the difference between the selling price (from cpq-agent-004/005) and the total cost. Expresses markup as both an absolute amount and a percentage. Supports multiple markup strategies: standard markup (fixed percentage over cost), target-margin markup (calculate selling price to achieve desired margin), and tiered markup (markup percentage varies by cost bracket). Uses `POST /api/v1/pricing/simulate` to model markup scenarios.

- **Margin Analysis**: Computes gross margin at three levels -- individual line item, product family aggregate, and total quote. Gross margin is calculated as (Selling Price - Total Cost) / Selling Price, expressed as a percentage. Compares actual margins against target margins defined per product family and customer segment. Surfaces margin variance (actual vs. target) and flags items below minimum acceptable margin thresholds. Retrieves customer pricing tier from `GET /api/v1/pricing-customers/{id}` to apply segment-specific margin targets.

- **Cost Breakdown Decomposition**: Parses the multi-level BOM cost structure from ERP and presents it as a hierarchical decomposition: raw materials, manufacturing/assembly, packaging, freight (inbound and outbound), insurance, duties/tariffs, warehousing, and vendor rebate credits. Each component is expressed in its source currency and converted to the quote currency for aggregation. This decomposition enables deal desk analysts to identify cost drivers and negotiate with suppliers.

- **Vendor Rebate Application**: Retrieves applicable vendor rebate schedules from the ERP cost data and applies them as cost reductions. Rebates may be conditional on volume commitments (quarterly or annual purchase thresholds), product mix requirements, or promotional windows. The agent evaluates rebate eligibility based on the customer's purchase history retrieved via `GET /api/v1/erp/purchase-history/{customerExternalId}` and current quote volume. Applied rebates reduce the effective cost basis and improve margin calculations.

---

## Inputs

| Input                          | Type              | Source                                          |
|--------------------------------|-------------------|-------------------------------------------------|
| Pricing Waterfall State        | JSON object       | Output from cpq-agent-005 (method-adjusted prices) |
| Quote Line Items with Prices   | JSON array        | From cpq-agent-004/005                           |
| Product External IDs           | Array of String   | Mapped from internal product IDs to ERP external IDs |
| Customer External ID           | String            | Mapped from internal customer ID to ERP external ID |
| Customer Pricing Profile       | JSON object       | Retrieved via `GET /api/v1/pricing-customers/{id}` |
| Quote Target Currency          | String (ISO 4217) | From quote header                                |
| Shipping Destination           | JSON object (country, region, postalCode) | From quote or customer profile |
| Markup Strategy Override       | String (Enum)     | Optional -- STANDARD, TARGET_MARGIN, TIERED      |
| Target Margin Override         | Decimal (0-1)     | Optional -- per-line-item or quote-level target   |
| Cost Data Cache TTL            | Integer (seconds) | Optional -- override default 900s cache duration  |

---

## Outputs

| Output                            | Format                  | Human-in-the-Loop Checkpoint                     |
|-----------------------------------|-------------------------|--------------------------------------------------|
| Line Item Cost Data               | JSON array of objects (lineItemId, productExternalId, cogs, landedCost, freightCost, totalCost, currency, retrievalTimestamp) | Informational; displayed in cost detail panel |
| Cost Breakdown Decomposition      | JSON array of objects (lineItemId, components[{name, category, amount, sourceCurrency, convertedAmount}]) | Informational; available on drill-down |
| Markup Calculations               | JSON array of objects (lineItemId, sellingPrice, totalCost, markupAmount, markupPercent) | Informational; no approval required for calculation |
| Margin Analysis Report            | JSON object (lineItems[{lineItemId, grossMargin, targetMargin, variance}], quoteLevelMargin, productFamilyMargins[]) | Advisory; margin warnings require acknowledgment |
| Vendor Rebate Applications        | JSON array of objects (lineItemId, rebateId, rebateType, rebateAmount, eligibilityCriteria, isApplied) | Conditional rebates require rep confirmation that volume commitment will be met |
| Cost-Based Price Adjustment Suggestions | JSON array of objects (lineItemId, currentPrice, suggestedPrice, rationale, marginImpact) | Requires explicit rep approval before price modification |

---

## Typical Workflow

1. **Receive Pricing Waterfall State**: After cpq-agent-005 applies pricing methods, this agent receives the waterfall state with all line items carrying their method-adjusted selling prices. Extracts the product external IDs needed for ERP cost retrieval.

2. **Retrieve Cost Data from ERP**: For quotes with fewer than 10 line items, the agent retrieves costs individually via `GET /api/v1/erp/costs/{productExternalId}`. For larger quotes, uses the bulk endpoint `GET /api/v1/erp/costs/bulk` with all product external IDs in a single call. Checks the local cache first and only calls ERP for uncached or expired entries.

3. **Normalize Cost Currency**: When ERP returns cost data in currencies different from the quote's target currency (common for globally sourced products), the agent retrieves exchange rates via `GET /api/v1/erp/fx-rates/{base}/{target}` and converts all cost components to the quote currency. Locks the exchange rate at retrieval time and records it per line item for audit.

4. **Decompose Cost Structure**: For each line item, the agent parses the ERP cost response into a structured breakdown: COGS (direct material + direct labor + manufacturing overhead), inbound freight, outbound freight (estimated based on shipping destination), duties/tariffs, insurance, warehousing allocation, and vendor rebate credits. Stores the decomposition for drill-down analysis.

5. **Evaluate Vendor Rebates**: The agent retrieves the customer's purchase history from `GET /api/v1/erp/purchase-history/{customerExternalId}` and evaluates each applicable vendor rebate's eligibility criteria. For volume-conditional rebates, checks whether the customer's year-to-date purchases plus the current quote volume meet the threshold. Applied rebates reduce the effective cost basis.

6. **Calculate Markup and Margin**: For each line item, the agent calculates: markup amount (selling price - total cost), markup percentage (markup / total cost), and gross margin ((selling price - total cost) / selling price). Aggregates these at the product family level and quote level. Compares actual margins against targets from the customer's pricing profile.

7. **Surface Margin Analysis**: The agent compiles the margin analysis report showing line-level, family-level, and quote-level margins with variance indicators. Items below target margin are highlighted with severity levels (BELOW_TARGET: yellow warning, BELOW_MINIMUM: red alert). The report is made available to the rep and passed to downstream agents (cpq-agent-011, cpq-agent-012).

8. **Propose Cost-Based Adjustments (if applicable)**: When margin analysis reveals line items significantly below target, the agent calculates a suggested price adjustment that would achieve the target margin. These suggestions are presented to the rep with full rationale (cost breakdown, current margin, target margin, suggested price, margin impact). Adjustments are only applied after explicit rep confirmation.

---

## Edge Cases & Exceptions

- **ERP Cost Data Unavailable (Timeout/Error)**: When the ERP adapter returns a timeout or error for cost retrieval, the agent retries with exponential backoff (3 attempts: 1s, 2s, 4s). If all retries fail, the agent applies a provisional cost estimate based on the product family's average cost multiplier (e.g., average COGS = 45% of list price for the product family). Flags the line item with `ESTIMATED_COST` and schedules a background retry. Notifies the rep that margin calculations are based on estimated costs.

- **Stale Cost Data (ERP Lag)**: When the ERP cost data's last-updated timestamp is older than the configurable staleness threshold (default: 30 days), the agent applies the cached data but flags the line item with `STALE_COST_DATA` and triggers an asynchronous cost refresh request to the ERP system. The margin analysis includes a confidence indicator (HIGH for fresh data, MEDIUM for data < 30 days old, LOW for data > 30 days old).

- **Multi-Currency BOM Components**: When a product's BOM includes components sourced in 3+ different currencies, the agent retrieves all necessary exchange rates in a single batch, converts each component to the quote currency, and calculates the aggregate cost. Currency conversion rounding errors are tracked and capped at 0.01% of total cost. The cost breakdown shows both source currency and converted amounts for each component.

- **Negative Margin After Rebate Application**: When vendor rebates reduce the cost basis enough that the margin becomes unusually high (>80%), or when rebate conditions are not fully met resulting in a lower-than-expected rebate that pushes margin negative, the agent recalculates with and without each rebate applied. Presents both scenarios to the rep with a clear indication of the rebate conditions that must be met to achieve each margin level.

- **Product Not Found in ERP**: When `GET /api/v1/erp/costs/{productExternalId}` returns 404, indicating the product has no cost record in ERP (common for new products or services-only offerings), the agent marks the line item with `NO_ERP_COST` and skips margin calculation. For service-type products, this is expected and logged at INFO level. For physical products, this is logged at WARN level and an alert is sent to the product data team.

- **Bulk Cost Retrieval Partial Failure**: When the bulk cost endpoint returns successful results for some products but errors for others, the agent processes all successful results normally and applies the individual-retrieval fallback for failed items. If individual retrieval also fails, follows the ERP timeout handling logic (provisional estimates). Provides a summary showing how many items have actual vs. estimated costs.

---

## Success Metrics

| Metric                                        | Target          |
|------------------------------------------------|-----------------|
| ERP cost retrieval success rate                | >= 97%          |
| Cost data freshness (% of line items with data < 7 days old) | >= 85% |
| Margin calculation accuracy (vs. post-deal actuals) | within 2 percentage points |
| Vendor rebate eligibility detection accuracy   | >= 95%          |
| Cost retrieval latency (p95, including bulk)   | < 3 seconds     |
