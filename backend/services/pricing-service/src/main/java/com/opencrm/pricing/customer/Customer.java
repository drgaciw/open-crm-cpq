package com.opencrm.pricing.customer;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Customer entity - pricing-specific customer reference.
 */
@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customer_external_id", columnList = "external_id"),
    @Index(name = "idx_customer_segment", columnList = "segment"),
    @Index(name = "idx_customer_region", columnList = "region")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Customer extends BaseEntity {

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type")
    @Builder.Default
    private String type = "DIRECT";

    @Enumerated(EnumType.STRING)
    @Column(name = "segment")
    private CustomerSegment segment;

    @Enumerated(EnumType.STRING)
    @Column(name = "region")
    private CustomerRegion region;

    @Column(name = "industry")
    private String industry;

    @Column(name = "churn_score")
    private Integer churnScore;

    @Column(name = "annual_revenue", precision = 19, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
