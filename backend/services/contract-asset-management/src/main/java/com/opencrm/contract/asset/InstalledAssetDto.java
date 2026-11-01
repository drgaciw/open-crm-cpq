package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An asset that is in an account's installed base on the requested date. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstalledAssetDto {
    private String assetId;
    private String accountId;
    private String contractLineId;
    private Long subscriptionId;
    private String productRef;
    private BigDecimal quantity;
    private LocalDate effectiveStart;
    private LocalDate effectiveEnd;
    private AssetStatus status;
}
