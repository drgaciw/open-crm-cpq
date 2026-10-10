package com.opencrm.pricing.ramp.migration;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Window policy only; does not enable ramp or alter the ACV default. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "opencrm.pricing.ramp")
public class MigrationWindowProperties {
    @Min(1)
    private int migrationWindowMonths = 12;
}
