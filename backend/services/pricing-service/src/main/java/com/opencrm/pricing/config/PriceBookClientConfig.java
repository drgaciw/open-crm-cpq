package com.opencrm.pricing.config;

import com.opencrm.security.client.OutboundAuth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PriceBookClientConfig {

    /**
     * pricebook-service {@code /internal/v1/price-books/**}. {@code /internal/**} needs the {@code Internal.Invoke}
     * app role, so this client always sends pricing-service's own token (ADR-016 decision 5). Timeouts are ADR-017's.
     */
    @Bean
    @Qualifier("priceBookRestClient")
    public RestClient priceBookRestClient(
            @Value("${opencrm.pricebook.base-url:http://localhost:8089}") String baseUrl, OutboundAuth outboundAuth) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(outboundAuth.serviceToken())
                .build();
    }
}
