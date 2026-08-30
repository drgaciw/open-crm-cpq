package com.opencrm.configuration.product;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sync REST fetch of product-service configuration rules
 * ({@code GET /api/v1/configuration/rules/product/{productId}}) with a thin in-process TTL cache (S2B-9).
 * No event-bus invalidation in this wave — entries expire by TTL only.
 */
@Component
@Slf4j
public class ProductConfigurationRuleClient {

    private static final ParameterizedTypeReference<ApiResponse<List<ProductConfigurationRuleDto>>> RULES_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient productServiceRestClient;
    private final Duration rulesCacheTtl;
    private final InstantSource clock;
    private final ConcurrentHashMap<Long, CacheEntry> rulesByProductId = new ConcurrentHashMap<>();

    public ProductConfigurationRuleClient(
            @Qualifier("productServiceRestClient") RestClient productServiceRestClient,
            @Value("${services.product.rules-cache-ttl:30s}") Duration rulesCacheTtl) {
        this(productServiceRestClient, rulesCacheTtl, InstantSource.system());
    }

    ProductConfigurationRuleClient(
            RestClient productServiceRestClient,
            Duration rulesCacheTtl,
            InstantSource clock) {
        this.productServiceRestClient = productServiceRestClient;
        this.rulesCacheTtl = rulesCacheTtl != null ? rulesCacheTtl : Duration.ofSeconds(30);
        this.clock = clock != null ? clock : InstantSource.system();
    }

    public List<ProductConfigurationRuleDto> getRulesForProduct(Long productId) {
        if (productId == null) {
            return Collections.emptyList();
        }

        Instant now = clock.instant();
        CacheEntry cached = rulesByProductId.get(productId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.rules();
        }

        List<ProductConfigurationRuleDto> fetched = fetchRules(productId);
        List<ProductConfigurationRuleDto> stored = List.copyOf(fetched);
        if (!rulesCacheTtl.isNegative() && !rulesCacheTtl.isZero()) {
            rulesByProductId.put(productId, new CacheEntry(stored, now.plus(rulesCacheTtl)));
        }
        return stored;
    }

    private List<ProductConfigurationRuleDto> fetchRules(Long productId) {
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

    private record CacheEntry(List<ProductConfigurationRuleDto> rules, Instant expiresAt) {}
}
