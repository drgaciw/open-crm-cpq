package com.opencrm.configuration.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/** Fetch fresh authoritative defaults for each mutation; never manufacture an empty success. */
@Component
public class ProductOptionDefaultsClient {
    private static final ParameterizedTypeReference<ApiResponse<List<ProductOptionGroupSnapshot>>> RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<ProductIdentity>> PRODUCT =
            new ParameterizedTypeReference<>() {};
    private final RestClient client;

    public ProductOptionDefaultsClient(@Qualifier("productServiceRestClient") RestClient client) {
        this.client = client;
    }

    public List<ProductOptionGroupSnapshot> getGroups(Long productId) {
        // The groups endpoint returns [] for an unknown parent; establish identity separately.
        requireProduct(productId);
        try {
            ApiResponse<List<ProductOptionGroupSnapshot>> response = client.get()
                    .uri("/api/v1/product-options/groups/product/{productId}", productId)
                    .retrieve().body(RESPONSE);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException("Catalog returned no option-group snapshot",
                        HttpStatus.BAD_GATEWAY, "INVALID_DEFAULTS_CATALOG");
            }
            return response.getData();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BusinessException("Configuration product was not found in the catalog",
                    HttpStatus.NOT_FOUND, "DEFAULTS_PRODUCT_NOT_FOUND");
        } catch (RestClientException ex) {
            throw new BusinessException("Catalog unavailable while loading configuration defaults",
                    HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_DEFAULTS_UNAVAILABLE");
        }
    }

    /** Confirm each newly selected default target through the catalog's non-deleted product lookup. */
    public void requireProduct(Long productId) {
        try {
            ApiResponse<ProductIdentity> product = client.get().uri("/api/v1/products/{id}", productId)
                    .retrieve().body(PRODUCT);
            if (product == null || !product.isSuccess() || product.getData() == null
                    || !java.util.Objects.equals(productId, product.getData().id())) {
                throw new BusinessException("Catalog did not confirm the selected product",
                        HttpStatus.BAD_GATEWAY, "INVALID_DEFAULTS_CATALOG");
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new BusinessException("Selected product was not found in the catalog",
                    HttpStatus.NOT_FOUND, "DEFAULTS_PRODUCT_NOT_FOUND");
        } catch (RestClientException ex) {
            throw new BusinessException("Catalog unavailable while checking a configuration product",
                    HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_DEFAULTS_UNAVAILABLE");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductIdentity(Long id) {}
}
