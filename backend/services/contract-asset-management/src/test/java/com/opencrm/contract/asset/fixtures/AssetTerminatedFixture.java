package com.opencrm.contract.asset.fixtures;

import com.opencrm.contract.asset.AssetHistoryFact;
import com.opencrm.contract.asset.AssetHistoryKind;

import java.time.LocalDate;

/** Test-local AssetTerminated shape. Not registered and not published. */
public final class AssetTerminatedFixture {

    private AssetTerminatedFixture() {
    }

    public static AssetHistoryFact of(String eventId, String assetId, String accountId, LocalDate effectiveDate,
                                      String sourceAmendmentId, String sourceApplyUnit) {
        return new AssetHistoryFact(eventId, AssetHistoryKind.TERMINATED, assetId, accountId, effectiveDate, null,
                sourceAmendmentId, sourceApplyUnit, null, null, null, null, null, null);
    }
}
