package com.opencrm.dealdesk.model.tcv;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PricingModel {

    private BigDecimal averagePayment;
    private List<RepricingTriggers> repricingTriggers;
    private BigDecimal creditPercentage;
    private BigDecimal commercialCreditPercentage;
    private BigDecimal durbinRegulatedPercentage;
}
