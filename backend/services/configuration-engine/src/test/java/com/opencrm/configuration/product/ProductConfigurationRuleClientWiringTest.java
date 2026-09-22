package com.opencrm.configuration.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full UAT 2026-09-19: configuration-engine could not boot —
 * {@code ProductConfigurationRuleClient: No default constructor found}. The class has a public
 * production constructor plus a package-private one that injects a clock for tests; with two
 * constructors and neither marked for injection, Spring falls back to a no-arg constructor that
 * does not exist. {@link ProductConfigurationRuleClientTest} calls the 3-arg constructor directly,
 * so nothing exercised container wiring. Same defect class as FIX-UAT1-QUOTE-BOOT-PRICING-CLIENT.
 */
@DisplayName("ProductConfigurationRuleClient Spring wiring")
class ProductConfigurationRuleClientWiringTest {

    // SpringApplication installs this conversion service in the real app; a bare runner does not,
    // and without it the "30s" default of rules-cache-ttl cannot bind to Duration.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean("productServiceRestClient", RestClient.class, RestClient::create)
            .withUserConfiguration(ProductConfigurationRuleClient.class);

    @Test
    @DisplayName("the container can construct the client despite the extra test-clock constructor")
    void containerConstructsClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductConfigurationRuleClient.class);
        });
    }
}
