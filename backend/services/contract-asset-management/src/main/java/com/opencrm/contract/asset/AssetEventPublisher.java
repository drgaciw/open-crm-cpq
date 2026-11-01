package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single outbox emitter for installed-asset lifecycle events.
 * Each contract is named at exactly one publish call (ADR-018 D9).
 */
@Service
public class AssetEventPublisher {

    static final String AGGREGATE_TYPE = "Asset";

    private final OutboxEventPublisher publisher;

    public AssetEventPublisher(OutboxEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** AssetCreated plus the id stored on the outbox row. */
    public record Created(UUID eventId, AssetCreatedEvent event) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Created publishCreated(Asset asset, String correlationId, String causationId) {
        UUID eventId = UUID.randomUUID();
        AssetCreatedEvent created = new AssetCreatedEvent(
                asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                asset.getEntitlementId(), asset.getProductRef(), asset.getQuantity(),
                asset.getStatus().name(), asset.getEffectiveStart(), asset.getEffectiveEnd());
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), causationId)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(),
                    EventContractRegistry.ASSET_CREATED_V1, created, eventId);
        }
        return new Created(eventId, created);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publishAmended(Asset asset, AssetAmendedEvent event, String correlationId) {
        UUID eventId = UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(),
                    EventContractRegistry.ASSET_AMENDED_V1, event, eventId);
        }
        return eventId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publishTerminated(Asset asset, AssetTerminatedEvent event, String correlationId) {
        UUID eventId = UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(),
                    EventContractRegistry.ASSET_TERMINATED_V1, event, eventId);
        }
        return eventId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publishSuperseded(Asset asset, AssetSupersededEvent event, String correlationId) {
        UUID eventId = UUID.randomUUID();
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                AssetAuditOutbox.envelopeCorrelation(correlationId), null)) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(),
                    EventContractRegistry.ASSET_SUPERSEDED_V1, event, eventId);
        }
        return eventId;
    }
}
