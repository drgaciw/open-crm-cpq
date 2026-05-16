package com.opencrm.pricing.execution;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * PricingAuditLog entity - immutable audit trail of pricing executions.
 */
@Entity
@Table(name = "pricing_audit_log", indexes = {
    @Index(name = "idx_audit_quote", columnList = "quote_external_id"),
    @Index(name = "idx_audit_customer", columnList = "customer_external_id"),
    @Index(name = "idx_audit_executed", columnList = "executed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, updatable = false)
    private String uuid;

    @Column(name = "quote_external_id")
    private String quoteExternalId;

    @Column(name = "customer_external_id")
    private String customerExternalId;

    @Column(name = "rule_set_id")
    private Long ruleSetId;

    @Column(name = "input_snapshot", nullable = false, columnDefinition = "TEXT")
    private String inputSnapshot;

    @Column(name = "output_snapshot", nullable = false, columnDefinition = "TEXT")
    private String outputSnapshot;

    @Column(name = "waterfall_trace", columnDefinition = "TEXT")
    private String waterfallTrace;

    @Column(name = "rules_fired", columnDefinition = "TEXT")
    private String rulesFired;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "executed_by")
    private String executedBy;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "pricebook_version")
    private String pricebookVersion;

    @Column(name = "ruleset_version")
    private String rulesetVersion;

    @Column(name = "metrics_snapshot", columnDefinition = "TEXT")
    private String metricsSnapshot;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        }
        if (this.executedAt == null) {
            this.executedAt = Instant.now();
        }
    }
}
