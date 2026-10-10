package com.opencrm.pricing.ramp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.pricing.waterfall.PricingWaterfallStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One priced ramp period. Field names match the quote view {@code PricedPeriodView}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricedPeriod {

    private Integer sequence;
    private Integer startMonth;
    private Integer months;
    private Integer quantity;
    private BigDecimal netUnitPrice;
    private BigDecimal netMonthly;
    private BigDecimal periodAmount;
    /** First declining period only; one-time credit never changes the recurring rate. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal grossPeriodAmount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal migrationCredit;
    private BigDecimal discountRate;
    private List<String> explanationCodes;
    private List<PricingWaterfallStep> waterfall;
}
