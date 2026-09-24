package com.opencrm.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

class PricingApplicationConfigurationTest {

    @Test
    void bindsAzureCompatibilitySettingsInSpringNamespaceAndKeepsGrpcOffByDefault() throws Exception {
        var sources = new YamlPropertySourceLoader()
                .load("pricing-application", new FileSystemResource("src/main/resources/application.yml"));
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(sources.getFirst());
        var binder = Binder.get(environment);

        assertThat(binder.bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
                .orElse(java.util.List.of()))
                .contains("com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration",
                        "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration");
        assertThat(binder.bind("spring.cloud.azure.compatibility-verifier.enabled", Boolean.class)
                .orElse(true)).isFalse();
        // ADR-016 step 2: the unauthenticated gRPC listener is off (-1) unless PRICING_GRPC_PORT opts in.
        assertThat(binder.bind("grpc.server.port", Integer.class).orElse(0)).isEqualTo(-1);
        environment.setProperty("PRICING_GRPC_PORT", "9090");
        assertThat(Binder.get(environment).bind("grpc.server.port", Integer.class).orElse(0)).isEqualTo(9090);
    }
}
