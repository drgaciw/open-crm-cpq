package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Terminated and downsized assets for one account over an inclusive period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetChurnReport {
    private String accountId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    /** Distinct assets with at least one churn event. */
    private int assetCount;
    private int eventCount;
    private BigDecimal quantityChurned;
    private List<AssetChurnEventDto> events;
}
