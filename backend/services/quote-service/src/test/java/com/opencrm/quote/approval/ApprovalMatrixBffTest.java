package com.opencrm.quote.approval;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingMatrixSubmitRequest;
import com.opencrm.quote.quote.ApprovalState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.quote.QuoteTenantResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote BFF forwards matrix writes and refuses another tenant")
class ApprovalMatrixBffTest {

    @Mock
    private QuoteRepository quotes;
    @Mock
    private ApprovalRoutingClient routing;
    @InjectMocks
    private ApprovalMatrixBff bff;

    @Test
    @DisplayName("submit copies the quote tenant into routing and does not write a local approval")
    void submitForwardsTheQuoteTenant() {
        Quote quote = new Quote();
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setVersion(4L);
        quote.setTenantId("tenant-a");
        when(quotes.findByIdAndTenantIdAndDeletedFalse(7L, "tenant-a")).thenReturn(Optional.of(quote));
        when(routing.submitMatrix(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                RoutingApprovalDto.builder().id(5L).quoteId("7").status("PENDING").approvalTier("DEAL_DESK").build()));

        try (MockedStatic<QuoteTenantResolver> tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant-a");
            bff.submitForApproval(7L, MatrixApprovalSubmitRequest.builder()
                    .verdict("REQUIRES_APPROVAL")
                    .breaches(List.of(MatrixApprovalSubmitRequest.Breach.builder()
                            .breachType("DISCOUNT_PERCENT")
                            .amount(new BigDecimal("10"))
                            .build()))
                    .build());
        }

        ArgumentCaptor<RoutingMatrixSubmitRequest> sent = ArgumentCaptor.forClass(RoutingMatrixSubmitRequest.class);
        verify(routing).submitMatrix(sent.capture());
        assertThat(sent.getValue().getQuoteTenantId()).isEqualTo("tenant-a");
        assertThat(sent.getValue().getQuoteVersion()).isEqualTo("4");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.PENDING);
        verify(quotes).save(quote);
    }

    @Test
    @DisplayName("a quote in another tenant is not submitted")
    void otherTenantIsNotSubmitted() {
        when(quotes.findByIdAndTenantIdAndDeletedFalse(7L, "tenant-b")).thenReturn(Optional.empty());
        try (MockedStatic<QuoteTenantResolver> tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant-b");
            assertThatThrownBy(() -> bff.submitForApproval(7L, MatrixApprovalSubmitRequest.builder()
                    .verdict("REQUIRES_APPROVAL")
                    .build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
        verify(routing, never()).submitMatrix(org.mockito.ArgumentMatchers.any());
        verify(quotes, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
