package com.opencrm.pricing.execution;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code opencrm.pricing.metrics.acv-version} (ADR-023 D-03).
 *
 * <p>Defaults to {@link AcvVersion#LEGACY}. This is not {@code opencrm.pricing.ramp.enabled}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "opencrm.pricing.metrics")
public class AcvVersionProperties {

    private AcvVersion acvVersion = AcvVersion.LEGACY;
}
