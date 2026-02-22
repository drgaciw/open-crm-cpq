package com.opencrm.quote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${services.pricing.url}")
    private String pricingServiceUrl;

    @Value("${services.product.url}")
    private String productServiceUrl;

    @Bean
    public RestClient pricingRestClient() {
        return RestClient.builder()
                .baseUrl(pricingServiceUrl)
                .requestFactory(createRequestFactory())
                .build();
    }

    @Bean
    public RestClient productRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceUrl)
                .requestFactory(createRequestFactory())
                .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }
}
