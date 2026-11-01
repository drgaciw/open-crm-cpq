package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.contract.documents.apply.ApplyLine;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetChangeApplyTest {

    private final AssetRepository assets = mock(AssetRepository.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final AssetAuditOutbox audit = mock(AssetAuditOutbox.class);
    private final InMemoryAssetHistoryStore historyStore = new InMemoryAssetHistoryStore();
    private final AssetChangeApply apply = new AssetChangeApply(
            assets, new AssetEventPublisher(publisher), audit, new AssetHistoryProjection(historyStore));

    private Asset asset;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        asset = Asset.builder()
                .uuid("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1")
                .accountId("acct-1")
                .contractLineId("line-1")
                .subscriptionId(1L)
                .productRef("prod-1")
                .quantity(new BigDecimal("5"))
                .status(AssetStatus.ACTIVE)
                .effectiveStart(LocalDate.parse("2026-01-01"))
                .effectiveEnd(LocalDate.parse("2026-12-31"))
                .build();
        subscription = new Subscription();
        subscription.setId(1L);
        subscription.setCustomerId("acct-1");
        when(assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(1L, "line-1")).thenReturn(Optional.of(asset));
        when(assets.save(any(Asset.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatePublishesAssetAmendedOnce() {
        apply.onUnitApplied(subscription, List.of(line(ActionCode.UPDATE, new BigDecimal("8"), null)),
                UUID.randomUUID(), 1, "corr");
        apply.onUnitApplied(subscription, List.of(line(ActionCode.UPDATE, new BigDecimal("8"), null)),
                UUID.randomUUID(), 1, "corr");

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.AMENDED);
        assertThat(asset.getQuantity()).isEqualByComparingTo("8");
        verify(publisher).publish(eq("Asset"), eq(asset.getUuid()), eq(EventContractRegistry.ASSET_AMENDED_V1),
                any(), any());
        assertThat(historyStore.findByAssetId(asset.getUuid()))
                .extracting(AssetHistoryEntry::getEventType)
                .containsExactly(AssetHistoryKind.AMENDED);
    }

    @Test
    void removePublishesAssetTerminatedAndAReplayDoesNot() {
        ApplyLine remove = line(ActionCode.REMOVE, null, LocalDate.parse("2026-06-01"));
        apply.onUnitApplied(subscription, List.of(remove), UUID.randomUUID(), 2, "corr");
        apply.onUnitApplied(subscription, List.of(remove), UUID.randomUUID(), 2, "corr");

        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TERMINATED);
        verify(publisher).publish(eq("Asset"), eq(asset.getUuid()), eq(EventContractRegistry.ASSET_TERMINATED_V1),
                any(), any());
        verify(audit).stage(eq(asset), any(), eq("corr"));
    }

    @Test
    void swapSavesTheSuccessorBeforeThePredecessorIsSuperseded() {
        ApplyLine swap = line(ActionCode.SWAP, new BigDecimal("5"), LocalDate.parse("2026-04-01"));
        apply.onUnitApplied(subscription, List.of(swap), UUID.randomUUID(), 3, "corr");

        InOrder order = inOrder(assets, publisher);
        order.verify(assets).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getStatus() == AssetStatus.ACTIVE && asset.getUuid().equals(saved.getSupersedes())));
        order.verify(publisher).publish(eq("Asset"), any(), eq(EventContractRegistry.ASSET_CREATED_V1), any(), any());
        order.verify(assets).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getStatus() == AssetStatus.SUPERSEDED && saved.getSupersededBy() != null));
        order.verify(publisher).publish(eq("Asset"), eq(asset.getUuid()),
                eq(EventContractRegistry.ASSET_SUPERSEDED_V1), any(), any());
        assertThat(historyStore.findByEventType(AssetHistoryKind.SUPERSEDED)).hasSize(1);
        assertThat(historyStore.findByEventType(AssetHistoryKind.CREATED)).hasSize(1);
        verify(audit, org.mockito.Mockito.times(2)).stage(any(), any(), eq("corr"));
    }

    @Test
    void upgradePublishesTheSuccessorBeforeThePredecessor() {
        apply.onUnitApplied(subscription, List.of(line(ActionCode.UPGRADE, new BigDecimal("5"),
                LocalDate.parse("2026-04-01"))), UUID.randomUUID(), 3, "corr");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.SUPERSEDED);
        verify(publisher).publish(eq("Asset"), any(), eq(EventContractRegistry.ASSET_CREATED_V1), any(), any());
        verify(publisher).publish(eq("Asset"), eq(asset.getUuid()),
                eq(EventContractRegistry.ASSET_SUPERSEDED_V1), any(), any());
    }

    @Test
    void terminatePublishesAssetTerminatedOnce() {
        ApplyLine terminate = line(ActionCode.TERMINATE, null, LocalDate.parse("2026-06-01"));
        apply.onUnitApplied(subscription, List.of(terminate), UUID.randomUUID(), 5, "corr");
        apply.onUnitApplied(subscription, List.of(terminate), UUID.randomUUID(), 5, "corr");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TERMINATED);
        verify(publisher).publish(eq("Asset"), eq(asset.getUuid()),
                eq(EventContractRegistry.ASSET_TERMINATED_V1), any(), any());
    }

    @Test
    void missingAssetDoesNotPublish() {
        when(assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(1L, "line-1")).thenReturn(Optional.empty());
        apply.onUnitApplied(subscription, List.of(line(ActionCode.TERMINATE, null, LocalDate.parse("2026-06-01"))),
                UUID.randomUUID(), 4, "corr");
        verify(publisher, never()).publish(any(), any(), any(), any(), any());
    }

    private static ApplyLine line(ActionCode action, BigDecimal quantity, LocalDate from) {
        return new ApplyLine(UUID.randomUUID(), 1, 1, action, "line-1", quantity, from, from,
                null, "prod-2", null);
    }
}
