package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService.submitForReview S2D-3a preflight")
class QuoteServiceSubmitForReviewTest {

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
                outboxEventPublisher
        );
    }

    @Test
    @DisplayName("submitForReview calls gateway validate and proceeds when valid")
    void submitForReviewSucceedsWhenValid() {
        Quote quote = draftQuoteWithProducts("10", "20");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(productServiceClient.validateConfiguration(List.of(10L, 20L)))
                .thenReturn(ConfigurationValidationResult.ok());
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).status(QuoteStatus.IN_REVIEW).build());

        QuoteDto dto = quoteService.submitForReview(1L);

        assertThat(dto.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
        verify(productServiceClient).validateConfiguration(List.of(10L, 20L));
        verify(quoteRepository).save(quote);
    }

    @Test
    @DisplayName("submitForReview blocks when valid=false and does not persist IN_REVIEW")
    void submitForReviewBlocksWhenInvalid() {
        Quote quote = draftQuoteWithProducts("10");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(productServiceClient.validateConfiguration(List.of(10L)))
                .thenReturn(ConfigurationValidationResult.invalid(List.of(
                        ConfigurationViolation.builder()
                                .ruleId(7L)
                                .ruleType("EXCLUDES")
                                .description("Product 10 excludes 30")
                                .remediation("Remove 30")
                                .build()
                )));

        assertThatThrownBy(() -> quoteService.submitForReview(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("CONFIGURATION_PREFLIGHT_FAILED");
                    assertThat(be.getMessage()).contains("EXCLUDES");
                    assertThat(be.getMessage()).contains("Product 10 excludes 30");
                });

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitForReview rejects non-numeric productExternalId")
    void submitForReviewRejectsNonNumericProductId() {
        Quote quote = draftQuoteWithProducts("prod-1");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> quoteService.submitForReview(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("QUOTE_INVALID_PRODUCT_ID");

        verify(productServiceClient, never()).validateConfiguration(anyList());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitForReview deduplicates line product IDs before validate")
    void submitForReviewDeduplicatesProductIds() {
        Quote quote = draftQuoteWithProducts("10", "10", "20");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(productServiceClient.validateConfiguration(anyList()))
                .thenReturn(ConfigurationValidationResult.ok());
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).build());

        quoteService.submitForReview(1L);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(productServiceClient).validateConfiguration(captor.capture());
        assertThat(captor.getValue()).containsExactly(10L, 20L);
    }

    private static Quote draftQuoteWithProducts(String... productExternalIds) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-1")
                .name("Test")
                .customerExternalId("cust-1")
                .status(QuoteStatus.DRAFT)
                .lineItems(new ArrayList<>())
                .build();
        quote.setId(1L);
        for (String productId : productExternalIds) {
            QuoteLineItem item = QuoteLineItem.builder()
                    .quote(quote)
                    .productExternalId(productId)
                    .quantity(1)
                    .listPrice(java.math.BigDecimal.TEN)
                    .build();
            quote.getLineItems().add(item);
        }
        return quote;
    }
}
