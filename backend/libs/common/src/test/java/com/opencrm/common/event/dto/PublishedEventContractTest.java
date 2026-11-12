package com.opencrm.common.event.dto;

import com.opencrm.common.event.EventContractRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedEventContractTest {

    @Test
    void quoteAcceptedV1CarriesPublishedLanguageSnapshots() {
        Instant acceptedAt = Instant.parse("2026-05-11T00:00:00Z");

        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId("quote-123")
                        .quoteNumber("Q-123")
                        .acceptedAt(acceptedAt)
                        .build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder()
                        .customerId("customer-123")
                        .accountName("Acme Corp")
                        .build(),
                QuoteAcceptedEvent.PricingSnapshot.builder()
                        .totalAmount(new BigDecimal("1200.00"))
                        .currencyCode("USD")
                        .build(),
                QuoteAcceptedEvent.ConfigurationSnapshot.builder()
                        .configurationId("config-123")
                        .committedVersion("v3")
                        .attributes(Map.of("edition", "enterprise"))
                        .build(),
                QuoteAcceptedEvent.TermSnapshot.builder()
                        .termMonths(12)
                        .startDate("2026-06-01")
                        .endDate("2027-05-31")
                        .build(),
                QuoteAcceptedEvent.CorrelationSnapshot.builder()
                        .correlationId("corr-123")
                        .causationId("quote-accept-command")
                        .build()
        );

        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.QUOTE_ACCEPTED_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getQuoteId()).isEqualTo("quote-123");
        assertThat(event.getCustomerId()).isEqualTo("customer-123");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("1200.00");
        assertThat(event.getCurrencyCode()).isEqualTo("USD");
        assertThat(event.getConfiguration().getAttributes()).containsEntry("edition", "enterprise");
    }

    @Test
    void approvalStepAndOutcomeEventsHaveSeparateContracts() {
        assertThat(EventContractRegistry.APPROVAL_STEP_DECIDED_V1.owner()).isEqualTo("approval-routing");
        assertThat(EventContractRegistry.APPROVAL_OUTCOME_COMPLETED_V1.owner()).isEqualTo("approval-routing");
        assertThat(EventContractRegistry.APPROVAL_STEP_DECIDED_V1.eventType())
                .isNotEqualTo(EventContractRegistry.APPROVAL_OUTCOME_COMPLETED_V1.eventType());
    }

    @Test
    void configurationCommittedV1CarriesCommittedConfigurationSnapshot() {
        Instant committedAt = Instant.parse("2026-05-11T01:00:00Z");

        ConfigurationCommittedEvent event = ConfigurationCommittedEvent.v1(
                "config-123",
                "v4",
                "product-123",
                "user-123",
                committedAt,
                Map.of("edition", "enterprise", "support", "premium"),
                ConfigurationCommittedEvent.CorrelationSnapshot.builder()
                        .correlationId("corr-config-123")
                        .causationId("complete-configuration")
                        .sourceService("configuration-engine")
                        .build()
        );

        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.CONFIGURATION_COMMITTED_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getCommittedVersion()).isEqualTo("v4");
        assertThat(event.getSelections()).containsEntry("support", "premium");
        assertThat(event.getCorrelation().getSourceService()).isEqualTo("configuration-engine");
    }

    @Test
    void configurationSessionStartedV1CarriesStartedSessionSnapshot() {
        Instant startedAt = Instant.parse("2026-05-11T01:00:00Z");

        ConfigurationSessionStartedEvent event = ConfigurationSessionStartedEvent.v1(
                "session-123",
                "product-123",
                null,
                "catalog-v9",
                "product-product-123-v1",
                "tenant-abc",
                "user-123",
                startedAt,
                ConfigurationSessionStartedEvent.CorrelationSnapshot.builder()
                        .correlationId("corr-session-123")
                        .causationId("start-configuration-session")
                        .sourceService("configuration-engine")
                        .build()
        );

        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.CONFIGURATION_SESSION_STARTED_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getSessionId()).isEqualTo("session-123");
        assertThat(event.getProductId()).isEqualTo("product-123");
        assertThat(event.getBundleId()).isNull();
        assertThat(event.getCatalogVersion()).isEqualTo("catalog-v9");
        assertThat(event.getRuleSetVersion()).isEqualTo("product-product-123-v1");
        assertThat(event.getTenantId()).isEqualTo("tenant-abc");
        assertThat(event.getCorrelation().getSourceService()).isEqualTo("configuration-engine");
    }

    @Test
    void contractExecutedV1SeparatesClmActivationFromBillingPayload() {
        Instant executedAt = Instant.parse("2026-05-11T02:00:00Z");

        ContractExecutedEvent event = ContractExecutedEvent.v1(
                "contract-123",
                "subscription-123",
                "SUB-Q-123",
                "quote-123",
                "customer-123",
                executedAt,
                ContractExecutedEvent.BillingSnapshot.builder()
                        .arr(new BigDecimal("12000.00"))
                        .mrr(new BigDecimal("1000.00"))
                        .currencyCode("USD")
                        .startDate("2026-06-01")
                        .endDate("2027-05-31")
                        .renewalDate("2027-05-01")
                        .build(),
                ContractExecutedEvent.CorrelationSnapshot.builder()
                        .correlationId("corr-contract-123")
                        .sourceService("contract-asset-management")
                        .build()
        );

        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.CONTRACT_EXECUTED_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getSubscriptionNumber()).isEqualTo("SUB-Q-123");
        assertThat(event.getBilling().getMrr()).isEqualByComparingTo("1000.00");
        assertThat(event.getCorrelation().getSourceService()).isEqualTo("contract-asset-management");
    }
}
