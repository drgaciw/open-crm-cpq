package com.opencrm.pricing.execution.margin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.pricing.cost.UnitCost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The cost basis row a margin was computed from, copied out of the port's {@link UnitCost} so no cost-basis
 * persistence type reaches the API or the audit trail (ADR-006). Dates are ISO-8601 strings so the trace reads
 * the same whichever Jackson mapper writes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CostEvidence {

    private String productExternalId;
    private String billingUnit;
    /** Per billing unit per month (per unit for usage, labour and one-time products), F-2. */
    private BigDecimal unitCost;
    private String currency;
    private String effectiveFrom;
    private Long version;

    static CostEvidence of(UnitCost cost) {
        return CostEvidence.builder()
                .productExternalId(cost.productExternalId())
                .billingUnit(cost.billingUnit())
                .unitCost(cost.unitCost())
                .currency(cost.currency())
                .effectiveFrom(cost.effectiveFrom() == null ? null : cost.effectiveFrom().toString())
                .version(cost.version())
                .build();
    }
}
