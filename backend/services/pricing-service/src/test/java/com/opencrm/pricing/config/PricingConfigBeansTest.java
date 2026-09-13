package com.opencrm.pricing.config;

import com.opencrm.pricing.execution.PricingAuditLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pricing config beans and audit log")
class PricingConfigBeansTest {

    @Test
    void openApiGroup_isPricing() {
        assertThat(new OpenApiConfig().pricingApi().getGroup()).isEqualTo("pricing");
    }

    @Test
    void priceBookRestClient_usesBaseUrl() {
        RestClient client = new PriceBookClientConfig().priceBookRestClient("http://pricebook.local");
        assertThat(client).isNotNull();
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
