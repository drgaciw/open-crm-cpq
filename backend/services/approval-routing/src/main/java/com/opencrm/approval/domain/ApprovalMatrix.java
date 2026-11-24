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

import java.util.ArrayList;
import java.util.List;

/**
 * One version of a breach-to-role approval matrix (BC-04-03, DRG-443). Versioning matches commercial policy:
 * a revision is a new row, the predecessor is deprecated, and in-flight requests keep the version they bound.
 */
@Entity
@Table(name = "approval_matrices", indexes = {
        @Index(name = "idx_approval_matrix_tenant_status", columnList = "tenant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ApprovalMatrix extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MatrixStatus status = MatrixStatus.ACTIVE;

    @Column(name = "matrix_version", nullable = false)
    @Builder.Default
    private Integer matrixVersion = 1;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    /** Entra {@code tid}. A matrix is visible only inside its tenant. */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @OneToMany(mappedBy = "matrix", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC, id ASC")
    @Builder.Default
    private List<ApprovalMatrixRule> rules = new ArrayList<>();

    public void addRule(ApprovalMatrixRule rule) {
        if (rules == null) {
            rules = new ArrayList<>();
        }
        rule.setMatrix(this);
        rules.add(rule);
    }

    public void deprecate() {
        if (status != MatrixStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE approval matrices can be deprecated");
        }
        this.status = MatrixStatus.DEPRECATED;
    }
}
