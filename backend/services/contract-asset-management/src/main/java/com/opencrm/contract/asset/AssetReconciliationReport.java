package com.opencrm.contract.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only inventory of acceptances, assets, and published asset events (DRG-902 AC3).
 * Counts are the rows examined. Deltas are the unexplained differences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetReconciliationReport {
    private LocalDate asOf;
    private long acceptanceCount;
    private long ratePeriodCount;
    private long assetCount;
    private long assetEventCount;
    private long historyCount;
    private long contractLineCount;
    private List<AssetReconciliationDelta> deltas;
}
