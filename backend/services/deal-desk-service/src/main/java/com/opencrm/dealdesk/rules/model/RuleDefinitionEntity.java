package com.opencrm.dealdesk.rules.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "rule_definitions", schema = "deal_desk", indexes = {
        @Index(name = "idx_rule_definitions_category", columnList = "category"),
        @Index(name = "idx_rule_definitions_enabled", columnList = "enabled")
})
@Getter
@Setter
@NoArgsConstructor
public class RuleDefinitionEntity extends BaseEntity {

    @Column(name = "rule_key", nullable = false, unique = true)
    private String ruleKey;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "action_expression", columnDefinition = "TEXT")
    private String actionExpression;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json", columnDefinition = "jsonb")
    private Map<String, Object> parametersJson = new LinkedHashMap<>();
}
