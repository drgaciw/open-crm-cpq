package com.opencrm.approval.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * One declarative threshold on a {@link CommercialPolicy} version: a limit on discount %, margin floor, term, or a
 * restricted non-standard clause (BC-04-01, DRG-441). Immutable once its owning policy is defined — a revision is a
 * new {@link CommercialPolicy} version with its own thresholds, not an edit of these rows.
 */
@Entity
@Table(name = "policy_thresholds", indexes = {
    @Index(name = "idx_policy_threshold_policy", columnList = "policy_id"),
    @Index(name = "idx_policy_threshold_type", columnList = "threshold_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PolicyThreshold extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CommercialPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_type", nullable = false, length = 30)
    private ThresholdType thresholdType;

    /**
     * The limit value. Semantics depend on {@link #thresholdType}: a maximum for
     * {@code DISCOUNT_PERCENT}/{@code TERM_MONTHS}, a minimum for {@code MARGIN_FLOOR}. Null for
     * {@code NON_STANDARD_CLAUSE}, which is governed by {@link #clauseCode} instead.
     */
    @Column(name = "limit_value", precision = 19, scale = 4)
    private BigDecimal limitValue;

    /** Required, and only meaningful, for {@code NON_STANDARD_CLAUSE}: the restricted clause's code. */
    @Column(name = "clause_code", length = 100)
    private String clauseCode;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
