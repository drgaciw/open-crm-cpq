package com.opencrm.pricebook;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.PriceBookPublishedEvent;
import com.opencrm.common.event.OutboxEventRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
// Name the application explicitly. The slice configuration in this package is not a second
// @SpringBootConfiguration, so an implicit lookup stays unambiguous as well.
// Keep security's management-port App off the scan so its DataSource/JPA excludes cannot wipe entityManagerFactory
// when a reactor mvn test puts security target/test-classes on the classpath (DRG-675).
@TypeExcludeFilters(ExcludeSecurityManagementPortAppFilter.class)
@SpringBootTest(classes = PriceBookServiceApplication.class, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.flyway.enabled=false"
})
class PriceBookOutboxWiringTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/init-test.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * DRG-431: pricebook now owns PriceBookPublished, published through the pricing schema's shared outbox
     * table. This test is outside src/main, so the ADR-018 D9 one-emitter check (PriceBookService#publish is
     * the real, sole call site) does not count it.
     */
    private static final EventContractRegistry.EventContract CONTRACT = EventContractRegistry.PRICE_BOOK_PUBLISHED_V1;

    @Autowired OutboxEventPublisher publisher;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void publisherPersistsCommittedEventsAndRollsBackWithCaller() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long before = repository.count();
        transaction.executeWithoutResult(status ->
            publisher.publish("PriceBook", "uat-commit", CONTRACT, payload("USD")));
        assertThat(repository.count()).isEqualTo(before + 1);
        transaction.executeWithoutResult(status -> {
            publisher.publish("PriceBook", "uat-rollback", CONTRACT, payload("EUR"));
            status.setRollbackOnly();
        });
        assertThat(repository.count()).isEqualTo(before + 1);
        assertThat(repository.findUnpublishedEvents()).anySatisfy(event -> {
            assertThat(event.getAggregateId()).isEqualTo("uat-commit");
            assertThat(event.getPayload()).contains("USD");
        });
    }

    private static PriceBookPublishedEvent payload(String currency) {
        return new PriceBookPublishedEvent(1L, "uat-uuid", 1, currency, "NA", null, null, null, null);
    }
}
