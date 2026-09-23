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
  - *Amended 2026-09-22:* while the relay is disabled the indicator reports **UNKNOWN**, with the backlog in its details, not DOWN. The relay is off everywhere until each environment has triaged its backlog, and every compose healthcheck reads `/actuator/health`, so DOWN would mark every producer container unhealthy. UNKNOWN never lowers the aggregate status. With the relay running, DOWN applies as written, plus any `FAILED` row. Revisit when the relay is on by default.
- Exclude the outbox packages from `scanBasePackages` so the bean cannot be registered twice.

**D2 — Acknowledge before marking.**
- The relay sends a bounded batch, waits for each future with a timeout, and marks a row published **only on broker ack**. Producer `acks=all` and idempotence are pinned explicitly, not left to client defaults.
- On failure, increment `retry_count`, record the error, and **skip later rows for the same aggregate in that tick** to preserve per-aggregate order.
- Rows that exceed the retry limit go to status `FAILED`, which raises the health alarm. They are never silently abandoned.

**D3 — Lease rows in PostgreSQL.**
- Fetch with `SELECT … FROM outbox_events WHERE published = false AND status <> 'FAILED' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT :batch` inside a short transaction, so replicas are safe without a new dependency.
- Cleanup is a single idempotent `DELETE … WHERE published AND published_at < now() - interval '7 days'`.
- *Refined during implementation (2026-09-22):*
  - `SKIP LOCKED` alone prevents double sends but not reordering. With two replicas, one can lease row 2 of an aggregate while the other still holds row 1.
  - The lease therefore takes only the **oldest unpublished row of each aggregate**: `NOT EXISTS` an older row of the same `(aggregate_type, aggregate_id)` whose status is not `PUBLISHED`.
  - Per-aggregate order then holds across failures, ticks and replicas, and a `FAILED` row blocks its aggregate until it is requeued.
  - This replaces D2's "skip later rows for the same aggregate in that tick". A tick keeps leasing while leases make progress, so a burst for one aggregate still drains in one tick.

**D4 — Standard envelope in Kafka headers.** Every record carries:
- `eventId` (the outbox row UUID)
- `eventType` (the registry type, e.g. `QuoteAccepted`)
- `eventVersion`
- `occurredAt`
- `producer` (service name)
- `correlationId` and `causationId`
- `actor` (`user:<sub>` or `service:<client-id>`, per ADR-016)

`OutboxEvent` gains a separate `topic` column (`event_type` then means the registry type), plus `status` and header columns. The Flyway migration is additive in each owning schema (ADR-008). Payloads stay JSON DTOs.
- *Refined during implementation (2026-09-22):*
  - **The `event_type` switch is split into expand and contract steps.** Changing what an existing column means in one release breaks this ADR's rule that every step can be undone by redeploying the previous version: a relay from before D4 would send new rows to a topic called `QuoteAccepted`.
    - The expand step adds `topic` and the header columns. It keeps writing the topic into `event_type`, and the relay reads `topic`.
    - The contract step switches `event_type` to the registry type once every environment runs the expand step.
  - **The `eventType` header comes from the registry**, looked up by topic. D5 makes topic-to-contract one-to-one and pins it by test.
  - **`correlationId` is always present.** An event that nothing correlated starts its own chain, correlated by its own `eventId`. A Kafka listener continues the chain of the record it is handling, and that record's `eventId` becomes the causation. A producer that already has a business correlation, such as a quote's UUID, opens a correlation scope around its publish.
  - **`actor` is `user:<sub>` for an authenticated principal and `service:<spring.application.name>` otherwise.** `service:<client-id>` for service principals waits for ADR-016.

**D5 — The registry is the contract.**
- `EventContractRegistry` maps each topic to (eventType, version, owner, **payload class actually published**).
- A contract test requires every topic used at an `OutboxEventPublisher.publish` call site to have a registry entry, and every entry to have a producer or be explicitly `RESERVED`.
- The `dto/v1` subset classes are **deprecated and removed** once contract tests pin the live payload classes.
- Consumers must tolerate unknown properties, and a test proves it.
- A breaking change means a new `eventVersion` with a dual-publish window. Adding fields is non-breaking.
- *Refined during implementation (2026-09-22):* the registry is also enforced at the publishing API. `OutboxEventPublisher.publish` takes an `EventContract`, not a topic string, so an unregistered topic does not compile. The publisher refuses a `RESERVED` contract or a payload of the wrong class.

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
- *Refined during implementation (2026-09-22):* the shared dedupe key is the **quote UUID**, not `eventId`. The Redis message carries no `eventId`, so an `eventId` key cannot dedupe across the two channels during the dual run. A quote is accepted once, so its UUID identifies the acceptance on both. The claim lives in crm's `processed_events`, written in the deal transaction (D6), and replaces the Redis `SETNX` key.

**D9 — One emitter per event type.** Remove the unwired `QuoteService.acceptQuote` publish path. A GitNexus or ArchUnit check enforces one publishing method per topic per service.
- *Refined during implementation (2026-09-22):* the check is a source-scanning unit test, `EventContractFitnessTest`, not GitNexus or ArchUnit.
  - ArchUnit works on bytecode. It sees that a method calls `publish`, but not which contract is passed to a given call, and topic strings are inlined as compile-time constants.
  - GitNexus is not in the build, and its index proved unreliable (review doc 39, §7).
  - The rule is also stricter: **one publish call site per contract, in the service that owns it.** A single call site implies a single publishing method.

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
  - *Implemented 2026-09-22* as `EventContractFitnessTest`, a unit-gate source scan, not a GitNexus query. See the D9 refinement.
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
- **Not done at that point:**
  - the rest of D1 (the `outbox` health indicator) and D2–D10 (D2, D3, the health indicator and part of D10 followed, see below)
  - running the full compose stack together; the end-to-end proof above uses Testcontainers, one service at a time

### Relay hardening: D2, D3, D1 health, D10 metrics (Rollout step 1)

Branch `feat/adr018-d2-d3-outbox-ack-lease`, 2026-09-22. All of it lives in `libs/common`, plus additive migrations; the wire format does not change. The ADR stays **Proposed**.

- **Ack before mark (D2).**
  - A lease's records are sent together and awaited against one deadline (`ack-timeout`, default 10 s).
  - A row is marked `PUBLISHED` only when its future completed. A failed future, a timeout or a synchronous send error leaves it unpublished, records the error, and schedules a retry at `next_attempt_at`, backing off 5 s → 5 min.
  - After `max-attempts` (10) the row becomes `FAILED`.
  - A synchronous send failure stops the lease, so the untouched rows are not charged an attempt.
  - When the relay is enabled, the producer is pinned through Boot's `DefaultKafkaProducerFactoryCustomizer`: `acks=all`, `enable.idempotence=true`, and `max.block.ms` = `ack-timeout`.
- **Leasing (D3).**
  - `OutboxEventRepository.leaseDueBatch` is a native `FOR UPDATE OF e SKIP LOCKED` query and leases only the oldest unpublished row per aggregate (see the D3 refinement).
  - Each lease is its own transaction and holds row locks for at most `ack-timeout`.
  - A tick keeps leasing while leases make progress, up to `max-drain-iterations` (10).
  - Cleanup is one `DELETE`.
- **Schema.**
  - One migration per owning schema: product `V1_5`, quote `V1_6`, pricing `V4` (also covers pricebook, which shares the `pricing` schema), configuration `V1_2`, approval `V1_4`, contract `V1_2`, document `V1_2`.
  - Each adds `status` (`PENDING|PUBLISHED|FAILED`, check constraint, backfilled from `published`), `next_attempt_at`, and two partial indexes.
  - `published` is kept in step for existing readers.
- **Health (D1).**
  - The `outbox` indicator states are set out in the D1 amendment.
  - Snapshots are cached for `snapshot-ttl` (10 s).
  - `management.health.outbox.enabled=false` switches it off.
- **Metrics (D10).**
  - Gauges: `opencrm.outbox.pending`, `opencrm.outbox.failed` and `opencrm.outbox.oldest.pending.age` (seconds).
  - Counter: `opencrm.outbox.relay.attempts{result=published|failed|exhausted}`.
  - Consumer lag and DLT depth wait for D7.
- **Auto-configuration.** There are four top-level auto-configurations:
  - `OutboxRelayAutoConfiguration`
  - `OutboxObservabilityAutoConfiguration` (the backlog probe)
  - `OutboxHealthAutoConfiguration`
  - `OutboxMetricsAutoConfiguration`

  They are separate, rather than nested `@Configuration` classes, because services component-scan `com.opencrm.common`. The scan would register a nested configuration without its enclosing class's conditions. `OutboxRelayRegistrationContextTest` caught exactly that.
- **Settings.**
  - `opencrm.outbox.relay.*`: `enabled`, `poll-interval` (ISO-8601), `batch-size`, `ack-timeout`, `max-attempts`, `initial-backoff`, `max-backoff`, `max-drain-iterations`, `retention`, `cleanup-cron`.
  - `opencrm.outbox.health.*`: `max-pending-age`, `snapshot-ttl`.
- **Tests (red first).**
  - The old unit test pinned the bug: it asserted that a failed send was still marked published. Flipping that assertion failed on the old relay with `expected: <false> but was: <true>`.
  - New unit tests:
    - 11 relay tests (`OutboxRelayPublisherTest`)
    - 4 new entity tests in `OutboxEventTest`
    - 4 health tests
    - 1 metrics test
    - 4 auto-configuration tests (pinned producer settings, observability without the relay, observer wiring, indicator switch)
  - `OutboxRelayLeasingIntegrationTest` (quote-service, Testcontainers PostgreSQL, real Flyway migrations) covers the ADR acceptance items:
    - two concurrent leases are disjoint and do not wait on each other;
    - only the head of each aggregate is leased;
    - a `FAILED` row blocks its aggregate only;
    - backoff is honoured;
    - a failing send future leaves the row unpublished in the database;
    - an aggregate's rows are published in order after a failed first attempt;
    - the indicator is DOWN with an aged pending row and UNKNOWN in the service, where the relay is disabled.
  - `OutboxRelayKafkaIntegrationTest` still passes against a real broker with the new relay. It now closes its context after the class, so its scheduled relay cannot touch later tests' rows.
- **Operations.**
  - To requeue after fixing the cause: `UPDATE outbox_events SET status = 'PENDING', retry_count = 0, next_attempt_at = NULL WHERE status = 'FAILED' AND …`.
  - A record whose acknowledgement timed out can still reach the broker and later be sent again, so duplicates remain possible. D6 (inbox) is what makes them harmless.
  - Each environment still has to decide on its backlog before enabling the relay.
- **Not done:** D4–D9, consumer lag and DLT metrics (D7), and a per-environment backlog decision.

### Consumers: D6 idempotency, D7 dead letters, first D4 headers (Rollout steps 2–3)

Branch `feat/adr018-d6-d7-idempotent-consumers-dlt`, 2026-09-22. The ADR stays **Proposed**.

- **Envelope headers (D4, partial).**
  - The relay now sends each record with three headers, `eventId`, `occurredAt` and `producer` (`EventHeaders`). `eventId` is the outbox row UUID, so it is identical on every re-send.
  - Topic, key and payload are unchanged.
  - Not yet sent: `eventType`, `eventVersion`, `correlationId`, `causationId` and `actor`. They need registry and outbox columns.
- **Inbox (D6).**
  - `KafkaInbox` in `libs/common` adds the dedupe claim: `INSERT … ON CONFLICT DO NOTHING` into the schema's `processed_events(consumer_group, event_id)`.
    - The claim is written in the listener's transaction, so it commits or rolls back with the side effect. It refuses to run without a transaction.
    - The key is the `eventId` header, or `topic-partition@offset` for records without one.
  - It is plain JDBC rather than a JPA entity. Every service scans `com.opencrm.common` with `ddl-auto: validate`, and only consuming schemas have the table.
  - It is opt-in with `opencrm.kafka.inbox.enabled`, and a daily job purges claims older than 30 days.
  - Migrations: approval `V1_5` and contract `V1_3`.
- **Consumers.**

  | Consumer | Before | Now |
  |----------|--------|-----|
  | approval-routing `DiscountApprovalEventConsumer` | new PENDING approval on every delivery | Inbox check, plus a natural-key backstop: an identical PENDING DISCOUNT request for the same quote line makes the event a duplicate. Different figures still create a request; whether they should supersede the open one is a business decision, not made here. |
  | contract-asset `QuoteAcceptedEventConsumer` | `UNIQUE(subscription_number)` violation on redelivery; ERP call inside the listener transaction | Inbox check, plus an `existsBySubscriptionNumber` backstop. The subscription is created with `erp_sync_status = QUEUED`, and the new `ErpSyncJob` handles the ERP step (see the next row). |
  | contract-asset `ErpSyncJob` (new) | n/a | Claims each `QUEUED` row with a compare-and-set to `IN_PROGRESS`, calls the ERP with no transaction open, then records the outcome: success → `SYNCED` and activated; rate limit → back to `QUEUED`; any other failure → `FAILED`. A stale `IN_PROGRESS` claim is requeued after 10 minutes. API-created subscriptions (`PENDING`) are untouched. |
  | document-generation `QuoteAcceptedEventConsumer` | idempotent by key; generation kicked off *inside* the transaction on an `@Async` thread, which could run before the commit and leave the job PENDING | Still idempotent by `idempotency_key`. Generation now starts after the commit. |
  | configuration-engine consumers (×3) | idempotent by nature, undocumented | Idempotence is now documented and proven by a redelivery test. |

- **Dead letters (D7).**
  - `KafkaConsumerErrorHandlingAutoConfiguration` provides a `DefaultErrorHandler` that Boot's listener container factory picks up:
    - it retries with exponential backoff (4 retries, 1 s doubling to 10 s; `opencrm.kafka.consumer.retry.*`);
    - then `DeadLetterPublishingRecoverer` publishes to `<topic>.DLT`, on the partition the key hashes to.
  - `NonRetryableEventException` and Jackson parse errors go to the DLT at once. All five listeners now throw `NonRetryableEventException` for unreadable or incomplete payloads.
  - It is on by default; `opencrm.kafka.consumer.dead-letter.enabled=false` turns it off.
  - There is no `ErrorHandlingDeserializer`, because listeners receive raw strings and parse them themselves.
- **Tests.**
  - Unit: inbox and error-handler wiring (6), the relay headers test, approval consumer (14), contract consumer (4), `ErpSyncJob` (5), document consumer (3), CE redelivery (2).
  - Testcontainers, real broker and PostgreSQL:
    - `DiscountApprovalKafkaIntegrationTest`: a second delivery with the same `eventId` gives one approval and one inbox row. An identical event without `eventId` is caught by the backstop.
    - `QuoteAcceptedSubscriptionKafkaIntegrationTest`: three deliveries give one subscription, which `ErpSyncJob` marks `SYNCED` and `ACTIVE`.
    - `QuoteAcceptedKafkaIntegrationTest`: the job now reaches `COMPLETED`. A poison record lands on `cpq.quote.status.accepted.DLT` with its origin and exception headers, and the next record on the same partition is still processed.
    - `OutboxRelayKafkaIntegrationTest`: the record's `eventId` equals the outbox row UUID.
- **Operations.**
  - DLT topics need retention and a replay runbook, and their depth needs an alert. DLT depth and consumer lag metrics are still open.
  - The ERP side must upsert by subscription number, because a sync repeated after a crash is possible.
- **Not done:** the rest of D4, D5, D8 (CRM onto Kafka), D9, DLT and lag metrics, and the per-environment backlog decision.

### CRM onto Kafka: D8 dual run and D9 single emitter (Rollout step 4)

Branch `feat/adr018-d8-crm-quote-accepted-kafka`, 2026-09-22. This covers D8 transition steps 1 and 2 and all of D9's code change. The ADR stays **Proposed**.

- **Redis publish after commit (D8 step 1).**
  - `QuoteEventPublisher.publishQuoteAccepted` registers a `TransactionSynchronization` and publishes in `afterCommit`. With no transaction active it publishes at once.
  - A serialization or Redis failure is logged, never thrown. Quote acceptance no longer depends on Redis, and a rolled-back acceptance never reaches CRM.
  - The outbox write in `QuoteService.accept` is unchanged. It is the reliable channel.
- **Contract additions (additive, D5).**
  - `QuoteAcceptedEvent` gains `deal { dealExternalId, dealType }` and `pricing.arr`, `pricing.tcv`, `pricing.acv`. `QuoteAcceptedKafkaEventFactory` fills them.
  - The event and its seven snapshot types are now `@JsonIgnoreProperties(ignoreUnknown = true)`. A consumer built against an older copy of the DTO therefore reads a newer payload even with a strict mapper. `QuoteAcceptedEventToleranceTest` proves it, and failed with `UnrecognizedPropertyException` before the annotation.
- **CRM Kafka listener (D8 step 2).**
  - crm-service gets `spring-boot-starter-kafka` and the Event Hubs auto-configuration exclude.
  - `QuoteAcceptedKafkaListener` consumes `cpq.quote.status.accepted` as group `crm-service`. It maps the event onto `QuoteAcceptedCrmMessage` and hands it to the same `QuoteAcceptedDealProcessor` the Redis listener uses.
  - It is **off by default**: `opencrm.crm.quote-accepted.kafka.enabled`, or `CRM_QUOTE_ACCEPTED_KAFKA_ENABLED` in compose. Turn it on with quote-service's relay; without the relay the topic stays empty.
  - An unreadable payload, or one without `quoteId`, throws `NonRetryableEventException` and goes to `cpq.quote.status.accepted.DLT` (D7). A quote with no deal is skipped.
- **Shared dedupe (D6 applied to D8; see the D8 refinement).**
  - `QuoteAcceptedDealProcessor` claims `processed_events('crm-quote-accepted', <quote UUID>)` through the new `KafkaInbox.claimKey(namespace, key)`, inside the deal transaction. `claim(group, record)` now delegates to it. If there is no UUID, the key falls back to `<deal>:<quoteNumber>`.
  - The namespace is `crm-quote-accepted`, not the Kafka group, because both channels share it.
  - A claim is written only when the deal exists. A missing deal leaves nothing claimed, so a redelivery can still apply it.
  - The Redis `SET NX` keys (`crm:quote-accepted:processed:*`) are gone. They lived outside the deal transaction and were lost with Redis.
  - crm migration `V5` creates `processed_events`. `opencrm.kafka.inbox.enabled` is on in crm, so the daily purge also runs there.
- **One emitter (D9).**
  - `QuoteService.acceptQuote(Long)` is removed. It had no callers: `rg` found none, and GitNexus `impact` agreed once the index had been rebuilt with `--force`.
  - `QuoteService.accept` is the only publisher of `cpq.quote.status.accepted`.
  - Not done: the GitNexus or ArchUnit fitness check (one publishing method per service and topic).
- **Tests.**
  - Unit:
    - `QuoteEventPublisherTest` (2): publishes after commit and never on rollback; a Redis failure is swallowed.
    - `QuoteAcceptedKafkaEventFactoryTest` (+1): deal and ARR/TCV/ACV.
    - `QuoteAcceptedEventToleranceTest` (1).
    - crm: `QuoteAcceptedKafkaListenerTest` (3), `QuoteAcceptedDealProcessorTest` (3), `QuoteAcceptedEventListenerTest` (3) and `QuoteAcceptedIdempotencyTest` (3), all rewritten for the inbox.
  - Testcontainers:
    - `QuoteAcceptedRedisIntegrationTest` now asserts `processed_events` rows instead of Redis keys.
    - The new `QuoteAcceptedDualRunIntegrationTest` runs PostgreSQL, Redis and `apache/kafka:4.3.1`. A Kafka-only acceptance marks the deal WON. The same acceptance sent on Redis and then Kafka is applied once, with one claim.
- **Operations.**
  - Enable per environment in order: quote-service's relay first (after the backlog decision), then `CRM_QUOTE_ACCEPTED_KAFKA_ENABLED=true`.
  - The CRM group starts at `earliest`, so it reads every acceptance the topic retains. An acceptance applied through Redis *after* this change is a no-op on Kafka, because of the shared claim. One whose Redis message was dropped marks its deal WON, which is the intended catch-up.
  - **Backlog caveat.** Acceptances applied through Redis *before* this change have no claim; their keys were in Redis. The relay's first run publishes the whole outbox backlog, so each historic acceptance would set WON, `wonAt`, amount and currency on its deal again, overwriting any later manual edit. Each environment's backlog decision therefore has to cover crm. There are three options:
    - Set the `crm-service` group's offsets to the end of the topic (`kafka-consumer-groups --reset-offsets --to-latest`) once the backlog has drained, and before enabling crm's listener. Redis covers the gap during the dual run, and the other consumers' replay stays a separate choice.
    - Mark the historic rows published, which also withholds them from document-generation and contract-asset.
    - Accept the re-application.
  - document-generation, contract-asset and crm all consume this topic and share its `.DLT`. A replay must filter on `kafka_dlt-original-consumer-group`, and the runbook has to say so.
- **Not done:**
  - D8 step 3: remove `QuoteEventPublisher`, `QuoteAcceptedEventListener` and `RedisSubscriberConfig` once each environment has proven the dual run.
  - The D9 fitness check.
  - The rest of D4, D5, DLT and lag metrics, and the per-environment backlog decision.

### The registry as the contract: D5 and the D9 check (Rollout step 5)

Branch `feat/adr018-d5-registry-contract-d9-fitness`, 2026-09-22. The ADR stays **Proposed**. The wire format does not change.

- **Registry (D5).** `EventContract` gains `payloadClass` and `status` (`PUBLISHED` or `RESERVED`). New lookups: `all()` and `forTopic()`.
  - The three published topics with no entry (Context 6) are now registered:
    - `ProductUpdated` and `BundleStructureChanged`: product-service, payload `ProductCatalogLifecycleEvent`.
    - `QuoteApprovalRejected`: approval-routing, payload `ApprovalRejectedEvent`.
  - The four entries with no producer are now `RESERVED`: `PriceCalculated`, `ContractExecuted`, `ApprovalStepDecided` and `ApprovalOutcomeCompleted`. The last two have no payload class yet.
  - That gives 8 `PUBLISHED` and 4 `RESERVED` contracts.
- **Publishing goes through the registry.** See the D5 refinement.
  - `OutboxEventPublisher.publish(aggregateType, aggregateId, EventContract, payload)` replaces the overload that took a topic string. The old overload is removed.
  - All 8 call sites pass their registry constant.
  - Storage is unchanged: `outbox_events.event_type` still holds the topic until D4 adds a `topic` column.
- **Fitness functions (D5, D9).** `EventContractFitnessTest` in `libs/common` runs in the unit gate and scans `services/*/src/main/java`. It checks that:
  - every `PUBLISHED` contract has exactly one publish call site, in its owning service;
  - a `RESERVED` contract has none;
  - each call names exactly one registry constant directly, with no static import and no variable;
  - no service references `KafkaTemplate`;
  - Redis `convertAndSend` appears only in `QuoteEventPublisher`, allowlisted for the D8 dual run. The test fails once the allowlisted file is gone, so D8 step 3 has to remove the entry.

  The test was red first: against the old call sites it reported all 8 as naming no contract. For publishing, it replaces the GitNexus Cypher items under "Acceptance and fitness functions" (see the D9 refinement).
- **Consumers tolerate unknown properties (D5).**
  - Every registered payload class, and every published-language type reachable through its fields, is `@JsonIgnoreProperties(ignoreUnknown = true)`. That is 20 types.
  - `PublishedPayloadToleranceTest` reads a JSON object with an unknown field into each type under a strict mapper. All 20 failed before the annotation was added.
- **`dto/v1` removed (D5).**
  - Deleted: the five `*EventV1` subset classes, the `toPublishedLanguageV1()` helpers and their test. Nothing produced or consumed them.
  - `PriceCalculatedEvent`'s line type moves to `PriceCalculatedEvent.PriceCalculatedLine`, with the same JSON shape.
- **Tests.**
  - `EventContractRegistryTest` (7). It checks that `all()` covers every constant, that there is one contract per topic, that type plus version is unique, and that every topic is a `KafkaTopics` constant. It also pins each contract's topic, type, owner and status.
  - `OutboxEventPublisherTest` (+3): a `RESERVED` contract, a payload of the wrong class, and a null payload are all refused.
  - The fitness test (5) and the tolerance test (40).
  - Six service tests now verify the contract instead of the topic string.
  - `OutboxRelayKafkaIntegrationTest` (quote) and `PriceBookOutboxWiringTest` now publish real contracts with real payloads, not ad-hoc maps.
- **Not done:**
  - The rest of D4. The `eventType`, `eventVersion`, `correlationId`, `causationId` and `actor` headers and the `topic` column still need the additive outbox migrations. The publisher already has the contract, so `eventType` and `eventVersion` need only those columns.
  - `KafkaTopics` still has 15 constants with no producer and no registry entry, such as `cpq.quote.created` and the AMCP mesh topics. They are names, not contracts; pruning them is housekeeping outside D5.
  - DLT and lag metrics, the per-environment backlog decision, and D8 step 3.

### The full envelope: D4 expand step, plus `KafkaTopics` clean-up

Branch `feat/adr018-d4-envelope-headers-topic-column`, 2026-09-22. The ADR stays **Proposed**. Payloads, topics and keys do not change; records gain headers.

- **Schema.** One migration per owning schema: product `V1_6`, quote `V1_7`, pricing `V5`, configuration `V1_3`, approval `V1_6`, contract `V1_4`, document `V1_3`.
  - Each adds `topic`, `event_version`, `correlation_id`, `causation_id` and `actor` to `outbox_events`.
  - The columns are nullable with no default, so PostgreSQL adds them without rewriting the table, and code from before D4 keeps inserting.
  - There is no backfill. The relay handles older rows.
- **Publisher.**
  - Each row now records the topic, the contract version, the correlation and causation, and the actor.
  - `event_type` still holds the topic; see the D4 refinement.
  - The event id is assigned before the save, so an event outside any correlation scope is correlated by its own id.
- **Relay.**
  - It sends to `topic`, falling back to `event_type` for an older row.
  - It adds five headers: `eventType` and `eventVersion` (from the registry by topic, the row's version first), `correlationId` (the row's, or its `eventId`), `causationId` and `actor`.
  - A header with no value is left out. A topic with no registry entry is still sent, without type headers.
- **Correlation.**
  - `EventCorrelation` is a thread-bound scope that nests and restores the previous scope when closed.
  - `EventCorrelationRecordInterceptor` opens one around every Kafka listener call: `correlationId` is the record's correlation (or its `eventId`), and `causationId` is its `eventId`. It closes after the record, whether the listener succeeded or failed.
  - Boot's listener container factory applies the interceptor. `EventEnvelopeAutoConfigurationTest` proves that on the factory Boot builds.
  - A service that defines its own `RecordInterceptor` replaces it.
  - quote-service (`QuoteAccepted`, correlated by the quote UUID) and configuration-engine (`ConfigurationCommitted`) publish inside a scope taken from their payload's correlation snapshot, so the headers match the payload.
- **Actor.**
  - `EventActorResolver` lives in `libs/common`. Its fallback, `EventActorAutoConfiguration`, answers `service:<spring.application.name>`.
  - `libs/security` adds `SecurityEventActorAutoConfiguration` (its first auto-configuration), ordered before the fallback. Its resolver answers `user:<sub>` from `SecurityUtils.getCurrentUser()`, which already ignores anonymous callers, and the service otherwise.
- **`KafkaTopics` clean-up.**
  - The 15 constants with no producer, no consumer, no reference and no contract are removed. Examples are `cpq.quote.created`, `cpq.approval.approved` and the six AMCP event mesh topics.
  - `KafkaTopics` now matches the registry one-to-one (12 topics). `EventContractRegistryTest` enforces this in both directions, so a topic name cannot come back without a `PUBLISHED` or `RESERVED` contract.
  - `product-config-mgmt.xml` still names planned topics such as `cpq.product.created` in prose. They become `RESERVED` contracts when their wave is planned; held work such as agent Kafka #241 is unaffected.
- **Tests.**
  - `EventCorrelationTest` (6): nesting, blank ids, the interceptor continuing a chain, an older record correlated by its `eventId`, no envelope, and a stale scope replaced rather than stacked.
  - `EventEnvelopeAutoConfigurationTest` (4) and `SecurityContextEventActorResolverTest` (4): JWT user, other principal, anonymous or no caller, and resolver precedence.
  - `OutboxEventPublisherTest` (+2) and `OutboxRelayPublisherTest` (+2, and the older-row case extended).
  - `ConfigurationSessionServiceTest` (+1): the scope matches the payload snapshot and is closed afterwards.
  - `OutboxRelayKafkaIntegrationTest` now asserts all eight headers on a real broker, and the row's `topic` and `event_version` after the real Flyway migration.
  - `ServiceSchemaIsolationIntegrationTest` pins one more migration each for configuration, approval, document and contract. It also proves that a raw insert without the new columns, which is what code from before D4 does, still works.
  - The approval, document and contract consumer ITs pass with the correlation interceptor active on real listener containers.
- **Operations.**
  - **Contract step (later).** Once every environment runs this release, switch `event_type` to the registry type: new rows get `contract.eventType()`, and `UPDATE outbox_events SET event_type = <type for topic> WHERE topic IS NOT NULL` fixes the rest. After that, rolling back past this release needs the reverse update, `SET event_type = topic`, before a relay is enabled.
  - Consumers can now log and trace by `correlationId`. Putting it into MDC and tracing is ADR-020.
- **Not done:** the D4 contract step, the service-principal `actor` (ADR-016), DLT and lag metrics, the per-environment backlog decision, and D8 step 3.

### Close-out: D10 metrics, D7 dead-letter triage, backlog triage, dual-run evidence

Branch `feat/adr018-close-out-metrics-dlt-replay-runbooks`, 2026-09-23. The operating procedure is [`docs/runbooks/event-operations.md`](../runbooks/event-operations.md).

- **Consumer lag (D10).** Nothing to build. Spring Boot's `KafkaMetricsAutoConfiguration` already attaches Micrometer listeners to every consumer factory, so `kafka.consumer.fetch.manager.records.lag.max` exists in every consumer. `DeadLetterTriageIntegrationTest` asserts that the meter is present.
- **Dead letters (D10).** `DeadLetterCountingRecoverer` wraps the D7 `DeadLetterPublishingRecoverer`. It counts `opencrm.kafka.consumer.dead.letters{topic,group,exception}`, using the listener's own exception, only after the DLT publish succeeded. If the publish throws, nothing is counted and the error handler retries.
- **DLT depth (D7, D10).** The consumer group `opencrm-dlt-triage` holds one triage cursor per DLT.
  - `opencrm.kafka.dlt.depth{topic}` is the end offset minus that cursor (or minus the start offset where there is no cursor). It is registered for each topic the service's listener containers subscribe to, once the application is ready.
  - Reads are cached (`opencrm.kafka.consumer.dead-letter.depth.cache-ttl`, 30 s) and bounded by a 5 s broker timeout.
  - Depth drops to zero after replay or skip, so "depth > 0 for 24 h" is the alert. The runbook defines it.
- **Replay (D7).**
  - `DeadLetterTriage` uses only `kafka-clients` and implements `depth`, `peek`, `replay` and `skip`. `DeadLetterTool` is a command-line wrapper that runs as an admin process inside any consumer image, through Boot's `PropertiesLauncher`. It adds no HTTP endpoint, which matters while ADR-016 is open.
  - Replay re-publishes to the original topic with the original key, value and envelope headers, minus `kafka_dlt-*`, and moves the cursor only after the broker acknowledges.
  - *Refinement:* replay does **not** filter on `kafka_dlt-original-consumer-group`, although the D6/D7 section said a replay "must". Every consumer is idempotent (D6), so a group that already processed the event ignores the replayed copy, and only the group that failed acts on it. The shared DLT needs no per-group handling.
- **Backlog triage (Rollout, D2).**
  - `OutboxStatus.SKIPPED` is a terminal state that an operator sets with the runbook SQL. It never goes to Kafka, no longer blocks its aggregate, and ages out with the published rows. Its `error_message` records why.
  - `FAILED` rows can now be given up, not only requeued.
  - Migrations widen `ck_outbox_events_status` in all seven schemas: product `V1_7`, quote `V1_8`, pricing `V6`, configuration `V1_4`, approval `V1_7`, contract `V1_5`, document `V1_4`. Each adds the constraint `NOT VALID` and then validates it, so no long exclusive lock is held.
  - The lease query now also treats `SKIPPED` as done. Two `<>` conditions keep the existing partial index usable.
- **Dual-run evidence (D8).**
  - `opencrm.crm.quote.accepted{channel=redis|kafka, outcome=applied|duplicate|deal-not-found|no-deal|failed}` is recorded by both CRM listeners after the deal transaction commits. `QuoteAcceptedDealProcessor` now returns a `QuoteAcceptedOutcome` instead of a boolean.
  - The runbook's proposed exit criterion for D8 step 3: over 7 consecutive days, Kafka's count equals Redis's and no crm dead letters appear.
- **Tests.**
  - `DeadLetterSupportTest` (6): the counter, including no count on a failed publish; header names equal to Spring Kafka's; replay headers; the origin fallback; the command line.
  - `KafkaConsumerSupportTest` (+1): the metrics bean follows the error handler's switch.
  - `DeadLetterTriageIntegrationTest` (document-generation, real broker):
    - a poison record is counted and gives depth 1;
    - `peek` names the group and exception, and `skip` takes depth to 0;
    - a fixed failure is dry-run, replayed and processed by the real listener, keeping its `eventId` and dropping `kafka_dlt-*`.
  - `OutboxRelayLeasingIntegrationTest` (+1): the runbook's `SKIPPED` statement, verbatim, unblocks a `FAILED` aggregate, is never leased, and ages out.
  - `QuoteAcceptedDualRunIntegrationTest`: the same acceptance on Redis then Kafka counts `redis/applied` once and `kafka/duplicate` once.
  - The CRM listener tests assert the outcome counters.
- **Not done:** live enablement in a shared environment. It follows the runbook's §2 and needs the owner decisions below. A full local compose run was not attempted: the workstation had about 0.5 GB of free memory, and every hop is already proven against a real broker by the ITs above.

## Acceptance checklist (2026-09-23)

Every decision is implemented. Accepting the ADR is the owner's call. The items below are what remains.

| Decision | State | Evidence |
|----------|-------|----------|
| D1 Kafka wiring, relay registration, health | Done | Context tests in all consumers; `OutboxRelayKafkaIntegrationTest`; `outbox` indicator (UNKNOWN while the relay is disabled) |
| D2 Ack before mark, bounded retry, `FAILED` | Done, plus `SKIPPED` | `OutboxRelayPublisherTest`, `OutboxRelayLeasingIntegrationTest` |
| D3 `SKIP LOCKED` leasing, per-aggregate order | Done (refined: oldest row per aggregate) | `OutboxRelayLeasingIntegrationTest` |
| D4 Envelope headers | Expand step done (all 8 headers). The contract step (`event_type` holds the registry type) waits for all environments | `OutboxRelayKafkaIntegrationTest`, `EventCorrelationTest` |
| D5 Registry is the contract | Done | `EventContractRegistryTest`, `EventContractFitnessTest`, `PublishedPayloadToleranceTest` |
| D6 Idempotent consumers | Done | Redelivery ITs for approval, contract, document and crm; CE redelivery test |
| D7 DLT, runbook | Done | `DeadLetterTriageIntegrationTest`, runbook §5 |
| D8 Retire Redis | Steps 1–2 done; step 3 waits for the dual-run exit criterion | `QuoteAcceptedDualRunIntegrationTest`, runbook §6 |
| D9 One emitter | Done | `EventContractFitnessTest` |
| D10 Metrics | Done | Runbook §1 |

**Owner decisions still open.** Each is listed in runbook §7:

- DLT retention and alert routing;
- the per-environment backlog decision;
- the dual-run window and exit criterion;
- the discount supersede rule;
- ERP upsert by subscription number;
- accepting this ADR.

## Sources

- Spring Boot 4.0.3 reference, [*Developing Auto-configuration → Condition Annotations → Bean Conditions*](https://github.com/spring-projects/spring-boot/blob/v4.0.3/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/features/developing-auto-configuration.adoc). Bean conditions "are evaluated based on what has been processed so far". It is recommended to use them only on auto-configuration classes, "since these are guaranteed to load after any user-defined bean definitions have been added." Checked 2026-09-22.
- Spring for Apache Kafka reference: `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` and `ErrorHandlingDeserializer`.
