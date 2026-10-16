package com.opencrm.crm.fx;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for currency rates (ADR-032 D-05).
 *
 * <p>The first adapter reads the {@code fx_rates} table ({@link TableFxRateProvider}). When BC-03-07 ships a rate
 * service, a new adapter replaces it; callers do not change.
 */
public interface FxRateProvider {

    /**
     * The most recent rate from {@code fromCurrency} to {@code toCurrency} effective at or before {@code asOf}.
     * Codes are already normalized and differ. Empty when no rate is known; callers must not assume 1:1.
     */
    Optional<FxRate> findRate(String fromCurrency, String toCurrency, Instant asOf);
}
