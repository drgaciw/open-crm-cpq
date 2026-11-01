package com.opencrm.contract.documents.apply;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import com.opencrm.contract.asset.Asset;
import com.opencrm.contract.asset.AssetAuditOutbox;
import com.opencrm.contract.asset.AssetChangeApply;
import com.opencrm.contract.asset.AssetEventPublisher;
import com.opencrm.contract.asset.AssetHistoryEntry;
import com.opencrm.contract.asset.AssetHistoryKind;
import com.opencrm.contract.asset.AssetHistoryProjection;
import com.opencrm.contract.asset.AssetRepository;
import com.opencrm.contract.asset.AssetStatus;
import com.opencrm.contract.asset.InMemoryAssetHistoryStore;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Asset events are emitted inside the document apply unit.
 * A later apply of an already applied unit does not emit them again, including after a blocked
 * sibling is cleared and the document is applied once more.
 */
@Tag("unit")
class DocumentApplyAssetReplayTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 4, 1);

    @Test
    void clearedBlockAppliesTheBlockedUnitWithoutRepeatingTheAppliedUnit() {
        ApplyFixture fixture = new ApplyFixture();
        World world = world(fixture);
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 9L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 3L);
        fixture.line(1, ActionCode.UPDATE, "line-1", new BigDecimal("8.0000"), DAY, END, "pb-1");
        fixture.line(2, ActionCode.UPDATE, "line-2", new BigDecimal("3.0000"), DAY, END, "pb-1");
        fixture.openPeriod(first, "line-1", new BigDecimal("5.0000"), START, END);
        fixture.openPeriod(second, "line-2", new BigDecimal("1.0000"), START, END);
        Asset kept = world.seed(first.getId(), "line-1", new BigDecimal("5.0000"));
        Asset blocked = world.seed(second.getId(), "line-2", new BigDecimal("1.0000"));

        ApplyResult partial = fixture.apply();

        assertThat(partial.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(1).getBlockReason()).isEqualTo("V3");
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(kept.getStatus()).isEqualTo(AssetStatus.AMENDED);
        assertThat(kept.getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(blocked.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(world.count(EventContractRegistry.ASSET_AMENDED_V1)).isEqualTo(1);
        assertThat(world.count(EventContractRegistry.ASSET_AUDIT_RECORDED_V1)).isEqualTo(1);

        fixture.units.get(1).setStatus(ApplicationUnitStatus.PENDING);
        fixture.units.get(1).setBlockReason(null);
        fixture.units.get(1).setApplyKeyDigest(null);
        fixture.units.get(1).setAttemptRevisionNo(null);
        fixture.units.get(1).setAttemptPinFingerprint(null);
        fixture.units.get(1).setObservedCommercialRevision(second.getCommercialRevision());
        fixture.document.setStatus(AmendmentDocumentStatus.EXECUTED);

        ApplyResult completed = fixture.apply();

        assertThat(completed.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(completed.replayed()).isFalse();
        assertThat(fixture.units).allMatch(unit -> unit.getStatus() == ApplicationUnitStatus.APPLIED);
        assertThat(blocked.getStatus()).isEqualTo(AssetStatus.AMENDED);
        assertThat(blocked.getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(kept.getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(world.count(EventContractRegistry.ASSET_AMENDED_V1)).isEqualTo(2);
        assertThat(world.count(EventContractRegistry.ASSET_AUDIT_RECORDED_V1)).isEqualTo(2);
        assertThat(world.forAsset(kept.getUuid(), EventContractRegistry.ASSET_AMENDED_V1)).hasSize(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(2);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);

        List<String> before = world.historyKeys();
        world.projection.dropProjection();
        assertThat(world.historyKeys()).isEmpty();
        world.replay();
        assertThat(world.historyKeys()).containsExactlyElementsOf(before);
        int rows = world.historyKeys().size();
        world.replay();
        assertThat(world.historyKeys()).hasSize(rows);
    }

    @Test
    void swapCreatesTheSuccessorBeforeSupersedingAndASecondApplyDoesNotRepeatIt() {
        ApplyFixture fixture = new ApplyFixture();
        World world = world(fixture);
        Subscription subscription = fixture.subscription(1L, 4L, START, END);
        fixture.unit(1, subscription, 4L);
        fixture.line(1, ActionCode.SWAP, "line-1", new BigDecimal("5.0000"), DAY, END, "pb-1");
        fixture.openPeriod(subscription, "line-1", new BigDecimal("5.0000"), START, END);
        Asset predecessor = world.seed(subscription.getId(), "line-1", new BigDecimal("5.0000"));

        fixture.apply();
        ApplyResult again = fixture.apply();

        assertThat(again.replayed()).isTrue();
        assertThat(predecessor.getStatus()).isEqualTo(AssetStatus.SUPERSEDED);
        assertThat(predecessor.getSupersededBy()).isNotBlank();
        Asset successor = world.asset(predecessor.getSupersededBy());
        assertThat(successor.getStatus()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(successor.getSupersedes()).isEqualTo(predecessor.getUuid());
        SubscriptionRatePeriod opened = fixture.periods.stream()
                .filter(period -> "DOCUMENT_APPLY".equals(period.getProvenance()))
                .findFirst()
                .orElseThrow();
        assertThat(successor.getContractLineId()).isEqualTo(opened.getAcceptedLineUuid());
        assertThat(world.order()).containsExactly(
                EventContractRegistry.ASSET_CREATED_V1,
                EventContractRegistry.ASSET_AUDIT_RECORDED_V1,
                EventContractRegistry.ASSET_SUPERSEDED_V1,
                EventContractRegistry.ASSET_AUDIT_RECORDED_V1);
        assertThat(world.count(EventContractRegistry.ASSET_CREATED_V1)).isEqualTo(1);
        assertThat(world.count(EventContractRegistry.ASSET_SUPERSEDED_V1)).isEqualTo(1);
        assertThat(world.stored()).isEqualTo(2);

        List<String> before = world.historyKeys();
        world.projection.dropProjection();
        world.replay();
        assertThat(world.historyKeys()).containsExactlyElementsOf(before);
        assertThat(world.store.findByEventType(AssetHistoryKind.CREATED)).hasSize(1);
        assertThat(world.store.findByEventType(AssetHistoryKind.SUPERSEDED)).singleElement()
                .extracting(AssetHistoryEntry::getSuccessorAssetId)
                .isEqualTo(successor.getUuid());
        world.replay();
        assertThat(world.historyKeys()).containsExactlyElementsOf(before);
    }

    @Test
    void removeAndTerminateEachEmitOneTerminationAndDoNotRepeatOnReplay() {
        ApplyFixture fixture = new ApplyFixture();
        World world = world(fixture);
        Subscription first = fixture.subscription(1L, 2L, START, END);
        Subscription second = fixture.subscription(2L, 2L, START, END);
        fixture.unit(1, first, 2L);
        fixture.unit(2, second, 2L);
        fixture.line(1, ActionCode.REMOVE, "line-1", null, DAY, null, "pb-1");
        fixture.line(2, ActionCode.TERMINATE, "line-2", null, DAY, null, "pb-1");
        fixture.openPeriod(first, "line-1", new BigDecimal("4.0000"), START, END);
        fixture.openPeriod(second, "line-2", new BigDecimal("4.0000"), START, END);
        Asset removed = world.seed(first.getId(), "line-1", new BigDecimal("4.0000"));
        Asset terminated = world.seed(second.getId(), "line-2", new BigDecimal("4.0000"));

        fixture.apply();
        ApplyResult again = fixture.apply();

        assertThat(again.replayed()).isTrue();
        assertThat(removed.getStatus()).isEqualTo(AssetStatus.TERMINATED);
        assertThat(terminated.getStatus()).isEqualTo(AssetStatus.TERMINATED);
        assertThat(world.count(EventContractRegistry.ASSET_TERMINATED_V1)).isEqualTo(2);
        assertThat(world.count(EventContractRegistry.ASSET_AUDIT_RECORDED_V1)).isEqualTo(2);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(2);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isZero();

        List<String> before = world.historyKeys();
        world.projection.dropProjection();
        world.replay();
        assertThat(world.historyKeys()).containsExactlyElementsOf(before);
        world.replay();
        assertThat(world.historyKeys()).hasSize(before.size());
    }

    private static World world(ApplyFixture fixture) {
        World world = new World();
        fixture.orchestrator.setAssetChanges(world.apply);
        return world;
    }

    private static final class World {
        final Map<String, Asset> assets = new LinkedHashMap<>();
        final List<Publication> publications = new ArrayList<>();
        final InMemoryAssetHistoryStore store = new InMemoryAssetHistoryStore();
        final AssetHistoryProjection projection = new AssetHistoryProjection(store);
        final AssetChangeApply apply;

        World() {
            AssetRepository repository = mock(AssetRepository.class);
            OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
            when(repository.findBySubscriptionIdAndContractLineIdAndDeletedFalse(any(), any()))
                    .thenAnswer(call -> Optional.ofNullable(assets.get(key(call.getArgument(0), call.getArgument(1)))));
            when(repository.save(any(Asset.class))).thenAnswer(call -> {
                Asset asset = call.getArgument(0);
                if (asset.getId() == null) {
                    asset.setId((long) assets.size() + 1);
                }
                assets.entrySet().removeIf(entry -> asset.getUuid() != null
                        && asset.getUuid().equals(entry.getValue().getUuid()));
                assets.put(key(asset.getSubscriptionId(), asset.getContractLineId()), asset);
                return asset;
            });
            doAnswer(call -> {
                publications.add(new Publication(call.getArgument(1), call.getArgument(2), call.getArgument(3),
                        call.getArgument(4).toString()));
                return null;
            }).when(publisher).publish(any(), any(), any(), any(), any());
            apply = new AssetChangeApply(repository, new AssetEventPublisher(publisher),
                    new AssetAuditOutbox(publisher), projection);
        }

        Asset seed(Long subscriptionId, String contractLineId, BigDecimal quantity) {
            Asset asset = Asset.builder()
                    .accountId("acct-" + subscriptionId)
                    .contractLineId(contractLineId)
                    .subscriptionId(subscriptionId)
                    .productRef("prod-" + contractLineId)
                    .quantity(quantity)
                    .status(AssetStatus.ACTIVE)
                    .effectiveStart(START)
                    .effectiveEnd(END)
                    .build();
            asset.setUuid("asset-" + subscriptionId + "-" + contractLineId);
            asset.setId(subscriptionId);
            assets.put(key(subscriptionId, contractLineId), asset);
            return asset;
        }

        Asset asset(String uuid) {
            return assets.values().stream().filter(row -> uuid.equals(row.getUuid())).findFirst().orElseThrow();
        }

        int stored() {
            return assets.size();
        }

        long count(EventContract contract) {
            return publications.stream().filter(row -> contract.equals(row.contract())).count();
        }

        List<Publication> forAsset(String assetId, EventContract contract) {
            return publications.stream()
                    .filter(row -> contract.equals(row.contract()) && assetId.equals(row.aggregateId()))
                    .toList();
        }

        List<EventContract> order() {
            return publications.stream().map(Publication::contract).toList();
        }

        List<String> historyKeys() {
            List<String> keys = new ArrayList<>();
            for (Asset asset : assets.values()) {
                for (AssetHistoryEntry row : store.findByAssetId(asset.getUuid())) {
                    keys.add(row.getEventId() + "|" + row.getEventType() + "|" + row.getAssetId()
                            + "|" + row.getSuccessorAssetId() + "|" + row.getQuantity());
                }
            }
            return keys;
        }

        void replay() {
            for (Publication publication : publications) {
                if (EventContractRegistry.ASSET_CREATED_V1.equals(publication.contract())) {
                    projection.apply((AssetCreatedEvent) publication.payload(), publication.eventId());
                } else if (EventContractRegistry.ASSET_AMENDED_V1.equals(publication.contract())) {
                    projection.apply((AssetAmendedEvent) publication.payload(), publication.eventId());
                } else if (EventContractRegistry.ASSET_SUPERSEDED_V1.equals(publication.contract())) {
                    projection.apply((AssetSupersededEvent) publication.payload(), publication.eventId());
                } else if (EventContractRegistry.ASSET_TERMINATED_V1.equals(publication.contract())) {
                    projection.apply((AssetTerminatedEvent) publication.payload(), publication.eventId());
                }
            }
        }

        private static String key(Long subscriptionId, String contractLineId) {
            return subscriptionId + ":" + contractLineId;
        }
    }

    private record Publication(String aggregateId, EventContract contract, Object payload, String eventId) {
    }
}
