# ADR-018: Event delivery guarantees, event envelope and idempotent consumers

**Status:** Proposed. This is documentation only; it changes no code, YAML or runtime behavior.
**Date:** 2026-09-22
**Deciders:** Pending (project owner). Drafted from the [GitNexus ADR review](../forge/39-adr-gitnexus-review-2026-09-22.md) (findings F2–F5).
**Amends:** [ADR-010](ADR-010-kafka-outbox-events.md). The *direction* stays: transactional outbox → Kafka, with a published language in `libs/common`. On Acceptance, this ADR replaces ADR-010's delivery, versioning and consumer rules and its "emitters never lose events" claim.
**Relates to:**
- [ADR-008](ADR-008-postgres-schema-per-service.md) (outbox and inbox tables per owning schema)
- [ADR-011](ADR-011-shared-maven-libs-published-language.md)
- [ADR-013](ADR-013-quote-bff-approvals-single-writer.md)
- [ADR-015](ADR-015-jev-advisory-decision-support.md) (shadow calls stay after commit)
- [ADR-016](ADR-016-identity-authentication-service-trust.md) (actor and service identity)

**Does not authorize:**
- new topics or integrations (Kafka S2B-9 and agent Kafka #241 stay **held**)
- adding Kafka to `docker-compose.yml` (runtime topology is reserved for ADR-022)
- removing the Redis `quote:accepted` channel before its replacement is proven
- any producer, consumer or schema change

---

## Context

ADR-010 was reverse-engineered on 2026-09-19. It verified that the outbox, topics and `v1` DTOs *exist*. Usage queries (GitNexus plus `rg` on HEAD `4a29d31`) show that the delivery guarantees it states are not delivered:

1. **Kafka is not wired in any service, and the relay is never registered (verified 2026-09-22).** There are two independent layers, and a third effect on consumers:
   - **Layer 1: no Kafka auto-configuration.** On Spring Boot 4, `KafkaAutoConfiguration` lives in the `spring-boot-kafka` module. All 7 Kafka services depend on plain `org.springframework.kafka:spring-kafka` (4.0.5), not `spring-boot-starter-kafka`. `mvnw dependency:tree` shows no `spring-boot-kafka` for quote-service or approval-routing. Boot therefore never creates a `KafkaTemplate`, a `ProducerFactory` or a `kafkaListenerContainerFactory`.
   - **Layer 2: condition ordering.** `OutboxRelayPublisher` is a component-scanned `@Component` guarded by `@ConditionalOnBean(KafkaTemplate.class)`. Spring Boot evaluates bean conditions against definitions processed so far and recommends them only on auto-configuration classes. Even when an auto-configuration *does* supply a `KafkaTemplate`, the scanned relay is skipped.
   - **Consumer impact.** approval-routing, configuration-engine, contract-asset-management and document-generation declare `@EnableKafka` with no container factory of their own. The listener context fails to start with `NoSuchBeanDefinitionException: No bean named 'kafkaListenerContainerFactory'`. This was run for approval-routing; the other three have the same wiring by inspection. It is consistent with the UAT specs recording approval-routing and configuration-engine as "down".
   - **Net effect.** The 7 services that write `outbox_events` (product, quote, pricing, configuration-engine, approval-routing, contract-asset-management, document-generation) persist rows that nothing relays, and the Kafka consumers cannot boot. Kafka is also absent from `backend/docker-compose.yml`, so none of this surfaced end-to-end.
   - **Evidence.** `services/quote-service/src/test/java/com/opencrm/quote/event/OutboxRelayRegistrationContextTest.java`: layer 1 ✖, layer 2 ✖, D1 remedy ✔. `services/approval-routing/src/test/java/com/opencrm/approval/event/KafkaListenerInfrastructureContextTest.java`: ✖. The failing tests are `@Disabled` with the ADR-018 D1 reason so CI stays green; the D1 fix must remove those annotations.
2. **Rows are marked published before the broker acknowledges.** `kafkaTemplate.send(...)` is asynchronous. The relay calls `event.markAsPublished()` right after `send()` returns and saves. A later broker failure is only logged in `whenComplete`, so the event is lost.
3. **No lease, unbounded fetch.** `findUnpublishedEventsWithRetryLimit` loads *every* unpublished row and the loop caps at 100. Nothing locks rows, so several replicas can publish the same rows concurrently. Every replica also runs the 03:00 cleanup.
4. **Per-aggregate ordering is not preserved.** Records are keyed by `aggregateId`, but a failed row is retried on a later tick while newer rows for the same aggregate have already been sent.
5. **No envelope on the wire.**
   - No Kafka headers are sent.
   - The outbox row's UUID (a natural `eventId`) is never transmitted.
   - `OutboxEvent.eventType` actually stores the *topic* name.
   - `QuoteAcceptedEvent` embeds `eventType`, `eventVersion` and a correlation snapshot in its payload. `DiscountApprovalRequiredEvent` and `ProductCatalogLifecycleEvent` carry none of these, so their consumers have no dedupe key.
6. **Versioning is out of step with the wire.**
   - The five `event/dto/v1/*EventV1` classes are narrower subsets. They are reached only through `to…V1()` helpers on their unversioned siblings, and **no producer or consumer uses them**.
   - `EventContractRegistry` lists 9 contracts. **4 have no producer** (`PRICE_CALCULATED`, `CONTRACT_EXECUTED`, `APPROVAL_STEP_DECIDED`, `APPROVAL_OUTCOME_COMPLETED`).
   - **3 published topics have no registry entry** (`PRODUCT_UPDATED`, `BUNDLE_STRUCTURE_CHANGED`, `QUOTE_APPROVAL_REJECTED`).
7. **Consumer idempotency is uneven, and there is no dead-letter path.** No `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` or `ErrorHandlingDeserializer` is configured. Spring Kafka's default handler retries a failing record a bounded number of times, then logs and skips it, so poison messages are silently dropped.

   | Consumer | Topic | Idempotent? |
   |----------|-------|-------------|
   | document-generation `QuoteAcceptedEventConsumer` | `cpq.quote.status.accepted` | ✔ `idempotency_key` UNIQUE + `existsBy…` |
   | contract-asset-management `QuoteAcceptedEventConsumer` | `cpq.quote.status.accepted` | ✖ A redelivery violates `UNIQUE(subscription_number)`, which throws and is then skipped. The ERP sync (an external side effect) runs inside the listener transaction |
   | configuration-engine `ProductRetiredEventConsumer` | `cpq.product.status.retired` | ✔ naturally (cancels `IN_PROGRESS` sessions) |
   | configuration-engine `ProductCatalogLifecycleEventConsumer` (×2) | `product.updated`, `bundle.structure.changed` | ✔ naturally (cache invalidation) |
   | approval-routing `DiscountApprovalEventConsumer` | `cpq.discount.approval.required` | ✖ **inserts a new PENDING approval on every delivery** |
   | crm-service `QuoteAcceptedEventListener` (Redis) | `quote:accepted` | ✔ `SETNX` + TTL, but see 8 |

8. **Quote→CRM runs over Redis Pub/Sub, outside the outbox.**
   - `QuoteService.accept` publishes `QuoteAcceptedCrmMessage` to Redis channel `quote:accepted` **inside the accept transaction**, *before* the outbox write.
   - A Redis error throws `BusinessException` and rolls the acceptance back, so quote acceptance depends on Redis being up.
   - A later rollback leaves CRM holding an acceptance that never committed.
   - Pub/Sub keeps nothing, so a message published while crm-service is down is lost.
   - This is the in-request dual write that ADR-010 rejects (its option B).
9. **There are two emitters for one event type.**
   - `QuoteService.accept` is the controller-wired path. It publishes the rich `QuoteAcceptedKafkaEventFactory` payload with `quoteId` = UUID.
   - `QuoteService.acceptQuote` is not wired. It publishes a 5-field payload with `quoteId` = quote number, on the **same topic**.

## Decision (Proposed)

**D1 — Kafka wiring, relay registration and visibility** (P0, verified defect).
- Replace plain `spring-kafka` with `spring-boot-starter-kafka` in the 7 Kafka services. `libs/common` keeps `spring-kafka` optional.
- Move the relay out of component scanning into an auto-configuration in `libs/common`: `OutboxAutoConfiguration`, `@AutoConfiguration(after = KafkaAutoConfiguration.class)`, registered in `META-INF/spring/…AutoConfiguration.imports`.
- The relay is **opt-in** through `opencrm.outbox.relay.enabled` (default `false`). It has never run, so every producer schema holds a backlog of unpublished rows. Enabling it replays that backlog to consumers (document jobs, subscriptions, ERP sync), so each environment decides first whether to replay, mark or age out the backlog.
- Add an `outbox` health indicator. It reports **DOWN** when unpublished rows older than a threshold exist, or when outbox rows exist but no relay bean does.
- Exclude the outbox packages from `scanBasePackages` so the bean cannot be registered twice.

**D2 — Acknowledge before marking.**
- The relay sends a bounded batch, waits for each future with a timeout, and marks a row published **only on broker ack**. Producer `acks=all` and idempotence are pinned explicitly, not left to client defaults.
- On failure, increment `retry_count`, record the error, and **skip later rows for the same aggregate in that tick** to preserve per-aggregate order.
- Rows that exceed the retry limit go to status `FAILED`, which raises the health alarm. They are never silently abandoned.

**D3 — Lease rows in PostgreSQL.**
- Fetch with `SELECT … FROM outbox_events WHERE published = false AND status <> 'FAILED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT :batch` inside a short transaction, so replicas are safe without a new dependency.
- Cleanup is a single idempotent `DELETE … WHERE published AND published_at < now() - interval '7 days'`.

**D4 — Standard envelope in Kafka headers.** Every record carries:
- `eventId` (the outbox row UUID)
- `eventType` (the registry type, e.g. `QuoteAccepted`)
- `eventVersion`
- `occurredAt`
- `producer` (service name)
- `correlationId` and `causationId`
- `actor` (`user:<sub>` or `service:<client-id>`, per ADR-016)

`OutboxEvent` gains a separate `topic` column (`event_type` then means the registry type), plus `status` and header columns. The Flyway migration is additive in each owning schema (ADR-008). Payloads stay JSON DTOs.

**D5 — The registry is the contract.**
- `EventContractRegistry` maps each topic to (eventType, version, owner, **payload class actually published**).
- A contract test requires every topic used at an `OutboxEventPublisher.publish` call site to have a registry entry, and every entry to have a producer or be explicitly `RESERVED`.
- The `dto/v1` subset classes are **deprecated and removed** once contract tests pin the live payload classes.
- Consumers must tolerate unknown properties, and a test proves it.
- A breaking change means a new `eventVersion` with a dual-publish window. Adding fields is non-breaking.

**D6 — Idempotent consumers (inbox).**
- Every `@KafkaListener` deduplicates on header `eventId`, using a per-schema `processed_events(consumer_group, event_id, processed_at)` row written **in the same transaction** as the side effect.
- A consumer may rely on natural idempotency instead only if its Javadoc states it and a redelivery test proves it (document-generation and CE qualify today).
- External side effects, such as the contract-asset ERP sync, move out of the listener transaction into a follow-up step with its own retry (outbox-driven or scheduled). They must not run inside a transaction that can roll back after the call succeeded.

**D7 — Errors and dead letters.**
- A shared `libs/common` error-handling configuration provides:
  - `ErrorHandlingDeserializer`
  - `DefaultErrorHandler` with bounded exponential backoff
  - `DeadLetterPublishingRecoverer` to `<topic>.DLT`
- Non-retryable exceptions (deserialization, validation) go straight to the DLT.
- DLT depth is alerted on, and a replay runbook is required.

**D8 — Retire the Redis `quote:accepted` channel.**
- crm-service consumes `cpq.quote.status.accepted` from Kafka. Its dedupe key is `eventId`, which replaces today's key built from quote and deal IDs.
- The CRM fields missing from `QuoteAcceptedEvent`, such as `dealExternalId` and ARR/ACV/TCV, are added **additively**.
- Transition: first move the Redis publish to after-commit, and make it log without failing on error. Then run Redis and Kafka side by side with a shared dedupe key. Then remove the Redis publisher and listener.

**D9 — One emitter per event type.** Remove the unwired `QuoteService.acceptQuote` publish path. A GitNexus or ArchUnit check enforces one publishing method per topic per service.

**D10 — Operability.** Micrometer metrics per service:
- outbox backlog count and age
- relay publish failures
- `FAILED` rows
- consumer lag
- DLT depth

Trace propagation through headers is deferred to ADR-020 (reserved).

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Hardened polling outbox + header envelope + inbox + DLT (selected)** | Keeps the landed pattern and libraries. PostgreSQL `SKIP LOCKED` gives safe concurrency with no new infrastructure. Con: polling latency (seconds) and one extra table per consuming schema |
| B. CDC (Debezium) over `outbox_events` | Best ordering, no polling, and relay failures disappear, but it adds Kafka Connect operations before AKS. **Deferred.** The D4 envelope columns are CDC-compatible, so B can replace the relay later |
| C. Kafka transactions for exactly-once | They cannot span PostgreSQL; the outbox dual-write problem remains. **Rejected** |
| D. Keep Redis Pub/Sub for quote→CRM | Lossy, runs before commit, and couples acceptance to Redis availability. **Rejected** |
| E. Schema registry (Avro or JSON Schema) | Heavier than 9 topics need. The registry plus contract tests in D5 cover it. **Deferred** until external consumers exist |

## Scope and boundaries

- **No new topics or integrations.** S2B-9 and #241 stay held. The registry clean-up in D5 only documents or removes contracts; it creates none.
- **Broker hosting is out of scope.** Azure Event Hubs Kafka vs self-managed, and local compose, belong to ADR-022 (reserved). The Boot-4 Event Hubs autoconfig exclusion remains (ADR-010).
- **ADR-013 and ADR-005 are untouched.** Changes to the approval-routing consumer must keep ADR-015's `discountTierShadow.recordAfterCommit` after the commit.
- **Actor values follow ADR-016.** Until ADR-016 lands, `actor` may be `service:<name>`.

## Rollout (after Acceptance and a wave plan)

0. **Verify: done 2026-09-22 and confirmed defective.** See Context 1. D1 is therefore a **P0 fix ahead of everything else**:
   - add `spring-boot-starter-kafka`
   - move the relay into an auto-configuration
   - remove the `@Disabled` annotations in the two context tests
   - confirm all four consumer services boot.
1. **Relay hardening (D1–D3, D10)** in `libs/common`, with additive migrations. No wire change.
2. **Headers (D4).** Consumers read `eventId` when present and fall back to natural keys.
3. **Inbox and DLT (D6, D7).** Start with approval-routing (duplicate PENDING) and contract-asset (ERP side effect).
4. **CRM onto Kafka (D8)** with a dual-run window, then remove Redis. Remove `acceptQuote` (D9).
5. **Registry and contract clean-up (D5)**, then delete the `dto/v1` subset classes.

Each step can be undone by redeploying the previous version. Schema changes are additive only.

## Consequences

- **Positive:**
  - At-least-once delivery that is actually true.
  - Duplicates become harmless.
  - Poison messages become visible instead of vanishing.
  - One quote-accepted channel.
  - The contract registry matches what is on the wire.
  - Backlog and lag become observable.
- **Negative:**
  - One inbox table per consuming schema and header columns on seven outbox tables.
  - The relay waits on broker acks, so throughput per tick is bounded by the batch and timeout (acceptable at CPQ volumes).
  - DLT topics need retention and a replay runbook.
- **Residual:**
  - Polling latency until CDC (option B).
  - No trace context in headers until ADR-020.

## Acceptance and fitness functions

- **Context tests:** `OutboxRelayRegistrationContextTest` (quote-service) and `KafkaListenerInfrastructureContextTest` (approval-routing) pass with no `@Disabled`. Equivalent tests exist in every producer and consumer service.
- **Relay tests (Testcontainers PostgreSQL):**
  - A failing send future leaves the row unpublished.
  - Two concurrent relays never publish the same row in one lease.
  - The rows of one aggregate are published in order when an earlier row fails.
- **Redelivery tests:** delivering the same record twice produces one side effect, for every consumer.
- **Contract test:** the set of registry entries equals the set of topics actually published (or marked `RESERVED`).
- **GitNexus Cypher:**
  - Zero callers of `KafkaTemplate.send` or `RedisTemplate.convertAndSend` for domain events outside the relay.
  - Exactly one publishing method per (service, topic).
- **Health:** the `outbox` indicator is **DOWN** in a test with an aged unpublished row.

## Evidence (HEAD `4a29d31`)

- `backend/libs/common/src/main/java/com/opencrm/common/event/`:
  - `OutboxRelayPublisher.java` (`@Component` + `@ConditionalOnBean(KafkaTemplate.class)`; `send()` then `markAsPublished()`)
  - `OutboxEventRepository.java` (unbounded query)
  - `OutboxEvent.java`
  - `EventContractRegistry.java`
  - `KafkaTopics.java`
  - `dto/`, `dto/v1/`
- The outbox table migrations: `product-service` `V1_3`, `quote-service` `V1_4`, `pricing-service` `V1_3`, and `V1_1` in configuration-engine, approval-routing, contract-asset-management and document-generation.
- Consumers:
  - `backend/services/approval-routing/.../event/DiscountApprovalEventConsumer.java`
  - `backend/services/contract-asset-management/.../event/QuoteAcceptedEventConsumer.java`
  - `backend/services/document-generation/.../event/QuoteAcceptedEventConsumer.java`
  - `backend/services/configuration-engine/.../event/{ProductRetired,ProductCatalogLifecycle}EventConsumer.java`
- Redis: `backend/services/quote-service/.../event/QuoteEventPublisher.java` and `backend/services/crm-service/.../event/{RedisSubscriberConfig,QuoteAcceptedEventListener,QuoteAcceptedIdempotency}.java`.
- Two emitters: `backend/services/quote-service/.../quote/QuoteService.java` (`accept` wired from `QuoteController`; `acceptQuote` unwired).

## Implementation evidence

Branch `fix/adr018-kafka-starter-outbox-relay`, 2026-09-22. It implements part of D1 while the ADR stays **Proposed**.

- **Kafka starter:** all 7 Kafka services now depend on `spring-boot-starter-kafka` instead of plain `spring-kafka`.
- **Relay registration:** `OutboxRelayPublisher` is no longer a scanned `@Component`. `libs/common` registers it through `OutboxRelayAutoConfiguration`, which is `@AutoConfiguration(afterName = …KafkaAutoConfiguration)` and requires `KafkaTemplate`, `OutboxEventRepository` and `opencrm.outbox.relay.enabled=true`.
- **Consumer configuration:** the 4 consumer services get the same Azure auto-configuration excludes and compatibility-verifier switch as the producer services. Their `bootstrap-servers` becomes `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`.
- **Tests:**
  - `OutboxRelayRegistrationContextTest` (quote-service) checks four things: auto-configuration discovery, a single relay when enabled, off by default, and a safe skip without Kafka.
  - `KafkaListenerInfrastructureContextTest` (approval-routing, configuration-engine, contract-asset-management, document-generation) checks that the listener container factory exists and that each consumer is subscribed to its topic.
  - All pass. Unit suites for `libs/*` and the 7 services show no regressions against the pre-change baseline.
- **End-to-end proof (2026-09-22, branch `feat/compose-kafka-cpq-services`, Docker/Testcontainers):**
  - `OutboxRelayKafkaIntegrationTest` (quote-service): a committed outbox row reaches `cpq.quote.status.accepted` on a real `apache/kafka:4.3.1` broker, and the relay marks it published.
  - `QuoteAcceptedKafkaIntegrationTest` (document-generation): the real listener creates exactly one document job, and a redelivery of the same event is ignored.
  - The consumer IT surfaced a **third layer of the D1 defect**. `backend/pom.xml` pinned `kafka.version=3.6.1` against spring-kafka 4.0.5, and every listener container died with `VerifyError` in `KafkaMessageListenerContainer$ListenerConsumer.run()`. The pin is removed, so Boot now manages kafka-clients 4.1.2.
  - Local compose gains Kafka and the four consumers under `--profile events`.
- **Not done:**
  - the rest of D1 (the `outbox` health indicator) and D2–D10
  - running the full compose stack together; the end-to-end proof above uses Testcontainers, one service at a time

## Sources

- Spring Boot 4.0.3 reference, [*Developing Auto-configuration → Condition Annotations → Bean Conditions*](https://github.com/spring-projects/spring-boot/blob/v4.0.3/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/features/developing-auto-configuration.adoc). Bean conditions "are evaluated based on what has been processed so far". It is recommended to use them only on auto-configuration classes, "since these are guaranteed to load after any user-defined bean definitions have been added." Checked 2026-09-22.
- Spring for Apache Kafka reference: `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` and `ErrorHandlingDeserializer`.
