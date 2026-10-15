package com.opencrm.pricing.cost;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port to the Finance cost basis (DRG-526 / BC-03-13).
 *
 * <p>Returns the cost effective on {@code asOf} for a catalog product. Empty means there is no usable
 * cost basis: no row, or the newest row is stale (older than two quarters). Callers must fail closed on
 * empty and never substitute a placeholder margin.
 */
public interface CostBasisLookup {

    Optional<UnitCost> find(String productExternalId, LocalDate asOf);
}
