package com.opencrm.configuration.product;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

/**
 * Sync REST fetch of product-service configuration rules
 * ({@code GET /api/v1/configuration/rules/product/{productId}}).
 */
@Component
@Slf4j
public class ProductConfigurationRuleClient {

    private static final ParameterizedTypeReference<ApiResponse<List<ProductConfigurationRuleDto>>> RULES_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient productServiceRestClient;

    public ProductConfigurationRuleClient(
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient) {
        this.productServiceRestClient = productServiceRestClient;
    }

    public List<ProductConfigurationRuleDto> getRulesForProduct(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }
        try {
            ApiResponse<List<ProductConfigurationRuleDto>> response = productServiceRestClient.get()
                    .uri("/api/v1/configuration/rules/product/{productId}", productId)
                    .retrieve()
                    .body(RULES_RESPONSE);

            if (response == null || response.getData() == null) {
                log.debug("No configuration rules returned for product {}", productId);
                return Collections.emptyList();
            }
            return response.getData();
        } catch (ResourceAccessException e) {
            throw new BusinessException(
                    "Product service unavailable while fetching configuration rules: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PRODUCT_SERVICE_UNAVAILABLE");
        } catch (RestClientException e) {
            throw new BusinessException(
                    "Failed to fetch configuration rules for product " + productId + ": " + e.getMessage(),
                    "PRODUCT_RULES_FETCH_ERROR");
        }
    }
}
