package com.opencrm.contract.asset.fixtures;

import com.opencrm.contract.asset.AssetHistoryFact;
import com.opencrm.contract.asset.AssetHistoryKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Test-local AssetAmended shape. Not registered and not published. */
public final class AssetAmendedFixture {

    private AssetAmendedFixture() {
    }

    public static AssetHistoryFact of(String eventId, String assetId, String accountId, LocalDate effectiveDate,
                                      String sourceAmendmentId, String sourceApplyUnit, BigDecimal quantity) {
        return new AssetHistoryFact(eventId, AssetHistoryKind.AMENDED, assetId, accountId, effectiveDate, null,
                sourceAmendmentId, sourceApplyUnit, null, null, null, null, null, quantity);
    }
}
