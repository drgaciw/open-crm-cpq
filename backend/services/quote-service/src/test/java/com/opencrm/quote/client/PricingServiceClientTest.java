package com.opencrm.quote.client;

import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PricingServiceClient typed execute/simulate (Wave J1)")
class PricingServiceClientTest {

    private MockRestServiceServer server;
    private PricingServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pricing.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PricingServiceClient(builder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    @DisplayName("executePricing reads String body and binds PricingExecuteView via Jackson 2")
    void executePricingBindsTypedView() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"quoteExternalId\":\"Q-1\"}"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Pricing executed successfully",
                          "data": {
                            "success": true,
                            "approvalState": "APPROVED",
                            "approvalTier": "TIER_1",
                            "lineResults": [
                              {
                                "productExternalId": "PROD-001",
                                "lineItemType": "SUBSCRIPTION",
                                "quantity": 5,
                                "listPrice": 100.00,
                                "unitPrice": 90.00,
                                "discountRate": 0.1,
                                "discountAmount": 50.00,
                                "finalPrice": 450.00,
                                "explanationCodes": ["LIST", "DISC"]
                              }
                            ],
                            "financialMetrics": {
                              "arr": 5400,
                              "tcv": 5400,
                              "acv": 450.00
                            },
                            "waterfall": {
                              "listPrice": 100.00,
                              "netPrice": 450.00,
                              "approvalState": "APPROVED"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ApiResponse<PricingExecuteView> response = client.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Pricing executed successfully");
        PricingExecuteView data = response.getData();
        assertThat(data).isNotNull();
        assertThat(data.isSuccess()).isTrue();
        assertThat(data.getApprovalState()).isEqualTo("APPROVED");
        assertThat(data.getApprovalTier()).isEqualTo("TIER_1");
        assertThat(data.getLineResults()).hasSize(1);

        PricingLineResultView line = data.getLineResults().getFirst();
        assertThat(line.getProductExternalId()).isEqualTo("PROD-001");
        assertThat(line.getLineItemType()).isEqualTo("SUBSCRIPTION");
        assertThat(line.getListPrice()).isEqualByComparingTo("100.00");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(line.getFinalPrice()).isEqualByComparingTo("450.00");
        assertThat(line.getExplanationCodes()).containsExactly("LIST", "DISC");

        assertThat(data.getFinancialMetrics().getArr()).isEqualByComparingTo(new BigDecimal("5400"));
        assertThat(data.getFinancialMetrics().getTcv()).isEqualByComparingTo(new BigDecimal("5400"));
        assertThat(data.getFinancialMetrics().getAcv()).isEqualByComparingTo("450.00");
        assertThat(data.getWaterfall()).isNotNull();
        assertThat(data.getWaterfall().path("netPrice").decimalValue()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("simulatePricing uses the same typed parse path")
    void simulatePricingBindsTypedView() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/simulate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "success": true,
                            "lineResults": [],
                            "errorMessage": null
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ApiResponse<PricingExecuteView> response = client.simulatePricing(Map.of("quoteExternalId", "Q-sim"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isSuccess()).isTrue();
        assertThat(response.getData().getLineResults()).isEmpty();
    }

    @Test
    @DisplayName("executePricing maps transport failure to RestClientException")
    void executePricingTransportFailure() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.executePricing(Map.of("quoteExternalId", "Q-1")))
                .isInstanceOf(RestClientException.class);
    }
}
