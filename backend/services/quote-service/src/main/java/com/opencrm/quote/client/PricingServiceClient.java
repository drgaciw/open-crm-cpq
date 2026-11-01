package com.opencrm.quote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
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
    private static final TypeReference<ApiResponse<ContinuationEvaluationView>> CONTINUATION_TYPE =
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
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public ApiResponse<PricingExecuteView> executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to execute pricing");
        return parsePricingResponse(postForBody("/api/v1/pricing/execute", pricingContext));
    }

    public ApiResponse<PricingExecuteView> simulatePricing(Map<String, Object> pricingContext) {
        log.debug("Calling pricing service to simulate pricing");
        return parsePricingResponse(postForBody("/api/v1/pricing/simulate", pricingContext));
    }

    /**
     * Add/Update/Remove continuation evaluation (DRG-609 option B).
     * Execute and simulate are unchanged. The response carries one PolicyDecision id per line.
     */
    public ApiResponse<ContinuationEvaluationView> evaluateContinuation(Map<String, Object> request) {
        log.debug("Calling pricing service to evaluate a continuation");
        return parse(postForBody("/api/v1/pricing/continuation/evaluate", request), CONTINUATION_TYPE);
    }

    /**
     * 4xx bodies are pricing refusals (422 with no success payload). 5xx stays a transport failure.
     */
    private String postForBody(String uri, Map<String, Object> pricingContext) {
        return restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(pricingContext)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new RestClientException(
                            "Pricing service status " + response.getStatusCode().value());
                })
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) ->
                        log.debug("Pricing service returned {}", response.getStatusCode().value()))
                .body(String.class);
    }

    private ApiResponse<PricingExecuteView> parsePricingResponse(String raw) {
        return parse(raw, RESPONSE_TYPE);
    }

    private <T> ApiResponse<T> parse(String raw, TypeReference<ApiResponse<T>> type) {
        if (raw == null || raw.isBlank()) {
            return ApiResponse.error("Empty pricing response");
        }
        try {
            return objectMapper.readValue(raw, type);
        } catch (IOException ex) {
            throw new RestClientException("Failed to parse pricing JSON: " + ex.getMessage(), ex);
        }
    }
}
