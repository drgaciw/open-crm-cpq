# DRG-699 — `com.opencrm.common.event` Boot auto-configuration inventory

**Issue:** [DRG-699](https://linear.app/drgamtd-workspace/issue/DRG-699/inventory-and-scope-comopencrmcommon-boot-auto-configuration-per)  
**Trigger:** #712 `CrmCommonBeanWiringTest` went red once #711 / DRG-510 registered event auto-configs in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. #712 narrowed the assertion to the scanned outbox trio and deferred ownership of the `@Bean` methods to this story.

**Scope:** the eight `com.opencrm.common.event` auto-configs listed in DRG-699 (plus `OutboxRelayAutoConfiguration`, which is on the same imports file and must stay off by default). No feature-flag flips. Relay enablement remains an ADR-018 rollout gate.

## How Boot discovers them

Every service that depends on `libs/common` loads the imports file. Component-scan of `com.opencrm.common` does **not** register these classes (`@SpringBootApplication` excludes listed auto-configurations). Conditions on each class decide whether beans appear.

| Auto-configuration | Beans | Primary conditions |
| --- | --- | --- |
| `OutboxObservabilityAutoConfiguration` | `OutboxBacklogProbe` | `@ConditionalOnBean(OutboxEventRepository)` |
| `OutboxHealthAutoConfiguration` | `OutboxHealthIndicator` | backlog probe + `management.health.outbox.enabled` (default on) |
| `OutboxMetricsAutoConfiguration` | `OutboxMetrics` | backlog probe + Micrometer on classpath |
| `EventActorAutoConfiguration` | fallback `EventActorResolver` (`service:<app>`) | `@ConditionalOnMissingBean(EventActorResolver)`; superseded by `libs/security` `SecurityEventActorAutoConfiguration` when security is present |
| `EventCorrelationKafkaAutoConfiguration` | `EventCorrelationRecordInterceptor` | `RecordInterceptor` on classpath; skipped if the service already defines a `RecordInterceptor` |
| `KafkaConsumerErrorHandlingAutoConfiguration` | `DefaultErrorHandler` (DLT) | `KafkaOperations` bean + `opencrm.kafka.consumer.dead-letter.enabled` (default `true`) |
| `DeadLetterMetricsAutoConfiguration` | `DeadLetterMetrics` | `KafkaAdmin` + same dead-letter property |
| `KafkaInboxAutoConfiguration` | `KafkaInbox`, `KafkaInboxPurge` | **opt-in** `opencrm.kafka.inbox.enabled=true` (needs `processed_events`) |
| `OutboxRelayAutoConfiguration` (related, not in the eight) | `OutboxRelayPublisher` (+ producer customizer when class matches) | **opt-in** `opencrm.outbox.relay.enabled=true` + `KafkaTemplate` + `OutboxEventRepository` |

## Per-service decision (APPLY / DO NOT APPLY)

Legend: **APPLY** = conditions should fire in the service’s default runtime config; **DO NOT APPLY** = conditions must stay false or classpath/scan must keep the class inert; **N/A** = common jar not effective for that path (no JPA outbox / no Kafka / no common dependency path that can satisfy conditions).

| Auto-configuration | crm | quote | product | pricing | approval-routing | contract-asset | document-gen | configuration-engine | pricebook | ai | gateway |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `OutboxObservabilityAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY¹ | N/A | N/A |
| `OutboxHealthAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY¹ | N/A | N/A |
| `OutboxMetricsAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY¹ | N/A | N/A |
| `EventActorAutoConfiguration` | SUPERSEDED² | SUPERSEDED² | SUPERSEDED² | SUPERSEDED² | APPLY³ | APPLY³ | APPLY³ | APPLY³ | SUPERSEDED² | SUPERSEDED² | SUPERSEDED² |
| `EventCorrelationKafkaAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | N/A | N/A | N/A |
| `KafkaConsumerErrorHandlingAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | N/A | N/A | N/A |
| `DeadLetterMetricsAutoConfiguration` | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | APPLY | N/A | N/A | N/A |
| `KafkaInboxAutoConfiguration` | APPLY⁴ | APPLY⁴ | DO NOT APPLY⁵ | DO NOT APPLY⁵ | APPLY⁴ | APPLY⁴ | DO NOT APPLY⁵ | DO NOT APPLY⁵ | N/A | N/A | N/A |
| `OutboxRelayAutoConfiguration` | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | DO NOT APPLY⁶ | N/A | N/A |

Notes:

1. **pricebook** scans `com.opencrm.common.event` repositories and has outbox wiring tests, but has no `outbox_events` Flyway migration in-tree yet. Observability applies only once the repository bean is real; keep aligned with pricebook outbox stories — do not enable the relay here.
2. **SUPERSEDED:** `libs/security` is on the classpath and registers `SecurityContextEventActorResolver` ahead of the common fallback. One `EventActorResolver` bean must exist; it must not be the service-only fallback when a verified caller is available.
3. Services that scan `com.opencrm.common` without depending on `security` keep the common fallback (`service:<spring.application.name>`).
4. `opencrm.kafka.inbox.enabled=true` is set in main `application.yml` and a `processed_events` migration exists.
5. No inbox enablement / no `processed_events` table — leave the auto-config inert (do not flip the property in this story).
6. Relay stays **off** (`opencrm.outbox.relay.enabled` unset / false). Enabling it is an ADR-018 environment rollout, not part of DRG-699.

**ai-service / gateway-service:** both depend on `common`, but neither provides `OutboxEventRepository` nor Kafka Boot auto-config. Outbox and Kafka consumer auto-configs stay inert. Actor resolution comes from `libs/security` when that jar is present.

## CRM-owned assertion (widened `#712` test)

`CrmCommonBeanWiringTest` keeps the scanned outbox trio check and adds an allowlist for beans whose factory is a `com.opencrm.common.event.*AutoConfiguration`:

| Expected bean type | Source auto-configuration |
| --- | --- |
| `OutboxBacklogProbe` | `OutboxObservabilityAutoConfiguration` |
| `OutboxHealthIndicator` | `OutboxHealthAutoConfiguration` |
| `OutboxMetrics` | `OutboxMetricsAutoConfiguration` |
| `EventCorrelationRecordInterceptor` | `EventCorrelationKafkaAutoConfiguration` |
| `DefaultErrorHandler` | `KafkaConsumerErrorHandlingAutoConfiguration` |
| `DeadLetterMetrics` | `DeadLetterMetricsAutoConfiguration` |
| `KafkaInbox` | `KafkaInboxAutoConfiguration` |
| `KafkaInboxAutoConfiguration$KafkaInboxPurge` | `KafkaInboxAutoConfiguration` |

Also asserted:

- Exactly one `EventActorResolver` (from security, not a second common fallback).
- No `OutboxRelayPublisher` under the default test profile (relay off).

## Non-goals

- No changes to held stories (Activities, NgRx, dual-read, Jev enablement flags).
- No `opencrm.outbox.relay.enabled=true` in committed CRM defaults.
- No per-service `spring.autoconfigure.exclude` churn in this PR unless a decision above requires it; current conditions already match the table.
