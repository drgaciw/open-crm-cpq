package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
        lenient().when(quoteMapper.toEntity(any(QuoteCreateRequest.class))).thenAnswer(invocation -> {
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
        lenient().when(quoteNumberGenerator.generate()).thenReturn("Q-100");
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

    static Stream<Arguments> tableRows() {
        return CrmQuoteDealTypeMapping.rows().entrySet().stream()
                .map(row -> Arguments.of(row.getKey(), row.getValue()));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("tableRows")
    @DisplayName("create stores each table row, including EXISTING_BUSINESS as EXPANSION")
    void mapsEachTableRowOntoTheCreatedQuote(String crmDealType, DealType expected) {
        stubPersistence();
        QuoteDto created = quoteService.create(baseRequest().crmDealType(crmDealType).build());

        assertThat(created.getDealType()).isEqualTo(expected);
        ArgumentCaptor<Quote> saved = ArgumentCaptor.forClass(Quote.class);
        verify(quoteRepository).save(saved.capture());
        assertThat(saved.getValue().getDealType()).isEqualTo(expected);
        assertThat(saved.getValue().getDealExternalId()).isEqualTo("deal-1");
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("a null crmDealType is missing and keeps the documented default NEW")
    void nullCrmDealTypeDefaultsToNew(String crmDealType) {
        stubPersistence();
        QuoteDto created = quoteService.create(baseRequest().crmDealType(crmDealType).build());

        assertThat(created.getDealType()).isEqualTo(DealType.NEW);
    }

    @Test
    @DisplayName("omitting crmDealType keeps the documented default NEW")
    void missingCrmDealTypeDefaultsToNew() {
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
    @DisplayName("a bare EXISTING_BUSINESS quote stored as EXPANSION names the missing target subscription")
    void bareExistingBusinessNamesMissingTargetSubscription() {
        Quote quote = new Quote();
        quote.setId(1L);
        quote.setDealType(CrmQuoteDealTypeMapping.toQuoteDealType("EXISTING_BUSINESS"));
        quote.setStatus(QuoteStatus.DRAFT);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(new QuoteLineItem()));

        assertThat(quote.getDealType()).isEqualTo(DealType.EXPANSION);
        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EXPANSION quote is missing a target subscription")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("SUBSCRIPTION_CONTINUATION_INVALID");
    }

    @Test
    @DisplayName("an unknown CRM value fails loudly and saves nothing")
    void rejectsUnknownCrmType() {
        assertThatThrownBy(() -> quoteService.create(baseRequest().crmDealType("NOT_A_DEAL_TYPE").build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_UNMAPPED");
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
