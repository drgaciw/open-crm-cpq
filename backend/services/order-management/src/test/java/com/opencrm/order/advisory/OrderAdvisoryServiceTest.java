package com.opencrm.order.advisory;

import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.testing.FakeJevTransport;
import com.opencrm.jev.transport.JevTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderAdvisoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private final JevProperties properties = new JevProperties();
    private final FakeJevTransport fake = new FakeJevTransport();
    private final OrderAdvisoryRepository repository = mock(OrderAdvisoryRepository.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final JevAdvisoryRunner runner = new JevAdvisoryRunner(1, executor);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TestTransactionManager manager = new TestTransactionManager();
    private final TransactionTemplate tx = new TransactionTemplate(manager);
    private OrderAdvisoryService service;

    @BeforeEach
    void setUp() {
        properties.setModel("offline-fixture-v1");
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; }).when(executor).execute(any());
        service = service(fake);
    }

    @Test
    void defaultDisabledDoesNotScheduleOrRead() {
        tx.executeWithoutResult(status -> service.afterCommit("order-local"));
        verifyNoInteractions(repository, executor);
        assertThat(fake.requests()).isEmpty();
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void transactionRollbackSchedulesNothing() {
        enable();
        tx.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            service.afterCommit("order-local");
            status.setRollbackOnly();
        });
        assertThat(manager.rollbacks).isEqualTo(1);
        verifyNoInteractions(repository, executor);
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void noTransactionAndSynchronizationWithoutTransactionAreRefused() {
        enable();
        service.afterCommit("order-local");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.afterCommit("order-local");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        verifyNoInteractions(repository, executor);
    }

    @Test
    void commitInvokesOnceAfterCommitAndLogsTypedAnswer() {
        enable(); successfulClaim(); fake.reply(reply());
        tx.executeWithoutResult(status -> {
            service.afterCommit("order-local");
            verifyNoInteractions(repository, executor);
        });
        verify(executor).execute(any());
        verify(repository).snapshots("order-local", "scope-local", NOW);
        var record = ArgumentCaptor.forClass(JevDecisionRecord.class);
        verify(repository).complete(any(), record.capture());
        assertThat(record.getValue().status()).isEqualTo("OK");
        assertThat(record.getValue().authoritativeSource()).isEqualTo(JevDecisionRecord.AuthoritativeSource.NONE);
        assertThat(record.getValue().featuresJson()).doesNotContain("order-local", "scope-local", "customer-local", "exception-local");
        assertThat(fake.requests()).hasSize(1);
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void duplicateClaimSuppressesProviderCall() {
        enable(); snapshots();
        commit();
        verify(repository, never()).complete(any(), any());
        assertThat(fake.requests()).isEmpty();
        assertThat(meters.counter("oms.advisory.duplicates").count()).isEqualTo(1);
    }

    @Test
    void providerExceptionIsRecordedWithoutFailingCommittedCommand() {
        enable(); successfulClaim(); fake.fail(new IllegalStateException("scripted outage"));
        assertThatCode(this::commit).doesNotThrowAnyException();
        var record = ArgumentCaptor.forClass(JevDecisionRecord.class);
        verify(repository).complete(any(), record.capture());
        assertThat(record.getValue().status()).isEqualTo("ERROR");
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void snapshotFailureCannotFailCommandOrCallProvider() {
        enable();
        when(repository.snapshots(anyString(), anyString(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(this::commit).doesNotThrowAnyException();
        assertThat(fake.requests()).isEmpty();
        assertThat(meters.counter("oms.advisory.failures").count()).isEqualTo(1);
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void claimFailureCannotFailCommandOrCallProvider() {
        enable(); snapshots();
        when(repository.claim(any(), anyString(), any(), any(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("write unavailable"));
        assertThatCode(this::commit).doesNotThrowAnyException();
        assertThat(fake.requests()).isEmpty();
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void completionFailureCannotFailCommittedCommand() {
        enable(); successfulClaim(); fake.reply(reply());
        doThrow(new IllegalStateException("write unavailable")).when(repository).complete(any(), any());
        assertThatCode(this::commit).doesNotThrowAnyException();
        assertThat(fake.requests()).hasSize(1);
        assertThat(meters.counter("oms.advisory.failures").count()).isEqualTo(1);
        assertThat(meters.counter("oms.advisory.completed").count()).isZero();
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void disablingDuringProviderCallSuppressesCompletion() {
        enable(); successfulClaim(); fake.reply(reply());
        var transport = mock(JevTransport.class);
        when(transport.enabled()).thenReturn(true);
        when(transport.name()).thenReturn("test");
        when(transport.wireFormat()).thenReturn("none");
        when(transport.decide(any())).thenAnswer(invocation -> {
            properties.setEnabled(false);
            return fake.decide(invocation.getArgument(0));
        });
        service = service(transport);
        assertThatCode(this::commit).doesNotThrowAnyException();
        assertThat(fake.requests()).hasSize(1);
        verify(repository, never()).complete(any(), any());
        assertThat(meters.counter("oms.advisory.completed").count()).isZero();
    }

    @Test
    void equalFeatureHashesRemainBoundToEachSubjectVersionAndModel() {
        enable();
        var snapshots = List.of(
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-local", 2, "exception-local", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-other", "customer-local", "order-local", 2, "exception-local", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-local", "customer-other", "order-local", 2, "exception-local", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-other", 2, "exception-local", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-local", 3, "exception-local", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-local", 2, "exception-other", 3, NOW, null),
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-local", 2, "exception-local", 4, NOW, null));
        // A rejected claim still exposes its calculated key, without needing provider responses.
        for (var snapshot : snapshots) {
            when(repository.snapshots("order-local", "scope-local", NOW)).thenReturn(List.of(snapshot));
            commit();
        }
        when(repository.snapshots("order-local", "scope-local", NOW)).thenReturn(List.of(snapshots.getFirst()));
        properties.setModel("offline-fixture-v2");
        commit();
        var keys = ArgumentCaptor.forClass(String.class);
        var states = ArgumentCaptor.forClass(com.opencrm.jev.state.JevState.class);
        verify(repository, times(8)).claim(any(), keys.capture(), any(), states.capture(), anyString(), anyString(), any());
        assertThat(keys.getAllValues()).hasSize(8).doesNotHaveDuplicates();
        assertThat(states.getAllValues().stream().map(com.opencrm.jev.state.JevState::sha256).distinct()).hasSize(1);
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void eitherMissingOrFalseFlagPreventsAdvisoryBeanCreation() {
        var context = new ApplicationContextRunner()
                .withUserConfiguration(OrderAdvisoryService.class)
                .withBean(JevClient.class, () -> new JevClient(properties, fake))
                .withBean(JevProperties.class, () -> properties)
                .withBean(JevAdvisoryRunner.class, () -> runner)
                .withBean(OrderAdvisoryRepository.class, () -> repository)
                .withBean(io.micrometer.core.instrument.MeterRegistry.class, () -> meters)
                .withPropertyValues("oms.scope-id=scope-local");
        context.run(result -> assertThat(result).hasNotFailed().doesNotHaveBean(OrderAdvisoryService.class));
        context.withPropertyValues("opencrm.jev.enabled=true").run(result ->
                assertThat(result).hasNotFailed().doesNotHaveBean(OrderAdvisoryService.class));
        context.withPropertyValues("opencrm.jev.oms-exception-triage.enabled=true").run(result ->
                assertThat(result).hasNotFailed().doesNotHaveBean(OrderAdvisoryService.class));
        context.withPropertyValues("opencrm.jev.enabled=false", "opencrm.jev.oms-exception-triage.enabled=true").run(result ->
                assertThat(result).hasNotFailed().doesNotHaveBean(OrderAdvisoryService.class));
        context.withPropertyValues("opencrm.jev.enabled=true", "opencrm.jev.oms-exception-triage.enabled=false").run(result ->
                assertThat(result).hasNotFailed().doesNotHaveBean(OrderAdvisoryService.class));
        verifyNoInteractions(repository);
        verify(executor, never()).execute(any());
    }

    @Test
    void mismatchedRecoveryScopeDisablesOnlyOptionalWork() {
        enable();
        for (String scopes : List.of("scope-other", "scope-local,scope-other", " scope-other , scope-local ")) {
            service = service(fake);
            service.configureRecoveryScopes(scopes);
            assertThatCode(this::commit).doesNotThrowAnyException();
        }
        assertThat(manager.commits).isEqualTo(3);
        assertThat(manager.rollbacks).isZero();
        verifyNoInteractions(repository, executor);
        assertThat(fake.requests()).isEmpty();
    }

    @Test
    void matchingOrAbsentRecoveryScopeAllowsConfiguredScope() {
        enable(); successfulClaim();
        for (String scopes : List.of("", "scope-local", " scope-local ", "scope-local,scope-local")) {
            service = service(fake);
            service.configureRecoveryScopes(scopes);
            fake.reply(reply());
            commit();
        }
        assertThat(fake.requests()).hasSize(4);
        verify(repository, times(4)).complete(any(), any());
    }

    private void enable() {
        properties.setEnabled(true);
        properties.useCase(OrderAdvisoryQuestions.USE_CASE).setEnabled(true);
    }

    private OrderAdvisoryService service(JevTransport transport) {
        return new OrderAdvisoryService(new JevClient(properties, transport), properties, runner, repository,
                "scope-local", Duration.ofMinutes(15), meters, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void snapshots() {
        when(repository.snapshots("order-local", "scope-local", NOW)).thenReturn(List.of(
                new OrderAdvisorySnapshot("scope-local", "customer-local", "order-local", 2,
                        "exception-local", 3, NOW, null)));
    }

    private void successfulClaim() {
        snapshots();
        when(repository.claim(any(), anyString(), any(), any(), anyString(), anyString(), any())).thenReturn(true);
    }

    private void commit() { tx.executeWithoutResult(status -> service.afterCommit("order-local")); }

    private static String reply() {
        return """
                {"investigation":{"type":"choice","choice":"INSUFFICIENT_INFORMATION","confidence":1,
                  "probabilities":{"CHECK_SOURCE":0,"CHECK_FULFILLMENT_EVIDENCE":0,"CHECK_ACTIVATION_ACK":0,
                  "CHECK_FINANCE_ACK":0,"CHECK_DELIVERY_STATUS":0,"ESCALATE_INTEGRATION":0,"INSUFFICIENT_INFORMATION":1}}}
                """;
    }

    /** Spring drives the real synchronization lifecycle; no database or manually-fired callback is used. */
    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
