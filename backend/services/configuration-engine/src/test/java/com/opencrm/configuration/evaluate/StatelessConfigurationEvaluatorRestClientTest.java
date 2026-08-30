package com.opencrm.configuration.evaluate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * End-to-end evaluator tests with mocked RestClient (MockRestServiceServer):
 * empty valid, REQUIRES miss, EXCLUDES hit.
 */
@DisplayName("StatelessConfigurationEvaluator + RestClient")
class StatelessConfigurationEvaluatorRestClientTest {

    private MockRestServiceServer server;
    private StatelessConfigurationEvaluator evaluator;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product.test");
        server = MockRestServiceServer.bindTo(builder).build();
        ProductConfigurationRuleClient ruleClient = new ProductConfigurationRuleClient(
                builder.build(), Duration.ofSeconds(30));
        evaluator = new StatelessConfigurationEvaluator(ruleClient);
    }

    @Test
    @DisplayName("empty selection → valid (no RestClient calls)")
    void emptyValid() {
        ConfigurationValidationResult result = evaluator.evaluate(Collections.emptyList());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getViolations()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("REQUIRES miss → invalid")
    void requiresMiss() throws Exception {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(101L)
                .ruleType("REQUIRES")
                .sourceProductId(10L)
                .targetProductId(20L)
                .errorMessage("Product 10 requires product 20")
                .build();
        String body = objectMapper.writeValueAsString(ApiResponse.success(List.of(rule)));

        server.expect(requestTo("http://product.test/api/v1/configuration/rules/product/10"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).singleElement().satisfies(v -> {
            assertThat(v.getRuleType()).isEqualTo("REQUIRES");
            assertThat(v.getDescription()).isEqualTo("Product 10 requires product 20");
        });
        server.verify();
    }

    @Test
    @DisplayName("EXCLUDES hit → invalid")
    void excludesHit() throws Exception {
        ProductConfigurationRuleDto exclude = ProductConfigurationRuleDto.builder()
                .id(202L)
                .ruleType("EXCLUDES")
                .sourceProductId(10L)
                .targetProductId(30L)
                .errorMessage("Product 10 is incompatible with product 30")
                .build();
        String for10 = objectMapper.writeValueAsString(ApiResponse.success(List.of(exclude)));
        String for30 = objectMapper.writeValueAsString(ApiResponse.success(List.of()));

        server.expect(requestTo("http://product.test/api/v1/configuration/rules/product/10"))
                .andRespond(withSuccess(for10, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://product.test/api/v1/configuration/rules/product/30"))
                .andRespond(withSuccess(for30, MediaType.APPLICATION_JSON));

        ConfigurationValidationResult result = evaluator.evaluate(List.of(10L, 30L));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getViolations()).singleElement().satisfies(v -> {
            assertThat(v.getRuleType()).isEqualTo("EXCLUDES");
            assertThat(v.getDescription()).contains("incompatible");
        });
        server.verify();
    }
}
