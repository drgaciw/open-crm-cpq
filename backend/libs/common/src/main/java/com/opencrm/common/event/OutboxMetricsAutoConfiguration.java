package com.opencrm.common.event;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The {@code opencrm.outbox.*} meters (ADR-018 D10); see {@link OutboxMetrics}. Needs Micrometer. The bean is also the
 * relay's {@link OutboxRelayObserver}, and Boot binds it to the registry as a {@code MeterBinder}.
 */
@AutoConfiguration(after = OutboxObservabilityAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(OutboxBacklogProbe.class)
public class OutboxMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxMetrics outboxMetrics(OutboxBacklogProbe probe) {
        return new OutboxMetrics(probe);
    }
}
