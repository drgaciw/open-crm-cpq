package com.opencrm.ai.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for agent-contract CPQ routes via the API gateway.
 */
@Slf4j
@Component
public class CpqAgentToolClients {

    private static final ParameterizedTypeReference<ApiResponse<ConfigurationValidationResult>> VALIDATE_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CpqAgentToolClients(
            @Value("${opencrm.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl,
            ObjectMapper objectMapper) {
        this.restClient = buildRestClient(gatewayBaseUrl);
        this.objectMapper = objectMapper;
    }

    CpqAgentToolClients(RestClient restClient) {
        this(restClient, new ObjectMapper());
    }

    CpqAgentToolClients(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public CpqToolResponse executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling gateway POST /api/v1/pricing/execute");
        return postJson("/api/v1/pricing/execute", pricingContext);
    }

    public CpqToolResponse getQuoteLineItems(long quoteId) {
        log.debug("Calling gateway GET /api/v1/quotes/{}/line-items", quoteId);
        return getJson("/api/v1/quotes/{quoteId}/line-items", quoteId);
    }

    /**
     * Wave F6 / H2: parse gateway {@code ApiResponse<ConfigurationValidationResult>}
     * ({@code valid} + {@code violations[]}) into the tool envelope.
     */
    public CpqToolResponse validateConfiguration(List<Long> selectedProductIds) {
        log.debug("Calling gateway POST /api/v1/configuration/validate");
        try {
            ApiResponse<ConfigurationValidationResult> response = restClient.post()
                    .uri("/api/v1/configuration/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(selectedProductIds)
                    .retrieve()
                    .body(VALIDATE_RESPONSE);

            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "Configuration validate returned an empty response";
                return CpqToolResponse.clientError(message);
            }

            ConfigurationValidationResult result = response.getData();
            JsonNode data = objectMapper.valueToTree(result);
            String message = response.getMessage() != null
                    ? response.getMessage()
                    : (result.isValid() ? "Configuration is valid" : "Configuration has validation errors");
            return CpqToolResponse.ok(data, message);
        } catch (ResourceAccessException ex) {
            log.warn("Gateway unreachable for POST /api/v1/configuration/validate: {}", ex.getMessage());
            return CpqToolResponse.gatewayUnavailable(ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("Gateway client error for POST /api/v1/configuration/validate: {}", ex.getMessage());
            return CpqToolResponse.clientError(ex.getMessage());
        }
    }

    private CpqToolResponse postJson(String path, Object body) {
        try {
            JsonNode response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return CpqToolResponse.ok(response, "Gateway call succeeded");
        } catch (ResourceAccessException ex) {
            log.warn("Gateway unreachable for POST {}: {}", path, ex.getMessage());
            return CpqToolResponse.gatewayUnavailable(ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("Gateway client error for POST {}: {}", path, ex.getMessage());
            return CpqToolResponse.clientError(ex.getMessage());
        }
    }

    private CpqToolResponse getJson(String pathTemplate, long quoteId) {
        try {
            JsonNode response = restClient.get()
                    .uri(pathTemplate, quoteId)
                    .retrieve()
                    .body(JsonNode.class);
            return CpqToolResponse.ok(response, "Gateway call succeeded");
        } catch (ResourceAccessException ex) {
            log.warn("Gateway unreachable for GET {}: {}", pathTemplate, ex.getMessage());
            return CpqToolResponse.gatewayUnavailable(ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("Gateway client error for GET {}: {}", pathTemplate, ex.getMessage());
            return CpqToolResponse.clientError(ex.getMessage());
        }
    }

    private static RestClient buildRestClient(String gatewayBaseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestFactory(factory)
                .build();
    }
}
