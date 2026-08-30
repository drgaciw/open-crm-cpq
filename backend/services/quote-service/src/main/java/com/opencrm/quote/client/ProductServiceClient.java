package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Product lookups hit product-service directly; configuration validate (S2D-3a)
 * goes through the gateway so Wave F structured {@link ConfigurationValidationResult}
 * is returned (not product {@code List<String>}).
 */
@Slf4j
@Component
public class ProductServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<ConfigurationValidationResult>> VALIDATE_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient productRestClient;
    private final RestClient gatewayRestClient;

    public ProductServiceClient(
            @Qualifier("productRestClient") RestClient productRestClient,
            @Qualifier("gatewayRestClient") RestClient gatewayRestClient) {
        this.productRestClient = productRestClient;
        this.gatewayRestClient = gatewayRestClient;
    }

    public JsonNode getProductById(Long productId) {
        log.debug("Fetching product by ID: {}", productId);
        return productRestClient.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        return productRestClient.get()
                .uri("/api/v1/products/sku/{sku}", sku)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * CONFIGURATION_ONLY preflight via gateway {@code POST /api/v1/configuration/validate}
     * (shim → configuration-engine evaluate).
     */
    public ConfigurationValidationResult validateConfiguration(List<Long> productIds) {
        log.debug("Validating product configuration via gateway for {} products", productIds.size());
        ApiResponse<ConfigurationValidationResult> response;
        try {
            response = gatewayRestClient.post()
                    .uri("/api/v1/configuration/validate")
                    .body(productIds)
                    .retrieve()
                    .body(VALIDATE_RESPONSE);
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call configuration validate via gateway: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "CONFIGURATION_VALIDATE_UNAVAILABLE"
            );
        }

        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null && response.getMessage() != null
                    ? response.getMessage()
                    : "Configuration validate returned an empty response";
            throw new BusinessException(message, HttpStatus.BAD_GATEWAY, "CONFIGURATION_VALIDATE_FAILED");
        }

        return response.getData();
    }
}
