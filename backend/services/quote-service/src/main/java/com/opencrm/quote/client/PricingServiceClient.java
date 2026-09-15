package com.opencrm.quote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP client for pricing-service execute/simulate.
 *
 * <p>Body is read as {@code String} then parsed with Jackson 2 {@link ObjectMapper}.
 * Spring RestClient Jackson 3 converters cannot bind {@code com.fasterxml.jackson.databind.JsonNode}
 * (Wave I2 proof); never use {@code body(JsonNode.class)}.
 */
@Slf4j
@Component
public class PricingServiceClient {

    private static final TypeReference<ApiResponse<PricingExecuteView>> RESPONSE_TYPE =
            new TypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Single injection constructor. A second ctor (even package-private) makes Spring
     * look for a no-arg default and fail boot (UAT-1: {@code BeanCreationException}
     * {@code pricingServiceClient} — no default constructor found).
     */
    public PricingServiceClient(@Qualifier("pricingRestClient") RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    public ApiResponse<PricingExecuteView> executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to execute pricing");
        String raw = restClient.post()
                .uri("/api/v1/pricing/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(pricingContext)
                .retrieve()
                .body(String.class);
        return parsePricingResponse(raw);
    }

    public ApiResponse<PricingExecuteView> simulatePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to simulate pricing");
        String raw = restClient.post()
                .uri("/api/v1/pricing/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(pricingContext)
                .retrieve()
                .body(String.class);
        return parsePricingResponse(raw);
    }

    private ApiResponse<PricingExecuteView> parsePricingResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ApiResponse.error("Empty pricing response");
        }
        try {
            return objectMapper.readValue(raw, RESPONSE_TYPE);
        } catch (IOException ex) {
            throw new RestClientException("Failed to parse pricing JSON: " + ex.getMessage(), ex);
        }
    }
}
