package com.opencrm.quote.client;

import com.opencrm.quote.config.RestClientConfig;
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
 * {@code @Autowired}). Slice proves the three named RestClient beans plus the
 * pricing client load without a full {@code @SpringBootTest}.
 */
@DisplayName("PricingServiceClient Spring wiring (UAT-1 boot)")
@SpringJUnitConfig(classes = {
        RestClientConfig.class,
        PricingServiceClient.class
})
@TestPropertySource(properties = {
        "services.pricing.url=http://pricing.test",
        "services.gateway.url=http://gateway.test",
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
    @DisplayName("loads pricingRestClient, gatewayRestClient, approvalRoutingRestClient, and PricingServiceClient")
    void loadsNamedRestClientsAndPricingServiceClient() {
        assertThat(pricingServiceClient).isNotNull();
        assertThat(pricingRestClient).isNotNull();
        assertThat(context.getBean("pricingRestClient", RestClient.class)).isSameAs(pricingRestClient);
        assertThat(context.getBean("gatewayRestClient", RestClient.class)).isNotNull();
        assertThat(context.getBean("approvalRoutingRestClient", RestClient.class)).isNotNull();
        assertThat(context.getBeansOfType(RestClient.class))
                .containsOnlyKeys("pricingRestClient", "gatewayRestClient", "approvalRoutingRestClient");
    }
}
