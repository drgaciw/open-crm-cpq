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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of a renewal policy body. Keyed by tenant plus account, agreement, or document line.
 * Versions are appended. This type does not add columns to the thin contract header.
 */
@Entity
@Table(name = "renewal_policy_record", uniqueConstraints = @UniqueConstraint(
        name = "uq_renewal_policy_tenant_scope_version",
        columnNames = {"tenant_id", "scope_kind", "scope_ref", "policy_version"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RenewalPolicyRecord extends DocumentAuditedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_kind", nullable = false, length = 40)
    private RenewalPolicyScopeKind scopeKind;

    @Column(name = "scope_ref", nullable = false)
    private UUID scopeRef;

    @Column(name = "policy_version", nullable = false)
    private Integer policyVersion;

    @Column(name = "mode", nullable = false, length = 40)
    private String mode;

    @Column(name = "basis", length = 40)
    private String basis;

    @Column(name = "uplift_percent_points", precision = 7, scale = 4)
    private BigDecimal upliftPercentPoints;

    @Column(name = "max_increase_pct", precision = 8, scale = 4)
    private BigDecimal maxIncreasePct;

    @Column(name = "min_increase_pct", precision = 8, scale = 4)
    private BigDecimal minIncreasePct;

    @Column(name = "min_unit_amount", precision = 19, scale = 6)
    private BigDecimal minUnitAmount;

    @Column(name = "not_above_list")
    private Boolean notAboveList;

    @Column(name = "not_below_exit_net")
    private Boolean notBelowExitNet;

    @Column(name = "price_as_of", length = 40)
    private String priceAsOf;

    @Column(name = "retention_marker", length = 128)
    private String retentionMarker;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (policyVersion == null) {
            policyVersion = 1;
        }
        if (status == null) {
            status = STATUS_ACTIVE;
        }
    }
}
