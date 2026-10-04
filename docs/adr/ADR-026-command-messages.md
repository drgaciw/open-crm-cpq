# ADR-026: Command messages between contexts

**Status:** Proposed on 2026-10-04. Not accepted.
**Date:** 2026-10-04
**Deciders:** Project owner (pending).
**Amends (when accepted):** [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md), by adding a kind and a producer to the registry it made the contract.
**Relates to:** [ADR-025](ADR-025-saga-process-manager.md) (process managers), [ADR-024](ADR-024-order-orchestration-context.md) (order context), [ADR-017](ADR-017-synchronous-inter-service-communication.md) (synchronous calls).
**Does not authorize:** any code, topic or registry entry.

---

## Context

The process managers in [ADR-025](ADR-025-saga-process-manager.md) must tell other contexts to act: activate a subscription, create a billing schedule, identify revenue obligations. Today's messaging has only events.

- `EventContractRegistry` describes each event: type, version, owner, topic, payload class and status, PUBLISHED or RESERVED (`EventContractRegistry.java:38-55`).
- Each topic carries exactly one contract (`EventContractRegistryTest.java:37-45`; ADR-018 D5).
- `EventContractFitnessTest` enforces one publish call site per published event, in its owning service (ADR-018 D9).
- Topic names all start with `cpq.`, but the rest has no single pattern (`KafkaTopics.java:19-40`). Most have four segments, such as `cpq.quote.status.accepted`. Some have three (`cpq.product.updated`), one has five (`cpq.product.bundle.structure.changed`), and the second segment is not always the owner (`cpq.discount.approval.required` is pricing's).

A command is the mirror image of an event: one context sends it and exactly one handles it. Without a convention, commands would be disguised as events, and the one-emitter rule would no longer say who may act on them.

## Decision

### D-01 — Commands are Kafka messages with registered replies

- **Topic.** A command goes to `cpq.<target-context>.command.<verb>`, where the context name supplies the noun. For example, `cpq.subscription.command.activate`.
- **Replies.** Every command has two reply events, owned by the target: one for success and one for rejection. For example, `SubscriptionActivated` on `cpq.subscription.activated` and `SubscriptionActivationRejected` on `cpq.subscription.activation.rejected`.
- **One contract per topic.** Each reply needs its own topic, because ADR-018 allows one contract per topic. A command therefore costs three topics.

### D-02 — The registry gains a kind and a producer

`EventContract` gains `kind` (`EVENT` or `COMMAND`) and, for commands, `producer`. For a `COMMAND` contract:

- the **owner** is the target context, the only one that consumes it;
- the **producer** is the one service whose process manager sends it;
- `EventContractFitnessTest` gains the mirror rule: exactly one `@KafkaListener` on each command topic, in the owner service, and the only publish call site in the producer service.

### D-03 — Envelope and idempotency

- **Envelope.** Commands use the ADR-018 D4 headers unchanged. The outbox generates `eventId` itself (`OutboxEventPublisher.java:61`) and does not return it, so the envelope id is not the command's identity.
- **Payload.** The payload carries `commandId`, generated and recorded by the process manager ([ADR-025](ADR-025-saga-process-manager.md) D-03), plus `sagaId`, `stepId`, `attempt` and a `deadline`. Both reply events echo `commandId`, `sagaId`, `stepId` and `attempt`. ADR-025 D-03 relies on this to claim each reply once. A reply's causation header is the command's envelope `eventId`, set by the existing interceptor.
- **Claim once.** Handlers claim each command once with `KafkaInbox.claimKey(<command type>, <commandId>)`.
- **Business-key idempotency.** Handlers are also idempotent on the business key. Activating a subscription that is already active for the same order line replies success, not an error.

### D-04 — Keys and ordering

The Kafka record key of a command and of both its replies is the **order line id**, the subject the saga acts on. The relay keys each record by the outbox row's aggregate id (`OutboxRelayPublisher.java:195`), so the producer writes the order line id there, and the target copies it from the command into its reply rows. The target aggregate cannot be the key, because `ActivateSubscription` runs before the subscription exists.

This keeps order only per key within one topic and one producer's outbox (ADR-018 D3). The design does not need more: a saga has at most one command in flight per order line and waits for its reply before sending the next ([ADR-025](ADR-025-saga-process-manager.md) D-03). Commands on different topics are never assumed to arrive in order.

### D-05 — HTTP is for reads, never for saga commands

A saga never changes another context's state with a synchronous call. Reads follow [ADR-017](ADR-017-synchronous-inter-service-communication.md).

## Options considered

| Option | Verdict |
|---|---|
| **A. Kafka commands with registered replies (chosen)** | Same delivery guarantees, DLT and tooling as events; the saga survives a target being down. Con: three topics and three contracts per command |
| B. HTTP `/internal/v1` commands | Simple request and reply, but couples the saga's availability to every target's and needs its own retry and idempotency rules |
| C. Events only (choreography) | Rejected in [ADR-025](ADR-025-saga-process-manager.md) D-01 |

## Consequences

- **Positive:** The registry stays the single contract, now for both directions. Retries, DLTs and replay tooling apply to commands as they do to events.
- **Negative:** Topic count grows by three per command. The fitness test grows a second rule. Existing topics keep their irregular names; only new command topics follow D-01.

## Fitness functions (on acceptance)

- `EventContractFitnessTest`: every `COMMAND` contract has exactly one listener, in its owner, and exactly one publish site, in its producer.
- A registry test fails on a command topic that does not match `cpq.<context>.command.<verb>`.
- A registry test fails on a command without both reply contracts.

## Open questions

- O-1: Whether rejection replies need a closed set of reason codes per command, or one shared set.
