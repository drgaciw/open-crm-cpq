package com.opencrm.quote.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(@Qualifier("productRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode getProductById(Long productId) {
        log.debug("Fetching product by ID: {}", productId);
        return restClient.get()
                .uri("/api/v1/products/{id}", productId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        return restClient.get()
                .uri("/api/v1/products/sku/{sku}", sku)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode validateConfiguration(List<Long> productIds) {
        log.debug("Validating product configuration for {} products", productIds.size());
        return restClient.post()
                .uri("/api/v1/configuration/validate")
                .body(productIds)
                .retrieve()
                .body(JsonNode.class);
    }
}
