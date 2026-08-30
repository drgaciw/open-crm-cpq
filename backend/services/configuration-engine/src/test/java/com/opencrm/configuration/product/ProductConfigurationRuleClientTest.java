package com.opencrm.configuration.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ProductConfigurationRuleClient")
class ProductConfigurationRuleClientTest {

    private MockRestServiceServer server;
    private ProductConfigurationRuleClient client;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProductConfigurationRuleClient(builder.build());
    }

    @Test
    @DisplayName("fetches rules via GET /api/v1/configuration/rules/product/{id}")
    void fetchesRulesForProduct() throws Exception {
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(1L)
                .ruleType("REQUIRES")
                .sourceProductId(10L)
                .targetProductId(20L)
                .errorMessage("needs addon")
                .build();
        String body = objectMapper.writeValueAsString(ApiResponse.success(List.of(rule)));

        server.expect(requestTo("http://product.test/api/v1/configuration/rules/product/10"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ProductConfigurationRuleDto> rules = client.getRulesForProduct(10L);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getRuleType()).isEqualTo("REQUIRES");
        assertThat(rules.get(0).getTargetProductId()).isEqualTo(20L);
        server.verify();
    }

    @Test
    @DisplayName("null product id returns empty list without HTTP call")
    void nullProductIdReturnsEmpty() {
        assertThat(client.getRulesForProduct(null)).isEmpty();
        server.verify();
    }
}
