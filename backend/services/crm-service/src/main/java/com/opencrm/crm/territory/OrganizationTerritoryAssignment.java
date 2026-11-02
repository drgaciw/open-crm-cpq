package com.opencrm.crm.territory;

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
 * The territory and segment currently assigned to one organization.
 * A soft-deleted row means the organization is unassigned.
 */
@Entity
@Table(name = "organization_territory_assignments", indexes = {
        @Index(name = "idx_org_territory_assignment_rule", columnList = "rule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrganizationTerritoryAssignment extends BaseEntity {

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "territory_code", nullable = false, length = 255)
    private String territoryCode;

    @Column(name = "segment_code", nullable = false, length = 255)
    private String segmentCode;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
}
