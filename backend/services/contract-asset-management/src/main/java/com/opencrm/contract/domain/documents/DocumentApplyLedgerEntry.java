package com.opencrm.contract.domain.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.UUID;

/** One committed apply of one unit. The replay key is document, unit, and digest. */
@Entity
@Table(name = "document_apply_ledger")
@IdClass(DocumentApplyLedgerEntry.Key.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class DocumentApplyLedgerEntry extends DocumentAuditedEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "unit_no", nullable = false)
    private Integer unitNo;

    @Column(name = "apply_key_digest", nullable = false, length = 64)
    private String applyKeyDigest;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID documentId;
        private Integer unitNo;
    }
}
