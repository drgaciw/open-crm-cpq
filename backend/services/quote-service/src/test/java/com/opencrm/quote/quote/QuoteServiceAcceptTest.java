package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStepRepository;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote accept publishes the totals the customer accepted")
class QuoteServiceAcceptTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private QuoteMapper quoteMapper;
    @Mock
    private QuoteNumberGenerator quoteNumberGenerator;
    @Mock
    private QuoteEventPublisher eventPublisher;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private PricingServiceClient pricingServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private QuoteRampStepRepository quoteRampStepRepository;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        RampScheduleGuard guard = new RampScheduleGuard(
                quoteLineItemRepository,
                quoteRampStepRepository,
                new RampScheduleValidator(),
                new RampPricingProperties(), org.mockito.Mockito.mock(com.opencrm.quote.client.ApprovalRoutingClient.class), org.mockito.Mockito.mock(com.opencrm.common.event.OutboxEventPublisher.class));
        quoteService = new QuoteService(
                quoteRepository,
                quoteMapper,
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                new ObjectMapper(),
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                guard);
    }

    /**
     * A priced quote totals 1,000.00, the sum of its lines' extended amounts. Re-deriving totals on accept added a
     * fixed 10% tax and published 1,100.00, which no longer matched {@code QuoteAccepted.lineItems}.
     */
    @Test
    @DisplayName("accept keeps the priced total, so the published lines add up to totalAmount")
    void acceptKeepsThePricedTotalSoLinesReconcile() {
        Quote quote = Quote.builder()
                .uuid("quote-uuid-1")
                .quoteNumber("Q-1")
                .customerExternalId("CUST-1")
                .status(QuoteStatus.SENT)
                .currency("USD")
                .subtotal(new BigDecimal("1000.00"))
                .totalAmount(new BigDecimal("1000.00"))
                .lineItems(new ArrayList<>())
                .build();
        quote.setId(1L);
        QuoteLineItem line = QuoteLineItem.builder()
                .uuid("line-uuid-1")
                .quote(quote)
                .productExternalId("prod-seat")
                .lineItemType("SUBSCRIPTION")
                .quantity(10)
                .listPrice(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("100.00"))
                .netPrice(new BigDecimal("100.0000"))
                .extendedAmount(new BigDecimal("1000.00"))
                .build();
        quote.getLineItems().add(line);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));

        quoteService.accept(1L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("Quote"), eq("Q-1"), eq(EventContractRegistry.QUOTE_ACCEPTED_V1), payload.capture());
        QuoteAcceptedEvent event = (QuoteAcceptedEvent) payload.getValue();
        BigDecimal lineTotal = event.getLineItems().stream()
                .map(QuoteAcceptedEvent.LineItemSnapshot::getExtendedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(lineTotal).isEqualByComparingTo(event.getTotalAmount());
    }
}
