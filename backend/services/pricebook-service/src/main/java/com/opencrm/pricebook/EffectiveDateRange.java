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
 * <p>The same rule is enforced in PostgreSQL by
 * {@code V11__price_and_cost_book_entry_range_exclusion.sql} so concurrent writers cannot insert overlapping
 * ranges that this in-process check would miss. Entry create/update also locks the parent book row.
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
