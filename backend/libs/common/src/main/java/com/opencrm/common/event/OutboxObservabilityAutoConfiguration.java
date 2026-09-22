package com.opencrm.common.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Backlog probe shared by the outbox health indicator ({@link OutboxHealthAutoConfiguration}) and metrics
 * ({@link OutboxMetricsAutoConfiguration}); ADR-018 D1, D10. Applies to every service that owns an
 * {@code outbox_events} table, whether or not its relay is enabled: the backlog must be visible before anyone switches
 * the relay on.
 *
 * <p>These are three top-level auto-configurations, each listed in {@code AutoConfiguration.imports}, rather than one
 * with nested {@code @Configuration} classes. Services component-scan {@code com.opencrm.common}; Boot's exclude
 * filter skips listed auto-configurations, but a nested configuration class would be scanned and registered without
 * its enclosing class's conditions.
 */
@AutoConfiguration(after = OutboxRelayAutoConfiguration.class)
@ConditionalOnBean(OutboxEventRepository.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxBacklogProbe outboxBacklogProbe(OutboxEventRepository repository, OutboxProperties properties) {
        return new OutboxBacklogProbe(repository, properties.getHealth().getSnapshotTtl(), Clock.systemUTC());
    }
}
