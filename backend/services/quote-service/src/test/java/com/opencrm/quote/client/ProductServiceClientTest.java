package com.opencrm.quote.client;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ProductServiceClient S2D-3a configuration-engine evaluate (direct, ADR-016 step 2)")
class ProductServiceClientTest {

    private MockRestServiceServer ceServer;
    private ProductServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder ceBuilder = RestClient.builder().baseUrl("http://configuration-engine.test");
        ceServer = MockRestServiceServer.bindTo(ceBuilder).build();

        client = new ProductServiceClient(ceBuilder.build());
    }

    @AfterEach
    void verifyServer() {
        ceServer.verify();
    }

    @Test
    @DisplayName("validateConfiguration POSTs to CE evaluate and parses valid=true")
    void validateConfigurationParsesValidResult() {
        ceServer.expect(requestTo("http://configuration-engine.test/internal/v1/configurations/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[10,20]"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Configuration is valid",
                          "data": { "valid": true, "violations": [] }
                        }
                        """, MediaType.APPLICATION_JSON));

        ConfigurationValidationResult result = client.validateConfiguration(List.of(10L, 20L));

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
    }

    @Test
    @DisplayName("validateConfiguration parses valid=false with violations")
    void validateConfigurationParsesInvalidResult() {
        ceServer.expect(requestTo("http://configuration-engine.test/internal/v1/configurations/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "message": "Configuration has validation errors",
                          "data": {
                            "valid": false,
                            "violations": [
                              {
                                "ruleId": 1,
                                "ruleType": "REQUIRES",
                                "description": "Product 10 requires 20",
                                "remediation": "Add product 20"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ConfigurationValidationResult result = client.validateConfiguration(List.of(10L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).hasSize(1);
        assertThat(result.getViolations().getFirst().getRuleType()).isEqualTo("REQUIRES");
        assertThat(result.getViolations().getFirst().getDescription()).isEqualTo("Product 10 requires 20");
    }

    @Test
    @DisplayName("validateConfiguration maps a CE transport failure to BusinessException")
    void validateConfigurationMapsTransportFailure() {
        ceServer.expect(requestTo("http://configuration-engine.test/internal/v1/configurations/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.validateConfiguration(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CONFIGURATION_VALIDATE_UNAVAILABLE");
    }
}
