# Agent PRD: cpq-agent-037

## Header

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| **Agent ID**      | cpq-agent-037                                  |
| **Name**          | Deal Desk Workflow Orchestration Agent          |
| **Group**         | Integration & Operations                       |
| **Primary Service** | quote-service (8084), pricing-service (8082), crm-service (8081) |
| **Autonomy Level** | Semi-autonomous                               |
| **Dependencies**  | cpq-agent-024 (Multi-Level Approval Routing Agent), cpq-agent-025 (Exception Handling Agent), cpq-agent-031 (Quote Document Generation Agent) |

---

## Purpose

The Deal Desk Workflow Orchestration Agent is the PRIMARY ORCHESTRATOR for complex deal workflows that span multiple agents, services, and approval chains. It coordinates the end-to-end deal desk process from the moment a quote is submitted for review through final approval, document generation, and handoff to contract management. Unlike individual agents that focus on specific capabilities (pricing, discounting, margin enforcement), this agent manages the sequencing, parallelization, and error handling of multi-agent workflows, ensuring that each step completes successfully before triggering the next.

The agent operates at a Semi-autonomous level: it automatically orchestrates standard deal desk workflows (submit → price review → margin check → approval → document generation) but requires human intervention for non-standard workflows such as split deals, multi-entity quotes, custom approval chains, and deals requiring legal review. The agent maintains a workflow state machine for each deal, enabling pause/resume capabilities, audit trail of all workflow transitions, and real-time status visibility for all stakeholders (sales rep, deal desk analyst, approver, management).

---

## Key Skills / Capabilities

- **Workflow Orchestration**: Manages the sequencing of agent invocations across the deal lifecycle. Coordinates cpq-agent-011 (margin enforcement), cpq-agent-024 (approval routing), cpq-agent-025 (exception handling), and cpq-agent-031 (document generation) in the correct order with proper error handling and rollback capabilities.

- **State Machine Management**: Maintains a persistent state machine for each deal workflow via quote-service state tracking. Supports states: SUBMITTED, PRICING_REVIEW, MARGIN_CHECK, PENDING_APPROVAL, EXCEPTION_REVIEW, APPROVED, DOCUMENT_GENERATION, READY_FOR_SIGNATURE, COMPLETED, REJECTED, CANCELLED. Handles transitions, guards, and side effects.

- **Parallel Workflow Coordination**: When a deal requires simultaneous actions (e.g., pricing review AND legal review), coordinates parallel workflow branches and merges results before proceeding. Handles partial completion, timeout, and conflict resolution across parallel branches.

- **Real-Time Status Broadcasting**: Publishes workflow status updates to all stakeholders in real-time via the `quote:status-changed` Redis channel. Provides a unified view of where a deal stands across all workflow steps.

- **Error Recovery and Retry**: When an agent invocation fails within a workflow, implements configurable retry logic (with exponential backoff), alternative path routing, and graceful degradation. Logs all errors and recovery actions for audit.

- **SLA Monitoring**: Tracks elapsed time at each workflow stage against configured SLAs. Triggers escalation workflows when stages approach or exceed their SLA deadlines.

---

## Inputs

| Input                        | Type              | Source                                          |
|------------------------------|-------------------|-------------------------------------------------|
| Quote ID                     | String (UUID)     | Quote submission trigger                         |
| Workflow Type                | String (Enum)     | STANDARD, EXPEDITED, CUSTOM                      |
| Deal ID                      | String (UUID)     | CRM deal context                                 |
| Submitter                    | User context      | Sales rep / deal desk analyst initiating workflow |
| Priority                     | String (Enum)     | NORMAL, HIGH, URGENT                             |
| Custom Workflow Steps        | JSON array        | Optional override of standard workflow sequence  |

---

## Outputs

| Output                        | Format                  | Human-in-the-Loop Checkpoint                     |
|-------------------------------|-------------------------|--------------------------------------------------|
| Workflow Instance             | JSON object (workflowId, state, steps, progress) | Real-time visibility for all stakeholders |
| Status Updates                | Event stream (Redis)    | Published to `quote:status-changed`              |
| Step Completion Results       | JSON per step           | Logged; errors trigger human intervention         |
| Final Outcome                 | Enum (APPROVED, REJECTED, CANCELLED) | Terminal state notification to all stakeholders |
| Workflow Audit Trail          | JSON array of transitions | Complete history for compliance                 |

---

## Typical Workflow

1. **Receive Submission**: Quote submitted for deal desk review via `POST /api/v1/quotes/{quoteId}/submit-for-review`. Creates workflow instance.

2. **Pricing Review**: Invokes pricing agents (cpq-agent-004 through 010) to validate pricing accuracy and completeness. Captures pricing summary.

3. **Margin Check**: Invokes cpq-agent-011 (Minimum Margin Enforcement) to validate margins. If margins are violated, routes to cpq-agent-024.

4. **Approval Routing**: Invokes cpq-agent-024 to determine and create the required approval workflow. Waits for approvals.

5. **Exception Handling**: If approvals require exceptions, invokes cpq-agent-025 to manage the exception process. Waits for exception resolution.

6. **Document Generation**: Upon full approval, invokes cpq-agent-031 to generate the quote document.

7. **Handoff to Contract**: Publishes `quote:accepted` event to trigger cpq-agent-030 (Contract Lifecycle Management) for contract creation.

8. **Complete Workflow**: Marks workflow as COMPLETED and notifies all stakeholders.

---

## Edge Cases & Exceptions

- **Workflow Timeout**: When a workflow has been in a non-terminal state for longer than the configured maximum duration (default 30 days), the agent marks it as STALE and notifies the deal desk manager for manual resolution.

- **Concurrent Modifications**: When the source quote is modified while a workflow is in progress, the agent pauses the workflow, validates whether the changes affect the current stage, and either resumes or restarts from the affected stage.

- **Agent Unavailability**: When a downstream agent is unavailable, the orchestrator retries with exponential backoff (3 attempts) before routing the workflow to manual processing.

- **Split Deal**: When a deal needs to be split into multiple quotes with separate approval chains, the orchestrator creates child workflows and manages them as a coordinated set, blocking the parent workflow completion until all children complete.

---

## Success Metrics

| Metric                              | Target          |
|--------------------------------------|-----------------|
| End-to-end workflow completion rate  | >= 95%          |
| Average workflow cycle time          | < 48 hours      |
| SLA compliance across workflow stages | >= 90%         |
| Workflow status visibility (real-time updates) | 100% |
| Error recovery success rate          | >= 90%          |
