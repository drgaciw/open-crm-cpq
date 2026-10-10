package com.opencrm.quote.client;

import com.opencrm.quote.config.RestClientConfig;
import com.opencrm.security.client.OpenCrmOutboundAuthAutoConfiguration;
import com.opencrm.security.client.OutboundAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UAT-1 #531: quote-service failed to boot because Spring could not construct
 * {@link PricingServiceClient} (no default constructor; two ctors, neither
 * {@code @Autowired}). Slice proves the named RestClient beans plus the
 * pricing client load without a full {@code @SpringBootTest}. Since ADR-016 step 2 each client takes the
 * {@link OutboundAuth} interceptors from the {@code libs/security} auto-configuration, and the gateway client is gone.
 */
@DisplayName("PricingServiceClient Spring wiring (UAT-1 boot)")
@SpringJUnitConfig(classes = {
        RestClientConfig.class,
        PricingServiceClient.class,
        OpenCrmOutboundAuthAutoConfiguration.class
})
@TestPropertySource(properties = {
        "services.pricing.url=http://pricing.test",
        "services.configuration-engine.url=http://configuration-engine.test",
        "services.approval-routing.url=http://routing.test"
})
class PricingServiceClientContextTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    PricingServiceClient pricingServiceClient;

    @Autowired
    @Qualifier("pricingRestClient")
    RestClient pricingRestClient;

    @Test
    @DisplayName("loads pricing, configuration, approval, subscription clients, and PricingServiceClient")
    void loadsNamedRestClientsAndPricingServiceClient() {
        assertThat(pricingServiceClient).isNotNull();
        assertThat(pricingRestClient).isNotNull();
        assertThat(context.getBean("pricingRestClient", RestClient.class)).isSameAs(pricingRestClient);
        assertThat(context.getBean("configurationEngineRestClient", RestClient.class)).isNotNull();
        assertThat(context.getBean("approvalRoutingRestClient", RestClient.class)).isNotNull();
        assertThat(context.getBean("subscriptionScheduleRestClient", RestClient.class)).isNotNull();
        assertThat(context.getBeansOfType(RestClient.class))
                .containsOnlyKeys("pricingRestClient", "configurationEngineRestClient", "approvalRoutingRestClient", "subscriptionScheduleRestClient");
        assertThat(context.getBean(OutboundAuth.class)).isNotNull();
    }
}
