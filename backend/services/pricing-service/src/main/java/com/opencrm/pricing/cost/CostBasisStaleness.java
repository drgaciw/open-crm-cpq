package com.opencrm.pricing.cost;

import java.time.LocalDate;
import java.time.Period;

/**
 * Finance decision F-2: a product whose newest cost is older than two quarters is treated as missing, so margin
 * fails closed.
 *
 * <p>Precisely: a cost effective from {@code E} is usable on {@code asOf} when {@code E <= asOf < E + 6 months}
 * (calendar months, {@link LocalDate#plusMonths} clamping a month end, so 31 August is stale from the last day of
 * February). Mock v1, effective 2026-10-01, is usable through 2027-03-31 and stale from 2027-04-01, the date the
 * Finance decision records. Quarterly refreshes effective on the first day of a quarter therefore leave one missed
 * refresh of grace.
 */
public final class CostBasisStaleness {

    /** Two quarters. */
    public static final Period MAXIMUM_AGE = Period.ofMonths(6);

    private CostBasisStaleness() {
    }

    /** The first date on which a cost effective from {@code effectiveFrom} is stale. */
    public static LocalDate staleFrom(LocalDate effectiveFrom) {
        return effectiveFrom.plus(MAXIMUM_AGE);
    }

    /** Whether a cost effective from {@code effectiveFrom} is too old to use on {@code asOf}. */
    public static boolean isStale(LocalDate effectiveFrom, LocalDate asOf) {
        return !asOf.isBefore(staleFrom(effectiveFrom));
    }
}
