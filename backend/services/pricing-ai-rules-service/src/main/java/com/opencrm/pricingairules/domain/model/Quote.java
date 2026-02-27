package com.opencrm.pricingairules.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quote", schema = "pricing_ai_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Quote extends BaseEntity {

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "quote_name")
    private String quoteName;

    @Column(name = "subscription_type")
    private String subscriptionType;

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Column(name = "monthly_price", precision = 19, scale = 4)
    private BigDecimal monthlyPrice;

    @Column(name = "base_price", precision = 19, scale = 4)
    private BigDecimal basePrice;

    @Column(name = "final_price", precision = 19, scale = 4)
    private BigDecimal finalPrice;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "discount_rate", precision = 5, scale = 4)
    private BigDecimal discountRate;

    @Column(name = "price_multiplier", precision = 5, scale = 4)
    private BigDecimal priceMultiplier;

    @Column(name = "status", nullable = false)
    private String status;

    // Financial metrics
    @Column(name = "arr", precision = 19, scale = 4)
    private BigDecimal ARR;

    @Column(name = "tcv", precision = 19, scale = 4)
    private BigDecimal TCV;

    @Column(name = "acv", precision = 19, scale = 4)
    private BigDecimal ACV;

    @Column(name = "clv", precision = 19, scale = 4)
    private BigDecimal CLV;

    // Additional fields
    @Column(name = "user_count")
    private Integer userCount;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "expected_duration")
    private Integer expectedDuration;

    @Column(name = "is_multi_region")
    private Boolean isMultiRegion;

    @Column(name = "has_custom_support")
    private Boolean hasCustomSupport;

    @Column(name = "churn_adjustment", precision = 5, scale = 4)
    private BigDecimal churnAdjustment;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode metadata;

    @Column(name = "discount_amount", precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Embedded
    private CustomerFeedback customerFeedback;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QuoteLineItem> lineItems = new ArrayList<>();

    /**
     * Get the total price of this quote
     *
     * @return the total price
     */
    public double getTotalPrice() {
        if (finalPrice != null) {
            return finalPrice.doubleValue();
        } else if (basePrice != null) {
            return basePrice.doubleValue();
        }
        return 0.0;
    }

    /**
     * Get the discount amount for this quote
     *
     * @return the discount amount
     */
    public double getDiscountAmount() {
        if (discountAmount != null) {
            return discountAmount.doubleValue();
        } else if (basePrice != null && finalPrice != null && basePrice.compareTo(finalPrice) > 0) {
            return basePrice.subtract(finalPrice).doubleValue();
        }
        return 0.0;
    }
}
