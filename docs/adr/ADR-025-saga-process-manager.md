# ADR-025: Saga mechanism — an in-house persisted process manager

**Status:** Accepted on 2026-10-17. The project owner told, on 2026-10-17 at 7:31 AM CT, as relayed by the Projects Manager bot, "yes to accepting ADR-017, ADR-024 to 027 and DRG-533 (#644); authorized opening their status-change PRs". The owner's merge of this PR is the confirming act and the status takes effect on merge. This supersedes the "retain their existing statuses" wording of PRs #662 and #663 only for ADR-025.
**Date:** 2026-10-04
**Deciders:** Project owner (accepted).
**Relates to:** [ADR-024](ADR-024-order-orchestration-context.md) (order context), [ADR-026](ADR-026-command-messages.md) (commands), [ADR-027](ADR-027-compensation-and-point-of-no-return.md) (compensation), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (outbox, inbox, envelope), ADR-020 (observability, reserved). Backlog: Notion EPIC SAGA and SAGA-01 to SAGA-03, BC-08-03; Linear DRG-289, DRG-290.
**Supersedes (when accepted):** the "Saga Orchestration (Temporal)" phase of `cpq-microservice-ddd-analysis.xml` (lines 128-132).
**Does not authorize:** any code, schema, dependency or topic.

---

## Context

Two long-running processes are in the backlog:

- **Quote-to-Revenue (SAGA-01):** react to `QuoteAccepted` and drive contract, order, subscription, billing and revenue recognition, with a tested compensation for every step (SAGA-02) and a view of stuck sagas (SAGA-03).
- **Fulfillment (BC-08-03):** dispatch each order line's fulfillment requests, with persisted, resumable state and a compensation per request.

As the backlog draws them, both would compensate the same subscription. [ADR-024](ADR-024-order-orchestration-context.md) D-02 removes that overlap by making subscription activation a fulfillment target.

The platform under a saga is already on `main` ([ADR-018](ADR-018-event-delivery-guarantees-idempotency.md)):

- a transactional outbox whose relay leases rows with `FOR UPDATE SKIP LOCKED` and keeps per-aggregate order (ADR-018 D3, line 81);
- `KafkaInbox.claim` and `claimKey` for claim-once consumption (`KafkaInbox.java:42,54`);
- dead-letter topics with a replay and skip tool (`DeadLetterTool.java:23-24`);
- correlation and causation headers, set from each consumed record and stamped on anything published while handling it (`EventCorrelationRecordInterceptor.java:30-32`);
- per-service outbox, relay and DLT metrics (ADR-018 D10).

The relay is off by default and has never run (`OutboxRelayAutoConfiguration.java:33`). An older plan, `cpq-microservice-ddd-analysis.xml` lines 128-132, specifies Temporal. No Temporal dependency or server exists, and ADR-018 deferred a comparable infrastructure addition (Debezium, line 155) until after AKS.

## Decision

### D-01 — Orchestration, not choreography

Both processes are orchestrated by a process manager that owns the sequence, deadlines and compensations. Participants react to commands and publish events. They do not react to each other's events to drive the process.

### D-02 — Two process managers, split by scope

| Process manager | Owns | Starts on | Ends on |
|---|---|---|---|
| Quote-to-Revenue (one saga per accepted quote) | contract (when policy requires), SubmitOrder, and per line: billing schedule and revenue obligations | `QuoteAccepted` | `OrderFulfilled` and every delivered line's billing and revenue steps done, or `OrderCancelled` |
| Fulfillment (one saga per order line) | dispatch the line to each target, wait for delivery evidence, publish `DeliveryRecorded` | `OrderDecomposed`, one saga for each line | that line `DELIVERED`, `CANCELLED` or `COMPENSATED` |

- **Per-line fulfillment.** One parked line never stops the others, and the order header is derived from its lines ([ADR-024](ADR-024-order-orchestration-context.md) D-05).

- **Billing starts per line.** Quote-to-Revenue reacts to each line's `DeliveryRecorded` by sending that line's `CreateBillingSchedule` command and, after its reply, the line's `IdentifyObligations` command. They are sequential because D-03 allows one command in flight per line. A line delivered on day 3 starts billing on day 3, not when the last line lands.
- **Placement.** Both run in order-service, in separate tables. The backlog classes the Quote-to-Revenue saga as Core while BC-08 is Supporting. Hosting a Core process in a Supporting service is a deployment convenience that avoids a thirteenth service. ADR-024 option B rejected a different thing: putting every provider integration into the subscription context. Moving Quote-to-Revenue out later only means moving its tables and its consumer group.

### D-03 — Persisted state machine on PostgreSQL

Each saga is a row in `saga_instance`: id, type, correlation id, state, current step, current attempt, version, deadline, timestamps. Every step attempt and every manual action is appended to `saga_step_log`: step, command id, attempt, outcome, error, and for manual actions the actor and a reason ([ADR-027](ADR-027-compensation-and-point-of-no-return.md) D-04).

- **One transaction per change.** A state change and the commands it issues are written together: the state row and the outbox rows. The process manager generates each `commandId` and records it in the same transaction ([ADR-026](ADR-026-command-messages.md) D-03). A crash before commit sends nothing. After commit the relay delivers at least once, and handlers deduplicate.
- **Replies are claimed once.** Replies echo `sagaId`, `stepId`, `attempt` and `commandId`. They are consumed through `KafkaInbox.claimKey` on (saga id, step, attempt), so a redelivered reply cannot advance a saga twice.
- **One command in flight per line.** A saga sends an order line's next command only after the reply to the previous one, so no step depends on message order across topics ([ADR-026](ADR-026-command-messages.md) D-04).
- **Stale replies are ignored.** A reply to an earlier attempt has a different key, so the inbox accepts it; the process manager then compares its `attempt` with the saga's current attempt and ignores it.
- **Optimistic concurrency** on `version`. A lost update retries from the reloaded state.

### D-04 — Correlation

The correlation id is the quote UUID, which `QuoteAccepted` already carries and the interceptor already propagates. Every command carries `sagaId` and `stepId`, and its causation id is the event that triggered it.

Commands sent outside a Kafka listener (by the deadline sweeper or an operations action) have no consumed record to inherit from. Without a scope the outbox would start a new chain with the command's own event id. The process manager therefore opens an `EventCorrelation` scope with the saga's correlation id before it publishes, as `QuoteService.accept` does today (`QuoteService.java:364-365`).

One accepted quote is one Quote-to-Revenue saga. An amendment is a new quote and so a new saga. It links to the order it changes through [ADR-024](ADR-024-order-orchestration-context.md) D-07, once the quote carries a reference to what it amends (ADR-024 O-4).

### D-05 — Deadlines and sweeping

Each step has a deadline. A scheduler sweeps due steps with `FOR UPDATE SKIP LOCKED`, the same leasing as the outbox relay, and applies [ADR-027](ADR-027-compensation-and-point-of-no-return.md): retry, compensate, or park for a person. Sweeper lag, saga age by state and parked count are metrics with alerts, following ADR-018 D10.

### D-06 — Temporal is deferred, with triggers to revisit

Revisit an engine (Temporal or similar) when any one of these becomes true:

- more than four saga types;
- timers longer than 30 days are needed;
- human tasks with their own UI and assignment are needed;
- the hand-written state machines need more than one change a sprint.

### D-07 — Prerequisite

No saga runs in an environment until the outbox relay runs there and its backlog is triaged, as the [event runbook](../runbooks/event-operations.md) describes. A saga whose commands sit unpublished in the outbox looks stuck, not failed.

## Options considered

| Option | Verdict |
|---|---|
| **A. In-house process manager on PostgreSQL plus the outbox and inbox (chosen)** | Reuses everything ADR-018 built and tested; no new infrastructure. Con: state machines and timers are hand-written |
| B. Temporal | Durable timers, replay and visibility out of the box. Con: a new server and SDK to operate before AKS; the team has no runbook for it |
| C. Spring State Machine | A library, not durable execution; still needs the persistence and outbox wiring of A |
| D. Choreography only | No owner of the sequence or the compensations, which is the failure mode today |
| E. BPMN engine (Camunda or similar) | Strong for human tasks, heavy for machine-to-machine steps |

## Consequences

- **Positive:** One owner per process. Saga state is queryable with SQL for SAGA-03. No new runtime dependency.
- **Negative:** Saga correctness rests on tests (SAGA-02 fault injection), not an engine's guarantees. A timer sweep adds load proportional to open sagas.
- **Follow-up:** ADR-020 (observability, reserved) should carry trace context through saga commands.

## Fitness functions (on acceptance)

- Every step type declares a compensation class from [ADR-027](ADR-027-compensation-and-point-of-no-return.md); a test fails on an undeclared step.
- SAGA-02: a fault-injection test per step proves the declared behavior.
- A test fails if `saga_instance` or `saga_step_log` appears outside the `orders` schema.

## Open questions

- O-1: Retention for completed sagas and step logs, and whether BC-20 needs a copy.
- O-2: Whether `saga_step_log` is the BC-20 audit trail for manual actions or feeds it. BC-20 has no code yet.
