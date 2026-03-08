# Agent PRD: cpq-agent-030

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-030                                  |
| **Name**          | Contract Lifecycle Management Agent            |
| **Group**         | Integration & Operations                       |
| **Primary Service** | quote-service (8084), crm-service (8081)      |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-024 (Multi-Level Approval Routing Agent) |

---

## Purpose

The Contract Lifecycle Management Agent manages the end-to-end contract lifecycle from quote acceptance through contract creation, execution, amendment, renewal, and termination. When a quote transitions to ACCEPTED status (published via the `quote:accepted` Redis Pub/Sub channel), this agent automatically creates the corresponding contract record, populates it with the quote's commercial terms, pricing, and product configuration, and initiates the contract execution workflow. Throughout the contract's active period, the agent tracks key milestones (signature deadlines, payment schedules, renewal windows) and triggers appropriate downstream workflows.

The agent operates at a Semi-autonomous level: standard contract creation from accepted quotes is fully automatic, but amendments, early terminations, and non-standard renewal terms require human approval. The agent integrates with the ERP adapter service via `GET /api/v1/erp/contracts/{customerExternalId}` to synchronize contract data with external systems and ensures that all contract actions are reflected in both the CPQ system and the ERP. It feeds contract renewal data to cpq-agent-028 (Renewal Opportunity Detection Agent) and provides contract context to cpq-agent-020 (Term & Co-Terming Agent) for alignment calculations.

---

## Key Skills / Capabilities

- **Contract Creation**: Automatically creates contract records from accepted quotes by subscribing to the `quote:accepted` Redis Pub/Sub channel. Extracts all commercial terms, pricing, product configuration, and approval history from the quote and populates the contract record. Generates a unique contract number and sets the initial contract status to PENDING_SIGNATURE.

- **Milestone Tracking**: Monitors contract milestones including signature deadline, effective date, payment schedule dates, renewal window opening (typically 90 days before expiration), and expiration date. Generates notifications and triggers workflows as milestones approach.

- **Amendment Management**: Processes contract amendments initiated through the quote system. Creates amendment records linked to the original contract, tracks the delta between original and amended terms, and routes amendments through the appropriate approval workflow via cpq-agent-024.

- **Renewal Workflow Trigger**: When the renewal window opens (configurable, default 90 days before expiration), creates a renewal opportunity in the CRM and notifies the assigned account team. Provides contract context (current terms, pricing, usage data) to cpq-agent-028 for renewal strategy recommendations.

- **ERP Synchronization**: Synchronizes contract data with external ERP systems via `GET /api/v1/erp/contracts/{customerExternalId}` and `POST /api/v1/erp/orders`. Ensures contract financial data (booking amount, billing schedule, revenue recognition) is reflected in the ERP.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote Accepted Event         | Event (Redis)     | `quote:accepted` channel                        |
| Quote ID                     | String (UUID)     | From accepted quote                              |
| Customer ID                  | String (UUID)     | Quote/CRM context                                |
| Contract Terms               | JSON object       | Extracted from quote (duration, billing, SLA)    |
| Product Configuration        | JSON array        | Quote line items with pricing                    |
| Amendment Request            | JSON object       | From quote amendment workflow                    |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Contract Record               | JSON object (contractId, status, terms) | Auto-created; rep reviews before sending for signature |
| Milestone Notifications       | Event stream            | Informational; triggers team notifications        |
| Amendment Record              | JSON object             | Requires approval via cpq-agent-024               |
| Renewal Opportunity           | CRM deal record         | Auto-created; account team decides strategy       |
| ERP Sync Status               | JSON object             | Logged; alerts on sync failures                   |

---

## Typical Workflow

1. **Receive Quote Acceptance**: Subscribes to `quote:accepted` Redis channel. On receipt, retrieves the full quote details via `GET /api/v1/quotes/{quoteId}`.

2. **Create Contract**: Creates the contract record with all commercial terms, pricing, and product configuration. Generates a contract number and sets status to PENDING_SIGNATURE.

3. **Trigger Document Generation**: Invokes cpq-agent-031 (Quote Document Generation) to produce the formal contract document for signature.

4. **Monitor Execution**: Tracks the contract through signature, countersignature, and activation. Updates status at each stage.

5. **Track Milestones**: Monitors all contract milestones throughout the active period. Triggers notifications and workflows as dates approach.

6. **Process Amendments**: When amendments are requested, creates amendment records and routes through approval.

7. **Trigger Renewal**: When the renewal window opens, creates the renewal opportunity and feeds data to cpq-agent-028.

---

## Edge Cases & Exceptions

- **Quote Accepted Without Full Approval**: If a quote reaches ACCEPTED status without all required approvals (system error), the agent blocks contract creation and raises an alert.

- **Multi-Year Contract with Annual Pricing Adjustments**: Creates sub-periods within the contract for each pricing adjustment date and tracks the adjustment schedule.

- **Contract Superseded by Amendment**: When an amendment replaces the original contract terms entirely, the agent marks the original terms as superseded while maintaining the audit trail.

- **ERP Sync Failure**: When ERP synchronization fails, the agent retries with exponential backoff and alerts the operations team after 3 failures.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Contract creation from accepted quote | 100% automated |
| Milestone notification delivery rate | >= 99%          |
| ERP sync success rate                | >= 98%          |
| Renewal window detection accuracy    | 100%            |
| Contract creation latency (p95)      | < 5 seconds     |
