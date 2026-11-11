package com.opencrm.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Computes the SHA-256 hash chain for {@link AuditEntry}. Package-private: callers go through
 * {@link AuditTrailService}, which is the only place that should decide what gets hashed and when.
 */
final class AuditHashing {

    /** previous_hash recorded on the first entry of the chain: SHA-256's output is 32 bytes, hex-encoded here as
     * 64 zero characters so every row's previous_hash is the same fixed-width, non-null value. */
    static final String GENESIS_HASH = "0".repeat(64);

    private static final String FIELD_SEPARATOR = "\u0001";

    private AuditHashing() {
    }

    /**
     * Hex-encoded SHA-256 over the entry's own content and the previous entry's hash. Any change to any field
     * here, or to {@code previousHash}, produces a different hash — which is what makes the chain tamper-evident:
     * changing one entry's content without recomputing everything after it leaves a detectable mismatch.
     */
    static String hash(String previousHash, String tenantId, String aggregateType, String aggregateId,
                        String principal, String action, String details, Instant occurredAt) {
        String canonical = String.join(FIELD_SEPARATOR,
                previousHash,
                nullToEmpty(tenantId),
                aggregateType,
                aggregateId,
                nullToEmpty(principal),
                action,
                nullToEmpty(details),
                occurredAt.toString());
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm; this would only fire on a broken/non-conformant JVM.
            throw new IllegalStateException("SHA-256 MessageDigest is not available", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
