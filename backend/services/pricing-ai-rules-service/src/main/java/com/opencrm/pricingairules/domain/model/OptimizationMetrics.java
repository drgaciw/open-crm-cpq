package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "optimization_metrics", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "optimization_metrics_seq")
    @SequenceGenerator(name = "optimization_metrics_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "optimization_id", nullable = false)
    private RuleOptimization optimization;

    @Column(name = "original_execution_time_ms")
    private Long originalExecutionTimeMs;

    @Column(name = "optimized_execution_time_ms")
    private Long optimizedExecutionTimeMs;

    @Column(name = "improvement_pct", precision = 5, scale = 2)
    private BigDecimal improvementPct;

    @Column(name = "tested_at", updatable = false)
    private ZonedDateTime testedAt;

    @PrePersist
    protected void onCreate() {
        testedAt = ZonedDateTime.now();
    }
}
