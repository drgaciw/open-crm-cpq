package com.opencrm.pricebook;

import com.opencrm.common.event.OutboxEventPublisher;
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
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
    "spring.flyway.enabled=false"
})
class PriceBookOutboxWiringTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OutboxEventPublisher publisher;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void publisherPersistsCommittedEventsAndRollsBackWithCaller() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long before = repository.count();
        transaction.executeWithoutResult(status ->
            publisher.publish("PriceBook", "uat-commit", "PriceBookUpdated", Map.of("currency", "USD")));
        assertThat(repository.count()).isEqualTo(before + 1);
        transaction.executeWithoutResult(status -> {
            publisher.publish("PriceBook", "uat-rollback", "PriceBookUpdated", Map.of("currency", "EUR"));
            status.setRollbackOnly();
        });
        assertThat(repository.count()).isEqualTo(before + 1);
        assertThat(repository.findUnpublishedEvents()).anySatisfy(event -> {
            assertThat(event.getAggregateId()).isEqualTo("uat-commit");
            assertThat(event.getPayload()).contains("USD");
        });
    }
}
