package com.opencrm.contract.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Service-owned contracted facts; effectiveEndDate can shorten coverage without erasing sold history. */
@Entity
@Table(name = "subscription_rate_periods", uniqueConstraints = @UniqueConstraint(
        name = "uq_subscription_line_period", columnNames = {"subscription_id", "accepted_line_uuid", "sequence"}),
        indexes = @Index(name = "idx_rate_due", columnList = "subscription_id,applied,start_date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class SubscriptionRatePeriod extends BaseEntity {
    @Column(name = "subscription_id", nullable = false) private Long subscriptionId;
    @Column(name = "accepted_line_uuid", nullable = false, length = 36) private String acceptedLineUuid;
    @Column(name = "source_line_uuid", length = 36) private String sourceLineUuid;
    @Column(name = "accepted_quote_id", length = 255) private String acceptedQuoteId;
    @Column(name = "product_external_id", length = 255) private String productExternalId;
    @Column(name = "product_name", length = 255) private String productName;
    @Column(name = "product_type", length = 50) private String productType;
    @Column(name = "line_item_type", length = 50) private String lineItemType;
    @Column(name = "billing_frequency", length = 30) private String billingFrequency;
    @Column(nullable = false) private Integer sequence;
    @Column(name = "start_month") private Integer startMonth;
    @Column private Integer months;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "billing_anchor_date", nullable = false) private LocalDate billingAnchorDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "effective_end_date", nullable = false) private LocalDate effectiveEndDate;
    @Column(precision = 19, scale = 4) private BigDecimal quantity;
    @Column(name = "net_unit_price", precision = 19, scale = 4) private BigDecimal netUnitPrice;
    @Column(name = "net_monthly", precision = 19, scale = 2) private BigDecimal netMonthly;
    @Column(name = "annual_run_rate", precision = 19, scale = 2) private BigDecimal annualRunRate;
    @Column(name = "period_amount", precision = 19, scale = 2) private BigDecimal periodAmount;
    @Column(name = "gross_period_amount", precision = 19, scale = 2) private BigDecimal grossPeriodAmount;
    @Column(name = "migration_credit", precision = 19, scale = 2) private BigDecimal migrationCredit;
    @Column(name = "discount_rate", precision = 19, scale = 8) private BigDecimal discountRate;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "explanation_codes", columnDefinition = "jsonb")
    private List<String> explanationCodes;
    @Column(name = "currency_code", length = 3) private String currencyCode;
    @Column(nullable = false, length = 30) private String provenance;
    @Column(nullable = false) @Builder.Default private boolean applied = false;
    @Column(nullable = false) @Builder.Default private boolean retired = false;
    @Column(name = "billing_period_start") private LocalDate billingPeriodStart;
    @Column(name = "billing_period_end") private LocalDate billingPeriodEnd;
}
