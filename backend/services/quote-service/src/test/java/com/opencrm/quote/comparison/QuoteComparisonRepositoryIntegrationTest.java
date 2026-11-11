package com.opencrm.quote.comparison;

import com.opencrm.common.exception.ResourceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Executes the actual Spring Data queries and service against PostgreSQL; no mocked tenancy filtering. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = QuoteComparisonRepositoryIntegrationTest.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.default_schema=quote",
        "spring.flyway.schemas=quote", "spring.flyway.default-schema=quote", "spring.flyway.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1",
        "spring.autoconfigure.exclude=com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
class QuoteComparisonRepositoryIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration @EntityScan(basePackages = "com.opencrm.quote")
    @EnableJpaRepositories(basePackageClasses = QuoteComparisonRepository.class)
    @Import({QuoteComparisonService.class, QuotePrimarySelectionRepository.class})
    static class PersistenceConfiguration {}

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=quote");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired QuoteComparisonService service;
    @Autowired JdbcTemplate jdbc;
    private String deal;
    private Long anchor;

    @BeforeEach void setup() {
        QuoteComparisonServiceTest.user("workforce");
        deal = UUID.randomUUID().toString();
        anchor = insert("workforce", false);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void actualQueriesExcludeOtherWorkforcesDeletedRowsAndNullLegacyPartition() {
        Long alternative = insert("workforce", false);
        Long foreign = insert("foreign", false);
        Long deleted = insert("workforce", true);
        Long legacy = insert(null, false);
        assertThat(service.compare(anchor).quotes()).extracting(QuoteComparisonDto.Alternative::id)
                .containsExactly(anchor, alternative);
        for (Long inaccessible : new Long[]{foreign, deleted, legacy}) {
            assertThatThrownBy(() -> service.compare(inaccessible)).isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.markPrimary(inaccessible)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test void persistedSelectionIsVisibleFromEveryAlternativeAndDeletedPrimaryIsIgnored() {
        Long alternative = insert("workforce", false);
        assertThat(service.markPrimary(alternative).primaryQuoteId()).isEqualTo(alternative);
        assertThat(service.compare(anchor).primaryQuoteId()).isEqualTo(alternative);
        jdbc.update("update quote.quotes set is_deleted=true where id=?", alternative);
        assertThat(service.compare(anchor).primaryQuoteId()).isNull();
        assertThat(service.markPrimary(anchor).primaryQuoteId()).isEqualTo(anchor);
    }

    @Test void actualMixedCustomerGroupRefusesReadAndWriteWithoutReplacingPreference() {
        assertThat(service.markPrimary(anchor).primaryQuoteId()).isEqualTo(anchor);
        Long inconsistent = insert("workforce", false);
        jdbc.update("update quote.quotes set customer_external_id='another-account' where id=?", inconsistent);
        for (Long id : new Long[]{anchor, inconsistent}) {
            assertThatThrownBy(() -> service.compare(id)).isInstanceOfSatisfying(
                    com.opencrm.common.exception.BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo("QUOTE_COMPARISON_CUSTOMER_MISMATCH"));
            assertThatThrownBy(() -> service.markPrimary(id)).isInstanceOfSatisfying(
                    com.opencrm.common.exception.BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo("QUOTE_COMPARISON_CUSTOMER_MISMATCH"));
        }
        assertThat(jdbc.queryForObject("select selected_quote_id from quote.quote_primary_selections "
                + "where tenant_id='workforce' and deal_external_id=?", Long.class, deal)).isEqualTo(anchor);
    }

    private Long insert(String tenant, boolean deleted) {
        return jdbc.queryForObject("insert into quote.quotes "
                + "(uuid, quote_number, name, customer_external_id, tenant_id, deal_external_id, is_deleted) "
                + "values (?, ?, 'Alternative', 'account', ?, ?, ?) returning id", Long.class,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), tenant, deal, deleted);
    }
}
