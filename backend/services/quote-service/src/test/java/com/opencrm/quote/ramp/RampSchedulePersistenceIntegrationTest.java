package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.common.event.OutboxEventPublisher;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Real PostgreSQL uniqueness and Hibernate orphan ordering for the full line-update service path. */
@Tag("integration")
@Testcontainers
@Transactional
@SpringBootTest(classes = RampSchedulePersistenceIntegrationTest.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.default_schema=quote", "spring.flyway.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1",
        "spring.autoconfigure.exclude=com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
class RampSchedulePersistenceIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration @EnableJpaAuditing
    @EntityScan(basePackages = "com.opencrm.quote")
    @EnableJpaRepositories(basePackages = {"com.opencrm.quote.quote", "com.opencrm.quote.lineitem", "com.opencrm.quote.ramp"})
    static class PersistenceConfiguration {}

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(256L * 1024 * 1024));
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=quote");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired QuoteRepository quotes;
    @Autowired QuoteLineItemRepository lines;
    @Autowired QuoteRampStepRepository steps;
    @Autowired EntityManager entityManager;

    @Test void identicalFullPutRetainsRowIdentitiesAndStoredPricedSnapshot() {
        Quote quote = quote("IDENTICAL");
        QuoteLineItemService service = service();
        var request = request(List.of(step(1, 1, 12, "34"), step(2, 13, 12, "45"), step(3, 25, 12, "50")));
        Long id = service.addLineItem(quote.getId(), request).getId();
        entityManager.flush(); entityManager.clear();
        QuoteLineItem line = lines.findById(id).orElseThrow();
        List<Long> originalIds = line.getRampSteps().stream().map(QuoteRampStep::getId).toList();
        String priced = "{\"pricedPeriods\":[{\"sequence\":1,\"netUnitPrice\":34},{\"sequence\":2,\"netUnitPrice\":45},{\"sequence\":3,\"netUnitPrice\":50}]}";
        line.setPricingWaterfallJson(priced); line.setExtendedAmount(new BigDecimal("77400"));
        line.getQuote().setPricingWaterfallJson("{\"pricingFingerprint\":\"bound-snapshot\"}");
        line.getQuote().setTcv(new BigDecimal("77400")); line.getQuote().setAcv(new BigDecimal("25800"));
        line.getQuote().setRampScheduleStale(false); // Successful pricing clears authoring staleness.
        entityManager.flush(); entityManager.clear();

        service.updateLineItem(quote.getId(), id, request);
        entityManager.flush(); entityManager.clear();

        line = lines.findById(id).orElseThrow();
        assertThat(line.getRampSteps()).extracting(QuoteRampStep::getId).containsExactlyElementsOf(originalIds);
        assertThat(line.getPricingWaterfallJson()).isEqualTo(priced);
        assertThat(line.getExtendedAmount()).isEqualByComparingTo("77400");
        assertThat(line.getQuote().getTcv()).isEqualByComparingTo("77400");
        assertThat(RampPricingSnapshot.fingerprint(line.getQuote())).isEqualTo("bound-snapshot");
        assertThat(line.getQuote().isRampScheduleStale()).isFalse();
    }

    @Test void fullReplacementUpdatesSameSequencesDeletesExtrasAndSupportsAddingThemBack() {
        Quote quote = quote("REPLACEMENT"); QuoteLineItemService service = service();
        Long id = service.addLineItem(quote.getId(), request(List.of(step(1, 1, 12, "34"),
                step(2, 13, 12, "45"), step(3, 25, 12, "50")))).getId();
        entityManager.flush(); entityManager.clear();
        QuoteLineItem line = lines.findById(id).orElseThrow();
        Long firstId = line.getRampSteps().getFirst().getId();
        Long deletedId = line.getRampSteps().getLast().getId();
        service.updateLineItem(quote.getId(), id, request(List.of(step(1, 1, 18, "40"), step(2, 19, 18, "48"))));
        entityManager.flush(); entityManager.clear();
        line = lines.findById(id).orElseThrow();
        assertThat(line.getRampSteps()).hasSize(2);
        assertThat(line.getRampSteps().getFirst().getId()).isEqualTo(firstId);
        assertThat(line.getRampSteps().getFirst().getMonths()).isEqualTo(18);
        assertThat(line.getRampSteps().getFirst().getUnitPrice()).isEqualByComparingTo("40");
        assertThat(entityManager.find(QuoteRampStep.class, deletedId)).isNull();
        service.updateLineItem(quote.getId(), id, request(List.of(step(1, 1, 12, "40"),
                step(2, 13, 12, "48"), step(3, 25, 12, "50"))));
        entityManager.flush(); entityManager.clear();
        assertThat(lines.findById(id).orElseThrow().getRampSteps()).hasSize(3);
    }

    private Quote quote(String number) {
        return quotes.saveAndFlush(Quote.builder().quoteNumber(number).name(number).customerExternalId("customer")
                .durationInMonths(36).currency("USD").build());
    }
    private QuoteLineItemService service() {
        var properties = new RampPricingProperties(); properties.setEnabled(true);
        var validator = new RampScheduleValidator();
        var guard = new RampScheduleGuard(lines, steps, validator, properties,
                mock(ApprovalRoutingClient.class), mock(OutboxEventPublisher.class));
        return new QuoteLineItemService(lines, quotes,
                new RampScheduleAttacher(properties, validator, new RampEscalatorExpander()), guard);
    }
    private LineItemCreateRequest request(List<RampStepDto> schedule) {
        return LineItemCreateRequest.builder().productExternalId("seats").productName("Seats")
                .productType("SUBSCRIPTION").lineItemType("SAAS_SEAT_NAMED").quantity(50)
                .listPrice(new BigDecimal("50")).billingFrequency("MONTHLY").sortOrder(0)
                .schedule(RampScheduleDto.builder().steps(schedule).build()).build();
    }
    private RampStepDto step(int sequence, int start, int months, String price) {
        return RampStepDto.builder().sequence(sequence).startMonth(start).months(months).quantity(50)
                .priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal(price)).build();
    }
}
