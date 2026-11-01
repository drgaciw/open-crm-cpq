package com.opencrm.contract.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One fact the history projection can apply.
 * Amended, superseded, and terminated facts are published by DRG-655.
 * Tests may still build them with local fixtures.
 */
public record AssetHistoryFact(
        String eventId,
        AssetHistoryKind kind,
        String assetId,
        String accountId,
        LocalDate effectiveDate,
        LocalDate effectiveEnd,
        String sourceAmendmentId,
        String sourceApplyUnit,
        String predecessorAssetId,
        String successorAssetId,
        String contractLineId,
        Long subscriptionId,
        String productRef,
        BigDecimal quantity) {
}
