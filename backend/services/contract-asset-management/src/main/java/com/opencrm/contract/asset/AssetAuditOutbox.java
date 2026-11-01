package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AssetAuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Stages one audit row for a commercial asset change. This does not enable the relay. */
@Service
public class AssetAuditOutbox {
    public static final String TOPIC = KafkaTopics.ASSET_AUDIT;
    public static final String AGGREGATE_TYPE = "Asset";
    /** Longer business correlations remain intact in the payload, with event-id correlation in the envelope. */
    static final int ENVELOPE_CORRELATION_LIMIT = 100;
    private final OutboxEventPublisher publisher;

    public AssetAuditOutbox(OutboxEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * {@code createdEventId} is the lifecycle outbox id this audit describes.
     * The audit row itself gets a new id: {@code outbox_events.uuid} is unique, and both contracts are outbox rows.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(Asset asset, UUID createdEventId, String correlationId) {
        AssetAuditEvent publication = new AssetAuditEvent(
                asset.getUuid(), asset.getAccountId(), asset.getContractLineId(), asset.getSubscriptionId(),
                asset.getEntitlementId(), digest(asset), correlationId, createdEventId);
        try (EventCorrelation.Scope ignored = EventCorrelation.begin(
                envelopeCorrelation(correlationId), createdEventId.toString())) {
            publisher.publish(AGGREGATE_TYPE, asset.getUuid(),
                    EventContractRegistry.ASSET_AUDIT_RECORDED_V1, publication, UUID.randomUUID());
        }
    }

    static String envelopeCorrelation(String correlationId) {
        return correlationId == null || correlationId.length() > ENVELOPE_CORRELATION_LIMIT ? null : correlationId;
    }

    private static String digest(Asset asset) {
        String value = String.join("|",
                text(asset.getUuid()), text(asset.getAccountId()), text(asset.getContractLineId()),
                String.valueOf(asset.getSubscriptionId()), text(asset.getEntitlementId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
