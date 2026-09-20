package com.opencrm.quote.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.*;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.client.*;
import com.opencrm.quote.event.QuoteEventPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Testcontainers @Tag("docker") @Tag("integration")
@SpringBootTest(classes=AcceptedSnapshotIntegrationTest.App.class, webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"oms.quote.enabled=true", "oms.quote.scope-id=pilot", "oms.quote.customer-ids[0]=a", "spring.cache.type=none"})
class AcceptedSnapshotIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=quote");
        r.add("spring.datasource.username",POSTGRES::getUsername); r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @SpringBootConfiguration @EnableAutoConfiguration(excludeName={
        "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration",
        "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration",
        "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration",
        "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration",
        "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration"}) @EnableJpaAuditing
    @EntityScan(basePackageClasses={Quote.class,QuoteLineItem.class,AcceptedSnapshot.class,OutboxEvent.class})
    @EnableJpaRepositories(basePackageClasses={QuoteRepository.class,QuoteLineItemRepository.class,AcceptedSnapshotRepository.class,OutboxEventRepository.class})
    @Import({QuoteService.class,AcceptedSnapshotService.class,OmsQuoteProperties.class,OutboxEventPublisher.class})
    static class App {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean QuoteMapper quoteMapper() { return mock(QuoteMapper.class); }
        @Bean QuoteNumberGenerator numberGenerator() { return mock(QuoteNumberGenerator.class); }
        @Bean QuoteEventPublisher eventPublisher() { return mock(QuoteEventPublisher.class); }
        @Bean PricingServiceClient pricing() { return mock(PricingServiceClient.class); }
        @Bean ProductServiceClient product() { return mock(ProductServiceClient.class); }
    }
    @Autowired QuoteRepository quotes;
    @Autowired AcceptedSnapshotRepository snapshots;
    @Autowired QuoteService service;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @BeforeEach void clean() { jdbc.execute("TRUNCATE accepted_order_snapshots, outbox_events, quote_line_items, quotes RESTART IDENTITY CASCADE"); }
    Long source() {
        return transactions.execute(tx -> {
            var q = Quote.builder().quoteNumber("Q-TEST").name("test").customerExternalId("a").status(QuoteStatus.SENT)
                .subtotal(new BigDecimal("50.00")).totalAmount(new BigDecimal("50.00")).build();
            var line = QuoteLineItem.builder().quote(q).productExternalId("p1").quantity(1).listPrice(new BigDecimal("50.00"))
                .unitPrice(new BigDecimal("50.00")).extendedAmount(new BigDecimal("50.00")).build();
            q.getLineItems().add(line); return quotes.saveAndFlush(q).getId();
        });
    }
    @Test void atomicallyPersistsExactPayloadAliasAndImmutableDatabaseRecord() throws Exception {
        Long id=source(); service.accept(id);
        var stored=snapshots.findAll().getFirst();
        assertThat(snapshots.lookup(stored.getSnapshotId(),"Q-TEST",1)).isPresent();
        assertThat(snapshots.lookup(stored.getSnapshotId(),stored.getQuoteId(),1)).isPresent();
        assertThat(snapshots.lookup(stored.getSnapshotId(),"Q-TEST",2)).isEmpty();
        assertThat(quotes.findById(id).orElseThrow().getFulfillmentOwner()).isEqualTo("OMS");
        String wire=jdbc.queryForObject("select payload from outbox_events where event_type='oms.quote-accepted.v1'",String.class);
        assertThat(AcceptedSnapshotService.sha256(mapper.readTree(wire).get("snapshot").toString())).isEqualTo(stored.getSnapshotHash());
        assertThatThrownBy(() -> jdbc.update("update accepted_order_snapshots set payload='{}' where id=?",stored.getId())).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from accepted_order_snapshots where id=?",stored.getId())).hasMessageContaining("immutable");
    }
    @Test void outboxFailureRollsBackOwnershipSnapshotAndAcceptedState() {
        Long id=source();
        jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT reject_oms_test CHECK (event_type <> 'oms.quote-accepted.v1')");
        try { assertThatThrownBy(() -> service.accept(id)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT reject_oms_test"); }
        var quote=quotes.findById(id).orElseThrow();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT); assertThat(quote.getFulfillmentOwner()).isEqualTo("LEGACY");
        assertThat(quote.getAcceptedRevision()).isZero(); assertThat(snapshots.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events",Long.class)).isZero();
    }
    @Test void hundredConcurrentAcceptancesProduceOneOwnedSnapshot() throws Exception {
        Long id=source();
        try (var pool=Executors.newFixedThreadPool(10)) {
            List<Callable<Boolean>> attempts=new ArrayList<>();
            for(int i=0;i<100;i++) attempts.add(() -> { try { service.accept(id); return true; } catch(RuntimeException e) { return false; } });
            int successes=0; for(var result:pool.invokeAll(attempts)) if(result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(snapshots.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events",Long.class)).isEqualTo(2);
        assertThat(quotes.findById(id).orElseThrow().getAcceptedRevision()).isEqualTo(1);
    }
}
