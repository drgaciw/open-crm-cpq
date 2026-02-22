package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "financial_metrics_log", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetricsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "financial_metrics_log_seq")
    @SequenceGenerator(name = "financial_metrics_log_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "metric_name", nullable = false)
    private String metricName;

    @Column(name = "metric_value", precision = 19, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "recorded_at", updatable = false)
    private ZonedDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = ZonedDateTime.now();
    }
}
