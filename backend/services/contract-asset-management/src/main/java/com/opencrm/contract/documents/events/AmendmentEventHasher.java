package com.opencrm.contract.documents.events;

import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * SHA-256 over the canonical event row, excluding {@code content_hash} itself.
 *
 * <p>{@code recorded_at} is also excluded. The database trigger overwrites it on insert, and the
 * append-only trigger rejects the UPDATE that would be required to store a hash of that stamp.
 * Canonical version 1 is the contract for that choice. The genesis {@code prev_hash} for sequence 1
 * is sixty-four zeros, which satisfies the lowercase-hex check on the column.
 */
public final class AmendmentEventHasher {

    public static final int CANONICAL_VERSION = 1;

    public static final String GENESIS_PREV_HASH = "0".repeat(64);

    private AmendmentEventHasher() {
    }

    public static String canonicalForm(AmendmentDocumentEvent event) {
        return new AmendmentJson()
                .number("canonicalVersion", CANONICAL_VERSION)
                .text("actorId", event.getActorId())
                .text("actorRole", event.getActorRole())
                .text("actorType", name(event.getActorType()))
                .text("correlationId", event.getCorrelationId())
                .text("createdAt", instant(event.getCreatedAt()))
                .text("createdBy", event.getCreatedBy())
                .text("documentId", uuid(event.getDocumentId()))
                .text("eventId", uuid(event.getEventId()))
                .text("eventType", event.getEventType())
                .text("fromStatus", name(event.getFromStatus()))
                .text("occurredAt", instant(event.getOccurredAt()))
                .text("payloadDigest", event.getPayloadDigest())
                .text("payloadRef", event.getPayloadRef())
                .text("prevHash", event.getPrevHash())
                .text("reasonCode", event.getReasonCode())
                .nullableInt("revisionNo", event.getRevisionNo())
                .number("seqNo", event.getSeqNo())
                .text("sourceEventId", uuid(event.getSourceEventId()))
                .text("tenantId", uuid(event.getTenantId()))
                .text("toStatus", name(event.getToStatus()))
                .nullableInt("unitNo", event.getUnitNo())
                .finish();
    }

    public static String contentHash(AmendmentDocumentEvent event) {
        return sha256(canonicalForm(event));
    }

    public static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String instant(Instant value) {
        return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
