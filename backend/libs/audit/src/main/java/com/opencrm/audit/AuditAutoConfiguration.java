package com.opencrm.audit;

import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wires the audit library into a consuming service without relying on component scanning (same pattern as
 * {@code libs/security}'s {@code OpenCrmSecurityAutoConfiguration}): this class is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so Spring Boot picks
 * it up purely from the {@code audit} dependency being on the classpath. {@code com.opencrm.audit} is never added
 * to a service's own {@code @ComponentScan}/{@code @EntityScan}/{@code @EnableJpaRepositories}
 * {@code basePackages}; this configuration supplies its own {@code @EntityScan}/{@code @EnableJpaRepositories}
 * (Spring Boot merges these across every configuration class in the context, so they add to, not replace, the
 * host service's own) and defines the service beans directly, so a consuming service's main application class
 * needs no changes at all.
 */
@AutoConfiguration
@EntityScan(basePackageClasses = AuditEntry.class)
@EnableJpaRepositories(basePackageClasses = AuditEntryRepository.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditEntryMapper auditEntryMapper() {
        return Mappers.getMapper(AuditEntryMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditTrailService auditTrailService(AuditEntryRepository auditEntryRepository, AuditEntryMapper auditEntryMapper) {
        return new AuditTrailService(auditEntryRepository, auditEntryMapper);
    }
}
