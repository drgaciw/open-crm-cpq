package com.opencrm.pricebook;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises real Hibernate JPQL, including every book-scoped lookup shadowed by getPriceBookId(). */
@Tag("integration")
@Testcontainers
@Transactional
@SpringBootTest(classes = PriceBookEntryRepositoryIntegrationTest.RepositoryConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.autoconfigure.exclude="
                + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
class PriceBookEntryRepositoryIntegrationTest {
    // Not @SpringBootConfiguration: that marker is what an implicit @SpringBootTest lookup finds.
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = PriceBook.class)
    @EnableJpaRepositories(basePackageClasses = PriceBookEntryRepository.class)
    @EnableJpaAuditing
    static class RepositoryConfiguration {}

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=pricing");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PriceBookRepository books;
    @Autowired PriceBookEntryRepository entries;

    @Test
    void everyScopedLookupUsesPersistedBookRelationAndExcludesOtherBooksAndSoftDeletes() {
        PriceBook first = books.saveAndFlush(PriceBook.builder().name("First").build());
        PriceBook second = books.saveAndFlush(PriceBook.builder().name("Second").build());
        PriceBookEntry active = entries.saveAndFlush(entry(first, "shared", false));
        PriceBookEntry extra = entries.saveAndFlush(entry(first, "extra", false));
        PriceBookEntry deleted = entries.saveAndFlush(entry(first, "deleted", true));
        entries.saveAndFlush(entry(second, "shared", false));

        assertThat(entries.findByPriceBookIdAndProductExternalIdAndDeletedFalse(first.getId(), "shared"))
                .get().extracting(PriceBookEntry::getId).isEqualTo(active.getId());
        assertThat(entries.findByPriceBookIdAndProductExternalIdAndDeletedFalse(first.getId(), "missing")).isEmpty();
        assertThat(entries.findByPriceBookIdAndProductExternalIdAndDeletedFalse(first.getId(), "deleted")).isEmpty();
        assertThat(entries.findByIdAndPriceBookIdAndDeletedFalse(active.getId(), first.getId())).isPresent();
        assertThat(entries.findByIdAndPriceBookIdAndDeletedFalse(active.getId(), second.getId())).isEmpty();
        assertThat(entries.findByIdAndPriceBookIdAndDeletedFalse(deleted.getId(), first.getId())).isEmpty();
        assertThat(entries.findByPriceBookIdAndDeletedFalse(first.getId())).extracting(PriceBookEntry::getId)
                .containsExactlyInAnyOrder(active.getId(), extra.getId());
        var page = entries.findByPriceBookIdAndDeletedFalse(first.getId(), PageRequest.of(0, 1, Sort.by("id")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(PriceBookEntry::getId).containsExactly(active.getId());
        assertThat(entries.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(first.getId(),
                List.of("shared", "extra", "deleted", "missing"))).extracting(PriceBookEntry::getId)
                .containsExactlyInAnyOrder(active.getId(), extra.getId());
    }

    private PriceBookEntry entry(PriceBook book, String product, boolean deleted) {
        return PriceBookEntry.builder().priceBook(book).productExternalId(product)
                .listPrice(new BigDecimal("50.0000")).deleted(deleted).build();
    }
}
