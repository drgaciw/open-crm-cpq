package com.opencrm.configuration.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a rule that governs configuration option relationships.
 */
@Entity
@Table(name = "config_rules", indexes = {
    @Index(name = "idx_cfg_rule_config_id", columnList = "configuration_id"),
    @Index(name = "idx_cfg_rule_source", columnList = "source_option_code"),
    @Index(name = "idx_cfg_rule_target", columnList = "target_option_code"),
    @Index(name = "idx_cfg_rule_type", columnList = "rule_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private ProductConfiguration configuration;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private RuleType ruleType;

    @Column(name = "source_option_code", nullable = false, length = 100)
    private String sourceOptionCode;

    @Column(name = "target_option_code", length = 100)
    private String targetOptionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_action", nullable = false, length = 30)
    @Builder.Default
    private RuleAction ruleAction = RuleAction.BLOCK;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "min_quantity")
    private Integer minQuantity;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
