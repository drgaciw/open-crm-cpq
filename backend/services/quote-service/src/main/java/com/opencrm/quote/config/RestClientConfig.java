package com.opencrm.quote.config;

import com.opencrm.security.client.OutboundAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * quote-service's clients of other services, each authenticated per ADR-016 decision 5. User-initiated calls to
 * another service's {@code /api/v1/**} relay the caller's token; calls to {@code /internal/**} carry this service's own
 * token. quote-service never calls the gateway.
 */
@Configuration
public class RestClientConfig {
    @Bean("subscriptionScheduleRestClient")
    public RestClient subscriptionScheduleRestClient(
            @Value("${services.contract-asset-management.url:http://localhost:8087}") String baseUrl, OutboundAuth outboundAuth) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(createRequestFactory())
                .requestInterceptor(outboundAuth.serviceToken()).build();
    }

    /** pricing {@code /api/v1/pricing/**}: user-initiated, so the caller's token is relayed. */
    @Bean("pricingRestClient")
    public RestClient pricingRestClient(@Value("${services.pricing.url}") String pricingServiceUrl,
                                        OutboundAuth outboundAuth) {
        return RestClient.builder()
                .baseUrl(pricingServiceUrl)
                .requestFactory(createRequestFactory())
                .requestInterceptor(outboundAuth.relayUserToken())
                .build();
    }

    /**
     * configuration-engine {@code /internal/v1/configurations/evaluate} for the S2D-3a preflight. Direct, not through
     * the gateway, and with the service token that {@code /internal/**} requires (ADR-016 step 2).
     */
    @Bean("configurationEngineRestClient")
    public RestClient configurationEngineRestClient(
            @Value("${services.configuration-engine.url}") String configurationEngineUrl, OutboundAuth outboundAuth) {
        return RestClient.builder()
                .baseUrl(configurationEngineUrl)
                .requestFactory(createRequestFactory())
                .requestInterceptor(outboundAuth.serviceToken())
                .build();
    }

    /** approval-routing {@code /api/v1/approvals/**} for S2C-12 single-writer approvals (G4): the caller's token. */
    @Bean("approvalRoutingRestClient")
    public RestClient approvalRoutingRestClient(
            @Value("${services.approval-routing.url}") String approvalRoutingServiceUrl, OutboundAuth outboundAuth) {
        return RestClient.builder()
                .baseUrl(approvalRoutingServiceUrl)
                .requestFactory(createRequestFactory())
                .requestInterceptor(outboundAuth.relayUserToken())
                .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }
}
