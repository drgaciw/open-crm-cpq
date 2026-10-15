package com.opencrm.crm.fx;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A conversion rate: {@code amountInTo = amountInFrom * rate}.
 *
 * @param source where the rate came from, e.g. {@code MANUAL}, {@code IDENTITY}, or a provider name
 */
public record FxRate(String fromCurrency, String toCurrency, BigDecimal rate, Instant asOf, String source) {

    public static final String SOURCE_IDENTITY = "IDENTITY";

    public static FxRate identity(String currency, Instant asOf) {
        return new FxRate(currency, currency, BigDecimal.ONE, asOf, SOURCE_IDENTITY);
    }
}
