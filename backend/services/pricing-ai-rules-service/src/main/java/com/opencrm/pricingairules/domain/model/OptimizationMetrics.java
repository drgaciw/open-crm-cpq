package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "optimization_metrics", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OptimizationMetrics extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "optimization_id", nullable = false)
    private RuleOptimization optimization;

    @Column(name = "original_execution_time_ms")
    private Long originalExecutionTimeMs;

    @Column(name = "optimized_execution_time_ms")
    private Long optimizedExecutionTimeMs;

    @Column(name = "improvement_pct", precision = 5, scale = 2)
    private BigDecimal improvementPct;
}
