package com.opencrm.pricing.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One stored cost component of a product, effective from a date, at one export version. Append-only: rows are never
 * updated or deleted, so every cost a quote was priced with stays on record. Never exposed at the API (ADR-006).
 */
@Entity
@Table(name = "cost_basis_rows", uniqueConstraints = @UniqueConstraint(name = "uq_cost_basis_row",
        columnNames = {"product_external_id", "component", "effective_from", "version"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CostBasisRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false, updatable = false)
    private Long importId;

    @Column(name = "product_external_id", nullable = false, updatable = false)
    private String productExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component", nullable = false, updatable = false, length = 30)
    private CostComponent component;

    @Column(name = "billing_unit", nullable = false, updatable = false, length = 100)
    private String billingUnit;

    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "version", nullable = false, updatable = false)
    private long version;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private String approvedBy;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @Column(name = "ingested_by", nullable = false, updatable = false)
    private String ingestedBy;

    /** Whether this stored row says the same as an export row of the same key and version. */
    boolean sameContentAs(CostExportRow row) {
        return unitCost.compareTo(row.unitCost()) == 0
                && billingUnit.equals(row.billingUnit())
                && currency.equals(row.currency());
    }
}
