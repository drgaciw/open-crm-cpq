package com.opencrm.contract.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Append-only canonical commercial envelope. Frozen bytes are never updated. */
@Entity
@Table(name = "amendment_commercial_envelopes")
public class AmendmentCommercialEnvelope {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "envelope_schema_version", nullable = false)
    private int envelopeSchemaVersion;

    @Column(name = "envelope_digest", nullable = false, length = 64)
    private String envelopeDigest;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "canonical_bytes", nullable = false)
    private byte[] canonicalBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    public static AmendmentCommercialEnvelope create(UUID id, UUID tenantId, UUID documentId, int revisionNo,
                                                     String digest, byte[] bytes, String createdBy) {
        AmendmentCommercialEnvelope row = new AmendmentCommercialEnvelope();
        row.id = id;
        row.tenantId = tenantId;
        row.documentId = documentId;
        row.revisionNo = revisionNo;
        row.envelopeSchemaVersion = 1;
        row.envelopeDigest = digest;
        row.canonicalBytes = bytes;
        row.createdAt = Instant.now();
        row.createdBy = createdBy;
        return row;
    }

    public UUID getId() {
        return id;
    }

    public byte[] getCanonicalBytes() {
        return canonicalBytes;
    }

    public String getEnvelopeDigest() {
        return envelopeDigest;
    }
}
