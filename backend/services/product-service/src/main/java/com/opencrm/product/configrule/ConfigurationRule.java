package com.opencrm.product.configrule;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "configuration_rules", indexes = {
    @Index(name = "idx_config_rule_source", columnList = "source_product_id"),
    @Index(name = "idx_config_rule_target", columnList = "target_product_id"),
    @Index(name = "idx_config_rule_type", columnList = "rule_type"),
    @Index(name = "idx_config_rule_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfigurationRule extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private ConfigRuleType ruleType;

    @Column(name = "source_product_id", nullable = false)
    private Long sourceProductId;

    @Column(name = "target_product_id")
    private Long targetProductId;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
}
