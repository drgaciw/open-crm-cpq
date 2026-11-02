package com.opencrm.crm.guided;

import com.opencrm.security.client.OutboundAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Guided selling configuration client")
class GuidedSellingConfigurationTest {

    @Test
    @DisplayName("a blank base URL bean records intent and does not throw")
    void blankBaseUrlBeanRecordsIntent() {
        ConfigurationSessionStarter starter = new GuidedSellingConfiguration()
                .configurationSessionStarter("  ", OutboundAuth.withoutServiceIdentity());

        StartedConfiguration started = starter.start("prod-growth-platform", Map.of("edition", "professional"));

        assertThat(started.recordedLocally()).isTrue();
        assertThat(started.configurationSessionId()).isNull();
        assertThat(started.productId()).isEqualTo("prod-growth-platform");
        assertThat(((HttpConfigurationSessionStarter) starter).recordedIntents()).hasSize(1);
    }

    @Test
    @DisplayName("a base URL builds a client without calling it")
    void configuredBaseUrlBuildsClient() {
        ConfigurationSessionStarter starter = new GuidedSellingConfiguration()
                .configurationSessionStarter("http://configuration.test/", OutboundAuth.withoutServiceIdentity());

        assertThat(starter).isInstanceOf(HttpConfigurationSessionStarter.class);
        assertThat(((HttpConfigurationSessionStarter) starter).recordedIntents()).isEmpty();
    }
}
