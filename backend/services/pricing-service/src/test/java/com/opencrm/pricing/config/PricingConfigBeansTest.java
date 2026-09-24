package com.opencrm.pricing.config;

import com.opencrm.pricing.execution.PricingAuditLog;
import com.opencrm.security.client.OutboundAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Pricing config beans and audit log")
class PricingConfigBeansTest {

    @Test
    void openApiGroup_isPricing() {
        assertThat(new OpenApiConfig().pricingApi().getGroup()).isEqualTo("pricing");
    }

    @Test
    @DisplayName("the price-book client sends pricing-service's own token to /internal (ADR-016 step 2)")
    void priceBookRestClient_sendsTheServiceToken() {
        RestClient client = new PriceBookClientConfig().priceBookRestClient("http://pricebook.local",
                new OutboundAuth(() -> Optional.of("pricing-service-token"), false));
        RestClient.Builder builder = client.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://pricebook.local/internal/v1/price-books/resolve-batch"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer pricing-service-token"))
                .andRespond(withSuccess());

        builder.build().post().uri("/internal/v1/price-books/resolve-batch").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void auditLog_builderRoundTrip() {
        Instant now = Instant.parse("2026-09-13T00:00:00Z");
        PricingAuditLog log = PricingAuditLog.builder()
                .id(1L).uuid("u-1").quoteExternalId("Q-1").customerExternalId("C-1")
                .ruleSetId(9L).executedAt(now).build();
        assertThat(log.getQuoteExternalId()).isEqualTo("Q-1");
        assertThat(log.getRuleSetId()).isEqualTo(9L);
        assertThat(log.getUuid()).isEqualTo("u-1");
    }
}
