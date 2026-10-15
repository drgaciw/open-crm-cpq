package com.opencrm.pricing.cost;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * {@code opencrm.pricing.cost-basis}. The staleness window is not configurable: it is Finance decision F-2
 * ({@link CostBasisStaleness}).
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "opencrm.pricing.cost-basis")
public class CostBasisProperties {

    /** Most data rows one export may carry; mock v1 has 31. A larger file is refused whole (422). */
    @Min(1)
    @Max(100_000)
    private int maxRows = 10_000;
}
