package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService.create applies the CRM deal type at the opportunity handoff")
class QuoteServiceCreateDealTypeTest {

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

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
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
        when(quoteMapper.toEntity(any(QuoteCreateRequest.class))).thenAnswer(invocation -> {
            QuoteCreateRequest request = invocation.getArgument(0);
            Quote quote = new Quote();
            quote.setName(request.getName());
            quote.setCustomerExternalId(request.getCustomerExternalId());
            quote.setDealExternalId(request.getDealExternalId());
            if (request.getDealType() != null) {
                quote.setDealType(request.getDealType());
            }
            return quote;
        });
        when(quoteNumberGenerator.generate()).thenReturn("Q-100");
    }

    private void stubPersistence() {
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quoteMapper.toDto(any(Quote.class))).thenAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            return QuoteDto.builder()
                    .quoteNumber(quote.getQuoteNumber())
                    .dealType(quote.getDealType())
                    .dealExternalId(quote.getDealExternalId())
                    .build();
        });
    }

    @Test
    @DisplayName("an opportunity UPSELL is stored as quote EXPANSION")
    void mapsCrmTypeOntoTheCreatedQuote() {
        stubPersistence();
        QuoteDto created = quoteService.create(QuoteCreateRequest.builder()
                .name("Expand seats")
                .customerExternalId("cust-1")
                .dealExternalId("deal-1")
                .crmDealType("UPSELL")
                .build());

        assertThat(created.getDealType()).isEqualTo(DealType.EXPANSION);
        ArgumentCaptor<Quote> saved = ArgumentCaptor.forClass(Quote.class);
        verify(quoteRepository).save(saved.capture());
        assertThat(saved.getValue().getDealType()).isEqualTo(DealType.EXPANSION);
        assertThat(saved.getValue().getDealExternalId()).isEqualTo("deal-1");
    }

    @Test
    @DisplayName("omitting both types keeps the documented default NEW")
    void defaultsToNewWhenNoTypeIsSupplied() {
        stubPersistence();
        QuoteDto created = quoteService.create(baseRequest().build());

        assertThat(created.getDealType()).isEqualTo(DealType.NEW);
    }

    @Test
    @DisplayName("an explicit quote type is kept when no CRM type is supplied")
    void keepsExplicitQuoteTypeWithoutCrmType() {
        stubPersistence();
        QuoteDto created = quoteService.create(baseRequest().dealType(DealType.RENEWAL).build());

        assertThat(created.getDealType()).isEqualTo(DealType.RENEWAL);
    }

    @Test
    @DisplayName("a CRM type that agrees with the quote type is stored")
    void acceptsAgreeingTypes() {
        stubPersistence();
        QuoteDto created = quoteService.create(baseRequest()
                .crmDealType("RENEWAL")
                .dealType(DealType.RENEWAL)
                .build());

        assertThat(created.getDealType()).isEqualTo(DealType.RENEWAL);
    }

    @Test
    @DisplayName("a CRM type that disagrees with the quote type fails and saves nothing")
    void rejectsConflictingTypes() {
        assertThatThrownBy(() -> quoteService.create(baseRequest()
                .crmDealType("UPSELL")
                .dealType(DealType.NEW)
                .build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_CONFLICT");
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blank CRM type fails the handoff and saves nothing")
    void rejectsBlankCrmType() {
        assertThatThrownBy(() -> quoteService.create(baseRequest().crmDealType(" ").build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_REQUIRED");
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("EXTENSION is not a CRM type and is not stored as RENEWAL")
    void rejectsExtension() {
        assertThatThrownBy(() -> quoteService.create(baseRequest().crmDealType("EXTENSION").build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_UNMAPPED");
        verify(quoteRepository, never()).save(any());
    }

    private static QuoteCreateRequest.QuoteCreateRequestBuilder baseRequest() {
        return QuoteCreateRequest.builder()
                .name("Handoff")
                .customerExternalId("cust-1")
                .dealExternalId("deal-1");
    }
}
