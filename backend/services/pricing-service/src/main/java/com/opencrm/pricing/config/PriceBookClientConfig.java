package com.opencrm.pricing.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PriceBookClientConfig {

    @Bean
    @Qualifier("priceBookRestClient")
    public RestClient priceBookRestClient(
            @Value("${opencrm.pricebook.base-url:http://localhost:8089}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
