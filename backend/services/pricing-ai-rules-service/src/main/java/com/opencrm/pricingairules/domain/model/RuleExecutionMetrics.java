package com.opencrm.pricingairules.domain.model;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "rule_execution_metrics", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RuleExecutionMetrics extends BaseEntity {

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
}
