package com.opencrm.pricing.ruleset;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * RuleSet entity - represents a versioned collection of pricing rules.
 */
@Entity
@Table(name = "rule_sets", indexes = {
    @Index(name = "idx_rule_set_status", columnList = "status"),
    @Index(name = "idx_rule_set_effective", columnList = "effective_date"),
    @Index(name = "idx_rule_set_engine", columnList = "engine_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleSet extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RuleSetStatus status = RuleSetStatus.DRAFT;

    @Column(name = "rule_set_version", nullable = false)
    @Builder.Default
    private Integer ruleSetVersion = 1;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "engine_type")
    @Builder.Default
    private String engineType = "DROOLS";

    /**
     * Activate the rule set.
     */
    public void activate() {
        if (this.status != RuleSetStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT rule sets can be activated");
        }
        this.status = RuleSetStatus.ACTIVE;
    }

    /**
     * Deprecate the rule set.
     */
    public void deprecate() {
        if (this.status != RuleSetStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE rule sets can be deprecated");
        }
        this.status = RuleSetStatus.DEPRECATED;
    }
}
