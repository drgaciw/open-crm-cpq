package com.opencrm.contract.signature;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * DRG-457 / BC-06-04. An envelope sent (or about to be sent) to an e-sign provider for signature.
 *
 * <p>Deliberately provider-agnostic: {@link #providerEnvelopeRef} is an opaque string the ACL adapter
 * ({@code com.opencrm.contract.signature.acl}) hands back from SendForSignature and uses on VoidEnvelope /
 * GetEnvelopeStatus. Nothing about this entity, or anything upstream of it, depends on which provider is behind the
 * ACL. {@link #aggregateType} and {@link #aggregateId} name the contract-lifecycle object this envelope was sent
 * for (for example a contract document), so other services can join back without knowing about signing at all.
 */
@Entity
@Table(name = "signature_envelopes", indexes = {
        @Index(name = "idx_signature_envelope_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_signature_envelope_provider_ref", columnList = "provider_envelope_ref")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SignatureEnvelope extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** Opaque ACL handle; never a provider SDK type, never parsed outside the ACL adapter. */
    @Column(name = "provider_envelope_ref")
    private String providerEnvelopeRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureEnvelopeStatus status;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
