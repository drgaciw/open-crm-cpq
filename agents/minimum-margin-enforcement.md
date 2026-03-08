# Agent PRD: cpq-agent-011

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-011                                  |
| **Name**          | Minimum Margin Enforcement Agent               |
| **Group**         | Pricing - Guardrails                           |
| **Primary Service** | pricing-service (8082), quote-service (8084)  |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-006 (Markup & Cost Integration Agent) |

---

## Purpose

The Minimum Margin Enforcement Agent is the final guardrail in the CPQ pricing chain, responsible for enforcing hard margin floor checks on every quote line item and at the aggregate quote level before a quote can progress beyond DRAFT status. It implements the logic defined in `approval-margin-exception.drl` (salience 930), which triggers DEAL_DESK approval when the gross margin on any line item or on the overall quote falls below 35%. The agent extends this single Drools rule into a three-tiered enforcement model: margin below 35% routes to DEAL_DESK approval, margin below 20% escalates to CFO approval, and margin below 0% (selling at or below cost) triggers an absolute hard block that cannot be overridden without CFO-level exception approval. The agent pulls real-time cost data from the ERP adapter service to ensure margin calculations are based on current COGS, landed costs, and overhead allocations rather than potentially stale catalog costs.

This agent operates at the Automated with constraints autonomy level, but its behavior is more restrictive than other agents at this tier. Standard margin checks that pass all thresholds proceed silently -- the agent confirms margin compliance and the pricing waterfall continues. When a margin threshold is breached, the agent's response is deterministic and cannot be overridden by any other agent or by sales representative action: it automatically creates the appropriate approval request and blocks quote progression until the approval is resolved. The hard block at margin below 0% is an absolute enforcement that even an approval cannot override without a specific CFO-level "below-cost exception" approval type. This agent operates at salience 930 in the Drools priority hierarchy, meaning it overrides all discount agents (which operate at salience 620 and below) and can only be superseded by system-level policy rules. Every margin check is logged with the complete cost basis, discount waterfall, and resulting margin calculation, providing a complete audit trail for regulatory compliance and financial governance.

---

## Key Skills / Capabilities

- **Margin Floor Check**: Executes real-time gross margin calculation for each line item and at the quote aggregate level. Retrieves current COGS and landed costs via `GET /api/v1/erp/costs/{productExternalId}` (or `GET /api/v1/erp/costs/bulk` for multi-line quotes), calculates gross margin as `(effectivePrice - totalCost) / effectivePrice * 100`, and evaluates the result against the three-tiered threshold model. Calls `POST /api/v1/pricing/execute` to run the `approval-margin-exception.drl` rule (salience 930) for deterministic validation against the Drools engine, ensuring the agent's calculation matches the rules engine exactly. Returns the margin result with pass/fail status and the specific threshold breached.

- **Approval Route Trigger**: Automatically creates and routes approval requests when margin thresholds are breached. For margin < 35%, creates a DEAL_DESK approval via `POST /api/v1/quotes/{quoteId}/approvals` with tier = DEAL_DESK. For margin < 20%, creates a CFO approval with tier = CFO. Includes the complete margin analysis in the approval request: line-item-level margin breakdown, cost basis details, discount waterfall showing how the margin was eroded, and the minimum price required to meet the threshold. Monitors approval status via `GET /api/v1/quotes/approvals/pending` and unblocks quote progression upon approval.

- **Line-Level Margin Validation**: Performs margin validation at the individual line item level, not just at the quote aggregate. This catches scenarios where the overall quote margin is healthy (e.g., 45%) but individual line items are deeply discounted below the margin floor (e.g., a loss-leader product at 10% margin). Retrieves all line items via `GET /api/v1/quotes/{quoteId}/line-items`, computes margin for each line item individually, and flags any line item that breaches a threshold even if the quote-level margin is compliant. This prevents margin cross-subsidization where profitable items mask unprofitable ones.

- **Quote-Level Margin Validation**: Computes the weighted-average margin across all line items on the quote, factoring in quantity, unit price, and unit cost for each line item. This provides the overall deal margin that determines quote-level approval requirements. The quote-level margin check runs in addition to (not instead of) line-level checks, meaning both must pass for the quote to proceed without approvals. For bundled line items, calculates both the bundle-level margin and the individual component margins where costs are available.

- **Hard Block Enforcement**: Implements an absolute block on quotes where any line item has a margin at or below 0% (selling at or below cost). This hard block is distinct from the approval-based enforcement at 35% and 20% thresholds: it cannot be resolved through a standard approval workflow. A hard block requires a specific "below-cost exception" approval type that is restricted to CFO-level approvers and requires a written business justification. The hard block is enforced at the API level, preventing the quote from transitioning to PENDING_APPROVAL or any subsequent state until resolved. All hard block events are flagged in the audit log with high-severity classification.

- **Margin Restoration Guidance**: When a margin threshold is breached, the agent generates actionable guidance showing exactly what changes would bring the margin back within acceptable bounds. Calculates: the minimum unit price that would achieve the 35% margin floor, the maximum combined discount percentage that maintains compliance, which specific discount layers could be reduced or removed to restore margin, and alternative product configurations (if a lower-cost variant exists) that could achieve the customer's requirements at a compliant margin. This guidance is included in the approval request and presented to the sales representative.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote context from pricing waterfall             |
| Line Item IDs                | List of String (UUID) | All line items in the quote                  |
| Effective Prices             | Map (lineItemId -> BigDecimal) | Post-all-discounts pricing from upstream agents |
| Product External IDs         | List of String    | Products for ERP cost lookup                     |
| Real-Time Product Costs      | Map (productId -> CostBreakdown) | From `GET /api/v1/erp/costs/bulk` (COGS, landed cost, overhead) |
| Applied Discount Waterfall   | List of Discount  | Accumulated from cpq-agent-007 through cpq-agent-010 |
| Quote Status                 | Enum              | From `GET /api/v1/quotes/{id}` (DRAFT, PENDING_APPROVAL, etc.) |
| Customer External ID         | String            | For customer-specific margin policy lookup       |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Margin Compliance Result           | JSON object (compliant: boolean, quoteMargin, lineItemMargins: array, thresholdBreaches: array) | If compliant, proceeds silently; no human action needed |
| Approval Request (DEAL_DESK)       | JSON object (tier: DEAL_DESK, quoteId, marginDetails, costBasis, discountWaterfall, restorationGuidance) | Automatic creation when margin < 35%; approver must approve/reject/modify |
| Approval Request (CFO)             | JSON object (tier: CFO, quoteId, marginDetails, costBasis, businessJustificationRequired: true) | Automatic creation when margin < 20%; CFO must approve/reject |
| Hard Block Notice                  | JSON object (blocked: true, reason: BELOW_COST, lineItemIds, negativeMarginAmount, restorationGuidance) | Absolute block; requires CFO below-cost exception to resolve |
| Margin Restoration Guidance        | JSON object (minimumCompliantPrice, maximumAllowedDiscount, discountRemovalOptions, alternativeConfigurations) | Advisory; included with approval requests and hard blocks |
| Audit Trail Entry                  | JSON object (agentId, quoteMargin, lineItemMargins, thresholdBreached, approvalTier, costBasis, timestamp) | Automatic; written to `ai.agent_audit_log` with high-priority flag for breaches |

---

## Typical Workflow

1. **Receive Margin Check Request**: The pricing waterfall reaches the margin enforcement stage after all discount agents (cpq-agent-007 through cpq-agent-010) have completed. The agent receives the quote ID and the complete pricing context including all line items with their effective prices and the full discount waterfall showing how each price was derived.

2. **Load Real-Time Cost Data**: Agent calls `GET /api/v1/erp/costs/bulk` with all product external IDs on the quote to retrieve current COGS, landed costs, component breakdowns, and overhead allocations. Validates that cost data is fresh (within the 1-hour cache TTL). If any product's cost data is missing or stale, the agent flags those line items as "cost data unavailable" and applies a conservative margin estimate using the highest historical cost for that product.

3. **Calculate Line-Level Margins**: For each line item, agent computes: gross margin = (effective unit price - total unit cost) / effective unit price * 100. Records the margin alongside the complete cost breakdown and discount waterfall for that line item. Identifies the specific discount layers that contributed most to margin erosion for each line item.

4. **Calculate Quote-Level Margin**: Agent computes the weighted-average margin across all line items: sum of (unit margin * quantity) / sum of (unit price * quantity) * 100. This aggregate margin determines the overall deal margin for quote-level threshold evaluation.

5. **Evaluate Against Thresholds**: Agent evaluates both line-level and quote-level margins against the three-tiered threshold model. Calls `POST /api/v1/pricing/execute` with the margin enforcement rule context to run `approval-margin-exception.drl` (salience 930) and confirm the agent's calculations match the Drools engine output. Categorizes the result: COMPLIANT (all margins above 35%), DEAL_DESK_REQUIRED (any margin between 20% and 35%), CFO_REQUIRED (any margin between 0% and 20%), or HARD_BLOCK (any margin at or below 0%).

6. **Enforce Compliance Action**: Based on the threshold evaluation result: if COMPLIANT, the agent logs the compliance confirmation and allows the pricing waterfall to continue. If DEAL_DESK_REQUIRED or CFO_REQUIRED, the agent automatically creates an approval request via `POST /api/v1/quotes/{quoteId}/approvals` with the appropriate tier, including the full margin analysis and restoration guidance. If HARD_BLOCK, the agent blocks the quote from any state transition and creates a below-cost exception request requiring CFO approval with a mandatory business justification field.

7. **Generate Restoration Guidance**: For any threshold breach, the agent calculates and includes restoration guidance: the minimum unit price per line item that would achieve 35% margin, the maximum combined discount that maintains compliance, which specific discount layers from the waterfall could be adjusted to restore margin (prioritizing deal-specific discounts first, then promotional, then segment), and any alternative product configurations that might meet the customer's needs at a compliant margin.

8. **Log Comprehensive Audit Entry**: Agent writes a detailed audit entry to `ai.agent_audit_log` including: quote ID, margin compliance result, all line-level margins with cost basis, quote-level margin, threshold evaluation detail, approval tier triggered (if any), cost data freshness indicators, discount waterfall summary, and timestamp. Margin breach events are logged at HIGH severity for compliance reporting via cpq-agent-036 (Audit & Compliance Reporting).

---

## Edge Cases & Exceptions

- **ERP Cost Data Unavailable for Critical Products**: When the ERP adapter service is unreachable or returns errors for specific products, the agent cannot compute an accurate margin. In this scenario, the agent applies a "fail-safe" policy: it uses the most recent cached cost (even if stale) and adds a 10% cost buffer to account for potential cost increases since the cache was populated. If no cached cost exists at all, the agent treats the margin as non-deterministic and automatically triggers a DEAL_DESK approval with a flag indicating "margin calculated with estimated costs -- manual cost verification required."

- **Bundle Margin vs. Component Margin Conflict**: When a product bundle has an overall compliant margin (e.g., 40%) but individual components within the bundle have margins below the threshold (e.g., a hardware component at 15% margin subsidized by a high-margin software component at 65% margin), the agent evaluates margin at the bundle level for standard enforcement. However, if the below-threshold component is available as a standalone SKU and could be ordered separately (unbundling risk), the agent generates an advisory warning about the component-level margin exposure and recommends the product team evaluate the bundle's component pricing.

- **Margin Threshold Crossed During Approval Wait**: When a quote is waiting for approval on a DEAL_DESK margin exception, and in the interim the ERP updates the product cost (making the margin even lower, now requiring CFO approval), the agent detects the cost change on any re-evaluation and escalates the approval tier accordingly. The existing DEAL_DESK approval is superseded by the new CFO-level approval, and both the sales representative and the original DEAL_DESK approver are notified of the escalation.

- **Currency Conversion Impact on Margin**: When a quote is denominated in a non-base currency, the margin calculation must account for the FX conversion. The agent retrieves the current FX rate and calculates margin in the base currency to ensure the margin floor is evaluated consistently. If FX rate volatility could push the margin below a threshold between quote creation and acceptance (e.g., margin is 36% today but could drop to 34% with a 2% adverse FX movement), the agent generates an advisory warning about FX margin risk.

- **Quote with Zero Revenue Line Items**: When a quote contains line items with zero effective price (e.g., free trial licenses, promotional giveaways, or included services), the agent handles these as special cases. Zero-price items with non-zero cost are treated as negative margin items and included in the quote-level margin calculation. If the zero-price item drives the quote margin below a threshold, the agent triggers the appropriate approval with clear attribution showing the zero-price item's contribution to the margin shortfall.

- **Retroactive Cost Increase After Quote Approval**: When a quote has already passed margin enforcement and been approved, but ERP costs increase before the quote is accepted by the customer, the agent does not retroactively revoke the approval. However, on any subsequent pricing recalculation (e.g., quote amendment, line item addition), the agent rechecks margins with current costs and triggers new approvals if thresholds are now breached. Logs the cost change detection in the audit trail.

---

## Success Metrics

| Metric                                              | Target          |
|-----------------------------------------------------|-----------------|
| Margin calculation accuracy vs. ERP actuals          | >= 99.5%        |
| Hard block enforcement rate (below-cost quotes blocked) | 100%          |
| Average margin check latency (p95)                   | < 300ms         |
| Approval routing correctness (correct tier assigned)  | 100%           |
| Audit trail completeness (cost basis + margin waterfall) | 100%         |
