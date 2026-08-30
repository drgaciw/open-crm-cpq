package com.opencrm.configuration.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ProductConfigurationRuleClient")
class ProductConfigurationRuleClientTest {

    private MockRestServiceServer server;
    private RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final InstantSource clock = () -> now.get();

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("http://product.test");
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        now.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    private ProductConfigurationRuleClient client(Duration ttl) {
        return new ProductConfigurationRuleClient(restClientBuilder.build(), ttl, clock);
    }

    @Test
    @DisplayName("fetches rules via GET /api/v1/configuration/rules/product/{id}")
    void fetchesRulesForProduct() throws Exception {
        ProductConfigurationRuleClient client = client(Duration.ofSeconds(30));
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
        ProductConfigurationRuleClient client = client(Duration.ofSeconds(30));
        assertThat(client.getRulesForProduct(null)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("second getRulesForProduct within TTL does not call RestClient")
    void secondFetchWithinTtlUsesCache() throws Exception {
        ProductConfigurationRuleClient client = client(Duration.ofSeconds(30));
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(1L)
                .ruleType("REQUIRES")
                .sourceProductId(10L)
                .targetProductId(20L)
                .build();
        String body = objectMapper.writeValueAsString(ApiResponse.success(List.of(rule)));

        server.expect(once(), requestTo("http://product.test/api/v1/configuration/rules/product/10"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ProductConfigurationRuleDto> first = client.getRulesForProduct(10L);
        now.set(now.get().plusSeconds(29));
        List<ProductConfigurationRuleDto> second = client.getRulesForProduct(10L);

        assertThat(first).hasSize(1);
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    @DisplayName("getRulesForProduct after TTL expiry refetches via RestClient")
    void fetchAfterTtlExpiryRefetches() throws Exception {
        ProductConfigurationRuleClient client = client(Duration.ofSeconds(30));
        ProductConfigurationRuleDto rule = ProductConfigurationRuleDto.builder()
                .id(1L)
                .ruleType("EXCLUDES")
                .sourceProductId(10L)
                .targetProductId(99L)
                .build();
        String body = objectMapper.writeValueAsString(ApiResponse.success(List.of(rule)));

        server.expect(times(2), requestTo("http://product.test/api/v1/configuration/rules/product/10"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.getRulesForProduct(10L)).hasSize(1);
        now.set(now.get().plusSeconds(30));
        assertThat(client.getRulesForProduct(10L)).hasSize(1);
        server.verify();
    }
}
