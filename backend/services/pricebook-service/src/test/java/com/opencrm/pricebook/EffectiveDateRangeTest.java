package com.opencrm.pricebook;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveDateRangeTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2026, 6, 30);
    private static final LocalDate JUL_1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2026, 12, 31);

    @Test
    void identicalRangesOverlap() {
        assertThat(EffectiveDateRange.overlaps(JAN_1, JUN_30, JAN_1, JUN_30)).isTrue();
    }

    @Test
    void partiallyOverlappingRangesOverlap() {
        // [Jan 1 .. Jun 30] vs [Apr 1 .. Dec 31] — the bug the AC caught: a later start date doesn't mean
        // no overlap.
        assertThat(EffectiveDateRange.overlaps(JAN_1, JUN_30, LocalDate.of(2026, 4, 1), DEC_31)).isTrue();
        assertThat(EffectiveDateRange.overlaps(LocalDate.of(2026, 4, 1), DEC_31, JAN_1, JUN_30)).isTrue();
    }

    @Test
    void oneRangeContainedInTheOtherOverlaps() {
        assertThat(EffectiveDateRange.overlaps(JAN_1, DEC_31, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)))
                .isTrue();
    }

    @Test
    void touchingOnTheBoundaryDayOverlapsBecauseBoundsAreInclusive() {
        assertThat(EffectiveDateRange.overlaps(JAN_1, JUN_30, JUN_30, DEC_31)).isTrue();
    }

    @Test
    void adjacentRangesWithAGapDoNotOverlap() {
        assertThat(EffectiveDateRange.overlaps(JAN_1, JUN_30, JUL_1, DEC_31)).isFalse();
        assertThat(EffectiveDateRange.overlaps(JUL_1, DEC_31, JAN_1, JUN_30)).isFalse();
    }

    @Test
    void openEndedRangeOverlapsAnyLaterRange() {
        // No end date: effective forever going forward.
        assertThat(EffectiveDateRange.overlaps(JAN_1, null, JUL_1, DEC_31)).isTrue();
        assertThat(EffectiveDateRange.overlaps(JUL_1, DEC_31, JAN_1, null)).isTrue();
    }

    @Test
    void openStartedRangeOverlapsAnyEarlierRange() {
        // No start date: effective since the beginning of time.
        assertThat(EffectiveDateRange.overlaps(null, JUN_30, JAN_1, JUN_30)).isTrue();
    }

    @Test
    void twoFullyOpenRangesAlwaysOverlap() {
        assertThat(EffectiveDateRange.overlaps(null, null, null, null)).isTrue();
    }

    @Test
    void openEndedRangeDoesNotOverlapALaterRangeThatStartsAfterItEnds() {
        // Even "open-ended" rows in this codebase are compared on the same terms; this just documents that
        // an explicit end date still blocks overlap with a range entirely after it.
        assertThat(EffectiveDateRange.overlaps(JAN_1, JUN_30, JUL_1, null)).isFalse();
    }
}
