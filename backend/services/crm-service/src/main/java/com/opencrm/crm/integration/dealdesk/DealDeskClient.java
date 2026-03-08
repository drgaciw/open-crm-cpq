package com.opencrm.crm.integration.dealdesk;

import com.opencrm.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.Objects;

/**
 * HTTP client for creating Deal Desk deals from CRM.
 */
@Component
public class DealDeskClient {

    private static final ParameterizedTypeReference<ApiResponse<DealPromotionResponse>> CREATE_DEAL_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public DealDeskClient(@Qualifier("dealDeskRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ApiResponse<DealPromotionResponse> createDeal(DealPromotionRequest request, String bearerToken) {
        Assert.notNull(request, "request must not be null");

        ApiResponse<DealPromotionResponse> response = restClient.post()
                .uri("/api/v1/deals")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(normalizeBearerToken(bearerToken)))
                .body(request)
                .retrieve()
                .body(CREATE_DEAL_RESPONSE_TYPE);

        return Objects.requireNonNull(response, "deal-desk response body must not be null");
    }

    private String normalizeBearerToken(String bearerToken) {
        Assert.hasText(bearerToken, "bearerToken must not be blank");

        String token = bearerToken.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return token.substring(7).trim();
        }
        return token;
    }
}
