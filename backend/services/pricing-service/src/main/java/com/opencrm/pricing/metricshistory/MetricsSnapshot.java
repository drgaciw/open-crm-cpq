package com.opencrm.pricing.metricshistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * On-demand financial metrics snapshot (PD2). Formulas stay in {@code MetricsCalculator}.
 */
@Entity
@Table(name = "metrics_snapshots", indexes = {
        @Index(name = "idx_metrics_snap_customer", columnList = "customer_external_id, snapshot_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", unique = true, nullable = false, updatable = false)
    private String uuid;

    @Column(name = "customer_external_id", nullable = false)
    private String customerExternalId;

    @Column(name = "quote_external_id")
    private String quoteExternalId;

    @Column(name = "arr", precision = 19, scale = 2)
    private BigDecimal arr;

    @Column(name = "tcv", precision = 19, scale = 2)
    private BigDecimal tcv;

    @Column(name = "acv", precision = 19, scale = 2)
    private BigDecimal acv;

    @Column(name = "clv", precision = 19, scale = 2)
    private BigDecimal clv;

    @Column(name = "churn_score")
    private Integer churnScore;

    @Column(name = "discount_rate", precision = 19, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "total_discount", precision = 19, scale = 2)
    private BigDecimal totalDiscount;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        if (this.snapshotAt == null) {
            this.snapshotAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }
}
