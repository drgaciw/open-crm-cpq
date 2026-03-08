# Agent PRD: cpq-agent-012

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-012                                  |
| **Name**          | Profitability Alert Agent                      |
| **Group**         | Pricing - Guardrails                           |
| **Primary Service** | pricing-service (8082), quote-service (8084)  |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-006 (Markup & Cost Integration Agent) |

---

## Purpose

The Profitability Alert Agent provides proactive, real-time warnings to sales representatives when quote configurations are trending toward low-margin territory, enabling corrective action before hard margin thresholds are actually breached. It works alongside cpq-agent-011 (Minimum Margin Enforcement Agent) but serves a fundamentally different role: where cpq-agent-011 is a blocking guardrail that enforces margin floors after thresholds are crossed, cpq-agent-012 is an early-warning advisory system that monitors the pricing waterfall in real-time and surfaces margin erosion trends as they develop. The agent continuously tracks the cumulative effect of each discount layer -- customer segment discounts, volume discounts, deal-specific discounts, and promotional discounts -- on the overall margin, and generates graduated alerts when margin approaches configurable warning thresholds (e.g., margin within 5 points of the 35% floor, or margin declining by more than 10 points from its initial level).

The agent operates at a purely Advisory autonomy level. It never blocks quote progression, rejects discount applications, or modifies pricing. All alerts are informational and presented exclusively to the sales representative, who retains complete discretion over whether to adjust the configuration in response. This design ensures the agent adds value without creating friction in the quoting workflow -- representatives receive actionable margin intelligence without being interrupted by hard stops during the creative pricing process. The agent retrieves real-time cost data via the ERP adapter to ensure margin calculations reflect current COGS rather than potentially stale catalog costs, and it maintains a per-session margin trajectory that shows the representative how each pricing action has affected the margin over the course of the quote building process. Every alert includes specific, actionable guidance on which discount layers are contributing most to margin erosion and what adjustments would restore the margin to a comfortable level.

---

## Key Skills / Capabilities

- **Real-Time Margin Monitoring**: Continuously tracks gross margin across all line items on the active quote as the pricing waterfall progresses. After each discount layer is applied by upstream agents (cpq-agent-007 through cpq-agent-010), recalculates the current margin by retrieving effective prices from the quote context and current costs via `GET /api/v1/erp/costs/bulk`. Computes both line-item-level and quote-level weighted-average margins. Maintains a rolling margin snapshot after each pricing event, building a per-session margin trajectory that visualizes margin erosion over the course of quote construction.

- **Graduated Alert Generation**: Implements a multi-tier alert model that surfaces warnings before hard thresholds are breached. Generates a CAUTION alert when margin falls within a configurable buffer zone of the 35% floor (default: margin between 35% and 40%), a WARNING alert when margin drops below 40% but specific discount combinations suggest it will breach 35% if additional discounts are applied, and a CRITICAL alert when margin is within 2 points of any enforcement threshold. Each alert level carries different visual prominence in the sales representative's UI and different levels of detail in the accompanying guidance.

- **Margin Erosion Attribution**: When an alert is triggered, the agent attributes margin erosion to specific discount layers and pricing actions. Analyzes the discount waterfall retrieved from the pricing context to identify which discount types (segment, volume, deal-specific, promotional) contributed the most to margin reduction. Presents a ranked breakdown showing each discount layer's contribution to the margin decline, enabling the representative to make targeted adjustments rather than guessing which discount to modify.

- **Predictive Margin Forecasting**: Projects the likely final margin based on the current discount trajectory and historical patterns for similar deal configurations. Analyzes past quotes for the same customer segment and product mix via `GET /api/v1/quotes` filtered by segment and product category to determine the typical discount escalation pattern during negotiation. Alerts the representative if the current margin level, combined with historical escalation patterns, suggests the final margin will breach enforcement thresholds.

- **Actionable Restoration Suggestions**: Accompanies every alert with specific, actionable suggestions for restoring margin to a comfortable level. Calculates which discount layers could be reduced or removed to restore margin, identifies alternative product configurations (e.g., substituting a higher-margin variant) via `GET /api/v1/products/search`, and suggests bundle alternatives via `GET /api/v1/product-bundles` that may offer better margin characteristics. Prioritizes suggestions by ease of implementation and minimal impact on customer-facing pricing.

- **Threshold Configuration Management**: Supports configurable alert thresholds that can vary by customer segment, product category, and deal size. Retrieves threshold configurations via `GET /api/v1/pricing/rules` and applies segment-specific overrides. For example, STRATEGIC segment customers may have a lower warning threshold (30%) than STARTUP customers (40%), reflecting different margin expectations across customer tiers.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Active quote session                             |
| Line Item IDs                | List of String (UUID) | All line items on the current quote           |
| Effective Prices             | Map (lineItemId -> BigDecimal) | Post-discount pricing from upstream agents |
| Product External IDs         | List of String    | Products for ERP cost lookup                     |
| Real-Time Product Costs      | Map (productId -> CostBreakdown) | From `GET /api/v1/erp/costs/bulk` (COGS, landed cost, overhead) |
| Applied Discount Waterfall   | List of Discount  | Accumulated from cpq-agent-007 through cpq-agent-010 |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |
| Alert Threshold Configuration| JSON object       | Configurable warning thresholds per segment      |
| Session Margin History       | List of MarginSnapshot | Running margin trajectory from current session |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Margin Alert (CAUTION)             | JSON object (level: CAUTION, currentMargin, threshold, buffer, topErosionFactors) | Advisory; displayed as informational banner to sales rep |
| Margin Alert (WARNING)             | JSON object (level: WARNING, currentMargin, projectedFinalMargin, erosionAttribution, restorationSuggestions) | Advisory; displayed as prominent warning with actionable suggestions |
| Margin Alert (CRITICAL)            | JSON object (level: CRITICAL, currentMargin, distanceToFloor, immediateActions, impactIfBreached) | Advisory; displayed as urgent alert with specific corrective actions |
| Margin Trajectory Report           | JSON object (snapshots: array of {timestamp, margin, event, delta}, trend: STABLE/DECLINING/IMPROVING) | Informational; visual margin trend displayed in quote builder |
| Erosion Attribution Breakdown      | JSON array of objects (discountType, discountAmount, marginImpactPoints, percentOfTotalErosion) | Informational; helps rep identify which discounts to adjust |
| Restoration Suggestions            | JSON array of objects (action, target, currentValue, suggestedValue, marginImpact, feasibility) | Advisory; rep decides which adjustments to make |

---

## Typical Workflow

1. **Initialize Margin Baseline**: When a new quote session begins or the agent is activated on an existing quote, it establishes the margin baseline. Retrieves all line items via `GET /api/v1/quotes/{quoteId}/line-items`, fetches current costs via `GET /api/v1/erp/costs/bulk`, and calculates the pre-discount margin for each line item and the quote overall. This baseline becomes the starting point for the margin trajectory.

2. **Monitor Discount Layer Application**: As each discount agent (cpq-agent-007 through cpq-agent-010) applies its pricing adjustments, the Profitability Alert Agent recalculates the updated margin. Records a margin snapshot after each discount layer, capturing the margin value, the discount event that caused the change, and the delta from the previous snapshot. Updates the margin trajectory visualization.

3. **Evaluate Alert Thresholds**: After each margin recalculation, the agent evaluates the current margin against the configured alert thresholds for the customer's segment. Checks whether the margin has entered any alert zone (CAUTION, WARNING, or CRITICAL) and whether the margin trend is stable, declining, or improving. Compares the current trajectory against historical patterns for similar deals.

4. **Generate Graduated Alert**: If an alert threshold is crossed, the agent constructs the appropriate alert with full context. For CAUTION alerts, includes the current margin, distance to the floor, and the top contributing discount layers. For WARNING alerts, adds projected final margin based on historical patterns and specific restoration suggestions. For CRITICAL alerts, includes immediate corrective actions and a clear statement of what will happen if the margin breaches the enforcement threshold (cpq-agent-011 will block the quote).

5. **Attribute Margin Erosion**: The agent analyzes the complete discount waterfall to attribute margin erosion to specific discount types and amounts. Calculates each discount layer's contribution to the total margin decline in both absolute points and percentage of total erosion. Ranks discount layers by impact magnitude to help the representative identify the most effective levers for margin restoration.

6. **Generate Restoration Suggestions**: Based on the erosion attribution, the agent generates prioritized suggestions for restoring margin. Identifies discount layers that could be reduced with minimal customer impact, alternative product configurations with better margin profiles retrieved via `GET /api/v1/products/search`, and bundle alternatives via `GET /api/v1/product-bundles` that may provide better economics. Each suggestion includes the estimated margin impact if implemented.

7. **Update Session Trajectory**: Regardless of whether an alert is triggered, the agent updates the session margin trajectory with the latest snapshot. This running history provides the representative with a visual timeline showing how each pricing decision has affected the margin, supporting more informed decision-making as the quote evolves.

8. **Log Alert Events**: All alert events are logged to `ai.agent_audit_log` with the alert level, current margin, threshold details, and suggestions provided. This creates an audit trail showing that the representative was warned about margin risks, which is valuable for post-deal analysis and for cpq-agent-036 (Audit & Compliance Reporting) compliance reporting.

---

## Edge Cases & Exceptions

- **Rapid Sequential Discount Application**: When multiple discount layers are applied in quick succession (e.g., segment discount, volume discount, and deal-specific discount applied within seconds during automated pricing), the agent batches margin recalculations to avoid alert fatigue. Evaluates the cumulative margin impact after all pending discount layers have been applied rather than generating separate alerts for each layer. However, if any intermediate state would trigger a CRITICAL alert, that alert is generated immediately without waiting for batch completion.

- **Cost Data Refresh During Session**: When ERP cost data is refreshed mid-session (cache TTL expiration triggers a fresh cost fetch), the agent recalculates all margin snapshots in the trajectory using the updated costs. If the cost increase causes a previously safe margin to enter an alert zone, generates an alert clearly attributed to cost change rather than pricing action, so the representative understands the alert is not caused by their discount decisions.

- **Zero-Cost Products in Configuration**: When the quote includes products with zero or null cost data from the ERP (e.g., pure services, digital deliverables with no COGS), the agent excludes these from percentage-margin calculations to avoid artificially inflating the apparent margin. Calculates and reports two margin figures: the blended margin including zero-cost items and the adjusted margin excluding them, with a note explaining the difference.

- **Multi-Currency Margin Calculation**: When the quote currency differs from the cost currency, FX conversion introduces margin volatility. The agent calculates margin in both the quote currency and the base cost currency, and generates a volatility advisory if the current FX rate is more than 2% different from the 30-day average, indicating that margin could shift materially due to FX movements alone.

- **Alert Fatigue from Iterative Quoting**: When a representative is iteratively adjusting discounts and the margin oscillates around an alert threshold (repeatedly entering and leaving the CAUTION zone), the agent implements a cooldown period. After an alert is acknowledged, the same alert level is not re-triggered for the same threshold within a 60-second window unless the margin deteriorates to a more severe alert level.

- **Margin Improving After Alert**: When the representative takes corrective action and the margin improves above the alert threshold, the agent generates a positive confirmation message showing the restored margin level and clearing the alert. Maintains the alert history in the trajectory for audit purposes but visually indicates the alert has been resolved.

---

## Success Metrics

| Metric                                              | Target          |
|-----------------------------------------------------|-----------------|
| Alert lead time (minutes before cpq-agent-011 enforcement) | >= 5 minutes avg |
| Representative corrective action rate after WARNING alert | >= 40%        |
| False positive alert rate (alerts where margin never breached floor) | <= 25% |
| Margin trajectory calculation accuracy vs. actuals   | >= 99%          |
| Alert generation latency (p95)                       | < 200ms         |
