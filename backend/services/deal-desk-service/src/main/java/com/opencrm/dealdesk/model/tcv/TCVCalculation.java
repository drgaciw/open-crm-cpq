package com.opencrm.dealdesk.model.tcv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TCVCalculation {

    private BigDecimal baseValue;
    private BigDecimal volumeDiscountAmount;
    private BigDecimal earlyPaymentDiscountAmount;
    private BigDecimal totalAdjustments;
    private BigDecimal contingentRevenueAmount;
    private BigDecimal finalValue;
}
