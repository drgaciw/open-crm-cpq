package com.opencrm.security.event;

import com.opencrm.common.event.EventActorAutoConfiguration;
import com.opencrm.common.event.EventActorResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Registers {@link SecurityContextEventActorResolver} ahead of the service-only fallback in {@code libs/common}, so
 * outbox events carry the authenticated user as their {@code actor} (ADR-018 D4, ADR-016).
 */
@AutoConfiguration(before = EventActorAutoConfiguration.class)
@ConditionalOnClass(SecurityContextHolder.class)
public class SecurityEventActorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventActorResolver.class)
    public EventActorResolver securityContextEventActorResolver(Environment environment) {
        return new SecurityContextEventActorResolver(environment.getProperty("spring.application.name"));
    }
}
