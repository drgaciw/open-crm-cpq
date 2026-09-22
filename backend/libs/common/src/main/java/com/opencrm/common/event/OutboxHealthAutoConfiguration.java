package com.opencrm.common.event;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * The {@code outbox} health indicator (ADR-018 D1, D2); see {@link OutboxHealthIndicator} for its states. Needs
 * {@code spring-boot-health} (brought by the actuator starter). Disable with
 * {@code management.health.outbox.enabled=false}.
 */
@AutoConfiguration(after = OutboxObservabilityAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(OutboxBacklogProbe.class)
@ConditionalOnEnabledHealthIndicator("outbox")
public class OutboxHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "outboxHealthIndicator")
    public OutboxHealthIndicator outboxHealthIndicator(
            OutboxBacklogProbe probe, ObjectProvider<OutboxRelayPublisher> relay, OutboxProperties properties) {
        return new OutboxHealthIndicator(
                probe, () -> relay.getIfAvailable() != null, properties.getHealth().getMaxPendingAge());
    }
}
