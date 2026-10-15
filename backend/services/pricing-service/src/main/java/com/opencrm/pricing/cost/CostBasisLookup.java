package com.opencrm.pricing.cost;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port to the Finance cost basis (DRG-526 / BC-03-13).
 *
 * <p>Two dates, because a ramp period can start long after the quote is priced:
 *
 * <ul>
 *   <li>{@code effectiveOn} selects the cost: the newest rows effective on or before that date (the
 *       start of the priced period, finance decision F-1).
 *   <li>{@code pricedOn} judges freshness: the newest rows effective on or before the pricing date must
 *       not be stale (older than two quarters) on that date. Staleness is never judged against a future
 *       period start, so a three-year ramp priced today on a fresh cost basis is not failed.
 * </ul>
 *
 * <p>Empty means there is no usable cost basis. Callers must fail closed on empty and never substitute a
 * placeholder margin.
 */
public interface CostBasisLookup {

    Optional<UnitCost> find(String productExternalId, LocalDate effectiveOn, LocalDate pricedOn);

    /** The cost effective on, and judged fresh on, the same date. */
    default Optional<UnitCost> find(String productExternalId, LocalDate asOf) {
        return find(productExternalId, asOf, asOf);
    }
}
