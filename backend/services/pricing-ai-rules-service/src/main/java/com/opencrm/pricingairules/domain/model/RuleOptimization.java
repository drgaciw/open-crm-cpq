package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_optimization", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleOptimization extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "rule_id")
    private Rule rule;

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private RuleSet ruleSet;

    @Column(name = "original_content", nullable = false, columnDefinition = "text")
    private String originalContent;

    @Column(name = "optimized_content", nullable = false, columnDefinition = "text")
    private String optimizedContent;

    @Column(name = "optimization_type", nullable = false)
    private String optimizationType;

    @Column(name = "performance_improvement_pct", precision = 5, scale = 2)
    private BigDecimal performanceImprovementPct;

    @Column(name = "applied")
    private Boolean applied;

    @Column(name = "applied_at")
    private ZonedDateTime appliedAt;

    @OneToMany(mappedBy = "optimization", cascade = CascadeType.ALL)
    @Builder.Default
    private List<OptimizationMetrics> metrics = new ArrayList<>();
}
