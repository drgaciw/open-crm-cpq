package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Atomic apply unit. The primary key (document_id, unit_no) is the apply idempotency key. */
@Entity
@Table(name = "amendment_application_units")
@IdClass(AmendmentApplicationUnitKey.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AmendmentApplicationUnit extends DocumentAuditedEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "unit_no", nullable = false)
    private Integer unitNo;

    @Column(name = "dependency_order", nullable = false)
    private Integer dependencyOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private ApplyTargetType targetType;

    @Column(name = "target_ref", nullable = false, length = 255)
    private String targetRef;

    @Column(name = "observed_commercial_revision", nullable = false)
    private Long observedCommercialRevision;

    @Column(name = "applied_commercial_revision")
    private Long appliedCommercialRevision;

    @Column(name = "apply_key_digest", length = 64)
    private String applyKeyDigest;

    /** Evidence committed with the terminal unit; null marks an unverifiable legacy attempt. */
    @Column(name = "attempt_revision_no")
    private Integer attemptRevisionNo;

    @Column(name = "attempt_pin_fingerprint", length = 67)
    private String attemptPinFingerprint;

    @Column(name = "block_reason", length = 64)
    private String blockReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ApplicationUnitStatus status;
}
