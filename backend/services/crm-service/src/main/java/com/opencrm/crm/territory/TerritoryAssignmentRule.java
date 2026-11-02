package com.opencrm.crm.territory;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A rule that assigns one territory and one segment when geography, industry, and size all match.
 */
@Entity
@Table(name = "territory_assignment_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TerritoryAssignmentRule extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "country", nullable = false, length = 255)
    private String country;

    /** Null matches any state in {@link #country}. */
    @Column(name = "state", length = 255)
    private String state;

    @Column(name = "industry", nullable = false, length = 255)
    private String industry;

    /** Inclusive. Null means no lower bound. */
    @Column(name = "min_employee_count")
    private Integer minEmployeeCount;

    /** Inclusive. Null means no upper bound. */
    @Column(name = "max_employee_count")
    private Integer maxEmployeeCount;

    @Column(name = "territory_code", nullable = false, length = 255)
    private String territoryCode;

    @Column(name = "segment_code", nullable = false, length = 255)
    private String segmentCode;

    /** Lower value wins when more than one rule matches. */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
