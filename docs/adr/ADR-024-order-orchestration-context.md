# ADR-024: Order orchestration context — placement, ownership and order model

**Status:** Proposed on 2026-10-04. Not accepted.
**Date:** 2026-10-04
**Deciders:** Project owner (pending).
**Amends (when accepted):** [ADR-008](ADR-008-postgres-schema-per-service.md), by adding one schema. [ADR-023](ADR-023-price-ramp-schedule-model.md) D-08's delivery path: after cutover the rate periods and `exitArr` arrive on the `ActivateSubscription` command instead of `QuoteAccepted`. D-08's decision that contract-asset-management hosts the rate schedule is unchanged (D-02).
**Relates to:** [ADR-025](ADR-025-saga-process-manager.md) (saga mechanism), [ADR-026](ADR-026-command-messages.md) (commands), [ADR-027](ADR-027-compensation-and-point-of-no-return.md) (compensation), [ADR-017](ADR-017-synchronous-inter-service-communication.md) (synchronous calls), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (events). Backlog: Notion EPIC BC-08 and stories BC-08-01 to BC-08-05; Linear DRG-290.
**Does not authorize:** any code, schema, service, topic or gateway route. Each needs its own PR after this ADR is accepted.

---

## Context

BC-08 Order Orchestration exists only as requirements. The capability map (Rev 2, §10) names its aggregates (Order, OrderItem, DecompositionRule, FulfillmentRequest, ChangeOrder) and events (OrderSubmitted, OrderDecomposed, FulfillmentDispatched, DeliveryRecorded, ChangeOrderRaised, OrderCancelled). There is no order code in `backend/` or `frontend/`.

What happens today when a quote is accepted:

- `QuoteService.accept` writes `QuoteAccepted` v1 to the outbox. Three consumers act on it independently: contract-asset-management, document-generation and crm-service.
- contract-asset-management creates a PENDING subscription straight from the event (`QuoteAcceptedEventConsumer.java:49-81`). It ignores `dealType`, so an accepted amendment or renewal creates a second subscription. Nothing coordinates the three consumers or undoes anything on failure.
- [ADR-023](ADR-023-price-ramp-schedule-model.md) D-08 (Accepted) extends that same consumer: it will build the subscription's rate periods from the ramp periods on `QuoteAccepted` and use the real term.
- BC-07 Contract has no code. The reserved `ContractExecuted` contract is owned by contract-asset-management and documented as emitted "when CLM activates a billable subscription" (`EventContractRegistry.java:108-110`, `ContractExecutedEvent.java:14`). That is downstream of fulfillment, not an order trigger.
- BC-08-01 and the DRG-290 exit criterion both say fulfillment starts from a contract. Today's code contradicts that, and BC-07's absence makes it impossible to build first.
- A quote can be accepted only once (`QuoteService.java:344-346`). There is no quote version beyond the JPA optimistic lock.
- Ports 8080 to 8090 are taken ([ARCHITECTURE.md](../ARCHITECTURE.md) §4). [ADR-008](ADR-008-postgres-schema-per-service.md) records the landed schemas and says it does not authorize new ones.
- PR #575 (open) adds a per-line snapshot, `lineItems`, to `QuoteAccepted` v1. That is the input decomposition needs.

## Decision

### D-01 — A new service, `order-service`

BC-08 is a new Spring Boot service, `order-service`, on port **8091**, owning one new PostgreSQL schema, **`orders`**. The schema is not called `order`, because `ORDER` is an SQL reserved word and would need quoting everywhere. Accepting this ADR amends ADR-008's schema list by that one entry.

The service owns `Order`, `OrderLine`, `DecompositionRule`, `FulfillmentRequest` and the persisted state of the process managers in [ADR-025](ADR-025-saga-process-manager.md). Two names differ from the capability map, on purpose:

- **`OrderLine`** replaces `OrderItem`, matching `QuoteLineItem` and `lineItems`.
- **A change order is an `Order`** with lines that act on what was delivered (D-07), not a separate `ChangeOrder` aggregate.

### D-02 — The order owns everything that must be delivered

Subscription activation becomes one fulfillment target of an order line. order-service sends it to contract-asset-management (BC-09) as an `ActivateSubscription` command ([ADR-026](ADR-026-command-messages.md)). The command carries what ADR-023 needs: the term, its dates, each line's ramp periods, and `exitArr`. contract-asset-management keeps building the rate periods and keeps hosting the schedule (ADR-023 D-08). Only the trigger changes, from `QuoteAccepted` to the command.

The change is a strangler, switched per environment:

- `opencrm.contract.subscription-source = quote-accepted | order`, default `quote-accepted`.
- Exactly one source is active per environment. Running both would create every subscription twice.
- Until the switch flips, ADR-023's consumer path (sprint R3) is how subscriptions and rate periods are created.
- The switch flips only after order-service runs in that environment and the outbox backlog is triaged ([event runbook](../runbooks/event-operations.md)).

order-service adds one event the capability map does not list, **`OrderFulfilled`**, published when the header becomes `FULFILLED` (D-05). The Quote-to-Revenue process manager waits for it, instead of coordinating BC-08 and BC-09 as siblings as the capability map's Diagram 6 shows.

### D-03 — Two order sources

| Source | When | Idempotency key (D-06) |
|---|---|---|
| Accepted quote on standard terms | No negotiated contract is required | (QUOTE, quote UUID) |
| Activated contract (BC-07) | A BC-04 policy requires a contract for this deal | (CONTRACT, contract id, amendment number) |

- Until BC-07 exists, only the first source exists.
- The contract source listens to a BC-07 event to be defined when BC-07 is built (the capability map names `ContractActivated`). It does not reuse `ContractExecuted`, which keeps its documented meaning.
- This replaces BC-08-01's "never from a quote" and the DRG-290 exit criterion. Their intent, never fulfil an unsigned commitment, is kept: an accepted quote on standard terms is a commitment.

### D-04 — Input comes from the event, not a read

Decomposition reads `QuoteAccepted.lineItems` (PR #575). order-service makes no synchronous call to quote-service to learn what was sold. Under [ADR-017](ADR-017-synchronous-inter-service-communication.md) it starts with no outbound synchronous edges. A payload without `lineItems` (an outbox row written before PR #575) is placed on hold for missing source data, never fulfilled empty.

Such holds are prevented first and repaired second:

- **Prevented.** order-service's consumer group starts at the latest offset when it is first deployed. Quotes accepted before then stay on the legacy subscription path (D-02), which the backlog triage in the [event runbook](../runbooks/event-operations.md) already covers.
- **Repaired.** If a held order lacks lines anyway, quote-service re-emits `QuoteAccepted` for that accepted quote, now carrying `lineItems`, on an operator action. order-service accepts a second payload for an order it already holds only to supply the missing lines, then releases the hold. That keeps D-06's key unchanged and needs no synchronous read.
  - The re-emit goes through `accept`'s existing publish call site, so ADR-018 D9's one-emitter rule still holds.
  - The three existing consumers already deduplicate by quote UUID: document-generation's `quote-<id>-pdf` key, contract-asset-management's `SUB-<id>` check, and crm's claim in `processed_events`. A re-emit creates nothing twice.

### D-05 — Order lifecycle

**Header states:** `SUBMITTED`, `ON_HOLD`, `DECOMPOSED`, `IN_FULFILLMENT`, `NEEDS_ATTENTION`, `FULFILLED`, `CANCELLED`.
**Line states:** `PENDING`, `DISPATCHED`, `DELIVERED`, `FAILED`, `COMPENSATED`, `CANCELLED`.

- **Holds.** A hold can be placed from any open header state and is released back to the state it left. Hold reasons are a closed set: missing source data (D-04), export licence (BC-15-03), credit, unmapped product (BC-08-02), missing provisioning data, manual.
- **One fulfillment saga per line** ([ADR-025](ADR-025-saga-process-manager.md) D-02), so a parked line never stops the others.
- **Parked lines.** The header is `NEEDS_ATTENTION` while any line's saga is parked under [ADR-027](ADR-027-compensation-and-point-of-no-return.md) D-04. It returns to `IN_FULFILLMENT` when the parked step is resolved.
- **Completion.** A line is finished when it is `DELIVERED`, `CANCELLED` or `COMPENSATED`. When every line is finished, the header becomes `FULFILLED` if at least one line is `DELIVERED`, and `CANCELLED` otherwise. `OrderFulfilled` or `OrderCancelled` is published accordingly; never both.
- **Failed lines.** A `FAILED` line is never finished. Its step is retried, compensated or parked.
- **Partial fulfillment** is a line-level fact, not a header state.
- **Point of no return.** Each line has one, defined in [ADR-027](ADR-027-compensation-and-point-of-no-return.md) D-02.

### D-06 — Identity and idempotency

`orderId` is a UUID. `SubmitOrder` is idempotent on the source key in D-03. It is not keyed on contract id alone, which would reject the second order an amendment raises on the same contract.

### D-07 — Change orders act on what was delivered

A change after delivery is a new order whose lines each carry an action against an existing subscription or asset: `ADD`, `MODIFY`, `RENEW`, `CANCEL`, `SUSPEND` or `RESUME`. Quote `DealType` (NEW, AMENDMENT, RENEWAL, EXPANSION) selects the default actions. Revising an order that is still in fulfillment is allowed only for lines before their point of no return. BC-08-04 splits into these two stories.

## Options considered

| Option | Verdict |
|---|---|
| **A. New `order-service`, own schema (chosen)** | Clear ownership of the supporting subdomain and its provider integrations. Con: a twelfth service to deploy and run |
| B. Module inside contract-asset-management | No new service, but it puts every provider integration and the saga state into the subscription context (BC-09, which the backlog classes as Supporting, and Core for SaaS sellers), next to the consumer being replaced |
| C. Inside quote-service | Mixes the seller's aggregate with fulfillment; quote-service already carries pricing, approvals and documents |
| D. Use the ERP's order management | Possible later behind an anti-corruption layer; there is no real ERP integration yet (`ErpAdapter` is simulated) |

## Consequences

- **Positive:** One place answers "is this deal fully provisioned?". Amendments and renewals stop creating duplicate subscriptions once the switch flips. Holds have somewhere to live.
- **Negative:** A new service, schema, gateway route and Flyway history. During the strangler, two code paths can create subscriptions, and only the switch keeps them apart.
- **Downstream:** contract-asset-management gains an `ActivateSubscription` handler and, after cutover, loses its `QuoteAccepted` subscription logic. The ADR-023 rate-period code moves from the consumer to the handler unchanged. CRM and document-generation are unaffected.

## Fitness functions (on acceptance)

- `EventContractRegistry` lists every order **event** with owner `order-service`. Every **command** order-service sends is owned by its target, with order-service as the named producer ([ADR-026](ADR-026-command-messages.md) D-02). `EventContractFitnessTest` enforces both, once extended per ADR-026.
- A test fails if a type named `Order*` is declared outside order-service and `backend/libs/common/.../event/dto`.
- A contract-asset test asserts that exactly one subscription source is active for a given configuration.

## Rollout

1. Accept ADR-024 to ADR-027 and ADR-017 together, or record which wait.
2. Scaffold order-service and the `orders` schema; register the events and commands as RESERVED.
3. SubmitOrder and decomposition from `QuoteAccepted`, with holds; no dispatch yet.
4. Subscription activation as the first fulfillment target, behind the switch, in one environment.
5. Flip the switch per environment; remove the legacy consumer logic after every environment has flipped.

## Open questions

- O-1: BC-12 shares only `AccountId`. Bill-to, ship-to and sold-to roles are needed for invoicing; where they live is undecided.
- O-2: Whether an ERP sales order is a fulfillment target in Phase 3 or waits for a real ERP adapter.
- O-3: The BC-04 policy that decides when a contract is required.
- O-4: `QuoteAccepted` does not say which subscription or asset an AMENDMENT, RENEWAL or EXPANSION acts on; neither does PR #575's line snapshot. D-07 needs that reference on the quote or its lines.

## Evidence (`main` at `1629070`)

- `backend/services/contract-asset-management/src/main/java/com/opencrm/contract/event/QuoteAcceptedEventConsumer.java:49-81` — subscription created from `QuoteAccepted`; `dealType` unused.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteService.java:344-346` — only a SENT quote can be accepted.
- `backend/libs/common/src/main/java/com/opencrm/common/event/EventContractRegistry.java:108-110` and `.../event/dto/ContractExecutedEvent.java:14` — `ContractExecuted` reserved, owned by contract-asset-management.
- `backend/services/quote-service/src/main/java/com/opencrm/quote/quote/DealType.java:4-7` — NEW, AMENDMENT, RENEWAL, EXPANSION.
- `backend/scripts/init-db.sql` — existing schemas; no `orders`.
- [ARCHITECTURE.md](../ARCHITECTURE.md) §4 (lines 104-116) — service table, ports 8080 to 8090. The §3 diagram still shows ai-service on 8086, which is stale.
- [ADR-023](ADR-023-price-ramp-schedule-model.md) D-08 (lines 131-140) — rate schedule hosted in contract-asset-management.
