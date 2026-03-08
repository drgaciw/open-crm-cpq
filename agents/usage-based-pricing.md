# Agent PRD: cpq-agent-021

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-021                                  |
| **Name**          | Usage-Based Pricing Agent                      |
| **Group**         | Subscription & Licensing                       |
| **Primary Service** | pricing-service (8082)                        |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004                                  |

---

## Purpose

The Usage-Based Pricing Agent manages all aspects of metered and consumption-based pricing models for SAAS_USAGE_METERED products. Its central responsibility is implementing the overage calculation logic defined in `usage-overage-charge.drl` (salience 610): `overageCharge = overageRate * (actualUnits - includedUnits)`, where `includedUnits` represents the customer's prepaid or committed usage baseline and `actualUnits` reflects their real consumption. The agent handles the full spectrum of usage-based pricing structures including pay-as-you-go metering, prepaid credit drawdown, committed spend floors with overage tiers, and hybrid models that combine a flat platform fee with metered consumption on top. It retrieves historical usage data to inform baseline recommendations and generates spend projections that help sales reps right-size the customer's committed tier.

The agent operates at an Automated with constraints autonomy level. It automatically calculates overage charges, applies tiered usage rates, and updates spend tracking without human intervention as long as the computed charges fall within configured guardrails (maximum overage cap, spend alert thresholds). When usage-based charges exceed customer-specific spend alert thresholds -- or when a prepaid credit balance would be exhausted before the term end based on current consumption trajectory -- the agent escalates to the sales representative with a spend alert containing the projected exhaustion date and a recommendation to upsell to a higher committed tier. The agent never autonomously adjusts committed baselines or overage rates; any structural changes to the usage pricing model require explicit rep action.

---

## Key Skills / Capabilities

- **Overage Calculation**: Implements the core overage formula from `usage-overage-charge.drl` (salience 610): `overageCharge = overageRate * (actualUnits - includedUnits)`. Retrieves the customer's included unit baseline from the contract or quote line item, compares it against actual or projected usage, and computes the overage charge. Supports tiered overage rates (e.g., first 1,000 overage units at $0.10, next 5,000 at $0.08, beyond 6,000 at $0.05). Executes calculations via `POST /api/v1/pricing/execute` with usage context parameters.

- **Prepaid Credit Drawdown Tracking**: Manages prepaid usage credit pools where the customer purchases a committed spend amount upfront and draws down against it as usage accrues. Tracks remaining credit balance, burn rate, and projected exhaustion date. Retrieves purchase history from `GET /api/v1/erp/purchase-history/{customerExternalId}` to establish the original credit pool size and prior drawdown events. Generates alerts when the remaining balance falls below configurable thresholds (25%, 10%, exhausted).

- **Committed Spend Floor Enforcement**: For committed spend models where the customer guarantees a minimum monthly or annual consumption amount, the agent enforces the floor by calculating the shortfall when actual usage falls below the committed level. Retrieves the committed spend terms from `GET /api/v1/erp/contracts/{customerExternalId}` and applies the minimum charge regardless of actual consumption.

- **Usage Baseline Recommendation**: Analyzes historical usage data retrieved from `GET /api/v1/erp/purchase-history/{customerExternalId}` to recommend an appropriate included unit baseline for new quotes or renewals. Calculates the customer's average, median, P75, and P95 usage over the trailing 12 months and recommends a committed tier that covers the P75 level to balance cost predictability against overage risk. Presents the analysis via `POST /api/v1/pricing/simulate`.

- **Spend Projection and Alerting**: Projects forward usage-based spend using trailing consumption trends. Calculates projected monthly and annual totals, identifies when projected spend would exceed budget thresholds or exhaust prepaid credits, and generates proactive spend alerts. Retrieves current usage metrics and applies linear or weighted moving average projections.

- **Multi-Meter Aggregation**: Handles products with multiple usage meters (e.g., API calls + storage + bandwidth), each with independent included baselines, overage rates, and tiered pricing. Retrieves meter definitions from `GET /api/v1/product-options/groups/product/{productId}` and calculates charges per meter independently before aggregating into a single line item charge. Supports meters with different billing granularities (per-second, per-hour, per-day, per-month).

---

## Inputs

| Input                    | Type              | Source                                          |
|--------------------------|-------------------|-------------------------------------------------|
| Product ID               | String (UUID)     | Quote line item / cpq-agent-004 output          |
| Customer ID              | String (UUID)     | Quote context / CRM record                      |
| Included Units Baseline  | Integer           | Contract terms or quote line item configuration  |
| Actual Units Consumed    | Integer           | Usage metering system / ERP purchase history     |
| Overage Rate             | Decimal           | Pricing rules (`GET /api/v1/rules/by-rule-set/{ruleSetId}`) |
| Prepaid Credit Amount    | Decimal           | Contract terms (`GET /api/v1/erp/contracts/{customerExternalId}`) |
| Committed Spend Floor    | Decimal           | Contract terms / quote configuration             |
| Billing Period           | Enum (BillingFrequency) | Quote line item (MONTHLY, QUARTERLY, ANNUAL) |
| Spend Alert Threshold    | Decimal (percentage) | Customer or account-level configuration       |
| Usage Meter Definitions  | JSON array        | Product options (`GET /api/v1/product-options/groups/product/{productId}`) |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Overage Charge Calculation    | JSON object (includedUnits, actualUnits, overageUnits, overageRate, tieredBreakdown, totalOverageCharge) | Automatic within guardrails; escalated if exceeds overage cap |
| Prepaid Credit Balance Report | JSON object (originalCredits, usedCredits, remainingCredits, burnRate, projectedExhaustionDate) | Spend alert generated when balance < 25%; mandatory rep notification at < 10% |
| Committed Spend Shortfall     | JSON object (committedFloor, actualSpend, shortfallAmount, billingPeriod) | Informational; shortfall charges are applied automatically per contract terms |
| Usage Baseline Recommendation | JSON object (avgUsage, medianUsage, p75Usage, p95Usage, recommendedBaseline, projectedSavings) | Advisory; rep must accept recommendation before applying to quote |
| Spend Projection Forecast     | JSON array of objects (period, projectedUnits, projectedCost, cumulativeSpend, budgetRemaining) | Advisory; rep reviews projection for quote sizing |

---

## Typical Workflow

1. **Receive Usage Pricing Request**: The agent is triggered when a SAAS_USAGE_METERED product is added to a quote or when a periodic usage reconciliation event occurs. Receives the product ID, customer ID, and usage context (current billing period, actual units consumed) from the invoking system or cpq-agent-004.

2. **Load Usage Contract Terms**: Agent calls `GET /api/v1/erp/contracts/{customerExternalId}` to retrieve the customer's active usage-based contract terms including included unit baselines, overage rates, tiered pricing schedules, committed spend floors, and prepaid credit balances.

3. **Retrieve Historical Usage Data**: Agent calls `GET /api/v1/erp/purchase-history/{customerExternalId}` to fetch trailing usage data for the product. Analyzes consumption patterns over the past 12 months to establish trend lines, seasonal variations, and growth rates for projection calculations.

4. **Calculate Overage Charges**: If `actualUnits > includedUnits`, the agent applies the `usage-overage-charge.drl` formula: `overageCharge = overageRate * (actualUnits - includedUnits)`. For tiered overage pricing, the agent segments the overage units across tiers and sums the per-tier charges. Executes the calculation via `POST /api/v1/pricing/execute` with the full usage context.

5. **Evaluate Prepaid Credit Impact**: If the customer has a prepaid credit pool, the agent deducts the current period's charges (base + overage) from the remaining credit balance. Calculates the updated burn rate and projects the credit exhaustion date. Generates a spend alert if the remaining balance falls below the configured threshold.

6. **Enforce Committed Spend Floor**: If the customer's actual consumption value falls below the committed minimum for the billing period, the agent calculates the shortfall and applies the committed floor as the billable amount. Logs the shortfall event for contract compliance tracking.

7. **Generate Baseline Recommendation**: For new quotes or renewals, the agent analyzes the historical usage data to recommend an optimal included unit baseline. Presents the recommendation with supporting data (average, P75, P95 usage) and projects the cost difference between the current and recommended baselines using `POST /api/v1/pricing/simulate`.

8. **Return Usage Pricing Result**: Agent returns the complete usage pricing breakdown including base charges, overage calculations, prepaid credit updates, committed floor adjustments, and spend projections. Passes the result to downstream agents for quote assembly or invoice generation.

---

## Edge Cases & Exceptions

- **Zero Usage in Billing Period**: When a customer records zero actual units in a billing period, the agent still enforces the committed spend floor if one exists. For prepaid credit models, no drawdown occurs but the agent notes the anomaly and flags it for the rep in case it indicates an integration or metering issue rather than genuine zero usage.

- **Usage Data Lag or Missing Data**: When real-time usage data is unavailable or delayed beyond the billing cutoff, the agent uses the most recent available data point and flags the charge as provisional. Generates a reconciliation event to true-up the charge when actual data becomes available. Does not block quote generation for missing usage data.

- **Overage Cap Exceeded**: When computed overage charges exceed the customer's contractual overage cap (maximum monthly overage), the agent caps the charge at the contractual maximum and generates an alert to the rep recommending an immediate upgrade to a higher committed tier. The capped amount is applied automatically; the upsell recommendation is advisory.

- **Negative Overage (Unused Included Units)**: When `actualUnits < includedUnits`, the agent reports zero overage charge and calculates the utilization percentage. If utilization is consistently below 50% over three consecutive periods, the agent generates a right-sizing recommendation suggesting the customer downgrade to a lower included-units tier to reduce waste.

- **Multi-Meter with Independent Ceilings**: When a product has multiple usage meters with independent overage caps, the agent evaluates each meter's cap independently. A cap breach on one meter does not affect the calculation for other meters. The agent aggregates all meter charges into a single line item but preserves per-meter detail in the breakdown.

- **Mid-Period Baseline Change**: When a customer's included unit baseline changes mid-billing-period (e.g., mid-month upgrade from 10,000 to 50,000 included API calls), the agent prorates both the old and new baselines proportionally to the days at each level and recalculates overage against the blended baseline for that period.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Overage calculation accuracy         | >= 99.9%       |
| Spend alert delivery timeliness      | <= 1 hour from threshold breach |
| Prepaid credit balance accuracy      | >= 99.95%      |
| Usage baseline recommendation adoption rate | >= 30%  |
| Agent response latency (p95)         | < 2 seconds    |
