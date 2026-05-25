package com.opencrm.common.configuration;

/**
 * Declares which service owns configuration validity decisions.
 * AI and other read-only assistants must not assert committed/valid configuration state.
 */
public final class ConfigurationValidityAuthority {

    public static final String OWNER_SERVICE = "configuration-engine";

    private ConfigurationValidityAuthority() {
    }
}
