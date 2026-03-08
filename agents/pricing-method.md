# Agent PRD: cpq-agent-005

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-005                                  |
| **Name**          | Pricing Method Agent                           |
| **Group**         | Pricing - Base                                 |
| **Primary Service** | pricing-service (8082), product-service (8083) |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Pricing Method Agent determines and applies the correct pricing methodology for each line item on a quote after cpq-agent-004 has established the base list prices. Enterprise product catalogs contain diverse product types -- perpetual software licenses, SaaS subscriptions, professional services, hardware, consumables, and hybrid offerings -- each of which may require a fundamentally different pricing calculation method. This agent maps each product's ProductType enum value to its corresponding pricing methodology and executes the appropriate calculation. Supported methods include block/tiered pricing (price varies by quantity brackets), percent-of-total pricing (line item price calculated as a percentage of other line items), cost-plus pricing (base cost plus a defined markup percentage or amount), and custom formula pricing driven by DRL (Drools Rule Language) rule sets. The agent ensures that the correct method is applied consistently and that method-specific parameters are validated before execution.

The agent operates at an "Automated with constraints" level. Standard pricing method application -- where the product type maps cleanly to a single method and all parameters are within defined bounds -- executes without human intervention. However, the agent enforces constraints that trigger escalation: pricing method conflicts (product eligible for multiple methods), missing method parameters (required cost data unavailable for cost-plus), custom rule validation failures, and calculated prices that deviate more than 20% from the list price established by cpq-agent-004. The agent uses `POST /api/v1/pricing/simulate` extensively for pre-calculation validation before committing prices, and coordinates with DRL rule sets via `GET /api/v1/rules/by-rule-set/{ruleSetId}` and `POST /api/v1/rules/{id}/validate` for custom pricing logic.

---

## Key Skills / Capabilities

- **Pricing Method Detection**: Retrieves the product definition via `GET /api/v1/products/{id}` and maps the ProductType enum (SOFTWARE, HARDWARE, SERVICE, SUBSCRIPTION, CONSUMABLE, HYBRID) to the appropriate pricing method. Maintains a configurable method mapping table that can be overridden per product or product family. Detects when a product has an explicit pricing method override defined at the product level.

- **Block/Tiered Pricing Calculation**: Implements tiered pricing where the unit price varies based on quantity brackets (e.g., 1-10 units at $100/unit, 11-50 at $85/unit, 51+ at $70/unit). Supports two tiered models: block pricing (all units priced at the tier rate for the total quantity) and graduated pricing (each quantity bracket priced at its own rate). Retrieves tier definitions from pricing rules via `GET /api/v1/rules/by-rule-set/{ruleSetId}` and calculates the effective unit price and extended price.

- **Percent-of-Total Pricing**: Calculates line item prices as a percentage of other line items on the quote. Used for services priced as a percentage of software/hardware (e.g., 18% annual maintenance, 15% implementation services). Resolves the reference line items, calculates the base total, applies the percentage, and handles circular reference detection when multiple percent-of-total items reference each other.

- **Cost-Plus Calculation**: For products priced on a cost-plus basis, retrieves the base cost (typically from ERP via cpq-agent-006) and applies the defined markup. Markup can be a fixed amount, a percentage, or a formula-driven value. Validates that cost data is available before attempting calculation. Uses `POST /api/v1/pricing/simulate` to preview cost-plus pricing before committing.

- **Pricing Simulation**: Leverages `POST /api/v1/pricing/simulate` to execute pricing calculations in a non-persisted mode for what-if analysis. Allows the rep to see the impact of quantity changes, tier transitions, and method overrides before committing to the quote. Returns a full pricing breakdown including method applied, calculation steps, and resulting price.

- **Custom Rule Validation**: For products with custom pricing formulas implemented in DRL rule sets, retrieves the applicable rules via `GET /api/v1/rules/by-rule-set/{ruleSetId}` and validates them before execution via `POST /api/v1/rules/{id}/validate`. Handles rule compilation errors, runtime evaluation failures, and unexpected output types gracefully with descriptive error messages.

---

## Inputs

| Input                          | Type              | Source                                          |
|--------------------------------|-------------------|-------------------------------------------------|
| Pricing Waterfall State        | JSON object       | Output from cpq-agent-004 (list prices applied)  |
| Quote Line Items with List Prices | JSON array     | From cpq-agent-004                               |
| Product Definitions            | JSON array of objects | Retrieved via `GET /api/v1/products/{id}`     |
| Product Type                   | String (Enum)     | From product definition (SOFTWARE, HARDWARE, SERVICE, SUBSCRIPTION, CONSUMABLE, HYBRID) |
| Quantity per Line Item         | Integer/Decimal   | From quote line item                              |
| Pricing Rule Set ID            | String (UUID)     | From product or product family configuration      |
| Cost Data                      | JSON object       | From cpq-agent-006 (for cost-plus products)       |
| Pricing Method Override        | String (Enum)     | Optional -- explicit method override per line item |
| Simulation Mode Flag           | Boolean           | When true, uses `/pricing/simulate`               |

---

## Outputs

| Output                            | Format                  | Human-in-the-Loop Checkpoint                     |
|-----------------------------------|-------------------------|--------------------------------------------------|
| Pricing Method Assignment         | JSON array of objects (lineItemId, productType, assignedMethod, methodSource: AUTO/OVERRIDE) | Informational; shown in pricing detail view |
| Calculated Line Item Prices       | JSON array of objects (lineItemId, listPrice, methodAdjustedPrice, calculationSteps[], method) | Automated for standard methods; deviation > 20% from list price triggers review |
| Tier Pricing Breakdown            | JSON array of objects (lineItemId, tiers[], effectiveUnitPrice, extendedPrice) | Informational; detailed breakdown available on expand |
| Percent-of-Total Resolution       | JSON array of objects (lineItemId, referenceLineItems[], baseTotal, percentage, calculatedPrice) | Informational; circular reference detected triggers escalation |
| Custom Rule Execution Results     | JSON array of objects (lineItemId, ruleSetId, ruleId, inputs, outputs, executionTime) | Rule validation failures block pricing and require rule fix |
| Pricing Simulation Report         | JSON object (scenarioId, lineItems[], totalQuotePrice, comparisonToCurrentPricing) | Advisory; rep can accept simulation or discard |

---

## Typical Workflow

1. **Receive Pricing Waterfall State**: After cpq-agent-004 applies list prices, this agent receives the pricing waterfall state containing all line items with their base list prices, resolved price book metadata, and floor price constraints.

2. **Load Product Definitions**: For each unique product on the quote, the agent calls `GET /api/v1/products/{id}` to retrieve the full product definition including ProductType, associated pricing rule set IDs, and any product-level pricing method overrides.

3. **Map Products to Pricing Methods**: The agent maps each product's ProductType to its pricing method using the method mapping table. Products with explicit method overrides use the override. Products with associated DRL rule sets are flagged for custom pricing. The mapping is logged for audit.

4. **Execute Block/Tiered Pricing**: For products mapped to tiered pricing, the agent retrieves the tier definitions from the pricing rule set via `GET /api/v1/rules/by-rule-set/{ruleSetId}`. Determines which tier the line item quantity falls into, calculates the effective unit price (block model) or the blended unit price (graduated model), and computes the extended price.

5. **Execute Percent-of-Total Pricing**: For percent-of-total products, the agent identifies the reference line items (typically all SOFTWARE and HARDWARE items), sums their current prices, and applies the defined percentage. Runs circular reference detection first. If references include other percent-of-total items, resolves using iterative convergence (max 10 iterations) or flags for manual resolution.

6. **Execute Cost-Plus Pricing**: For cost-plus products, the agent retrieves cost data from cpq-agent-006 (which sources from ERP). Applies the defined markup (FIXED, PERCENTAGE, or formula). If cost data is unavailable, the agent queues the line item for deferred pricing and notifies cpq-agent-006 to retrieve the cost data.

7. **Validate Custom Rules**: For products with DRL custom pricing rules, the agent validates each rule via `POST /api/v1/rules/{id}/validate` before execution. Executes valid rules and captures the output price. Handles rule failures by falling back to list price and flagging the item for pricing team review.

8. **Commit or Simulate Pricing**: If in simulation mode, returns the full pricing calculation without persisting. If in execution mode, calls `POST /api/v1/pricing/execute` (or the relevant pricing commit endpoint) to write the method-adjusted prices to the quote line items. Passes the updated pricing waterfall state to cpq-agent-006 for cost/markup integration.

---

## Edge Cases & Exceptions

- **Product Type Unmapped to Pricing Method**: When a product has a ProductType that does not exist in the method mapping table (e.g., a newly created type), the agent defaults to applying the list price as-is (pass-through method) and generates an alert to the pricing administration team. The line item is flagged with `UNMAPPED_METHOD`.

- **Tiered Pricing with Quantity Below Minimum Tier**: When the ordered quantity falls below the minimum quantity defined in the first tier (e.g., minimum tier starts at 5 but the rep enters 2), the agent applies the first tier's unit price for the entered quantity and displays an informational note about the minimum tier quantity. Does not block the quote.

- **Percent-of-Total Circular Reference**: When two or more percent-of-total line items reference each other (directly or transitively), the agent attempts iterative convergence (recalculate all percent-of-total items until prices stabilize within $0.01 tolerance, max 10 iterations). If convergence fails, flags the affected items and asks the rep to break the circular reference by fixing one item's price manually.

- **Cost Data Unavailable for Cost-Plus Product**: When cpq-agent-006 cannot retrieve cost data from ERP for a cost-plus product, the agent applies the list price as a provisional value, marks the item with `PENDING_COST_DATA`, and subscribes to a cost data availability event. When cost data arrives, automatically recalculates the line item and notifies the rep of the price change.

- **DRL Rule Compilation Error**: When a custom pricing rule fails to compile (syntax error, missing variables), the agent captures the compilation error, logs it with the rule ID and rule set ID, applies the list price as fallback, and notifies the pricing rules administrator. The line item is flagged with `RULE_ERROR` and the specific error message.

- **Method-Adjusted Price Deviates >20% from List Price**: When the calculated price after method application deviates more than 20% from the list price (either above or below), the agent flags the line item for review. Upward deviations greater than 20% trigger an informational warning. Downward deviations greater than 20% trigger a mandatory approval gate requiring pricing manager sign-off before the price is committed.

---

## Success Metrics

| Metric                                        | Target          |
|------------------------------------------------|-----------------|
| Pricing method auto-detection accuracy         | >= 98%          |
| Pricing calculation correctness (vs. manual verification) | 100% |
| Custom DRL rule execution success rate         | >= 95%          |
| Pricing method application latency (p95)       | < 800ms per line item |
| Percent-of-total circular reference auto-resolution rate | >= 80% |
