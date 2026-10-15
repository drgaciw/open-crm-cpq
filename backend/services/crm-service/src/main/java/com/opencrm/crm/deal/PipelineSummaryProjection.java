package com.opencrm.crm.deal;

import java.math.BigDecimal;

/**
 * Spring Data JPA interface projection for deal pipeline aggregates by stage, in the base currency (ADR-032).
 */
public interface PipelineSummaryProjection {
    DealStage getStage();

    Long getCount();

    BigDecimal getTotalAmount();

    BigDecimal getWeightedAmount();
    Long getUnconvertedCount();
}
