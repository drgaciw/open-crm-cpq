package com.opencrm.pricing.rule;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Rule entity - represents a single pricing rule within a rule set.
 */
@Entity
@Table(name = "rules", indexes = {
    @Index(name = "idx_rule_rule_set", columnList = "rule_set_id"),
    @Index(name = "idx_rule_type", columnList = "rule_type"),
    @Index(name = "idx_rule_priority", columnList = "priority"),
    @Index(name = "idx_rule_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Rule extends BaseEntity {

    @Column(name = "rule_set_id", nullable = false)
    private Long ruleSetId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RuleStatus status = RuleStatus.DRAFT;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "condition_group", columnDefinition = "TEXT")
    private String conditionGroup;

    @Column(name = "action_set", columnDefinition = "TEXT")
    private String actionSet;

    @Column(name = "conflict_policy")
    @Builder.Default
    private String conflictPolicy = "HIGHEST_PRIORITY_THEN_IMPACT";

    @Column(name = "explanation_code")
    private String explanationCode;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
