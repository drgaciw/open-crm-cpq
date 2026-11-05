package com.opencrm.quote.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.OmsAcceptedSnapshotEvent;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.lineitem.QuoteLineItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptedSnapshotServiceTest {
    private final AcceptedSnapshotRepository repository = mock(AcceptedSnapshotRepository.class);
    private final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OmsQuoteProperties properties = new OmsQuoteProperties();
    private final AcceptedSnapshotService service = new AcceptedSnapshotService(repository, outbox, mapper, properties);

    @Test void intakeRequiresEnabledScopeAndExplicitCustomerCohort() {
        Quote quote = quote();
        assertThat(service.owns(quote)).isFalse();
        properties.setEnabled(true);
        assertThat(service.owns(quote)).isFalse();
        properties.setScopeId("pilot");
        assertThat(service.owns(quote)).isFalse();
        properties.setCustomerIds(Set.of("account-1"));
        assertThat(service.owns(quote)).isTrue();
    }
    @Test void capturesExactImmutablePayloadAndMissingEvidenceWithoutInventingTerms() throws Exception {
        properties.setEnabled(true); properties.setScopeId("pilot"); properties.setCustomerIds(Set.of("account-1"));
        Quote quote = quote();
        QuoteLineItem line = QuoteLineItem.builder().uuid("line-1").productExternalId("p1")
            .productName("Service").quantity(2).unitPrice(new BigDecimal("25.00"))
            .discountAmount(BigDecimal.ZERO).extendedAmount(new BigDecimal("50.00")).build();
        var stored = service.capture(quote, List.of(line));
        assertThat(stored.getSnapshotHash()).isEqualTo(AcceptedSnapshotService.sha256(stored.getPayload()));
        assertThat(stored.getHolds()).contains("MISSING_TAX_EVIDENCE", "MISSING_APPROVAL_REFERENCE");
        var snapshot = mapper.readValue(stored.getPayload(), com.opencrm.common.order.AcceptedOrderSnapshot.class);
        assertThat(snapshot.total()).isEqualTo("50.00");
        assertThat(snapshot.lines().getFirst().fulfillmentType()).isNull();
        assertThat(snapshot.lines().getFirst().startDate()).isNull();
        quote.setTotalAmount(BigDecimal.TEN); line.setProductName("Changed");
        assertThat(stored.getPayload()).contains("Service", "50.00").doesNotContain("Changed");
        verify(repository).save(stored);
        verify(outbox).publish(eq("Quote"), eq(quote.getUuid()), eq(com.opencrm.common.event.EventContractRegistry.OMS_ACCEPTED_SNAPSHOT_V1), any(OmsAcceptedSnapshotEvent.class));
    }
    @Test void approvedMappingAndCompleteEvidenceProduceReconciledSnapshotAndIdenticalEventHash() throws Exception {
        properties.setEnabled(true); properties.setScopeId("pilot"); properties.setCustomerIds(Set.of("account-1"));
        properties.setProductFulfillmentTypes(java.util.Map.of("p1", "DIGITAL")); properties.setClassificationMappingVersion("pilot-v1");
        Quote quote = quote(); quote.setApprovalState(com.opencrm.quote.quote.ApprovalState.APPROVED); quote.setOmsApprovalReference("approved-ref");
        quote.setMetadata("{\"oms\":{\"address\":\"address\",\"taxEvidence\":\"tax-ref\",\"configurationReference\":\"config-ref\"}}");
        QuoteLineItem line = QuoteLineItem.builder().uuid("line-1").productExternalId("p1").productName("License")
            .quantity(2).unitPrice(new BigDecimal("25.00")).discountAmount(BigDecimal.ZERO).extendedAmount(new BigDecimal("50.00"))
            .unitOfMeasure("seat").billingFrequency("ANNUAL")
            .metadata("{\"oms\":{\"fulfillmentType\":\"DIGITAL\",\"chargeType\":\"RECURRING\",\"tax\":\"0.00\",\"startDate\":\"2026-09-20\",\"endDate\":\"2027-09-20\"}}")
            .build();
        var stored = service.capture(quote,List.of(line));
        assertThat(stored.getHolds()).isEqualTo("[]");
        var event = org.mockito.ArgumentCaptor.forClass(OmsAcceptedSnapshotEvent.class);
        verify(outbox).publish(eq("Quote"),eq(quote.getUuid()),eq(com.opencrm.common.event.EventContractRegistry.OMS_ACCEPTED_SNAPSHOT_V1),event.capture());
        String wire = mapper.writeValueAsString(event.getValue());
        assertThat(AcceptedSnapshotService.sha256(mapper.readTree(wire).get("snapshot").toString())).isEqualTo(stored.getSnapshotHash());
        assertThat(event.getValue().snapshot().configurationReference()).contains("classification-mapping=pilot-v1");
        properties.setEnabled(false);
        assertThat(service.owns(quote)).isTrue();
    }
    private Quote quote() {
        return Quote.builder().uuid("b70be08b-f70d-406f-82db-96c3f53410dc").quoteNumber("Q-001")
            .customerExternalId("account-1").acceptedAt(Instant.parse("2026-09-20T00:00:00Z"))
            .subtotal(new BigDecimal("50.00")).totalDiscount(BigDecimal.ZERO).totalTax(BigDecimal.ZERO)
            .totalAmount(new BigDecimal("50.00")).build();
    }
}
