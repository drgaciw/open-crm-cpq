package com.opencrm.quote.client;

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
 * Configuration validate (S2D-3a): configuration-engine's stateless evaluate, which returns the Wave F structured
 * {@link ConfigurationValidationResult} (not product {@code List<String>}). Called directly on CE's
 * {@code /internal/v1/configurations/evaluate} with this service's token; ADR-016 step 2 removed the old hairpin
 * through the gateway's {@code /api/v1/configuration/validate} shim.
 */
@Slf4j
@Component
public class ProductServiceClient {

    private static final ParameterizedTypeReference<ApiResponse<ConfigurationValidationResult>> VALIDATE_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient configurationEngineRestClient;

    public ProductServiceClient(
            @Qualifier("configurationEngineRestClient") RestClient configurationEngineRestClient) {
        this.configurationEngineRestClient = configurationEngineRestClient;
    }

    /**
     * CONFIGURATION_ONLY preflight: {@code POST /internal/v1/configurations/evaluate} on configuration-engine.
     */
    public ConfigurationValidationResult validateConfiguration(List<Long> productIds) {
        log.debug("Validating product configuration with configuration-engine for {} products", productIds.size());
        ApiResponse<ConfigurationValidationResult> response;
        try {
            response = configurationEngineRestClient.post()
                    .uri("/internal/v1/configurations/evaluate")
                    .body(productIds)
                    .retrieve()
                    .body(VALIDATE_RESPONSE);
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call configuration-engine evaluate: " + ex.getMessage(),
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
