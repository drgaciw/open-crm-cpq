package com.opencrm.pricing.ramp;

import com.opencrm.pricing.execution.AcvVersion;
import com.opencrm.pricing.execution.AcvVersionProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Ramp pricing is only valid when ACV uses term-years (ADR-023 D-03 and D-06).
 */
@Component
public class RampPricingStartup implements InitializingBean {

    private final RampPricingProperties ramp;
    private final AcvVersionProperties acv;

    public RampPricingStartup(RampPricingProperties ramp, AcvVersionProperties acv) {
        this.ramp = ramp;
        this.acv = acv;
    }

    @Override
    public void afterPropertiesSet() {
        if (ramp != null && ramp.isEnabled() && (acv == null || acv.getAcvVersion() != AcvVersion.TERM_YEARS)) {
            throw new IllegalStateException(
                    "opencrm.pricing.ramp.enabled requires opencrm.pricing.metrics.acv-version=term-years");
        }
    }
}
