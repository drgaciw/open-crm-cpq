package com.opencrm.quote.client;

import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
    @DisplayName("Spring can select an injection constructor (UAT-1: no default ctor)")
    void springInjectionConstructorIsUnambiguous() {
        Constructor<?>[] declared = PricingServiceClient.class.getDeclaredConstructors();
        long autowired = Arrays.stream(declared)
                .filter(c -> c.getAnnotation(Autowired.class) != null)
                .count();
        assertThat(declared.length == 1 || autowired == 1)
                .as("Spring cannot pick among %s declared constructors without @Autowired (UAT-1 BeanCreationException: No default constructor found)",
                        declared.length)
                .isTrue();
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
    @DisplayName("executePricing binds an engine success body including ramp fields and extra metrics")
    void executePricingBindsEngineSuccessBody() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "timestamp": "2026-10-05T12:00:00Z",
                          "data": {
                            "success": true,
                            "executionTimeMs": 18,
                            "explanationCodes": ["RAMP"],
                            "lineResults": [{
                              "productExternalId": "PROD-SEAT",
                              "lineItemType": "SAAS_SEAT_NAMED",
                              "quantity": 50,
                              "listPrice": 50.00,
                              "unitPrice": 40.0000,
                              "discountRate": 0.2000,
                              "discountAmount": 500.00,
                              "surchargeAmount": 0.00,
                              "finalPrice": 2000.00,
                              "explanationCodes": ["LIST_PRICE"],
                              "metadata": {"seat": "named"},
                              "pricedPeriods": [{
                                "sequence": 1,
                                "startMonth": 1,
                                "months": 12,
                                "quantity": 50,
                                "netUnitPrice": 40.0000,
                                "netMonthly": 2000.00,
                                "periodAmount": 24000.00,
                                "discountRate": 0.2000,
                                "explanationCodes": ["LIST_PRICE"],
                                "waterfall": [{"stepType": "LIST_PRICE", "amount": 50.00}]
                              }]
                            }],
                            "financialMetrics": {
                              "arr": 24000.00,
                              "tcv": 81000.00,
                              "acv": 27000.00,
                              "exitArr": 30000.00,
                              "clv": 162000.00,
                              "churnScore": 10,
                              "discountRate": 0.2000,
                              "totalDiscount": 9000.00,
                              "totalSurcharge": 0.00,
                              "priceMultiplier": 1.0,
                              "estimatedMarginPct": 40.0
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ApiResponse<PricingExecuteView> response = client.executePricing(Map.of("quoteExternalId", "Q-RAMP-01"));

        assertThat(response.isSuccess()).isTrue();
        PricingExecuteView data = response.getData();
        assertThat(data.getExecutionTimeMs()).isEqualTo(18L);
        assertThat(data.getExplanationCodes()).containsExactly("RAMP");
        PricingLineResultView line = data.getLineResults().getFirst();
        assertThat(line.getSurchargeAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getMetadata()).containsEntry("seat", "named");
        PricedPeriodView period = line.getPricedPeriods().getFirst();
        assertThat(period.getDiscountRate()).isEqualByComparingTo("0.2000");
        assertThat(period.getPeriodAmount()).isEqualByComparingTo("24000.00");
        assertThat(period.getWaterfall().get(0).get("stepType").asText()).isEqualTo("LIST_PRICE");
        PricingFinancialMetricsView metrics = data.getFinancialMetrics();
        assertThat(metrics.getTcv()).isEqualByComparingTo("81000.00");
        assertThat(metrics.getAcv()).isEqualByComparingTo("27000.00");
        assertThat(metrics.getArr()).isEqualByComparingTo("24000.00");
        assertThat(metrics.getExitArr()).isEqualByComparingTo("30000.00");
        assertThat(metrics.getClv()).isEqualByComparingTo("162000.00");
        assertThat(metrics.getChurnScore()).isEqualTo(10);
        assertThat(metrics.getDiscountRate()).isEqualByComparingTo("0.2000");
        assertThat(metrics.getTotalDiscount()).isEqualByComparingTo("9000.00");
        assertThat(metrics.getTotalSurcharge()).isEqualByComparingTo("0.00");
        assertThat(metrics.getPriceMultiplier()).isEqualByComparingTo("1.0");
        assertThat(metrics.getEstimatedMarginPct()).isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("flag-off 422 is an error envelope with no success body")
    void executePricingReturnsFlagOffEnvelope() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "success": false,
                                  "message": "A ramp schedule cannot be priced while opencrm.pricing.ramp.enabled is false",
                                  "errorCode": "RAMP_NOT_SUPPORTED",
                                  "traceId": "trace-off",
                                  "timestamp": "2026-10-05T12:00:00Z"
                                }
                                """));

        ApiResponse<PricingExecuteView> response = client.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("RAMP_NOT_SUPPORTED");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("disallowed line 422 keeps RAMP_SCHEDULE_INVALID and its reason")
    void executePricingReturnsScheduleInvalidEnvelope() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/execute"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "success": false,
                                  "message": "RAMP_NOT_ALLOWED_FOR_LINE_TYPE",
                                  "errorCode": "RAMP_SCHEDULE_INVALID",
                                  "traceId": "trace-type",
                                  "timestamp": "2026-10-05T12:00:00Z"
                                }
                                """));

        ApiResponse<PricingExecuteView> response = client.executePricing(Map.of("quoteExternalId", "Q-1"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("RAMP_SCHEDULE_INVALID");
        assertThat(response.getMessage()).isEqualTo("RAMP_NOT_ALLOWED_FOR_LINE_TYPE");
        assertThat(response.getData()).isNull();
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
