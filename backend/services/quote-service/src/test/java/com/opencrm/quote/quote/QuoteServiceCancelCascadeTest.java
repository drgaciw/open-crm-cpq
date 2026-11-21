package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("D1-3 Quote cancel cascades PENDING approval")
class QuoteServiceCancelCascadeTest {

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
    @Mock
    private ApprovalRoutingClient approvalRoutingClient;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(
                quoteRepository,
                Mappers.getMapper(QuoteMapper.class),
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                new ObjectMapper(),
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                RampScheduleTestSupport.guard(quoteLineItemRepository, outboxEventPublisher));
        quoteService.setApprovalRoutingClient(approvalRoutingClient);
    }

    @Test
    @DisplayName("cancel DRAFT with no approval still works")
    void cancelDraftWithNoApproval() {
        Quote quote = stored(QuoteStatus.DRAFT);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of());

        QuoteDto dto = quoteService.cancel(42L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        assertThat(dto.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        verify(approvalRoutingClient, never()).cancel(any());
    }

    @Test
    @DisplayName("cancel PENDING_APPROVAL cancels only PENDING routing rows")
    void cancelPendingApprovalCascades() {
        Quote quote = stored(QuoteStatus.PENDING_APPROVAL);
        when(approvalRoutingClient.getByQuoteId(42L)).thenReturn(List.of(
                RoutingApprovalDto.builder().id(9L).quoteId("42").status("PENDING").build(),
                RoutingApprovalDto.builder().id(10L).quoteId("42").status("APPROVED").build()
        ));
        when(approvalRoutingClient.cancel(9L)).thenReturn(
                RoutingApprovalDto.builder().id(9L).quoteId("42").status("CANCELLED").build());

        quoteService.cancel(42L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        verify(approvalRoutingClient).cancel(9L);
        verify(approvalRoutingClient, never()).cancel(10L);
    }

    @Test
    @DisplayName("ACCEPTED still CANNOT_CANCEL_ACCEPTED and does not touch routing")
    void cannotCancelAccepted() {
        stored(QuoteStatus.ACCEPTED);

        assertThatThrownBy(() -> quoteService.cancel(42L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("CANNOT_CANCEL_ACCEPTED");
                });
        verify(approvalRoutingClient, never()).getByQuoteId(any());
        verify(approvalRoutingClient, never()).cancel(any());
    }

    private Quote stored(QuoteStatus status) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-42")
                .customerExternalId("cust-1")
                .status(status)
                .currency("USD")
                .build();
        quote.setId(42L);
        when(quoteRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(quote));
        lenient().when(quoteRepository.save(quote)).thenReturn(quote);
        return quote;
    }
}
