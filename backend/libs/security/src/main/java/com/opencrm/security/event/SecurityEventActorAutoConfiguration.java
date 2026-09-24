package com.opencrm.security.event;

import com.opencrm.common.event.EventActorAutoConfiguration;
import com.opencrm.common.event.EventActorResolver;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.OpenCrmActorAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Registers {@link SecurityContextEventActorResolver} ahead of the service-only fallback in {@code libs/common}, so
 * outbox events carry the verified caller as their {@code actor} (ADR-018 D4, ADR-016).
 */
@AutoConfiguration(before = EventActorAutoConfiguration.class, after = OpenCrmActorAutoConfiguration.class)
@ConditionalOnClass(SecurityContextHolder.class)
public class SecurityEventActorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventActorResolver.class)
    public EventActorResolver securityContextEventActorResolver(CurrentActor currentActor) {
        return new SecurityContextEventActorResolver(currentActor);
    }
}
