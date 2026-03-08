# Agent PRD: cpq-agent-036

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-036                                  |
| **Name**          | Audit & Compliance Reporting Agent             |
| **Group**         | Integration & Operations                       |
| **Primary Service** | pricing-service (8082), quote-service (8084)  |
| **Autonomy Level** | Automated with constraints                    |
| **Dependencies**  | cpq-agent-011 (Minimum Margin Enforcement Agent), cpq-agent-024 (Multi-Level Approval Routing Agent) |

---

## Purpose

The Audit & Compliance Reporting Agent generates comprehensive audit trails and compliance reports for all pricing decisions, discount approvals, margin exceptions, and quote lifecycle events. It subscribes to `quote:status-changed` and `pricing:executed` Redis Pub/Sub channels to capture events in real-time, and enriches the raw event data with contextual information (who made the decision, what the alternatives were, what guardrails were active, and what the financial impact was). The agent supports SOX compliance requirements for financial controls over pricing decisions and provides the data foundation for regulatory audits, internal audit reviews, and pricing governance oversight.

The agent operates at the Automated with constraints level: audit log capture is fully automatic with no human intervention, but report generation parameters and distribution lists are configured by administrators. The agent never modifies pricing data or approval decisions -- it is strictly a read-and-report agent that cannot influence the pricing waterfall. Every audit record is immutable once created, and the agent validates audit trail completeness by reconciling logged events against quote status transitions to detect any gaps in the audit chain.

---

## Key Skills / Capabilities

- **Real-Time Event Capture**: Subscribes to `quote:status-changed` and `pricing:executed` Redis channels and logs every pricing-related event with timestamp, user context, input parameters, output values, and decision rationale. Captures margin checks from cpq-agent-011, approval decisions from cpq-agent-024, and exception approvals from cpq-agent-025.

- **Audit Trail Assembly**: Constructs the complete audit trail for any quote from initial creation through approval and acceptance via `GET /api/v1/pricing/audit-log?quoteId={id}`. Shows every pricing calculation, discount application, margin check, approval request, and status transition with full context.

- **Compliance Report Generation**: Generates periodic compliance reports (daily, weekly, monthly) including: total quotes processed, quotes requiring approval, approval turnaround times, margin exception frequency, below-cost exception count, and average discount percentages by segment and region.

- **Audit Gap Detection**: Reconciles the audit log against quote status transitions to identify any events that should have been logged but weren't. Raises high-severity alerts for audit gaps that could indicate system issues or tampering.

- **Data Export**: Exports audit data in standardized formats (CSV, JSON, PDF) for external audit teams. Supports configurable report templates and scheduled distribution.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote Status Change Events   | Event (Redis)     | `quote:status-changed` channel                   |
| Pricing Execution Events     | Event (Redis)     | `pricing:executed` channel                       |
| Report Parameters            | JSON object       | Date range, scope, format, recipients            |
| Audit Query                  | JSON object       | Quote ID, date range, event type filters         |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Audit Log Entries             | Immutable records       | Automatic; no human review required for capture  |
| Compliance Reports            | PDF/CSV                 | Delivered to configured recipients                |
| Audit Gap Alerts              | High-severity event     | Requires immediate investigation by compliance team |
| Quote Audit Trail             | JSON/PDF                | Available on demand for any quote                 |

---

## Typical Workflow

1. **Capture Events**: Continuously subscribes to Redis channels and logs all pricing and quote events.

2. **Enrich Context**: Augments raw events with user identity, role, decision rationale, and financial impact.

3. **Store Immutably**: Persists audit records to the `ai.agent_audit_log` table with append-only semantics.

4. **Generate Reports**: On schedule or on demand, assembles compliance reports from the audit log.

5. **Detect Gaps**: Periodically reconciles the audit trail against expected event sequences and alerts on gaps.

---

## Edge Cases & Exceptions

- **High Event Volume**: During bulk quote processing, the agent batches audit writes to maintain throughput without dropping events.

- **Redis Channel Unavailable**: When the Redis subscription fails, the agent falls back to polling quote and pricing service APIs for missed events. Logs the gap period for manual reconciliation.

- **Audit Record Immutability Violation**: If any attempt is made to modify an existing audit record, the agent blocks the modification and raises a critical security alert.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| Audit event capture completeness     | 100%            |
| Audit gap detection accuracy         | 100%            |
| Compliance report generation on schedule | >= 99%       |
| Audit query response time (p95)      | < 2 seconds     |
| SOX audit finding rate (agent-related) | 0             |
