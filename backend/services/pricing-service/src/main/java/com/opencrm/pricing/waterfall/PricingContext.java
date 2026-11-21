package com.opencrm.pricing.waterfall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Context for pricing execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingContext {

    private String customerExternalId;
    private String quoteExternalId;
    private List<PricingLineRequest> lineItems;
    private String region;
    private String currency;
    private Integer durationInMonths;
    private String subscriptionType; // NEW, AMENDMENT, RENEWAL
    private Map<String, Object> metadata;

    /** Deal Desk residual: contingent amount on the quote. Null = none. */
    private BigDecimal contingentRevenueAmount;
    /** When true, commercial TCV includes {@link #contingentRevenueAmount}. ARR/ACV/CLV stay waterfall. */
    private Boolean contingentIncludedInTcv;
    /** NPV discount rate as a fraction (0.10 = 10%). Null → {@code 0.10}. */
    private BigDecimal npvDiscountRate;
}
