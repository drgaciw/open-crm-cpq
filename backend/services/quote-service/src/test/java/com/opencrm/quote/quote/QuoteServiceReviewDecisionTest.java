package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-016 step 4: {@code POST /api/v1/quotes/{id}/approve|reject} passes or fails a review. A quote waiting on a tiered
 * approval is decided through that approval in approval-routing, which checks the approver's role; deciding it here
 * would bypass that check and leave the approval open.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService approve/reject: reviews only, never a routed approval (ADR-016 step 4)")
class QuoteServiceReviewDecisionTest {

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
        quoteService = new QuoteService(quoteRepository, quoteMapper, quoteNumberGenerator, eventPublisher,
                quoteLineItemRepository, pricingServiceClient, productServiceClient, new ObjectMapper(),
                outboxEventPublisher);
    }

    private Quote quote(QuoteStatus status) {
        Quote quote = Quote.builder().quoteNumber("Q-7").status(status).build();
        quote.setId(7L);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        return quote;
    }

    @Test
    @DisplayName("a PENDING_APPROVAL quote cannot be approved or rejected here: 409 APPROVAL_PENDING")
    void routedQuoteIsRefused() {
        Quote quote = quote(QuoteStatus.PENDING_APPROVAL);

        for (Runnable decision : new Runnable[]{() -> quoteService.approve(7L), () -> quoteService.reject(7L, "no")}) {
            assertThatThrownBy(decision::run).isInstanceOfSatisfying(BusinessException.class, refused -> {
                assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(refused.getErrorCode()).isEqualTo("APPROVAL_PENDING");
            });
        }
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("an IN_REVIEW quote still passes or fails its review here")
    void reviewIsDecidedHere() {
        Quote quote = quote(QuoteStatus.IN_REVIEW);
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(7L).status(QuoteStatus.APPROVED).build());

        quoteService.approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
    }

    @Test
    @DisplayName("other states are still an invalid transition")
    void otherStatesAreInvalid() {
        quote(QuoteStatus.DRAFT);

        assertThatThrownBy(() -> quoteService.reject(7L, null)).isInstanceOfSatisfying(BusinessException.class,
                refused -> assertThat(refused.getErrorCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }
}
