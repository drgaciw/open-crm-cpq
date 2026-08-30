package com.opencrm.crm.deal;

import java.math.BigDecimal;

/**
 * Spring Data JPA interface projection for deal pipeline aggregates by stage.
 */
public interface PipelineSummaryProjection {
    DealStage getStage();

    Long getCount();

    BigDecimal getTotalAmount();

    BigDecimal getWeightedAmount();
}
