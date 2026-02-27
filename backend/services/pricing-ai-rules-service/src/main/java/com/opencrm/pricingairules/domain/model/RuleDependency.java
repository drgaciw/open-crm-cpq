package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "rule_dependency", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleDependency extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @ManyToOne
    @JoinColumn(name = "dependent_rule_id", nullable = false)
    private Rule dependentRule;

    @Column(name = "dependency_type", nullable = false)
    private String dependencyType;
}
