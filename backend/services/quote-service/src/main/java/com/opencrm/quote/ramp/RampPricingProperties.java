package com.opencrm.quote.ramp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default off. Tests construct this type and call {@link #setEnabled(boolean)}.
 * The committed yaml value stays false.
 */
@ConfigurationProperties(prefix = "opencrm.pricing.ramp")
public class RampPricingProperties {

    /** When false, a schedule on a write or expand is 422 RAMP_NOT_SUPPORTED. */
    private boolean enabled = false;

    /** Maximum explicit periods on one line. */
    private int maxPeriods = 36;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxPeriods() {
        return maxPeriods;
    }

    public void setMaxPeriods(int maxPeriods) {
        this.maxPeriods = maxPeriods;
    }
}
