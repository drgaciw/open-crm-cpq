package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Quote-local view of pricing financial metrics used by calculate apply.
 *
 * <p>Tolerates fields pricing adds later: {@link PricingServiceClient} parses with a default mapper, which would
 * otherwise reject any unknown field and fail the whole calculate (ADR-006 additive-contract convention).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PricingFinancialMetricsView {

    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    /** Optional exit ARR. Absent until pricing emits it. Not required. */
    private BigDecimal exitArr;
    private BigDecimal clv;
    private Integer churnScore;
    private BigDecimal discountRate;
    private BigDecimal totalDiscount;
    private BigDecimal totalSurcharge;
    private BigDecimal priceMultiplier;
    /** A PERCENT (40.0 = 40%). The 40.0 placeholder unless pricing runs with the cost basis; null on a missing cost. */
    private BigDecimal estimatedMarginPct;
    /** Optional cost-basis margin as a FRACTION (0.35 = 35%). Absent under the placeholder or on a missing cost. */
    private BigDecimal marginFraction;
    /** Optional. {@code cost-basis} when pricing derived margin from the cost basis; absent otherwise. */
    private String marginSource;
}
