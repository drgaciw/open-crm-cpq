package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Amendment document aggregate root. State transitions are DRG-581; this mapping only stores the row. */
@Entity
@Table(name = "amendment_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uq_amendment_document_lifecycle", columnNames = "lifecycle_change_id"),
        @UniqueConstraint(name = "uq_amendment_document_seq", columnNames = {"contract_id", "amendment_seq"})
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AmendmentDocument extends DocumentAuditedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "lifecycle_change_id", nullable = false)
    private UUID lifecycleChangeId;

    @Column(name = "lifecycle_change_revision", nullable = false)
    private Integer lifecycleChangeRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", nullable = false, length = 40)
    private AmendmentIntent intent;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiation_trigger", nullable = false, length = 40)
    private InitiationTrigger initiationTrigger;

    @Column(name = "request_identifier", length = 255)
    private String requestIdentifier;

    @Column(name = "provisional_number", nullable = false, length = 128)
    private String provisionalNumber;

    @Column(name = "amendment_seq")
    private Integer amendmentSeq;

    @Column(name = "amendment_number", length = 128)
    private String amendmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_kind", nullable = false, length = 40)
    private DocumentKind documentKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AmendmentDocumentStatus status;

    @Column(name = "current_revision_no", nullable = false)
    private Integer currentRevisionNo;

    @Column(name = "executed_revision_no")
    private Integer executedRevisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_policy", nullable = false, length = 40)
    private SignaturePolicy signaturePolicy;

    @Column(name = "approval_ref", length = 255)
    private String approvalRef;

    @Column(name = "signature_envelope_ref", length = 255)
    private String signatureEnvelopeRef;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "contract_effective_date", nullable = false)
    private LocalDate contractEffectiveDate;

    @Column(name = "service_activation_date")
    private LocalDate serviceActivationDate;

    @Column(name = "customer_acceptance_date")
    private LocalDate customerAcceptanceDate;

    @Column(name = "backdating_reason_code", length = 128)
    private String backdatingReasonCode;

    @Column(name = "co_term_date")
    private LocalDate coTermDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "tcv_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal tcvDelta;

    @Column(name = "arr_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal arrDelta;

    @Column(name = "one_time_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal oneTimeTotal;

    @Column(name = "credit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditTotal;

    @Column(name = "content_digest", nullable = false, length = 64)
    private String contentDigest;

    @Column(name = "rendition_digest", length = 64)
    private String renditionDigest;

    @Column(name = "signed_copy_sha256", length = 64)
    private String signedCopySha256;

    @Column(name = "template_id", length = 255)
    private String templateId;

    @Column(name = "template_version", length = 64)
    private String templateVersion;

    @Column(name = "supersedes_document_id")
    private UUID supersedesDocumentId;

    @Column(name = "superseded_by_document_id")
    private UUID supersededByDocumentId;

    @Column(name = "voids_document_id")
    private UUID voidsDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_class", nullable = false, length = 40)
    private EvidenceClass evidenceClass;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
