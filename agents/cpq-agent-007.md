# Agent PRD: cpq-agent-007

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-007                                  |
| **Name**          | Volume Discount Agent                          |
| **Group**         | Pricing - Discounts                            |
| **Primary Service** | pricing-service (8082)                       |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent)   |

---

## Purpose

The Volume Discount Agent applies tiered and volume-based discount schedules to quote line items based on ordered quantities. It implements the deterministic logic defined in the `volume-discount-named-seat.drl` Drools rule (salience 620), which establishes three volume tiers for named-seat licensing: 100-499 seats at 7% discount, 500-999 seats at 12% discount, and 1000+ seats at 18% discount, with a minimum 12-month term requirement for eligibility. Beyond executing this specific rule, the agent supports both "all units" tier models (where the discount applies uniformly to every unit once a tier threshold is reached) and "graduated" tier models (where each tier's discount applies only to units within that tier's range). The agent also calculates aggregate volume across multiple line items and across related quotes for the same customer, enabling volume commitments that span separate orders to unlock higher discount tiers.

This agent is a critical component of the discount pricing chain, executing after cpq-agent-004 (List Price Application) has established base prices and before downstream guardrail agents (cpq-agent-011, cpq-agent-012) validate margin compliance. It operates at the Automated with constraints autonomy level: standard volume discount applications that fall within defined tier schedules are applied automatically without human intervention, but any scenarios involving cross-quote volume aggregation, retroactive tier reclassification, or discount stacking with other discount types require explicit validation or approval. The agent's deterministic behavior is governed entirely by Drools rules and pricing rule sets, ensuring auditability, consistency, and zero pricing drift across all quotes. Every discount application is logged to the pricing audit trail with full tier resolution details, enabling complete reconstruction of how any volume discount was calculated.

---

## Key Skills / Capabilities

- **Volume Tier Resolution**: Determines the applicable discount tier for a given quantity by evaluating the line item quantity against the active volume discount rule set. Calls `GET /api/v1/rules/by-rule-set/{ruleSetId}` to retrieve the tier thresholds and discount percentages for the specific product or product family. Supports both discrete tier boundaries (e.g., 100, 500, 1000) and continuous sliding-scale models. Returns the matched tier, the discount percentage, and the quantity distance to the next tier for upsell nudging.

- **Discount Schedule Application**: Applies the resolved volume discount to line item pricing by invoking `POST /api/v1/pricing/execute` with the volume discount rule context. Supports "all units" model (single discount rate applied to total quantity) and "graduated" model (each quantity tranche priced at its tier rate). Calculates the effective blended discount rate for graduated models and writes the discount breakdown to the line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}`.

- **Aggregate Volume Calculation**: Computes total volume across multiple line items within a single quote and across historical quotes for the same customer. Queries `GET /api/v1/erp/purchase-history/{customerExternalId}` for prior purchase volumes and `GET /api/v1/quotes/{id}` with `GET /api/v1/quotes/{quoteId}/line-items` for active quotes. Sums qualifying quantities to determine the effective volume for tier resolution, enabling customers to unlock higher tiers through cumulative purchasing.

- **Tier Simulation**: Provides what-if analysis showing the discount impact of adjusting quantities to reach the next tier threshold. Calls `POST /api/v1/pricing/simulate` with hypothetical quantities to calculate the price difference between the current tier and the next tier. Returns a simulation result showing: current tier discount, next tier threshold, additional units needed, incremental cost of additional units, and net savings from reaching the next tier. This powers the "add X more seats to save Y%" recommendation in the UI.

- **Discount Stacking Validation**: Validates whether the volume discount can be combined with other active discounts (segment, promotional, deal-specific) by checking the stacking policy defined in the rule set via `GET /api/v1/rule-sets`. Enforces mutual exclusivity rules where applicable (e.g., volume discount cannot stack with a promotional flat-rate discount on the same line item). When stacking is permitted, determines the application order (typically: list price -> volume discount -> segment discount -> promotional discount) and validates the combined discount does not exceed the maximum allowed discount cap.

- **Term Eligibility Enforcement**: Validates that the quote's subscription term meets the minimum term requirement specified in the volume discount rule. For the `volume-discount-named-seat.drl` rule, this means verifying the term is at least 12 months. Retrieves term information from the quote record via `GET /api/v1/quotes/{id}` and blocks volume discount application with a clear error message if the term requirement is not met. Suggests the minimum qualifying term to the sales representative.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote context from pricing waterfall             |
| Line Item ID                 | String (UUID)     | Individual line item being priced                |
| Product External ID          | String            | Product identifier for rule lookup               |
| Quantity                     | Integer           | Line item quantity from quote                    |
| Base Unit Price              | BigDecimal        | Output from cpq-agent-004 (List Price Application) |
| Subscription Term (months)   | Integer           | Quote-level term from `GET /api/v1/quotes/{id}` |
| Customer External ID         | String            | Customer identifier for aggregate volume lookup  |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |
| Active Rule Sets             | List of RuleSet   | From `GET /api/v1/rule-sets` (status = ACTIVE)  |
| Historical Purchase Volume   | Integer           | From `GET /api/v1/erp/purchase-history/{customerExternalId}` |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Applied Volume Discount            | JSON object (tierId, tierName, discountPercent, discountAmount, model: ALL_UNITS or GRADUATED) | Automatic within defined tiers; no approval needed for standard application |
| Updated Line Item Pricing          | JSON object (lineItemId, originalPrice, discountedPrice, discountBreakdown) | Automatic; written to quote line item via PUT endpoint |
| Tier Simulation Results            | JSON object (currentTier, nextTier, unitsToNextTier, potentialSavings) | Advisory only; presented to sales rep as recommendation |
| Stacking Validation Result         | JSON object (stackable: boolean, conflicts: array, combinedDiscountPercent) | If stacking conflict detected, rep must choose which discount to apply |
| Term Eligibility Warning           | JSON object (eligible: boolean, minimumTermRequired, currentTerm) | Blocks discount application; rep must adjust term or acknowledge |
| Audit Trail Entry                  | JSON object (agentId, action, ruleId, tierResolution, inputQuantity, outputDiscount, timestamp) | Automatic; written to `ai.agent_audit_log` |

---

## Typical Workflow

1. **Receive Pricing Request**: The pricing waterfall reaches the volume discount stage after cpq-agent-004 has applied the base list price. The agent receives the quote ID, line item ID, product external ID, quantity, and base unit price as inputs from the orchestration context.

2. **Load Volume Discount Rules**: Agent calls `GET /api/v1/rule-sets` filtered by type = VOLUME_DISCOUNT and status = ACTIVE, then calls `GET /api/v1/rules/by-rule-set/{ruleSetId}` for each matching rule set to retrieve tier definitions (thresholds, discount percentages, tier model type, minimum term requirements, and stacking policies).

3. **Validate Term Eligibility**: Agent calls `GET /api/v1/quotes/{id}` to retrieve the quote-level subscription term. Compares against the minimum term requirement in the matched volume discount rule (e.g., 12 months for `volume-discount-named-seat.drl`). If the term is insufficient, the agent returns a term eligibility warning and halts discount application for this line item.

4. **Calculate Aggregate Volume**: Agent determines whether aggregate volume calculation is applicable by checking the rule set's aggregation policy. If enabled, calls `GET /api/v1/erp/purchase-history/{customerExternalId}` for historical volumes and `GET /api/v1/quotes/{quoteId}/line-items` for current quote volumes. Sums qualifying quantities across the defined aggregation scope (single quote, customer lifetime, or rolling 12-month window).

5. **Resolve Tier and Calculate Discount**: Agent evaluates the effective quantity (direct or aggregated) against the tier thresholds. For "all units" models, applies the single matched tier's discount percentage to the full quantity. For "graduated" models, calculates the blended discount by applying each tier's rate to the quantity within that tier's range. Calls `POST /api/v1/pricing/execute` with the volume discount context to execute the Drools rule and obtain the deterministic discount result.

6. **Validate Discount Stacking**: Agent checks whether other discounts have already been applied or are queued for this line item. Evaluates stacking rules from the rule set configuration. If conflicts exist (e.g., volume discount is mutually exclusive with an active promotional discount), the agent flags the conflict and presents the options to the sales representative for resolution.

7. **Apply Discount to Line Item**: Agent writes the resolved discount to the quote line item via `PUT /api/v1/quotes/{quoteId}/line-items/{lineItemId}`, updating the discount percentage, discount amount, and effective unit price. Generates a tier simulation showing the proximity to the next tier to support upsell conversations.

8. **Log Audit Trail**: Agent writes a comprehensive audit entry to `ai.agent_audit_log` including: agent ID, matched rule ID, input quantity, aggregation details (if applicable), tier resolution path, applied discount percentage and amount, stacking validation result, and timestamp. This enables full pricing reconstruction and compliance reporting.

---

## Edge Cases & Exceptions

- **Cross-Quote Volume Aggregation**: When a customer has multiple active quotes in DRAFT or PENDING status, the agent must decide whether to include quantities from those quotes in the aggregate volume calculation. The agent applies the rule set's aggregation policy: if set to COMMITTED_ONLY, only quantities from ACCEPTED quotes and ERP purchase history are included; if set to PIPELINE_INCLUSIVE, quantities from DRAFT and PENDING quotes are also included with a visual indicator showing the aggregation breakdown. The rep receives a notification listing all quotes contributing to the volume tier.

- **Mid-Tier Boundary Quantities**: When the ordered quantity falls exactly on a tier boundary (e.g., exactly 100, 500, or 1000 seats), the agent applies the higher tier's discount per the rule's boundary policy (inclusive lower bound). Additionally, when the quantity is within 10% of the next tier threshold, the agent generates a proactive tier simulation showing the cost of adding units to reach the next tier, enabling the rep to propose a volume uplift to the customer.

- **Volume Discount + Segment Discount Stacking**: When both a volume discount and a customer segment discount (cpq-agent-008) apply to the same line item, the agent checks the stacking policy. If compounding is allowed, discounts are applied sequentially (volume first at salience 620, then segment at its defined salience). If best-of policy applies, the agent calculates both discounts independently and applies whichever yields the greater discount amount. The non-applied discount is logged as "superseded" in the audit trail.

- **Retroactive Tier Qualification**: When adding a new line item to a quote causes the aggregate quantity to cross a tier threshold, all previously priced line items of the same product family must be retroactively repriced at the new tier rate. The agent detects this scenario by comparing pre- and post-addition aggregate volumes, triggers a reprice of all affected line items via `POST /api/v1/pricing/execute`, and presents the rep with a summary of all pricing changes including the total incremental discount gained.

- **Term Change Invalidating Volume Discount**: When a quote's subscription term is modified after volume discounts have been applied, and the new term falls below the minimum requirement (e.g., changed from 12 months to 6 months), the agent automatically revokes the volume discount from all affected line items. The agent notifies the rep with a clear explanation of why the discount was removed and the financial impact of the term change.

- **Zero-Quantity or Negative-Quantity Line Items**: If a line item has a quantity of zero (placeholder) or a negative quantity (credit/return), the agent skips volume discount application for that line item but still includes the absolute quantity value in aggregate volume calculations if the aggregation policy includes credits. Logs the skip reason in the audit trail.

---

## Success Metrics

| Metric                                        | Target          |
|-----------------------------------------------|-----------------|
| Volume discount application accuracy          | >= 99.9%        |
| Average tier resolution latency (p95)         | < 200ms         |
| Tier simulation upsell conversion rate        | >= 12%          |
| Retroactive reprice correctness rate          | 100%            |
| Audit trail completeness (all fields populated) | 100%          |
