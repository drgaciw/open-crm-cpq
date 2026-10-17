package com.opencrm.pricebook;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.OutboxEventRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
// Name the application explicitly. The slice configuration in this package is not a second
// @SpringBootConfiguration, so an implicit lookup stays unambiguous as well.
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
     * pricebook publishes no event of its own; it shares the pricing schema's outbox. Any PUBLISHED contract proves the
     * wiring, and this test is outside src/main, so the ADR-018 D9 one-emitter check does not count it.
     */
    private static final EventContractRegistry.EventContract CONTRACT = EventContractRegistry.DISCOUNT_APPROVAL_REQUIRED_V1;

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

    private static DiscountApprovalRequiredEvent payload(String currency) {
        return new DiscountApprovalRequiredEvent("Q-UAT", "line-1", BigDecimal.ONE, BigDecimal.TEN, currency);
    }
}
