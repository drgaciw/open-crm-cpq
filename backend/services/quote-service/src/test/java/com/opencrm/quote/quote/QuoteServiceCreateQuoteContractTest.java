package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteCreatedEvent;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-447 / BC-05-01 "Create a quote from an opportunity or account".
 *
 * <p>Covers the acceptance criteria directly: the created quote carries the account (customerExternalId), currency
 * and ValidityPeriod (validUntil), starts QuoteStatus.DRAFT, a QuoteCreated event is emitted exactly once through
 * the outbox (ADR-018 D5/D9), and ListQuotes filtered by the account returns the quote.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateQuote (DRG-447): account, currency, ValidityPeriod, Draft status, QuoteCreated, ListQuotes(account)")
class QuoteServiceCreateQuoteContractTest {

    @Mock
    private QuoteRepository quoteRepository;
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

    private QuoteMapperImpl quoteMapper;
    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteMapper = new QuoteMapperImpl();
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
                RampScheduleTestSupport.guard(quoteLineItemRepository)
        );
        when(quoteNumberGenerator.generate()).thenReturn("Q-447");
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            quote.setId(1L);
            quote.setUuid("quote-uuid-1");
            return quote;
        });
    }

    private static QuoteCreateRequest.QuoteCreateRequestBuilder baseRequest() {
        return QuoteCreateRequest.builder()
                .name("Acme renewal")
                .customerExternalId("acct-123")
                .dealExternalId("deal-456");
    }

    @Test
    @DisplayName("the created quote carries the account, currency, ValidityPeriod and starts in Draft")
    void createdQuoteCarriesAccountCurrencyAndValidityPeriod() {
        QuoteDto created = quoteService.create(baseRequest().currency("EUR").build());

        assertThat(created.getCustomerExternalId()).isEqualTo("acct-123");
        assertThat(created.getCurrency()).isEqualTo("EUR");
        assertThat(created.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(created.getValidUntil()).isNotNull();
    }

    @Test
    @DisplayName("an explicit ValidityPeriod is kept as given")
    void explicitValidUntilIsKept() {
        LocalDate validUntil = LocalDate.now().plusDays(45);
        QuoteDto created = quoteService.create(baseRequest().validUntil(validUntil).build());

        assertThat(created.getValidUntil()).isEqualTo(validUntil);
    }

    @Test
    @DisplayName("an omitted ValidityPeriod defaults to the documented window from creation")
    void omittedValidUntilDefaults() {
        QuoteDto created = quoteService.create(baseRequest().build());

        assertThat(created.getValidUntil())
                .isEqualTo(LocalDate.now().plusDays(QuoteService.DEFAULT_VALIDITY_DAYS));
    }

    @Test
    @DisplayName("currency defaults to USD, matching the entity default, when omitted")
    void currencyDefaultsToUsd() {
        QuoteDto created = quoteService.create(baseRequest().build());

        assertThat(created.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("QuoteCreated is emitted exactly once through the outbox, naming the registered contract")
    void quoteCreatedIsEmittedThroughTheOutbox() {
        QuoteDto created = quoteService.create(baseRequest().currency("GBP").build());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("Quote"), eq("1"), eq(EventContractRegistry.QUOTE_CREATED_V1), payload.capture());

        assertThat(payload.getValue()).isInstanceOf(QuoteCreatedEvent.class);
        QuoteCreatedEvent event = (QuoteCreatedEvent) payload.getValue();
        assertThat(event.getQuoteNumber()).isEqualTo("Q-447");
        assertThat(event.getAccountId()).isEqualTo("acct-123");
        assertThat(event.getDealExternalId()).isEqualTo("deal-456");
        assertThat(event.getCurrency()).isEqualTo("GBP");
        assertThat(event.getStatus()).isEqualTo("DRAFT");
        assertThat(event.getValidUntil()).isEqualTo(created.getValidUntil());
        assertThat(event.getCreatedAt()).isNotNull();

        // EventContractRegistry.QUOTE_CREATED_V1 only accepts its registered payload class (ADR-018 D5); a wrong
        // payload type would have thrown IllegalArgumentException inside OutboxEventPublisher before reaching here,
        // but that is mocked here, so this also pins the DTO contract directly.
        assertThat(EventContractRegistry.QUOTE_CREATED_V1.payloadClass()).isEqualTo(QuoteCreatedEvent.class);
    }

    @Test
    @DisplayName("ListQuotes filtered by the account returns the created quote")
    void listQuotesByAccountReturnsCreatedQuote() {
        QuoteDto created = quoteService.create(baseRequest().build());

        Quote persisted = new Quote();
        persisted.setId(1L);
        persisted.setQuoteNumber(created.getQuoteNumber());
        persisted.setCustomerExternalId("acct-123");
        persisted.setStatus(QuoteStatus.DRAFT);
        persisted.setCurrency("USD");

        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        Page<Quote> page = new PageImpl<>(List.of(persisted), pageable, 1);
        when(quoteRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<QuoteDto> result = quoteService.list(pageable, null, null, "acct-123");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCustomerExternalId()).isEqualTo("acct-123");
        assertThat(result.getContent().get(0).getQuoteNumber()).isEqualTo("Q-447");
    }
}
