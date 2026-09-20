# ADR-010: Kafka integration via transactional outbox and versioned published-language events

**Status:** Accepted *(reverse-engineered from code 2026-09-19 — the outbox + `v1` event contracts landed in shared libs before any ADR recorded them)*
**Date (decision embedded):** ~2026-09 (S2 stacks / Waves F–N) · **Documented:** 2026-09-19
**Deciders:** forge S2 event stacks (de facto) · **Documented by:** architecture reverse-engineering pass
**Provenance:** Reverse-engineered ADR — documents landed event architecture. Does **not** un-hold Kafka S2B-9 (event-driven rule invalidation) or agent Kafka #241.

---

## Context

CPQ workflows span service boundaries (quote accepted → CRM sync; configuration committed; catalog product retired; discount approval required; document jobs). The legacy system was a single-process Frappe app; the microservice split required a durable async mechanism that would not lose events when Kafka or a consumer is down, and would not break consumers when payloads evolve.

Verified on the tree (2026-09-19), `backend/libs/common/src/main/java/com/opencrm/common/event/`:

- **Transactional outbox:** `OutboxEvent` (+`OutboxEventRepository`) persisted in the emitting service's own schema; `OutboxEventPublisher` writes; `OutboxRelayPublisher` relays to Kafka. Unit tests: `OutboxEventPublisherTest`, `OutboxRelayPublisherTest`, `OutboxEventTest`.
- **Canonical topics:** `KafkaTopics` constants + `EventContractRegistry` (with `KafkaTopicsTest`).
- **Published language, versioned:** `event/dto/` domain events (`QuoteAcceptedEvent`, `ConfigurationCommittedEvent`, `ProductCatalogLifecycleEvent`, `ProductRetiredEvent`, `DiscountApprovalRequiredEvent`, `PriceCalculatedEvent`, `ContractExecutedEvent`, `SubscriptionRenewalEligibleEvent`, `QuoteAcceptedCrmMessage`, `ApprovalRejectedEvent`) and immutable **`v1`** copies under `event/dto/v1/` (`PriceCalculatedEventV1`, `QuoteAcceptedEventV1`, `ProductRetiredEventV1`, `DiscountApprovalRequiredEventV1`, `SubscriptionRenewalEligibleEventV1`), guarded by `PublishedEventContractTest` + `PublishedLanguageEventV1Test`.
- **Consumers:** document-generation (`group-id: document-generation`, earliest reset, String codecs); configuration-engine `ProductRetiredEventConsumer`; crm-service consumes quote-accepted (`QuoteAcceptedCrmMessage`). quote-service produces with String serializers.
- **Scope guardrails still held:** S2B-9 event-driven rule invalidation and agent Kafka #241 are **not** implemented; CPQ services needing local boot exclude Azure Event Hubs Kafka OAuth2 autoconfig on Boot 4.

## Decision

**Cross-service integration events are published through a transactional outbox in the emitting service's schema and relayed to canonical Kafka topics. Event payloads are shared DTOs from `libs/common` (`com.opencrm.common.event`), with breaking changes shipped as new versioned types (`*EventV1`) registered in `EventContractRegistry` and pinned by contract tests. Consumers own idempotent application of events; nothing reads another service's database to learn about changes.**

1. Write path: aggregate change + `OutboxEvent` insert in **one local transaction**; relay publishes to `KafkaTopics` constants; no direct producer-in-controller publishes for state-changing events.
2. Payload rules: shared published DTOs only (no JPA entities on the wire — reinforces [ADR-006](ADR-006-entities-never-at-api-boundary.md)); String JSON serialization on both ends.
3. Versioning: additive changes to a live `*EventV1` are forbidden; new contract = new versioned class + registry entry + contract-test update.
4. Consumers must tolerate replay (`auto-offset-reset: earliest` is the default posture).
5. New async integrations (rule invalidation S2B-9, agent events #241) remain **held** until a wave plan un-holds them — the outbox is the required pattern when they land.

## Options considered

| Option | Verdict |
|--------|---------|
| **A. Outbox + versioned published language (landed)** | No dual-write loss; schema evolution safe via `v1` types + contract tests; replayable. Con: relay latency (at-least-once), outbox table housekeeping per schema. |
| B. Direct Kafka publish in request threads | Dual-write anomaly (DB commit succeeds, publish fails) and lost events on broker outage. **Rejected.** |
| C. HTTP-only integration (no events) | Tight coupling + cascading latency; quote→CRM sync and document jobs need durability. Rejected for those flows. |
| D. Schema registry / Avro | Heavier tooling than the tree carries; JSON DTOs + contract tests achieve the goal at this scale. Not chosen. |

## Consequences

- **Positive:** Emitters never lose events to broker downtime; consumers evolve independently; contract tests fail fast on payload drift; topic names centralized.
- **Negative / residual:** At-least-once delivery pushes idempotency onto consumers (document jobs, CRM quote sync); outbox relay lag is visible in eventual consistency; Azure Event-Hubs Kafka autoconfig must stay excluded on Boot 4 until spring-cloud-azure 7.x.
- **Governance:** This ADR does not authorize S2B-9 / #241; those holds persist in [ARCHITECTURE.md §17](../ARCHITECTURE.md).

## Verification evidence (2026-09-19)

- `backend/libs/common/src/main/java/com/opencrm/common/event/*` — outbox quartet, `KafkaTopics`, `EventContractRegistry`, 10 domain DTOs, 5 `v1` DTOs.
- Tests: `OutboxEventPublisherTest`, `OutboxRelayPublisherTest`, `PublishedEventContractTest`, `PublishedLanguageEventV1Test`, `KafkaTopicsTest`.
- `backend/services/document-generation/src/main/resources/application.yml` — Kafka consumer config (group `document-generation`, earliest).
- `backend/services/quote-service/src/main/resources/application.yml` — Kafka producer (String serializers).
- Configuration-engine `ProductRetiredEventConsumer` (GitNexus-indexed; confirmed present in tree).

## References

- [docs/ARCHITECTURE.md §7](../ARCHITECTURE.md) — Event architecture
- [docs/adr/ADR-006-entities-never-at-api-boundary.md](ADR-006-entities-never-at-api-boundary.md) — no entities on the wire
- [docs/adr/ADR-011-shared-maven-libs-published-language.md](ADR-011-shared-maven-libs-published-language.md) — why events live in `libs/common`
