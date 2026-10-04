package com.opencrm.quote.ramp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RampPricingProperties.class)
public class RampPricingConfiguration {
}
