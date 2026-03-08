# Agent PRD: cpq-agent-010

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-010                                  |
| **Name**          | Deal-Specific Discount Agent                   |
| **Group**         | Pricing - Discounts                            |
| **Primary Service** | pricing-service (8082), quote-service (8084)  |
| **Autonomy Level** | Advisory                                      |
| **Dependencies**  | cpq-agent-004 (List Price Application Agent), cpq-agent-011 (Minimum Margin Enforcement Agent) |

---

## Purpose

The Deal-Specific Discount Agent manages manual and negotiated one-off discounts that fall outside the scope of automated volume, segment, and promotional discount schedules. These are bespoke pricing concessions driven by competitive pressure, strategic account acquisition, multi-year commitment incentives, or executive-level relationship agreements. Unlike the other discount agents in Group 3, this agent operates at a strictly Advisory autonomy level: it never applies discounts autonomously. Instead, it provides comprehensive margin impact analysis, historical precedent comparison, approval tier determination, and competitive context integration to help sales representatives and deal desk analysts make informed discounting decisions. Every deal-specific discount -- regardless of size -- must flow through the human approval process before being applied to a quote.

The agent serves as the analytical backbone of the negotiated pricing workflow. When a sales representative requests a custom discount, the agent immediately calculates the full margin impact by pulling real-time product costs from the ERP via `GET /api/v1/erp/costs/{productExternalId}`, determines which approval tier the request falls into based on the ApprovalTier enum (DEAL_DESK for margin below 35%, SALES_VP for discounts exceeding 25%, CFO for margin below 20%), retrieves historical precedent data showing how similar discount requests were handled for comparable customers and deal sizes, and integrates competitive context from the CRM deal record to evaluate whether the discount is justified by competitive dynamics. The agent then packages this analysis into a structured approval request with a recommendation (approve, approve with conditions, or decline with alternative), routes it to the appropriate approver via `POST /api/v1/quotes/{quoteId}/approvals`, and tracks the approval lifecycle through to resolution. This ensures that every negotiated discount is data-driven, consistently evaluated, and fully auditable.

---

## Key Skills / Capabilities

- **Discount Impact Analysis**: Calculates the comprehensive financial impact of a proposed deal-specific discount on the quote. Retrieves real-time product costs via `GET /api/v1/erp/costs/{productExternalId}` (or `GET /api/v1/erp/costs/bulk` for multi-line quotes) to compute: gross margin before discount, gross margin after discount, total revenue impact, margin delta in both percentage and absolute terms, and annualized revenue impact for subscription products. Simulates the discount through `POST /api/v1/pricing/simulate` to show the complete pricing waterfall including the deal-specific discount layered on top of any automated discounts. Presents a side-by-side comparison of the quote with and without the proposed discount.

- **Historical Precedent Comparison**: Searches the pricing audit trail and historical quote data to find precedent cases where similar deal-specific discounts were approved or rejected. Queries `GET /api/v1/erp/purchase-history/{customerExternalId}` for the customer's discount history and searches across all customers for comparable deals by matching on: customer segment, deal size range (within 20%), product family, and discount percentage range. Returns the top 5 most relevant precedents with their outcomes (approved/rejected/modified), the approver, the final discount applied, and the subsequent deal outcome (won/lost). This gives approvers objective data points for decision-making.

- **Approval Tier Determination**: Automatically determines the required approval tier based on the proposed discount's margin impact. Implements the ApprovalTier enum logic: if the post-discount margin falls below 35%, the request requires DEAL_DESK approval (aligned with `approval-margin-exception.drl` at salience 930); if the discount percentage exceeds 25% of list price, SALES_VP approval is required; if the post-discount margin falls below 20%, CFO approval is required. When multiple thresholds are triggered, the highest authority tier is applied. Routes the approval request via `POST /api/v1/quotes/{quoteId}/approvals` with the determined tier and supporting analysis.

- **Competitive Context Integration**: Pulls competitive intelligence from the CRM deal record via `GET /api/v1/deals/{id}` to evaluate whether the discount request is driven by genuine competitive pressure. Extracts: identified competitors, competitor pricing (if known), competitive positioning notes, customer's stated alternatives, and deal risk assessment. Incorporates this context into the approval recommendation, distinguishing between discounts justified by competitive dynamics ("Competitor X is offering 30% below our list price") and discounts driven by buyer negotiation tactics without substantive competitive threat.

- **Margin Simulation**: Provides interactive margin simulation capabilities allowing the sales representative and approver to explore different discount levels and their margin implications before committing to a specific discount. Calls `POST /api/v1/pricing/simulate` with multiple discount scenarios (e.g., 10%, 15%, 20%, 25%) and returns a margin curve showing the relationship between discount percentage and resulting margin for each line item and at the quote level. Highlights the margin thresholds that trigger different approval tiers, helping reps calibrate their discount requests to minimize approval complexity.

- **Approval Package Assembly**: Assembles a complete approval package that provides all the information an approver needs to make a decision. The package includes: the proposed discount amount and percentage, margin impact analysis (pre/post discount), historical precedent comparison, competitive context summary, customer lifetime value and strategic importance score, deal probability and pipeline impact, alternative discount structures (e.g., "offer 15% instead of 20% with extended payment terms"), and the agent's recommendation with confidence level. Submits the package via `POST /api/v1/quotes/{quoteId}/approvals` and monitors `GET /api/v1/quotes/approvals/pending` for status updates.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Active quote being negotiated                    |
| Line Item ID(s)              | List of String (UUID) | Line items receiving the deal-specific discount |
| Requested Discount Percentage | BigDecimal       | Sales rep input                                  |
| Requested Discount Amount    | BigDecimal        | Alternative to percentage; sales rep input       |
| Discount Justification       | String (free text)| Sales rep rationale for the discount             |
| Product External ID(s)       | List of String    | Products on the affected line items              |
| Customer External ID         | String            | Customer identifier for precedent lookup         |
| Customer Segment             | Enum (STARTUP, SMB, MID_MARKET, ENTERPRISE, STRATEGIC) | From `GET /api/v1/pricing-customers/{id}` |
| Deal ID                      | String (UUID)     | From `GET /api/v1/deals/{id}` for competitive context |
| Current Effective Prices     | Map (lineItemId -> BigDecimal) | Post-automated-discount prices from upstream agents |
| Real-Time Product Costs      | Map (productId -> BigDecimal) | From `GET /api/v1/erp/costs/{productExternalId}` |

---

## Outputs

| Output                             | Format                  | Human-in-the-Loop Checkpoint                     |
|------------------------------------|-------------------------|--------------------------------------------------|
| Discount Impact Analysis Report    | JSON object (marginBefore, marginAfter, revenueImpact, annualizedImpact, lineItemBreakdown) | Advisory; presented to sales rep before approval submission |
| Historical Precedent Report        | JSON array of objects (precedentQuoteId, customerSegment, dealSize, discountPercent, outcome, approver) | Advisory; included in approval package |
| Approval Tier Determination        | JSON object (requiredTier: DEAL_DESK or SALES_VP or CFO, triggeringThresholds, routingDetails) | Automatic tier determination; rep cannot override tier |
| Approval Package                   | JSON object (discountDetails, marginAnalysis, precedents, competitiveContext, recommendation, alternativeStructures) | ALL deal-specific discounts require human approval via `POST /api/v1/quotes/{quoteId}/approvals` |
| Margin Simulation Results          | JSON object (scenarios: array of discountPercent/marginPercent/approvalTier combinations) | Advisory; interactive tool for rep and approver |
| Audit Trail Entry                  | JSON object (agentId, requestedDiscount, marginImpact, approvalTier, precedentsFound, recommendation, timestamp) | Automatic; written to `ai.agent_audit_log` |

---

## Typical Workflow

1. **Receive Discount Request**: A sales representative requests a deal-specific discount for one or more line items on an active quote. The request includes the desired discount percentage or amount and a free-text justification. The agent receives the quote ID, affected line item IDs, and the discount parameters.

2. **Load Real-Time Costs**: Agent calls `GET /api/v1/erp/costs/bulk` with all affected product external IDs to retrieve current COGS, landed costs, and overhead allocations. This ensures margin calculations reflect real-time cost data rather than stale catalog costs. If ERP is unavailable, falls back to the last cached cost data and flags the analysis as "based on cached costs as of [timestamp]."

3. **Calculate Margin Impact**: Agent computes the pre-discount and post-discount margin for each affected line item and at the quote level. Factors in all automated discounts already applied by cpq-agent-007 (volume), cpq-agent-008 (segment), and cpq-agent-009 (promotional). Produces a comprehensive margin waterfall showing the contribution of each discount layer to the final margin, with the proposed deal-specific discount as the last layer.

4. **Determine Approval Tier**: Agent evaluates the post-discount margin against the ApprovalTier thresholds: margin < 35% triggers DEAL_DESK, discount > 25% triggers SALES_VP, margin < 20% triggers CFO. Applies the highest triggered tier. Calls `POST /api/v1/pricing/simulate` to verify the margin calculation against the Drools rule engine (specifically `approval-margin-exception.drl` at salience 930) to ensure consistency between the agent's calculation and the rules engine.

5. **Search Historical Precedents**: Agent queries historical pricing data to find comparable deal-specific discount approvals. Searches by customer segment, deal size range, product family, and discount range. Retrieves the top 5 precedents with full context including approval decision, approver, final discount, and deal outcome. Calculates aggregate statistics: average approved discount for similar deals, approval rate, and average time-to-approval.

6. **Integrate Competitive Context**: Agent calls `GET /api/v1/deals/{id}` to retrieve the CRM deal record including competitor information, competitive notes, deal risk assessment, and customer's stated alternatives. Evaluates the discount justification against the competitive context to assess whether the request is competitively justified. Flags cases where no competitive data exists but a large discount is requested.

7. **Assemble and Submit Approval Package**: Agent compiles all analysis into a structured approval package including: discount details, margin impact analysis with waterfall, historical precedents, competitive context, customer strategic value assessment, and an AI-generated recommendation (approve/approve with conditions/decline with alternative). Submits the package via `POST /api/v1/quotes/{quoteId}/approvals` routed to the determined approval tier. Includes alternative discount structures that would achieve a lower approval tier.

8. **Monitor Approval and Log Audit**: Agent monitors the approval status via `GET /api/v1/quotes/approvals/pending` and notifies the sales representative of any status changes. Once the approver renders a decision, the agent logs the complete lifecycle to `ai.agent_audit_log`: request details, analysis performed, approval tier, approver identity, decision (APPROVED/REJECTED/MODIFIED), any modifications to the original request, and total processing time.

---

## Edge Cases & Exceptions

- **Discount Pushes Margin Below Zero (Negative Margin)**: When the proposed deal-specific discount would result in a negative margin (selling below cost), the agent flags the request as a "below-cost exception" and automatically escalates to CFO tier regardless of the standard approval tier calculation. The analysis includes a clear red-flag indicator showing the per-unit loss, total deal loss, and break-even discount percentage. Additionally, the agent checks with cpq-agent-011 (Minimum Margin Enforcement) to determine if the request would trigger a hard block under the margin floor policy.

- **No Historical Precedents Found**: When the precedent search returns zero comparable deals (e.g., a novel product category or an unusually large discount request), the agent explicitly states that no precedents were found and expands the search parameters progressively (first relaxing deal size range to 50%, then removing product family filter, then removing segment filter). If still no precedents are found, the analysis is flagged as "unprecedented request" and the agent recommends additional scrutiny from the approver, including a note that this decision will establish a new precedent.

- **Competitive Data Contradicts Discount Justification**: When the CRM deal record shows no identified competitors or explicitly notes "no competitive threat" but the sales representative's discount justification claims competitive pressure, the agent flags the inconsistency in the approval package. It does not block the request but highlights the discrepancy for the approver's consideration and recommends the approver verify the competitive situation with the sales representative before approving.

- **Multiple Concurrent Approval Requests on Same Quote**: When a sales representative submits multiple deal-specific discount requests for different line items on the same quote before the first request is approved, the agent aggregates the margin impact across all pending requests and recalculates the approval tier based on the combined impact. If the individual requests each fall within DEAL_DESK tier but the combined impact drops the quote margin below 20%, the agent escalates the combined approval to CFO tier and notifies the rep that all pending discount requests have been consolidated into a single approval package.

- **Approver Modifies the Discount Amount**: When an approver approves the discount but at a different amount than requested (e.g., rep requested 25% but approver approved 18%), the agent recalculates the margin impact at the approved discount level, verifies the approved discount still falls within the approver's authority tier, and presents the modified discount to the sales representative for acknowledgment before applying it to the quote. If the rep does not acknowledge within the configurable timeout (default 48 hours), the approved-but-unacknowledged discount is logged and the quote remains at its pre-discount pricing.

- **ERP Cost Data Unavailable**: When the ERP adapter service is unreachable or returns stale data (cache age exceeding 24 hours), the agent proceeds with the analysis using the most recent available cost data but prominently flags all margin calculations as "estimated based on costs from [date]." The approval package includes a warning that real-time cost verification was not possible and recommends the approver validate costs independently before approving discounts that bring the margin close to any threshold boundary.

---

## Success Metrics

| Metric                                              | Target          |
|-----------------------------------------------------|-----------------|
| Approval package completeness (all sections populated) | >= 98%        |
| Margin calculation accuracy vs. Drools engine        | >= 99.9%        |
| Average approval package assembly time               | < 3 seconds     |
| Approval tier determination correctness              | 100%            |
| Historical precedent retrieval relevance score       | >= 80%          |
