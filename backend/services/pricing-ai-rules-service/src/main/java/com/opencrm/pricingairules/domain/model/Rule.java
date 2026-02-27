package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Rule extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "status", nullable = false)
    private String status;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleDependency> dependencies = new ArrayList<>();

    @OneToMany(mappedBy = "dependentRule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RuleDependency> dependents = new ArrayList<>();
}
