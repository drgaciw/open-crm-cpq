package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventConsumerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ErpAdapter erpAdapter;

    @InjectMocks
    private QuoteAcceptedEventConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new QuoteAcceptedEventConsumer(subscriptionRepository, erpAdapter, objectMapper);
    }

    @Test
    void activatesSubscriptionFromQuoteAcceptedEvent() throws Exception {
        QuoteAcceptedEvent event = publishedLanguageEvent("quote-uuid-3", "cust-3", new BigDecimal("2400.00"));

        when(subscriptionRepository.existsByQuoteId("quote-uuid-3")).thenReturn(false);
        when(erpAdapter.syncToErp(any(Subscription.class))).thenReturn("erp-1");
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.handleQuoteAccepted(objectMapper.writeValueAsString(event));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Subscription saved = captor.getAllValues().getLast();
        assertThat(saved.getQuoteId()).isEqualTo("quote-uuid-3");
        assertThat(saved.getCustomerId()).isEqualTo("cust-3");
        assertThat(saved.getSubscriptionNumber()).isEqualTo("SUB-quote-uuid-3");
        assertThat(saved.getArr()).isEqualByComparingTo("2400.00");
        assertThat(saved.getCurrencyCode()).isEqualTo("USD");
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void skipsWhenSubscriptionAlreadyExistsForQuote() throws Exception {
        QuoteAcceptedEvent event = publishedLanguageEvent("quote-uuid-4", "cust-4", new BigDecimal("100.00"));
        when(subscriptionRepository.existsByQuoteId("quote-uuid-4")).thenReturn(true);

        consumer.handleQuoteAccepted(objectMapper.writeValueAsString(event));

        verify(subscriptionRepository, never()).save(any());
        verify(erpAdapter, never()).syncToErp(any());
    }

    private static QuoteAcceptedEvent publishedLanguageEvent(
            String quoteId,
            String customerId,
            BigDecimal total
    ) {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId(quoteId)
                        .acceptedAt(Instant.parse("2026-05-16T00:00:00Z"))
                        .build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder().customerId(customerId).build(),
                QuoteAcceptedEvent.PricingSnapshot.builder().totalAmount(total).currencyCode("USD").build(),
                null,
                QuoteAcceptedEvent.TermSnapshot.builder()
                        .termMonths(12)
                        .startDate(LocalDate.now().toString())
                        .endDate(LocalDate.now().plusYears(1).toString())
                        .build(),
                QuoteAcceptedEvent.CorrelationSnapshot.builder().correlationId("corr-" + quoteId).build()
        );
        event.setEventType(EventContractRegistry.QUOTE_ACCEPTED_V1.eventType());
        event.setEventVersion(EventContractRegistry.QUOTE_ACCEPTED_V1.eventVersion());
        return event;
    }
}
