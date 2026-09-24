package com.opencrm.ai.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.security.client.OutboundAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for agent-contract CPQ routes via the API gateway.
 *
 * <p>ADR-016 step 2: every call relays the bearer token of the user who invoked the agent, so the CPQ services see the
 * real actor; with no user (a background run) ai-service's own token is sent. Calling the gateway is an allowlisted
 * exception to decision 5, pinned by {@code SecurityBaselineFitnessTest}; step 3 decides it.
 */
@Slf4j
@Component
public class CpqAgentToolClients {

    private static final ParameterizedTypeReference<ApiResponse<ConfigurationValidationResult>> VALIDATE_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CpqAgentToolClients(
            @Value("${opencrm.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl,
            ObjectMapper objectMapper, OutboundAuth outboundAuth) {
        this.restClient = buildRestClient(gatewayBaseUrl, outboundAuth);
        this.objectMapper = objectMapper;
    }

    CpqAgentToolClients(RestClient restClient) {
        this(restClient, new ObjectMapper());
    }

    CpqAgentToolClients(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Wave I2: unwrap {@code ApiResponse} and expose pricing {@code success}/{@code lineResults}
     * on the tool data node (no pricing-service Maven dependency).
     *
     * <p>Body is read as {@code String} then parsed with Jackson 2 {@link ObjectMapper}.
     * Spring RestClient Jackson 3 converters cannot bind {@code com.fasterxml.jackson.databind.JsonNode}.
     */
    public CpqToolResponse executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling gateway POST /api/v1/pricing/execute");
        try {
            String raw = restClient.post()
                    .uri("/api/v1/pricing/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pricingContext)
                    .retrieve()
                    .body(String.class);
            JsonNode envelope = parseGatewayJson(raw);
            JsonNode data = envelope != null ? envelope.path("data") : null;
            if (envelope == null || !envelope.path("success").asBoolean(true)
                    || data == null || data.isMissingNode() || data.isNull()) {
                String message = envelope != null && envelope.hasNonNull("message")
                        ? envelope.get("message").asText()
                        : "Pricing execute returned an empty response";
                return CpqToolResponse.clientError(message);
            }
            boolean pricingOk = !data.has("success") || data.path("success").asBoolean(true);
            String message = envelope.hasNonNull("message")
                    ? envelope.get("message").asText()
                    : (pricingOk ? "Pricing execute succeeded" : data.path("errorMessage").asText("Pricing execute failed"));
            return CpqToolResponse.ok(data, message);
        } catch (ResourceAccessException ex) {
            log.warn("Gateway unreachable for POST /api/v1/pricing/execute: {}", ex.getMessage());
            return CpqToolResponse.gatewayUnavailable(ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("Gateway client error for POST /api/v1/pricing/execute: {}", ex.getMessage());
            return CpqToolResponse.clientError(ex.getMessage());
        }
    }

    /**
     * Wave I2: unwrap {@code ApiResponse} so tool data is the line-item array.
     */
    public CpqToolResponse getQuoteLineItems(long quoteId) {
        log.debug("Calling gateway GET /api/v1/quotes/{}/line-items", quoteId);
        try {
            String raw = restClient.get()
                    .uri("/api/v1/quotes/{quoteId}/line-items", quoteId)
                    .retrieve()
                    .body(String.class);
            JsonNode envelope = parseGatewayJson(raw);
            JsonNode data = envelope != null ? envelope.path("data") : null;
            if (envelope == null || !envelope.path("success").asBoolean(true)
                    || data == null || data.isMissingNode() || data.isNull()) {
                String message = envelope != null && envelope.hasNonNull("message")
                        ? envelope.get("message").asText()
                        : "Quote line items returned an empty response";
                return CpqToolResponse.clientError(message);
            }
            String message = envelope.hasNonNull("message")
                    ? envelope.get("message").asText()
                    : "Quote line items loaded";
            return CpqToolResponse.ok(data, message);
        } catch (ResourceAccessException ex) {
            log.warn("Gateway unreachable for GET /api/v1/quotes/{}/line-items: {}", quoteId, ex.getMessage());
            return CpqToolResponse.gatewayUnavailable(ex.getMessage());
        } catch (RestClientException ex) {
            log.warn("Gateway client error for GET /api/v1/quotes/{}/line-items: {}", quoteId, ex.getMessage());
            return CpqToolResponse.clientError(ex.getMessage());
        }
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

    /**
     * Parse gateway JSON with Jackson 2 so {@link CpqToolResponse} can keep {@code JsonNode} data.
     */
    private JsonNode parseGatewayJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException ex) {
            throw new RestClientException("Failed to parse gateway JSON: " + ex.getMessage(), ex);
        }
    }

    private static RestClient buildRestClient(String gatewayBaseUrl, OutboundAuth outboundAuth) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestFactory(factory)
                .requestInterceptor(outboundAuth.relayUserToken())
                .build();
    }
}
