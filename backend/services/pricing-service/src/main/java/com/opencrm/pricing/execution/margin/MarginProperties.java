package com.opencrm.pricing.execution.margin;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code opencrm.pricing.margin.source}: {@code placeholder} (default) or {@code cost-basis}.
 *
 * <p>The default keeps today's responses and governance unchanged. It is not the ramp flag and not the ACV version.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "opencrm.pricing.margin")
public class MarginProperties {

    private MarginSource source = MarginSource.PLACEHOLDER;
}
