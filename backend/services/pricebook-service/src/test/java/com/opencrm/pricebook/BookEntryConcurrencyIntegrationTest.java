package com.opencrm.pricebook;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = BookEntryConcurrencyIntegrationTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.autoconfigure.exclude="
                + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
class BookEntryConcurrencyIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = PriceBook.class)
    @EnableJpaRepositories(basePackageClasses = PriceBookRepository.class)
    @EnableJpaAuditing
    @Import({PriceBookService.class, CostBookService.class, PriceBookMapperImpl.class, CostBookMapperImpl.class})
    static class TestConfiguration {
        @Bean
        OutboxEventPublisher outboxEventPublisher() {
            return mock(OutboxEventPublisher.class);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024))
            .withInitScript("db/init-test.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=pricing");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PriceBookRepository priceBooks;
    @Autowired CostBookRepository costBooks;
    @Autowired PriceBookService priceBookService;
    @Autowired CostBookService costBookService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void concurrentPriceEntriesCannotOverlap() throws Exception {
        Long id = priceBooks.saveAndFlush(PriceBook.builder().name("Concurrent prices").build()).getId();
        assertOverlappingWriteIsSerialized(() -> priceBookService.createEntry(id,
                PriceBookEntryCreateRequest.builder().productExternalId("SKU-1")
                        .listPrice(new BigDecimal("100.00")).build()));
    }

    @Test
    void concurrentCostEntriesCannotOverlap() throws Exception {
        Long id = costBooks.saveAndFlush(CostBook.builder().name("Concurrent costs").build()).getId();
        assertOverlappingWriteIsSerialized(() -> costBookService.createEntry(id,
                CostBookEntryCreateRequest.builder().sku("SKU-1")
                        .cost(new BigDecimal("30.00")).build()));
    }

    private void assertOverlappingWriteIsSerialized(Runnable createEntry) throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                createEntry.run();
                firstWritten.countDown();
                try {
                    if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to commit first entry");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            try {
                assertThat(firstWritten.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    createEntry.run();
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(BusinessException.class);
            } finally {
                releaseFirst.countDown();
            }
        }
    }
}
