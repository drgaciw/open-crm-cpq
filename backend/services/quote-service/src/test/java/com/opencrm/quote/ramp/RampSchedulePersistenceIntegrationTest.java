package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.common.event.OutboxEventPublisher;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Real PostgreSQL uniqueness and Hibernate orphan ordering for the full line-update service path. */
@Tag("integration")
@Testcontainers
@Transactional
@SpringBootTest(classes = RampSchedulePersistenceIntegrationTest.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=quote", "spring.flyway.enabled=true",
        "spring.flyway.schemas=quote", "spring.flyway.default-schema=quote",
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
    @Autowired PlatformTransactionManager transactionManager;

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

    @Test void atomicExtendSurvivesReloadWithTermScheduleAndIncrementedRootVersion() {
        Quote quote = quote("EXTEND");
        QuoteLineItemService service = service();
        Long id = service.addLineItem(quote.getId(), request(List.of(step(1, 1, 36, "40")))).getId();
        entityManager.flush(); entityManager.clear();
        Quote before = quotes.findById(quote.getId()).orElseThrow();
        Long expectedVersion = before.getVersion();
        var schedule = RampScheduleDto.builder().steps(List.of(step(1, 1, 36, "40"), step(2, 37, 12, "42"))).build();
        Quote saved = service.persistExtendedRamp(quote.getId(), id, 48, expectedVersion, schedule);
        assertThat(saved.getVersion()).isGreaterThan(expectedVersion);
        Long returnedVersion = saved.getVersion();
        entityManager.clear();
        Quote reloaded = quotes.findById(quote.getId()).orElseThrow();
        assertThat(reloaded.getDurationInMonths()).isEqualTo(48);
        assertThat(reloaded.getVersion()).isEqualTo(returnedVersion);
        assertThat(reloaded.isRampScheduleStale()).isTrue();
        assertThat(lines.findById(id).orElseThrow().getRampSteps()).extracting(QuoteRampStep::getMonths)
                .containsExactly(36, 12);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.persistExtendedRamp(quote.getId(), id,
                60, expectedVersion, schedule)).isInstanceOf(com.opencrm.common.exception.BusinessException.class)
                .extracting("errorCode").isEqualTo("QUOTE_VERSION_CONFLICT");
    }

    @Test void rejectedExtensionLeavesPersistedTermAndScheduleUnchanged() {
        Quote quote = quote("INVALID-EXTEND");
        QuoteLineItemService service = service();
        Long id = service.addLineItem(quote.getId(), request(List.of(step(1, 1, 36, "40")))).getId();
        entityManager.flush(); entityManager.clear();
        Quote before = quotes.findById(quote.getId()).orElseThrow();
        Long expectedVersion = before.getVersion();
        var invalid = RampScheduleDto.builder().steps(List.of(step(1, 1, 36, "40"))).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.persistExtendedRamp(quote.getId(), id,
                48, expectedVersion, invalid)).isInstanceOf(RampScheduleException.class);
        entityManager.flush(); entityManager.clear();
        Quote reloaded = quotes.findById(quote.getId()).orElseThrow();
        assertThat(reloaded.getDurationInMonths()).isEqualTo(36);
        assertThat(reloaded.getVersion()).isEqualTo(expectedVersion);
        assertThat(lines.findById(id).orElseThrow().getRampSteps()).extracting(QuoteRampStep::getMonths)
                .containsExactly(36);
    }

    @Test void nonCompoundingAuthoringSurvivesDatabaseReloadAndExtension() {
        Quote quote = quote("NON-COMPOUNDING");
        QuoteLineItemService service = service();
        RampEscalatorDto escalator = RampEscalatorDto.builder().startPrice(new BigDecimal("100"))
                .mode(RampEscalatorMode.PERCENT).amount(new BigDecimal("10"))
                .everyMonths(12).compounding(false).build();
        var authored = request(new RampEscalatorExpander().expand(36, escalator, 50));
        authored.getSchedule().setEscalator(escalator);
        Long id = service.addLineItem(quote.getId(), authored).getId();
        entityManager.flush(); entityManager.clear();

        QuoteLineItem reloaded = lines.findById(id).orElseThrow();
        assertThat(reloaded.getRampEscalatorCompounding()).isFalse();
        RampScheduleDto read = RampScheduleViews.toDto(reloaded);
        assertThat(read.getEscalator().getCompounding()).isFalse();
        assertThat(read.getSteps()).extracting(RampStepDto::getUnitPrice)
                .containsExactly(new BigDecimal("100.0000"), new BigDecimal("110.0000"), new BigDecimal("120.0000"));
        assertThat(new RampEscalatorExpander().extend(read.getSteps(), 48, read.getEscalator()).getLast().getUnitPrice())
                .isEqualByComparingTo("130.0000");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void flushedExtensionRollsBackAsOneUnit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(transaction, "ROLLBACK");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service().persistExtendedRamp(fixture.quoteId(), fixture.lineId(), 48, fixture.version(),
                    RampScheduleDto.builder().steps(List.of(step(1, 1, 48, "42"))).build());
            entityManager.flush();
            throw new IllegalStateException("Fail after the extension SQL has been flushed");
        })).isInstanceOf(IllegalStateException.class);

        transaction.executeWithoutResult(status -> {
            Quote reloaded = quotes.findById(fixture.quoteId()).orElseThrow();
            assertThat(reloaded.getDurationInMonths()).isEqualTo(36);
            assertThat(reloaded.getVersion()).isEqualTo(fixture.version());
            assertThat(lines.findById(fixture.lineId()).orElseThrow().getRampSteps())
                    .extracting(QuoteRampStep::getMonths).containsExactly(36);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sameVersionTransactionsProduceOneCommitAndOneConflict() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(transaction, "CONCURRENT");
        CountDownLatch bothLoaded = new CountDownLatch(2);
        ExtensionAttempt committed;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentAttempt(fixture, bothLoaded, "42"));
            var second = executor.submit(() -> concurrentAttempt(fixture, bothLoaded, "43"));
            List<ExtensionAttempt> attempts = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(attempts).filteredOn(ExtensionAttempt::committed).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.committed())
                    .extracting(ExtensionAttempt::errorCode).containsExactly("QUOTE_VERSION_CONFLICT");
            committed = attempts.stream().filter(ExtensionAttempt::committed).findFirst().orElseThrow();
        }
        transaction.executeWithoutResult(status -> {
            Quote reloaded = quotes.findById(fixture.quoteId()).orElseThrow();
            assertThat(reloaded.getDurationInMonths()).isEqualTo(48);
            assertThat(reloaded.getVersion()).isGreaterThan(fixture.version()).isEqualTo(committed.version());
            assertThat(lines.findById(fixture.lineId()).orElseThrow().getRampSteps())
                    .extracting(QuoteRampStep::getMonths).containsExactly(48);
            assertThat(lines.findById(fixture.lineId()).orElseThrow().getRampSteps().getFirst().getUnitPrice())
                    .isEqualByComparingTo(committed.price());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void extensionWithOldQuoteVersionMustNotOverwriteNewerScheduleEdit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(tx, "STALE-LINE-EDIT");
        tx.executeWithoutResult(status -> service().updateLineItem(fixture.quoteId(), fixture.lineId(),
                request(List.of(step(1, 1, 36, "41")))));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service().persistExtendedRamp(
                fixture.quoteId(), fixture.lineId(), 48, fixture.version(),
                RampScheduleDto.builder().steps(List.of(step(1, 1, 48, "40"))).build())))
                .isInstanceOf(com.opencrm.common.exception.BusinessException.class)
                .extracting("errorCode").isEqualTo("QUOTE_VERSION_CONFLICT");
        tx.executeWithoutResult(status -> assertThat(lines.findById(fixture.lineId()).orElseThrow().getRampSteps().getFirst().getUnitPrice())
                .isEqualByComparingTo("41"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void addAndRemoveAdvanceQuoteVersionEvenWhenTotalsAreUnchanged() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(tx, "VERSION-LINE-OPS");
        Long zeroLineId = tx.execute(status -> {
            var zero = request(List.of(step(1, 1, 36, "0")));
            zero.setListPrice(BigDecimal.ZERO);
            return service().addLineItem(fixture.quoteId(), zero).getId();
        });
        long committedAfterAdd = tx.execute(status -> quotes.findById(fixture.quoteId()).orElseThrow().getVersion());
        assertThat(committedAfterAdd).isGreaterThan(fixture.version());
        tx.executeWithoutResult(status -> service().removeLineItem(fixture.quoteId(), zeroLineId));
        Long committedAfterRemove = tx.execute(status -> quotes.findById(fixture.quoteId()).orElseThrow().getVersion());
        assertThat(committedAfterRemove).isGreaterThan(committedAfterAdd);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLineEditAndExtensionCannotBothCommit() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(tx, "EDIT-EXTEND-RACE");
        CountDownLatch loaded = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var edit = pool.submit(() -> raceEditOrExtend(fixture, loaded, false));
            var extend = pool.submit(() -> raceEditOrExtend(fixture, loaded, true));
            List<String> result = List.of(edit.get(30, TimeUnit.SECONDS), extend.get(30, TimeUnit.SECONDS));
            assertThat(result).filteredOn("COMMITTED"::equals).hasSize(1);
            assertThat(result).filteredOn(value -> !"COMMITTED".equals(value)).hasSize(1);
            System.out.println("edit/extend race outcomes " + result);
        }
    }

    private String raceEditOrExtend(PersistedRamp fixture, CountDownLatch loaded, boolean extend) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                quotes.findById(fixture.quoteId()).orElseThrow();
                lines.findById(fixture.lineId()).orElseThrow().getRampSteps().size();
                loaded.countDown();
                try { if (!loaded.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                if (extend) service().persistExtendedRamp(fixture.quoteId(), fixture.lineId(), 48, fixture.version(),
                        RampScheduleDto.builder().steps(List.of(step(1, 1, 48, "40"))).build());
                else service().updateLineItem(fixture.quoteId(), fixture.lineId(), request(List.of(step(1, 1, 36, "41"))));
            });
            return "COMMITTED";
        } catch (RuntimeException ex) { return ex.getClass().getName() + ":" + ex.getMessage(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unchangedTermAndScheduleStillAdvanceVersionAndReturnCommittedVersion() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        PersistedRamp fixture = persistedRamp(tx, "SAME-TERM-VERSION");
        Long returned = tx.execute(status -> {
            Quote q = quotes.findById(fixture.quoteId()).orElseThrow();
            assertThat(q.isRampScheduleStale()).isTrue();
            return service().persistExtendedRamp(fixture.quoteId(), fixture.lineId(), 36, fixture.version(),
                    RampScheduleDto.builder().steps(List.of(step(1, 1, 36, "40"))).build()).getVersion();
        });
        Long committed = tx.execute(status -> quotes.findById(fixture.quoteId()).orElseThrow().getVersion());
        assertThat(committed).isGreaterThan(fixture.version()).isEqualTo(returned);
    }

    private ExtensionAttempt concurrentAttempt(PersistedRamp fixture, CountDownLatch bothLoaded, String price) {
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(quotes.findById(fixture.quoteId()).orElseThrow().getVersion()).isEqualTo(fixture.version());
                // Both persistence contexts hold the same row versions before either writes.
                assertThat(lines.findById(fixture.lineId()).orElseThrow().getRampSteps()).hasSize(1);
                bothLoaded.countDown();
                try {
                    assertThat(bothLoaded.await(15, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                Quote saved = service().persistExtendedRamp(fixture.quoteId(), fixture.lineId(), 48, fixture.version(),
                        RampScheduleDto.builder().steps(List.of(step(1, 1, 48, price))).build());
                return new ExtensionAttempt(true, null, saved.getVersion(), price);
            });
        } catch (com.opencrm.common.exception.BusinessException conflict) {
            assertThat(conflict.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
            return new ExtensionAttempt(false, conflict.getErrorCode(), null, price);
        }
    }

    private PersistedRamp persistedRamp(TransactionTemplate transaction, String number) {
        return transaction.execute(status -> {
            Quote quote = quote(number);
            Long lineId = service().addLineItem(quote.getId(), request(List.of(step(1, 1, 36, "40")))).getId();
            entityManager.flush();
            return new PersistedRamp(quote.getId(), lineId, quote.getVersion());
        });
    }

    private record PersistedRamp(Long quoteId, Long lineId, Long version) {}
    private record ExtensionAttempt(boolean committed, String errorCode, Long version, String price) {}

    private Quote quote(String number) {
        return quotes.saveAndFlush(Quote.builder().quoteNumber(number).name(number).customerExternalId("customer")
                .durationInMonths(36).currency("USD").build());
    }
    private QuoteLineItemService service() {
        var properties = new RampPricingProperties(); properties.setEnabled(true);
        var validator = new RampScheduleValidator();
        var guard = new RampScheduleGuard(lines, steps, validator, properties,
                mock(ApprovalRoutingClient.class), mock(OutboxEventPublisher.class));
        var service = new QuoteLineItemService(lines, quotes,
                new RampScheduleAttacher(properties, validator, new RampEscalatorExpander()), guard);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
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
