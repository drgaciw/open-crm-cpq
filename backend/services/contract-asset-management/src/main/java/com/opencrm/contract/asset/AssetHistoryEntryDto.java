package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One history fact returned by the asset Open Host Service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetHistoryEntryDto {
    private String assetId;
    private String accountId;
    private String eventType;
    private LocalDate effectiveDate;
    private LocalDate effectiveEnd;
    private String sourceAmendmentId;
    private String sourceApplyUnit;
    private String predecessorAssetId;
    private String successorAssetId;
    private String contractLineId;
    private Long subscriptionId;
    private String productRef;
    private BigDecimal quantity;
}
