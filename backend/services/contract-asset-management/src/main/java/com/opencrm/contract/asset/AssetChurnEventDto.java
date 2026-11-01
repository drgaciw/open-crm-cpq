package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One termination or downsize. Supersede rows are not represented here. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetChurnEventDto {
    private String assetId;
    private String accountId;
    private AssetChurnKind churnKind;
    private LocalDate effectiveDate;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;
    private BigDecimal quantityChurned;
    private String productRef;
}
