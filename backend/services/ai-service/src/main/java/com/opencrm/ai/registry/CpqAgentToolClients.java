package com.opencrm.ai.registry;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for agent-contract CPQ routes via the API gateway.
 */
@Slf4j
@Component
public class CpqAgentToolClients {

    private final RestClient restClient;

    public CpqAgentToolClients(
            @Value("${opencrm.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl) {
        this.restClient = buildRestClient(gatewayBaseUrl);
    }

    CpqAgentToolClients(RestClient restClient) {
        this.restClient = restClient;
    }

    public CpqToolResponse executePricing(Map<String, Object> pricingContext) {
        log.debug("Calling gateway POST /api/v1/pricing/execute");
        return postJson("/api/v1/pricing/execute", pricingContext);
    }

    public CpqToolResponse getQuoteLineItems(long quoteId) {
        log.debug("Calling gateway GET /api/v1/quotes/{}/line-items", quoteId);
        return getJson("/api/v1/quotes/{quoteId}/line-items", quoteId);
    }

    public CpqToolResponse validateConfiguration(List<Long> selectedProductIds) {
        log.debug("Calling gateway POST /api/v1/configuration/validate");
        return postJson("/api/v1/configuration/validate", selectedProductIds);
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
