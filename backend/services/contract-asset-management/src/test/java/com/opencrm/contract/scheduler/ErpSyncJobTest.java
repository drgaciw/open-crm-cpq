package com.opencrm.contract.scheduler;

import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.acl.ErpRateLimitException;
import com.opencrm.contract.acl.ErpSyncException;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ErpSyncJob (ADR-018 D6: ERP sync outside the listener transaction)")
class ErpSyncJobTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Mock
    private SubscriptionRepository repository;

    @Mock
    private ErpAdapter erpAdapter;

    private ErpSyncJob job;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        job = new ErpSyncJob(repository, erpAdapter, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                Clock.fixed(NOW, ZoneOffset.UTC), 20, Duration.ofMinutes(10));
        subscription = Subscription.builder()
                .subscriptionNumber("SUB-Q-9").customerId("C-1").quoteId("Q-9")
                .status(SubscriptionStatus.PENDING).erpSyncStatus(ErpSyncStatus.IN_PROGRESS).build();
        subscription.setId(5L);
        when(repository.findIdsByErpSyncStatus(eq(ErpSyncStatus.QUEUED), any(Pageable.class))).thenReturn(List.of(5L));
        when(repository.transitionErpSyncStatus(5L, ErpSyncStatus.QUEUED, ErpSyncStatus.IN_PROGRESS, NOW)).thenReturn(1);
        when(repository.findById(5L)).thenReturn(Optional.of(subscription));
    }

    @Test
    @DisplayName("a queued subscription is synced, marked SYNCED and activated")
    void syncsAndActivates() {
        when(erpAdapter.syncToErp(subscription)).thenReturn("ERP-SUB-Q-9");

        job.syncQueued();

        assertThat(subscription.getErpSyncStatus()).isEqualTo(ErpSyncStatus.SYNCED);
        assertThat(subscription.getErpSubscriptionId()).isEqualTo("ERP-SUB-Q-9");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repository).save(subscription);
    }

    @Test
    @DisplayName("a row another replica claimed first is not synced")
    void unclaimedRowIsNotSynced() {
        when(repository.transitionErpSyncStatus(5L, ErpSyncStatus.QUEUED, ErpSyncStatus.IN_PROGRESS, NOW)).thenReturn(0);

        job.syncQueued();

        verify(erpAdapter, never()).syncToErp(any());
    }

    @Test
    @DisplayName("an ERP rate limit puts the row back in the queue")
    void rateLimitRequeues() {
        when(erpAdapter.syncToErp(subscription)).thenThrow(new ErpRateLimitException("slow down"));

        job.syncQueued();

        verify(repository).transitionErpSyncStatus(5L, ErpSyncStatus.IN_PROGRESS, ErpSyncStatus.QUEUED, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("any other ERP failure marks the row FAILED and leaves the subscription PENDING")
    void failureMarksFailed() {
        when(erpAdapter.syncToErp(subscription)).thenThrow(new ErpSyncException("down", new RuntimeException()));

        job.syncQueued();

        verify(repository).transitionErpSyncStatus(5L, ErpSyncStatus.IN_PROGRESS, ErpSyncStatus.FAILED, NOW);
        verify(repository, never()).save(any());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    @DisplayName("claims left IN_PROGRESS longer than stale-after are requeued first")
    void staleClaimsAreRequeued() {
        when(repository.findIdsByErpSyncStatus(eq(ErpSyncStatus.QUEUED), any(Pageable.class))).thenReturn(List.of());

        job.syncQueued();

        verify(repository).requeueStaleErpSyncs(NOW.minus(Duration.ofMinutes(10)), NOW);
        verify(repository, never()).transitionErpSyncStatus(anyLong(), any(), any(), any());
    }
}
