package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscountApprovalEventConsumer")
class DiscountApprovalEventConsumerTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private DiscountTierShadowService discountTierShadow;

    private DiscountApprovalEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DiscountApprovalEventConsumer(approvalRequestRepository, new ObjectMapper(), discountTierShadow);
    }

    private static String event(String marginDelta) {
        return """
                {"quoteId":"Q-1","lineId":"L-1","marginDelta":%s,"requestedDiscount":18.5,"currencyCode":"USD"}"""
                .formatted(marginDelta);
    }

    private ApprovalRequest saved() {
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "margin delta {0} routes to {1}")
    @CsvSource({
            "0, MANAGER",
            "20, MANAGER",
            "20.01, DIRECTOR",
            "30, DIRECTOR",
            "30.01, VP_SALES",
            "75, VP_SALES"
    })
    @DisplayName("tier thresholds are exclusive at 20 and 30")
    void tierThresholds(String marginDelta, String expectedTier) {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));

        consumer.handleDiscountApprovalRequired(event(marginDelta));

        assertThat(saved().getApprovalTier()).isEqualTo(expectedTier);
    }

    @Test
    @DisplayName("creates a pending discount approval request from the event")
    void createsPendingRequest() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));

        consumer.handleDiscountApprovalRequired(event("25"));

        ApprovalRequest request = saved();
        assertThat(request.getQuoteId()).isEqualTo("Q-1");
        assertThat(request.getLineId()).isEqualTo("L-1");
        assertThat(request.getApprovalType()).isEqualTo(ApprovalType.DISCOUNT);
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(request.getRequestedDiscount()).isEqualByComparingTo("18.5");
        assertThat(request.getMarginDelta()).isEqualByComparingTo("25");
        assertThat(request.getCurrencyCode()).isEqualTo("USD");
        assertThat(request.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("malformed JSON is rethrown so Kafka redelivers, and nothing is saved")
    void malformedJsonIsRethrown() {
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired("{not json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process discount approval event");

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("a repository failure is rethrown so Kafka redelivers")
    void repositoryFailureIsRethrown() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(event("25")))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("db down");
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("hands the rule's tier to the shadow only after the request is saved")
    void shadowReceivesTheAuthoritativeTier() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));

        consumer.handleDiscountApprovalRequired(event("25"));

        verify(discountTierShadow).recordAfterCommit(isNull(), any(), eq("DIRECTOR"));
        assertThat(saved().getApprovalTier()).isEqualTo("DIRECTOR");
    }
}
