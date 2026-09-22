package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiscountApprovalEventConsumer")
class DiscountApprovalEventConsumerTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private DiscountTierShadowService discountTierShadow;

    @Mock
    private KafkaInbox inbox;

    private DiscountApprovalEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DiscountApprovalEventConsumer(approvalRequestRepository, new ObjectMapper(), discountTierShadow,
                inbox);
        when(inbox.claim(eq(DiscountApprovalEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING)).thenReturn(List.of());
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, 0, 7L, "Q-1", payload);
    }

    private static ConsumerRecord<String, String> event(String marginDelta) {
        return record("""
                {"quoteId":"Q-1","lineId":"L-1","marginDelta":%s,"requestedDiscount":18.5,"currencyCode":"USD"}"""
                .formatted(marginDelta));
    }

    private ApprovalRequest saved() {
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ApprovalRequest open(String lineId, String discount, String marginDelta) {
        return ApprovalRequest.builder()
                .quoteId("Q-1").lineId(lineId).approvalType(ApprovalType.DISCOUNT).status(ApprovalStatus.PENDING)
                .requestedDiscount(new BigDecimal(discount)).marginDelta(new BigDecimal(marginDelta))
                .currencyCode("USD").requesterId("system").build();
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
        consumer.handleDiscountApprovalRequired(event(marginDelta));

        assertThat(saved().getApprovalTier()).isEqualTo(expectedTier);
    }

    @Test
    @DisplayName("creates a pending discount approval request from the event")
    void createsPendingRequest() {
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
    @DisplayName("D6: an event the inbox has already seen creates nothing")
    void alreadyProcessedEventIsSkipped() {
        when(inbox.claim(eq(DiscountApprovalEventConsumer.CONSUMER_GROUP), any())).thenReturn(false);

        consumer.handleDiscountApprovalRequired(event("25"));

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("D6: an identical PENDING request for the quote line makes the event a duplicate (scale-insensitive)")
    void identicalOpenRequestIsADuplicate() {
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(open("L-1", "18.50", "25.0000")));

        consumer.handleDiscountApprovalRequired(event("25"));

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("D6: different figures, or another line, still create a request (no supersede rule is invented)")
    void differentFiguresStillCreate() {
        when(approvalRequestRepository.findByQuoteIdAndStatus("Q-1", ApprovalStatus.PENDING))
                .thenReturn(List.of(open("L-1", "18.50", "22"), open("L-2", "18.50", "25")));

        consumer.handleDiscountApprovalRequired(event("25"));

        assertThat(saved().getMarginDelta()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("D7: malformed JSON is not retryable, so it goes to the DLT; nothing is saved")
    void malformedJsonIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(record("{not json")))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("Unreadable discount approval event");

        verify(approvalRequestRepository, never()).save(any());
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("D7: an event without quoteId or marginDelta is not retryable")
    void incompleteEventIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(record("{\"lineId\":\"L-1\"}")))
                .isInstanceOf(NonRetryableEventException.class);

        verify(approvalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("a repository failure propagates so the error handler retries it")
    void repositoryFailurePropagates() {
        when(approvalRequestRepository.save(any(ApprovalRequest.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(event("25")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        verifyNoInteractions(discountTierShadow);
    }

    @Test
    @DisplayName("hands the rule's tier to the shadow only after the request is saved")
    void shadowReceivesTheAuthoritativeTier() {
        consumer.handleDiscountApprovalRequired(event("25"));

        verify(discountTierShadow).recordAfterCommit(isNull(), any(), eq("DIRECTOR"));
        assertThat(saved().getApprovalTier()).isEqualTo("DIRECTOR");
    }
}
