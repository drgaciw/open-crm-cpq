package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rule_optimization", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleOptimization {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_optimization_seq")
    @SequenceGenerator(name = "rule_optimization_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

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

    @OneToMany(mappedBy = "optimization", cascade = CascadeType.ALL)
    private List<OptimizationMetrics> metrics = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "applied_at")
    private ZonedDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
