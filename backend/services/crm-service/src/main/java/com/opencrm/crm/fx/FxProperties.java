package com.opencrm.crm.fx;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Currency settings for CRM reporting (ADR-032).
 *
 * <p>{@code opencrm.crm.fx.base-currency} is the interim base currency until BC-19-02 provides one per tenant
 * (ADR-032 acceptance note 1). It must match the Flyway placeholder {@code crm_base_currency}.
 */
@ConfigurationProperties(prefix = "opencrm.crm.fx")
public record FxProperties(String baseCurrency) {

    public static final String DEFAULT_BASE_CURRENCY = "USD";

    public FxProperties {
        baseCurrency = CurrencyCodes.normalize(baseCurrency);
        if (baseCurrency == null) {
            baseCurrency = DEFAULT_BASE_CURRENCY;
        }
        if (!CurrencyCodes.isWellFormed(baseCurrency)) {
            throw new IllegalArgumentException("opencrm.crm.fx.base-currency must be a 3-letter ISO 4217 code, got: " + baseCurrency);
        }
    }
}
