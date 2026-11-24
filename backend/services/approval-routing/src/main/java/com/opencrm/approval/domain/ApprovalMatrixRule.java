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
 * One role inside one step of a matrix version, for one breach type and an amount band
 * {@code [amountMin, amountMax)}. Null bounds are open. The role is an existing quote tier
 * ({@link QuoteApprovalTier}); {@link com.opencrm.approval.service.ApprovalAuthority} still decides who may act.
 */
@Entity
@Table(name = "approval_matrix_rules", indexes = {
        @Index(name = "idx_approval_matrix_rule_matrix", columnList = "matrix_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalMatrixRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matrix_id", nullable = false)
    private ApprovalMatrix matrix;

    /** DRG-441 {@code ThresholdType} name. */
    @Column(name = "breach_type", nullable = false, length = 30)
    private String breachType;

    @Column(name = "amount_min", precision = 19, scale = 4)
    private BigDecimal amountMin;

    @Column(name = "amount_max", precision = 19, scale = 4)
    private BigDecimal amountMax;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_mode", nullable = false, length = 16)
    private MatrixStepMode stepMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false, length = 20)
    private QuoteApprovalTier approverRole;
}
