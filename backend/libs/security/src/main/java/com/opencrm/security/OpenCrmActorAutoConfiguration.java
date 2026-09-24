package com.opencrm.security;

import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Actors for ADR-016 decision 6: the {@link CurrentActor} every service records actors with, and the JPA auditor, so
 * {@code BaseEntity.createdBy} and {@code updatedBy} hold the verified caller, or the service itself for background
 * work. Before this, every service enabled JPA auditing without an auditor and both columns stayed empty.
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenCrmSecurityProperties.class)
public class OpenCrmActorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CurrentActor openCrmCurrentActor(OpenCrmSecurityProperties properties, Environment environment) {
        boolean dev = OpenCrmSecurityAutoConfiguration.mode(properties, environment) == SecurityMode.DEV;
        return new CurrentActor(CurrentActor.selfActor(environment), dev);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuditorAware.class)
    static class Auditing {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        public AuditorAware<String> openCrmAuditorAware(CurrentActor currentActor) {
            return () -> Optional.of(currentActor.id());
        }
    }
}
