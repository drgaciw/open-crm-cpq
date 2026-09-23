package com.opencrm.common.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Registers {@link EventCorrelationRecordInterceptor} (ADR-018 D4). Boot's listener container factory applies a
 * single {@link RecordInterceptor} bean, so a service that defines its own replaces this one and has to carry the
 * correlation itself.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(RecordInterceptor.class)
public class EventCorrelationKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    public EventCorrelationRecordInterceptor eventCorrelationRecordInterceptor() {
        return new EventCorrelationRecordInterceptor();
    }
}
