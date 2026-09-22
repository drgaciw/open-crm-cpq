package com.opencrm.common.event;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;

/**
 * Registers the transactional-outbox relay ({@link OutboxRelayPublisher}); see ADR-010 and ADR-018 D1–D3.
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
 * jobs, subscriptions and ERP sync. Enable per environment only once a broker is available and the backlog has been
 * triaged; see the ADR-018 rollout. The other {@code opencrm.outbox.relay.*} settings are in
 * {@link OutboxProperties.Relay}.
 *
 * <p>Listed in {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}. Services
 * that component-scan {@code com.opencrm.common} skip it through {@code @SpringBootApplication}'s
 * auto-configuration exclude filter, so it is registered only once.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass({KafkaTemplate.class, DefaultKafkaProducerFactoryCustomizer.class})
@ConditionalOnProperty(name = OutboxRelayAutoConfiguration.ENABLED_PROPERTY, havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxRelayAutoConfiguration {

    /** Opt-in switch for the outbox relay. */
    public static final String ENABLED_PROPERTY = "opencrm.outbox.relay.enabled";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({KafkaTemplate.class, OutboxEventRepository.class})
    public OutboxRelayPublisher outboxRelayPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            OutboxProperties properties,
            ObjectProvider<OutboxRelayObserver> observer,
            Environment environment) {
        return new OutboxRelayPublisher(
                outboxEventRepository,
                kafkaTemplate,
                new TransactionTemplate(transactionManager),
                properties.getRelay(),
                observer.getIfUnique(() -> OutboxRelayObserver.NOOP),
                Clock.systemUTC(),
                environment.getProperty("spring.application.name"));
    }

    /**
     * ADR-018 D2: pin the producer's delivery settings instead of relying on client defaults. {@code acks=all} and
     * idempotence make an acknowledgement mean "replicated, no duplicate from producer retries". {@code max.block.ms}
     * is capped at the relay's ack timeout, so an unreachable broker cannot block a send (and the lease holding row
     * locks) for the client default of 60 seconds.
     *
     * <p>A {@code @Bean} method, not a nested {@code @Configuration}: services component-scan {@code com.opencrm.common},
     * and a nested configuration class would be picked up by that scan without this class's conditions.
     */
    @Bean
    DefaultKafkaProducerFactoryCustomizer outboxRelayProducerDeliverySettings(OutboxProperties properties) {
        long maxBlockMs = properties.getRelay().getAckTimeout().toMillis();
        return producerFactory -> producerFactory.updateConfigs(Map.of(
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs));
    }
}
