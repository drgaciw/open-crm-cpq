package com.opencrm.common.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Registers the transactional-outbox relay ({@link OutboxRelayPublisher}); see ADR-010 and ADR-018 D1.
 *
 * <p>Declared as an auto-configuration ordered after Spring Boot's {@code KafkaAutoConfiguration} (the
 * {@code spring-boot-kafka} module, brought in by {@code spring-boot-starter-kafka}). The bean conditions
 * therefore see both the auto-configured {@link KafkaTemplate} and the {@link OutboxEventRepository} that each
 * service registers through {@code @EnableJpaRepositories}.
 *
 * <p>This does nothing when either is missing: services without {@code spring-kafka} on the classpath (gateway,
 * crm, ai) and services that do not scan the common repositories.
 *
 * <p><b>Off by default</b> ({@value #ENABLED_PROPERTY}{@code =true} turns it on, e.g. env
 * {@code OPENCRM_OUTBOX_RELAY_ENABLED=true}). The relay has never run, so every producer schema holds a backlog of
 * unpublished {@code outbox_events}. Enabling it replays that backlog to Kafka, which consumers act on: document
 * jobs, subscriptions and ERP sync. An unreachable broker also blocks each send for {@code max.block.ms} inside the
 * relay's transaction and scheduler thread. Enable per environment only once a broker is available and the backlog
 * has been triaged; see the ADR-018 rollout.
 *
 * <p>Listed in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. Services
 * that component-scan {@code com.opencrm.common} skip it through {@code @SpringBootApplication}'s
 * auto-configuration exclude filter, so it is registered only once.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = OutboxRelayAutoConfiguration.ENABLED_PROPERTY, havingValue = "true")
public class OutboxRelayAutoConfiguration {

    /** Opt-in switch for the outbox relay. */
    public static final String ENABLED_PROPERTY = "opencrm.outbox.relay.enabled";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({KafkaTemplate.class, OutboxEventRepository.class})
    public OutboxRelayPublisher outboxRelayPublisher(
            OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxRelayPublisher(outboxEventRepository, kafkaTemplate);
    }
}
