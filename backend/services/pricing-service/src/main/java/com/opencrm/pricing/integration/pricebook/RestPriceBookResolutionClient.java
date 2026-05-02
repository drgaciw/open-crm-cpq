package com.opencrm.pricing.integration.pricebook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class RestPriceBookResolutionClient implements PriceBookResolutionClient {

    private final RestClient priceBookRestClient;
    private final ObjectMapper objectMapper;

    public RestPriceBookResolutionClient(
            @Qualifier("priceBookRestClient") RestClient priceBookRestClient,
            ObjectMapper objectMapper) {
        this.priceBookRestClient = priceBookRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolveBatchResponse resolveBatch(String region, List<String> productExternalIds) {
        record ResolveBatchHttpRequest(String region, List<String> productExternalIds) {}

        try {
            ApiResponse<ResolveBatchResponse> response = priceBookRestClient.post()
                    .uri("/internal/v1/price-books/resolve-batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResolveBatchHttpRequest(region, productExternalIds != null ? productExternalIds : List.of()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        try {
                            byte[] bodyBytes = res.getBody().readAllBytes();
                            String body = new String(bodyBytes, StandardCharsets.UTF_8);
                            ApiResponse<Void> err = objectMapper.readValue(body, new TypeReference<ApiResponse<Void>>() {});
                            String code = err.getErrorCode() != null ? err.getErrorCode() : "PRICEBOOK_CLIENT_ERROR";
                            throw new BusinessException(
                                    err.getMessage() != null ? err.getMessage() : "Price book service error",
                                    code);
                        } catch (IOException e) {
                            throw new BusinessException("Price book service error", "PRICEBOOK_CLIENT_ERROR");
                        }
                    })
                    .body(new ParameterizedTypeReference<ApiResponse<ResolveBatchResponse>>() {});

            if (response == null || response.getData() == null) {
                throw new BusinessException("Empty response from price book service", "PRICEBOOK_CLIENT_ERROR");
            }
            return response.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new BusinessException(
                    "Price book service unavailable: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PRICEBOOK_UNAVAILABLE");
        } catch (RestClientException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(
                    "Price book service error: " + e.getMessage(),
                    "PRICEBOOK_CLIENT_ERROR");
        }
    }
}
