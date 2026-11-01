package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Feed report fixtures through the published event-to-history mapping. */
final class AssetReportTestEvents {
    private AssetReportTestEvents() {}

    static void amended(AssetHistoryProjection projection, String eventId, String assetId, String accountId,
                        LocalDate effectiveDate, String amendmentId, String unit, BigDecimal quantity) {
        projection.apply(new AssetAmendedEvent(assetId, accountId, null, null, null, null, quantity,
                "ACTIVE", null, null, effectiveDate, amendmentId, unit), eventId);
    }

    static void superseded(AssetHistoryProjection projection, String eventId, String assetId, String accountId,
                           LocalDate effectiveDate, String amendmentId, String unit, String successorId) {
        projection.apply(new AssetSupersededEvent(assetId, accountId, successorId, assetId,
                "SUPERSEDED", effectiveDate, amendmentId, unit), eventId);
    }

    static void terminated(AssetHistoryProjection projection, String eventId, String assetId, String accountId,
                           LocalDate effectiveDate, String amendmentId, String unit) {
        projection.apply(new AssetTerminatedEvent(assetId, accountId, null, null, null,
                "TERMINATED", effectiveDate, amendmentId, unit), eventId);
    }
}
