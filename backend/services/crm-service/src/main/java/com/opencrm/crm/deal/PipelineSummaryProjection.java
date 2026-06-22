package com.opencrm.crm.deal;

import java.math.BigDecimal;

public interface PipelineSummaryProjection {
    DealStage getStage();
    Long getCount();
    BigDecimal getTotalAmount();
    BigDecimal getWeightedAmount();
}
