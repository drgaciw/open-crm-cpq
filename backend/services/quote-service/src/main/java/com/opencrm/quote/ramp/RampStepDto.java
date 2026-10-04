package com.opencrm.quote.ramp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Same field names as the shared {@code RampStep} contract. No validation here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampStepDto {

    private Integer sequence;
    private Integer startMonth;
    private Integer months;
    private Integer quantity;
    private RampPriceBasis priceBasis;
    private BigDecimal unitPrice;
    private BigDecimal discountFraction;
}
