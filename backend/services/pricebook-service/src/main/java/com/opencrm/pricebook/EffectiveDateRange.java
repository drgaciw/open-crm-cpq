package com.opencrm.pricebook;

import java.time.LocalDate;

/**
 * DRG-431: the overlap rule for EffectiveDateRange on price book / cost book entries. Both bounds are
 * inclusive; a {@code null} start means open-ended toward the beginning of time, a {@code null} end means
 * open-ended toward the future (no planned end).
 *
 * <p>Two ranges for the same key (Sku + currency + UnitOfMeasure) must never overlap: that is the natural
 * reading of "entries are keyed by ... EffectiveDateRange" (DRG-431 AC) — a resolution date must land in at
 * most one entry's range, so GetListPrice/GetCost's "latest start date covering the date" pick is provably
 * correct rather than a tie-break hiding ambiguous data. To replace a currently-open-ended entry with a new
 * version, end-date the existing entry first (set its {@code effectiveEndDate}), then create the new one.
 *
 * <p>No existing EXCLUDE/date-range-overlap precedent was found elsewhere in this codebase (checked for
 * Postgres {@code EXCLUDE USING gist}/{@code daterange} and for application-level overlap helpers); the
 * closest analog is {@code AcceptedRampValidator}'s month-interval overlap check in
 * contract-asset-management, which uses the same half-open/inclusive interval comparison shape this class
 * uses.
 */
final class EffectiveDateRange {

    private EffectiveDateRange() {
    }

    static boolean overlaps(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        boolean firstStartsOnOrBeforeSecondEnds = start1 == null || end2 == null || !start1.isAfter(end2);
        boolean secondStartsOnOrBeforeFirstEnds = start2 == null || end1 == null || !start2.isAfter(end1);
        return firstStartsOnOrBeforeSecondEnds && secondStartsOnOrBeforeFirstEnds;
    }
}
