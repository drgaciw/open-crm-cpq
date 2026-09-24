package com.opencrm.configuration.config;

import com.opencrm.security.client.OutboundAuth;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ProductServiceClientConfig {

    /**
     * product-service {@code /api/v1/configuration/rules/**}. Rule reads happen while a user evaluates a configuration,
     * so the caller's token is relayed (ADR-016 decision 5); a cache refresh with no user calls as this service.
     */
    @Bean
    @Qualifier("productServiceRestClient")
    public RestClient productServiceRestClient(
            @Value("${services.product.url:http://localhost:8083}") String baseUrl, OutboundAuth outboundAuth) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(outboundAuth.relayUserToken())
                .build();
    }
}
