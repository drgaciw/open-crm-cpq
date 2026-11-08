package com.opencrm.crm.organization;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Audit row for one account hierarchy link / re-link / unlink (DRG-714).
 * Written in the same transaction as the parent change and {@code HierarchyChanged} outbox row.
 */
@Entity
@Table(name = "account_hierarchy_changes", indexes = {
        @Index(name = "idx_account_hierarchy_changes_child", columnList = "child_organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AccountHierarchyChange extends BaseEntity {

    @Column(name = "child_organization_id", nullable = false)
    private Long childOrganizationId;

    @Column(name = "child_account_id", nullable = false, length = 36)
    private String childAccountId;

    @Column(name = "previous_parent_organization_id")
    private Long previousParentOrganizationId;

    @Column(name = "new_parent_organization_id")
    private Long newParentOrganizationId;

    @Column(name = "decided_by", nullable = false, length = 255)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}
