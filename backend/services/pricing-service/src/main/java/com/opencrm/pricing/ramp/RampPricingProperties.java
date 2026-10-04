package com.opencrm.pricing.ramp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code opencrm.pricing.ramp}. Default off. Not the ACV version switch.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "opencrm.pricing.ramp")
public class RampPricingProperties {

    private boolean enabled = false;

    private int maxPeriods = 36;
}
