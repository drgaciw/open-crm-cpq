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
@Table(name = "deal_rules", schema = "deal_desk", indexes = {
        @Index(name = "idx_deal_rules_category", columnList = "category"),
        @Index(name = "idx_deal_rules_active", columnList = "active"),
        @Index(name = "idx_deal_rules_rule_key", columnList = "rule_key")
})
@Getter
@Setter
@NoArgsConstructor
public class DealRule extends BaseEntity {

    @Column(name = "rule_key", nullable = false, unique = true)
    private String ruleKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category = "general";

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Column(name = "condition_expression", nullable = false, columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "action_expression", columnDefinition = "TEXT")
    private String actionExpression;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "current_version_number")
    private Integer currentVersionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();
}
