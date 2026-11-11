package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Commercial policy (BC-04-01, DRG-441): a declarative, versioned set of {@link PolicyThreshold}s governing
 * discount %, margin floor, term and non-standard clauses, scoped by {@link #segment}, {@link #productFamily} and
 * {@link #region} (null in any of the three means "applies to all").
 *
 * <p>{@link #policyVersion} is a local integer starting at 1 — the same stand-in already used by
 * {@code pricing-service} {@code RuleSet.ruleSetVersion} and {@code crm-service} {@code Playbook.definitionVersion}:
 * BC-18 (cross-service configuration/model versioning) is not landed, so this is not a promoted BC-18 revision.
 * Defining a revision creates a new row — {@link #supersedesId} names the earlier version it replaces, which is
 * deprecated in the same transaction (mirroring the "supersede" vocabulary already used by
 * {@code ApprovalRequest#supersede} and the contract-asset superseded lifecycle) — so each defined version stays
 * immutable and independently auditable.
 */
@Entity
@Table(name = "commercial_policies", indexes = {
    @Index(name = "idx_commercial_policy_status", columnList = "status"),
    @Index(name = "idx_commercial_policy_segment", columnList = "segment"),
    @Index(name = "idx_commercial_policy_product_family", columnList = "product_family"),
    @Index(name = "idx_commercial_policy_region", columnList = "region")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CommercialPolicy extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Column(name = "policy_version", nullable = false)
    @Builder.Default
    private Integer policyVersion = 1;

    /** The earlier policy version this one replaces, when defined as a revision. Null for the first version. */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    /** Null = applies to every customer segment. */
    @Column(name = "segment", length = 100)
    private String segment;

    /** Null = applies to every product family. */
    @Column(name = "product_family", length = 100)
    private String productFamily;

    /** Null = applies to every region. */
    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PolicyThreshold> thresholds = new ArrayList<>();

    public void addThreshold(PolicyThreshold threshold) {
        if (thresholds == null) {
            thresholds = new ArrayList<>();
        }
        threshold.setPolicy(this);
        thresholds.add(threshold);
    }

    /** Only an ACTIVE policy can be deprecated — either superseded by a new version, or retired directly. */
    public void deprecate() {
        if (status != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE commercial policies can be deprecated");
        }
        this.status = PolicyStatus.DEPRECATED;
    }
}
