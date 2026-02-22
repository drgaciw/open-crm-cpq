package com.opencrm.pricingairules.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "rule_execution_metrics", schema = "pricing_ai_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_execution_metrics_seq")
    @SequenceGenerator(name = "rule_execution_metrics_seq", sequenceName = "pricing_ai_rules.hibernate_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rule_id")
    private Rule rule;

    @ManyToOne
    @JoinColumn(name = "rule_set_id")
    private RuleSet ruleSet;

    @ManyToOne
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "execution_date", updatable = false)
    private ZonedDateTime executionDate;

    @PrePersist
    protected void onCreate() {
        executionDate = ZonedDateTime.now();
    }
}
