package com.opencrm.crm.deal;

import java.math.BigDecimal;

/**
 * Projection for pipeline summary statistics.
 */
public interface PipelineSummaryProjection {
    DealStage getStage();
    Long getCount();
    BigDecimal getTotalAmount();
    BigDecimal getWeightedAmount();
}
