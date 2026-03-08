package com.opencrm.dealdesk.rules.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "rule_versions", schema = "deal_desk",
        uniqueConstraints = @UniqueConstraint(name = "uk_rule_versions_rule_version", columnNames = {
                "deal_rule_id", "rule_version_number"
        }),
        indexes = {
                @Index(name = "idx_rule_versions_rule", columnList = "deal_rule_id"),
                @Index(name = "idx_rule_versions_active", columnList = "active")
        })
@Getter
@Setter
@NoArgsConstructor
public class RuleVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_rule_id", nullable = false)
    private DealRule dealRule;

    @Column(name = "rule_version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "condition_expression", nullable = false, columnDefinition = "TEXT")
    private String conditionExpression;

    @Column(name = "action_expression", columnDefinition = "TEXT")
    private String actionExpression;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "draft", nullable = false)
    private boolean draft = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = new LinkedHashMap<>();
}
