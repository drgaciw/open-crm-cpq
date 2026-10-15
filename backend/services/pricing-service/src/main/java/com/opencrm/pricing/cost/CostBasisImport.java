package com.opencrm.pricing.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Audit entry of one cost export ingestion: the export's version and approver, the verified principal who ingested
 * it (ADR-016 step 4, from the token through {@code CurrentActor}), when, the file's SHA-256, and the row counts.
 * Written in the same transaction as the rows. Never exposed at the API (ADR-006); see
 * {@link CostBasisImportSummary}.
 */
@Entity
@Table(name = "cost_basis_imports")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CostBasisImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PACKAGE)
    private Long id;

    /** Null when no row carried a parseable version. */
    @Column(name = "export_version", updatable = false)
    private Long exportVersion;

    /** Null when no row carried an approver. */
    @Column(name = "approved_by", updatable = false)
    private String approvedBy;

    @Column(name = "ingested_by", nullable = false, updatable = false)
    private String ingestedBy;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "rows_received", nullable = false, updatable = false)
    private int rowsReceived;

    /** Inserted + unchanged + superseded. */
    @Column(name = "rows_accepted", nullable = false, updatable = false)
    private int rowsAccepted;

    @Column(name = "rows_inserted", nullable = false, updatable = false)
    private int rowsInserted;

    /** Same key and version already stored with the same content: the replay no-op. */
    @Column(name = "rows_unchanged", nullable = false, updatable = false)
    private int rowsUnchanged;

    /** A higher version of the key is already stored, so this row is not. */
    @Column(name = "rows_superseded", nullable = false, updatable = false)
    private int rowsSuperseded;

    @Column(name = "rows_rejected", nullable = false, updatable = false)
    private int rowsRejected;

    /** Accepted rows whose product states no billing unit in the catalog, so the unit was not checked. */
    @Column(name = "rows_unit_unchecked", nullable = false, updatable = false)
    private int rowsUnitUnchecked;

    CostBasisImportSummary toSummary() {
        return new CostBasisImportSummary(id, exportVersion, approvedBy, ingestedBy, ingestedAt, contentSha256,
                rowsReceived, rowsAccepted, rowsInserted, rowsUnchanged, rowsSuperseded, rowsRejected,
                rowsUnitUnchecked);
    }
}
