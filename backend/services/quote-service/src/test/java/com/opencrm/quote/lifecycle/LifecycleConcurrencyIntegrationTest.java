package com.opencrm.quote.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ContinuationEvaluationView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.security.test.TestCallers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises actual database compare-and-increment and transaction rollback, not a mocked repository. */
@Tag("integration")
@Testcontainers
@ActiveProfiles("lifecycle-concurrency")
@SpringBootTest(classes = LifecycleConcurrencyIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.default_schema=quote", "spring.flyway.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=5", "spring.datasource.hikari.minimum-idle=1",
        "spring.autoconfigure.exclude=com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
})
class LifecycleConcurrencyIntegrationTest {
    private static final String TENANT = "tenant-a";
    private static final String SUBSCRIPTION = "SUB-TEST";

    @SpringBootConfiguration @EnableAutoConfiguration @Profile("lifecycle-concurrency")
    @EntityScan(basePackageClasses = LifecycleChange.class)
    @EnableJpaRepositories(basePackageClasses = LifecycleChangeRepository.class)
    static class Config {
        @Bean LifecycleChangeAuditWriter audit() { return mock(LifecycleChangeAuditWriter.class); }
        @Bean SubscriptionTargetReader subscriptions() { return mock(SubscriptionTargetReader.class); }
        @Bean PricingServiceClient pricing() { return mock(PricingServiceClient.class); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LifecycleCreateLock createLock(JdbcTemplate jdbc) { return new LifecycleCreateLock(jdbc); }
        @Bean LifecycleChangeService lifecycleService(LifecycleChangeRepository changes, LifecycleChangeAuditWriter audit,
                SubscriptionTargetReader subscriptions, PricingServiceClient pricing, ObjectMapper json, LifecycleCreateLock lock) {
            return new LifecycleChangeService(changes, audit, subscriptions, pricing, json, lock);
        }
    }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=quote");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired LifecycleChangeService service;
    @Autowired LifecycleChangeRepository changes;
    @Autowired SubscriptionTargetReader subscriptions;
    @Autowired PricingServiceClient pricing;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void setUp() {
        reset(pricing, subscriptions);
        when(subscriptions.read(SUBSCRIPTION)).thenReturn(new SubscriptionTarget(SUBSCRIPTION, "ACTIVE", 4L));
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
    }

    @AfterEach void cleanup() { TestCallers.clear(); }

    @Test void competingPricesCannotBothCommitAndVersionsAdvanceForRepricing() throws Exception {
        UUID id = service.create(request(), null).body().getId();
        CountDownLatch inPricing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(pricing.evaluateContinuation(any())).thenAnswer(invocation -> {
            inPricing.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("pricing wait timed out");
            return priced(invocation.getArgument(0));
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> priceAsUser(id, 0));
            assertThat(inPricing.await(10, TimeUnit.SECONDS)).isTrue();
            var secondStarted = new CountDownLatch(1);
            var second = pool.submit(() -> { secondStarted.countDown(); return priceAsUser(id, 0); });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && waitingVersionUpdates() == 0) Thread.sleep(20);
            assertThat(waitingVersionUpdates()).as("second transaction waits on the version update").isGreaterThan(0);
            release.countDown();
            Object a = first.get(20, TimeUnit.SECONDS);
            Object b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a, b)).filteredOn(LifecycleChangeResponse.class::isInstance).hasSize(1);
            assertThat(List.of(a, b)).filteredOn(value -> value instanceof BusinessException error
                    && error.getStatus() == HttpStatus.CONFLICT && "VERSION_CONFLICT".equals(error.getErrorCode())).hasSize(1);
            assertThat(changes.findById(id).orElseThrow().getVersion()).isEqualTo(1L);
            doAnswer(invocation -> priced(invocation.getArgument(0))).when(pricing).evaluateContinuation(any());
            assertThat(service.price(id, priceCommand(1)).body().getVersion()).isEqualTo(2L);
            assertThat(service.submit(id, new VersionedCommand(2L)).body().getVersion()).isEqualTo(3L);
            assertThat(service.submit(id, new VersionedCommand(2L)).replayed()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test void failedPricingRollsBackVersionAndStaleSubscriptionIsRejectedAtSubmit() {
        UUID id = service.create(request(), null).body().getId();
        when(pricing.evaluateContinuation(any())).thenThrow(new BusinessException("offline", HttpStatus.BAD_GATEWAY, "PRICING_UNAVAILABLE"));
        assertThatThrownBy(() -> service.price(id, priceCommand(0))).isInstanceOf(BusinessException.class);
        assertThat(changes.findById(id).orElseThrow().getVersion()).isZero();
        doAnswer(invocation -> priced(invocation.getArgument(0))).when(pricing).evaluateContinuation(any());
        service.price(id, priceCommand(0));
        when(subscriptions.read(SUBSCRIPTION)).thenReturn(new SubscriptionTarget(SUBSCRIPTION, "ACTIVE", 5L));
        assertThatThrownBy(() -> service.submit(id, new VersionedCommand(1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("V3");
        LifecycleChange current = changes.findById(id).orElseThrow();
        assertThat(current.getVersion()).isEqualTo(1L);
        assertThat(current.getStatus()).isEqualTo(LifecycleChangeStatus.PRICED);
    }

    @Test void simultaneousIdenticalSubmitCommandsCommitOnceAndReplay() throws Exception {
        UUID id = service.create(request(), null).body().getId();
        when(pricing.evaluateContinuation(any())).thenAnswer(invocation -> priced(invocation.getArgument(0)));
        service.price(id, priceCommand(0));
        CountDownLatch inValidation = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            inValidation.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("validation wait timed out");
            return new SubscriptionTarget(SUBSCRIPTION, "ACTIVE", 4L);
        }).when(subscriptions).read(SUBSCRIPTION);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> submitAsUser(id, 1));
            assertThat(inValidation.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> submitAsUser(id, 1));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && waitingVersionUpdates() == 0) Thread.sleep(20);
            assertThat(waitingVersionUpdates()).as("second submit waits on the version update").isGreaterThan(0);
            release.countDown();
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results).extracting(LifecycleChangeResult::replayed).containsExactlyInAnyOrder(false, true);
            assertThat(results).allSatisfy(result -> assertThat(result.body().getVersion()).isEqualTo(2L));
            assertThat(changes.findById(id).orElseThrow().getStatus()).isEqualTo(LifecycleChangeStatus.IN_APPROVAL);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test void simultaneousIdenticalCreatesCommitOnceAndReplay() throws Exception {
        assertCreateRace(false);
    }

    @Test void simultaneousDifferentBodiesWithSameKeyReturnIdempotencyConflict() throws Exception {
        assertCreateRace(true);
    }

    @Test void joinedRepeatableReadCreateFailsClosedBeforeWriting() {
        String key = UUID.randomUUID().toString();
        TransactionTemplate outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> outer.execute(status -> service.create(request(), key)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("READ COMMITTED");
        assertThat(changes.findByTidAndIdempotencyKey(TENANT, key)).isEmpty();
    }

    private void assertCreateRace(boolean different) throws Exception {
        CreateLifecycleChangeRequest firstRequest = request();
        CreateLifecycleChangeRequest secondRequest = different ? request() : firstRequest;
        String key = UUID.randomUUID().toString();
        CountDownLatch firstValidation = new CountDownLatch(1);
        CountDownLatch secondValidation = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) firstValidation.countDown();
            else secondValidation.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("validation wait timed out");
            return new SubscriptionTarget(SUBSCRIPTION, "ACTIVE", 4L);
        }).when(subscriptions).read(SUBSCRIPTION);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> createAsUser(firstRequest, key));
            assertThat(firstValidation.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> createAsUser(secondRequest, key));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline && waitingCreateLocks() == 0 && secondValidation.getCount() != 0) Thread.sleep(20);
            assertThat(waitingCreateLocks()).as("second create waits on the idempotency lock").isGreaterThan(0);
            release.countDown();
            Object a = first.get(20, TimeUnit.SECONDS);
            Object b = second.get(20, TimeUnit.SECONDS);
            assertThat(a).isInstanceOf(LifecycleChangeResult.class);
            LifecycleChangeResult created = (LifecycleChangeResult) a;
            assertThat(created.replayed()).isFalse();
            if (different) {
                assertThat(b).isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
            } else {
                assertThat(b).isInstanceOfSatisfying(LifecycleChangeResult.class, replay -> {
                    assertThat(replay.replayed()).isTrue();
                    assertThat(replay.body().getId()).isEqualTo(created.body().getId());
                });
            }
            assertThat(changes.findByTidAndIdempotencyKey(TENANT, key)).isPresent();
            assertThat(calls).hasValue(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private Object priceAsUser(UUID id, long version) {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        try { return service.price(id, priceCommand(version)).body(); }
        catch (BusinessException ex) { return ex; }
        finally { TestCallers.clear(); }
    }

    private LifecycleChangeResult submitAsUser(UUID id, long version) {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        try { return service.submit(id, new VersionedCommand(version)); }
        finally { TestCallers.clear(); }
    }

    private Object createAsUser(CreateLifecycleChangeRequest request, String key) {
        LifecycleCallers.signIn("seller", TENANT, "CPQ.User");
        try { return service.create(request, key); }
        catch (BusinessException ex) { return ex; }
        finally { TestCallers.clear(); }
    }

    private long waitingVersionUpdates() {
        Long count = jdbc.queryForObject("""
                select count(*) from pg_stat_activity
                where datname = current_database() and wait_event_type = 'Lock'
                  and lower(query) like '%update%lifecycle_changes%'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private long waitingCreateLocks() {
        Long count = jdbc.queryForObject("""
                select count(*) from pg_stat_activity
                where datname = current_database() and wait_event_type = 'Lock'
                  and lower(query) like '%pg_advisory_xact_lock%'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private static PriceLifecycleChangeRequest priceCommand(long version) {
        return PriceLifecycleChangeRequest.builder().version(version).build();
    }

    private static CreateLifecycleChangeRequest request() {
        return CreateLifecycleChangeRequest.builder().intent("AMEND").initiationTrigger("MANUAL")
                .actionDate(LocalDate.of(2026, 11, 1)).contractEffectiveDate(LocalDate.of(2026, 11, 1))
                .contractId(UUID.randomUUID()).currencyCode("USD")
                .lines(List.of(ChangeLineWrite.builder().actionCode("UPDATE").quantitySemantics("DELTA")
                        .targetSubscriptionId(SUBSCRIPTION).observedCommercialRevision(4L)
                        .startQty("1").deltaQty("1").endQty("2").effectiveFrom(LocalDate.of(2026, 11, 1))
                        .productExternalId("sku").build())).build();
    }

    @SuppressWarnings("unchecked")
    private static ApiResponse<ContinuationEvaluationView> priced(Map<String, Object> request) {
        var input = (List<Map<String, Object>>) request.get("lines");
        var line = new ContinuationEvaluationView.Line();
        line.setLineRef(input.getFirst().get("lineRef").toString());
        line.setPolicyDecisionId("decision"); line.setPolicyId("policy"); line.setPolicyVersion("1");
        line.setTraceId("trace"); line.setProductExternalId("sku"); line.setPricebookVersion("pb");
        line.setUnitRate("10"); line.setAmount("20");
        var result = new ContinuationEvaluationView(); result.setLines(List.of(line));
        return ApiResponse.success(result);
    }
}
