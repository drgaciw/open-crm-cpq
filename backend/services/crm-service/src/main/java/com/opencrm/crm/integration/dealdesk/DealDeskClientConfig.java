package com.opencrm.crm.integration.dealdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient configuration for Deal Desk integration.
 */
@Configuration
public class DealDeskClientConfig {

    @Value("${services.deal-desk.url}")
    private String dealDeskServiceUrl;

    @Bean("dealDeskRestClient")
    public RestClient dealDeskRestClient() {
        return RestClient.builder()
                .baseUrl(dealDeskServiceUrl)
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
