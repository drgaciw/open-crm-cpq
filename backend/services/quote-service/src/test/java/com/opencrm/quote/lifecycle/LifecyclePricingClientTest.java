package com.opencrm.quote.lifecycle;

import com.opencrm.quote.client.ContinuationEvaluationView;
import com.opencrm.quote.client.PricingServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Additive pricing-client read. PolicyDecision fields are not on the execute view.
 * AC-S5-2 needs the continuation evaluation, including a null configuration hash.
 */
@DisplayName("Pricing continuation client")
class LifecyclePricingClientTest {

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
    @DisplayName("evaluateContinuation binds policy decision ids and a null configuration hash")
    void bindsPolicyDecision() {
        server.expect(requestTo("http://pricing.test/api/v1/pricing/continuation/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "traceId": "trace-1",
                            "policyId": "policy-amend",
                            "policyVersion": "9",
                            "lines": [
                              {
                                "lineRef": "line-1",
                                "policyDecisionId": "dec-1",
                                "policyId": "policy-amend",
                                "policyVersion": "9",
                                "traceId": "trace-1",
                                "productExternalId": "sku-seat",
                                "pricebookVersion": "pb-2026.10",
                                "configurationHash": null,
                                "procedureStatus": "PRICED"
                              }
                            ]
                          }
                        }
                        """, org.springframework.http.MediaType.APPLICATION_JSON));

        ContinuationEvaluationView view = client.evaluateContinuation(java.util.Map.of("currency", "USD")).getData();

        assertThat(view.getTraceId()).isEqualTo("trace-1");
        assertThat(view.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getPolicyDecisionId()).isEqualTo("dec-1");
            assertThat(line.getPolicyId()).isEqualTo("policy-amend");
            assertThat(line.getPolicyVersion()).isEqualTo("9");
            assertThat(line.getTraceId()).isEqualTo("trace-1");
            assertThat(line.getProductExternalId()).isEqualTo("sku-seat");
            assertThat(line.getPricebookVersion()).isEqualTo("pb-2026.10");
            assertThat(line.getConfigurationHash()).isNull();
        });
    }
}
