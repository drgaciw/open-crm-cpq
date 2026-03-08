# Agent PRD: cpq-agent-024

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-024                                  |
| **Name**          | Multi-Level Approval Routing Agent             |
| **Group**         | Governance & Approvals                         |
| **Primary Service** | quote-service (8084), pricing-service (8082)  |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-011 (Minimum Margin Enforcement Agent) |

---

## Purpose

The Multi-Level Approval Routing Agent determines the appropriate approval workflow for quotes that exceed standard authorization thresholds, automatically creating and routing approval requests to the correct approver tier based on configurable business rules. The agent evaluates three primary dimensions -- total quote amount, maximum discount percentage applied, and minimum gross margin -- against threshold matrices that map to approval tiers: Sales Manager, Deal Desk, VP Sales, and CFO. When cpq-agent-011 (Minimum Margin Enforcement Agent) flags a margin violation, this agent receives the violation details and constructs the appropriate approval request with the complete pricing context, discount waterfall, and business justification fields.

The agent operates at the Automated with constraints level: standard threshold-based routing is fully automatic with no human intervention required, but the agent cannot override approval requirements, skip approval tiers, or auto-approve requests. Every approval route determination is logged with the full calculation showing which threshold was breached and why the specific tier was selected. The agent supports parallel approval chains (e.g., Deal Desk AND Legal simultaneously), sequential chains (e.g., Sales Manager THEN VP Sales), and conditional chains (e.g., VP Sales only if Deal Desk rejects). It monitors approval status and automatically unblocks quote progression when all required approvals are obtained.

---

## Key Skills / Capabilities

- **Threshold Evaluation**: Evaluates the quote against configurable threshold matrices via `POST /api/v1/pricing/execute` and internal rule evaluation. Checks quote total amount against tier thresholds (e.g., >$100K = Sales Manager, >$500K = VP Sales, >$1M = CFO), maximum line-item or quote-level discount percentage, and gross margin against the three-tiered model from cpq-agent-011 (35% = DEAL_DESK, 20% = CFO, 0% = hard block).

- **Approval Workflow Creation**: Creates approval workflow instances via `POST /api/v1/quotes/{quoteId}/approvals` with the determined tier, approver list, required context (margin analysis, discount waterfall, deal justification), and SLA deadline. Supports parallel, sequential, and conditional approval chain types.

- **Approver Resolution**: Resolves the specific approver(s) for each tier based on organizational hierarchy, deal territory assignment, and availability. Falls back to tier-level pools when specific approvers are unavailable. Retrieves approver information from `GET /api/v1/quotes/approvals/approvers?tier={tier}`.

- **Approval Status Monitoring**: Monitors pending approvals via `GET /api/v1/quotes/approvals/pending` and `GET /api/v1/quotes/{quoteId}/approvals`. Sends reminders when approvals approach SLA deadlines. Automatically transitions quote status from PENDING_APPROVAL to APPROVED when all required approvals are obtained.

- **Escalation Management**: When an approval request exceeds its SLA deadline without a response, automatically escalates to the next tier or to a designated escalation contact. Logs all escalation events for audit trail compliance.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote submission context                         |
| Quote Total Amount           | BigDecimal        | Calculated from quote line items                 |
| Maximum Discount Percentage  | BigDecimal        | From discount waterfall (cpq-agent-007 through 010) |
| Minimum Gross Margin         | BigDecimal        | From cpq-agent-011 margin calculation            |
| Margin Violation Details     | JSON object       | From cpq-agent-011 (tier breached, line items affected) |
| Deal ID                      | String (UUID)     | CRM deal context for territory/approver resolution |
| Submitting User Role         | String (Enum)     | SALES_REP, SALES_MANAGER, DEAL_DESK              |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Approval Workflow Instance    | JSON object (workflowId, tiers, approvers, status) | Informational; rep sees approval status in UI |
| Approval Tier Determination   | String (Enum)           | Logged for audit; not modifiable by sales rep    |
| Approval Request Package      | JSON object (context, justification, pricing analysis) | Rep can add business justification before submission |
| Approval Status Updates       | Event stream            | Rep receives real-time notifications              |
| Quote Status Transition       | Enum change             | Automatic on full approval; no manual intervention |

---

## Typical Workflow

1. **Receive Quote Submission**: Triggered when a quote transitions from DRAFT to submission. Receives the complete quote context including total amount, discount waterfall, and margin calculations.

2. **Evaluate Thresholds**: Runs the quote against all configured threshold matrices. Determines which thresholds are breached and maps each breach to the required approval tier.

3. **Determine Highest Required Tier**: When multiple thresholds are breached, selects the highest required approval tier. Constructs the approval chain based on whether the organization requires sequential or parallel approvals.

4. **Resolve Approvers**: Identifies specific approvers for each tier based on deal territory, organizational hierarchy, and availability. Falls back to tier pools when needed.

5. **Create Approval Workflow**: Creates the approval workflow instance via `POST /api/v1/quotes/{quoteId}/approvals`. Transitions the quote status to PENDING_APPROVAL. Notifies all designated approvers.

6. **Monitor and Complete**: Monitors approval responses. When all required approvals are obtained, transitions the quote to APPROVED and triggers downstream agents (cpq-agent-031 for document generation).

---

## Edge Cases & Exceptions

- **No Thresholds Breached**: When the quote meets all standard thresholds, the agent auto-approves and transitions the quote directly to APPROVED status without creating an approval workflow.

- **Approver Unavailable**: When a designated approver is out-of-office or unresponsive, escalates to the backup approver or tier pool after the configured SLA window.

- **Approval Rejection**: When an approver rejects the quote, the agent transitions the quote back to DRAFT with the rejection reason and notifies the submitting rep. The rep can modify and resubmit.

- **Partial Approval in Parallel Chain**: When one branch of a parallel approval chain is approved but another is rejected, the agent follows the configured conflict resolution rule (require-all vs require-any).

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Correct tier determination accuracy   | 100%           |
| Approval routing latency (p95)        | < 500ms        |
| Average time to first approver response | < 4 hours    |
| SLA compliance for approval deadlines | >= 95%         |
| Escalation rate for timeout           | < 5%           |
