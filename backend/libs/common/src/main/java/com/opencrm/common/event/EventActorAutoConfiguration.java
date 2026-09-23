package com.opencrm.common.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * The fallback {@link EventActorResolver} (ADR-018 D4): every event is attributed to the service itself,
 * {@code service:<spring.application.name>}. {@code libs/security} registers a resolver that reports the
 * authenticated user instead, ordered before this one.
 */
@AutoConfiguration
public class EventActorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventActorResolver.class)
    public EventActorResolver serviceEventActorResolver(Environment environment) {
        String actor = EventActorResolver.serviceActor(environment.getProperty("spring.application.name"));
        return () -> actor;
    }
}
