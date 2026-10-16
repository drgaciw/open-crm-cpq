package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/** One revision of an amendment document. A frozen row cannot be updated or deleted. */
@Entity
@Table(name = "amendment_document_revisions", uniqueConstraints = @UniqueConstraint(
        name = "uq_amendment_revision_lifecycle",
        columnNames = {"document_id", "lifecycle_change_revision"}))
@IdClass(AmendmentRevisionKey.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AmendmentDocumentRevision extends DocumentAuditedEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    @Column(name = "lifecycle_change_revision", nullable = false)
    private Integer lifecycleChangeRevision;

    @Column(name = "target_quote_id", nullable = false)
    private UUID targetQuoteId;

    @Column(name = "content_digest", nullable = false, length = 64)
    private String contentDigest;

    @Column(name = "inputs_snapshot_ref", nullable = false, length = 1024)
    private String inputsSnapshotRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_close", nullable = false, length = 40)
    private RevisionCloseStatus statusAtClose;

    @Column(name = "frozen_at")
    private Instant frozenAt;
}
