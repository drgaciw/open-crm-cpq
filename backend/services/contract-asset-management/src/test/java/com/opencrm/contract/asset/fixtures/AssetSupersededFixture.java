package com.opencrm.contract.asset.fixtures;

import com.opencrm.contract.asset.AssetHistoryFact;
import com.opencrm.contract.asset.AssetHistoryKind;

import java.time.LocalDate;

/** Test-local AssetSuperseded shape. Not registered and not published. */
public final class AssetSupersededFixture {

    private AssetSupersededFixture() {
    }

    public static AssetHistoryFact of(String eventId, String assetId, String accountId, LocalDate effectiveDate,
                                      String sourceAmendmentId, String sourceApplyUnit, String successorAssetId) {
        return new AssetHistoryFact(eventId, AssetHistoryKind.SUPERSEDED, assetId, accountId, effectiveDate, null,
                sourceAmendmentId, sourceApplyUnit, assetId, successorAssetId, null, null, null, null);
    }
}
