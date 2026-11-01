package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Assets still in the installed base on {@code windowEnd} whose effective end
 * falls in the inclusive window. The asset rows are the as-of query's shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingRenewalsReport {
    private String accountId;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    private int assetCount;
    private List<InstalledAssetDto> assets;
}
