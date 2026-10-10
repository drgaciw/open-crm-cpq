package com.opencrm.quote.ramp;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RampGovernanceDto {
    private Long governingLineItemId;
    private Integer governingPeriod;
    private BigDecimal discountPercent;
    private boolean stepDown;
    private List<String> reasons;
    private HybridMigrationContext hybridMigration;
}
