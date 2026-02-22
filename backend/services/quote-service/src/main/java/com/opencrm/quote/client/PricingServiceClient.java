package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class PricingServiceClient {

    private final RestClient restClient;

    public PricingServiceClient(@Qualifier("pricingRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to execute pricing");
        return restClient.post()
                .uri("/api/v1/pricing/execute")
                .body(pricingContext)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode simulatePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to simulate pricing");
        return restClient.post()
                .uri("/api/v1/pricing/simulate")
                .body(pricingContext)
                .retrieve()
                .body(JsonNode.class);
    }
}
