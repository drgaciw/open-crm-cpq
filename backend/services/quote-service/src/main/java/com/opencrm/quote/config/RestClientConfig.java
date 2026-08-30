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

    @Value("${services.gateway.url}")
    private String gatewayServiceUrl;

    @Value("${services.approval-routing.url}")
    private String approvalRoutingServiceUrl;

    @Bean
    public RestClient pricingRestClient() {
        return RestClient.builder()
                .baseUrl(pricingServiceUrl)
                .requestFactory(createRequestFactory())
                .build();
    }

    /** Gateway RestClient for S2D-3a validate (structured ConfigurationValidationResult). */
    @Bean
    public RestClient gatewayRestClient() {
        return RestClient.builder()
                .baseUrl(gatewayServiceUrl)
                .requestFactory(createRequestFactory())
                .build();
    }

    /** approval-routing RestClient for S2C-12 single-writer approval records (G4). */
    @Bean
    public RestClient approvalRoutingRestClient() {
        return RestClient.builder()
                .baseUrl(approvalRoutingServiceUrl)
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
