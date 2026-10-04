package com.opencrm.quote.ramp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("quote-service ramp flag default")
class RampPricingYamlTest {

    @Test
    @DisplayName("committed application.yml keeps the ramp flag off and the period cap at 36")
    void rampFlagDefaultsOff() throws IOException {
        String yaml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).isNotNull();
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        int ramp = yaml.indexOf("\n    ramp:\n");
        assertThat(ramp).isGreaterThan(yaml.indexOf("\nopencrm:\n"));
        String slice = yaml.substring(ramp, yaml.indexOf("\n---", ramp));

        assertThat(slice).contains("enabled: false");
        assertThat(slice).contains("max-periods: 36");
        assertThat(slice).doesNotContain("enabled: true");
        assertThat(yaml).contains("legacy-local-read-enabled: ${APPROVALS_LEGACY_LOCAL_READ:true}");
        assertThat(yaml).contains("enabled: ${JEV_ENABLED:false}");
    }
}
