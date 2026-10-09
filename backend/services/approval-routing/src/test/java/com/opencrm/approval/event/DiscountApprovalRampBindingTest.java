package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.*;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.service.ApprovalService;
import com.opencrm.common.event.*;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DiscountApprovalRampBindingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ApprovalRequestRepository repository = mock(ApprovalRequestRepository.class);
    private final KafkaInbox inbox = mock(KafkaInbox.class);
    private final DiscountApprovalEventConsumer consumer = new DiscountApprovalEventConsumer(repository, json,
            mock(DiscountTierShadowService.class), inbox, new CurrentActor("service:approval-routing", false));

    @Test void changedFingerprintSupersedesEvenWithSameWorstDiscountAndLateCanonicalDecisionConflicts() throws Exception {
        ApprovalRequest older = open("a".repeat(64));
        when(inbox.claim(any(), any())).thenReturn(true);
        when(repository.findByQuoteIdAndStatus("1", ApprovalStatus.PENDING)).thenReturn(List.of(older));
        when(repository.save(any())).thenAnswer(call -> { ApprovalRequest request = call.getArgument(0);
            if (request.getId() == null) request.setId(200L); return request; });
        consumer.handleDiscountApprovalRequired(record(event("b".repeat(64), true)));
        assertThat(older.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        assertThat(older.getSupersededBy()).isEqualTo(200L);
        when(repository.findById(100L)).thenReturn(Optional.of(older));
        com.opencrm.security.test.TestCallers.signInUser("reviewer", "SalesVP");
        older.setApprovalTier("SALES_VP");
        ApprovalService routing = new ApprovalService(repository, mock(OutboxEventPublisher.class),
                mock(ApprovalRequestMapper.class), new CurrentActor("service:approval-routing", false));
        assertThatThrownBy(() -> routing.approve(100L)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("APPROVAL_SUPERSEDED");
        com.opencrm.security.test.TestCallers.clear();
    }
    @Test void identicalFingerprintDoesNotSupersedeDespiteNumericColumnScale() throws Exception {
        ApprovalRequest older = open("a".repeat(64)); older.setRequestedDiscount(new BigDecimal("0.33"));
        when(inbox.claim(any(), any())).thenReturn(true);
        when(repository.findByQuoteIdAndStatus("1", ApprovalStatus.PENDING)).thenReturn(List.of(older));
        consumer.handleDiscountApprovalRequired(record(event("a".repeat(64), true)));
        verify(repository, never()).save(any());
        assertThat(older.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }
    @Test void policyClearedRetiresPendingAcrossGoverningLines() throws Exception {
        ApprovalRequest older = open("a".repeat(64)); older.setLineId("previous-line");
        when(inbox.claim(any(), any())).thenReturn(true);
        when(repository.findByQuoteIdAndStatus("1", ApprovalStatus.PENDING)).thenReturn(List.of(older));
        when(repository.save(any())).thenAnswer(call -> { ApprovalRequest request = call.getArgument(0);
            if (request.getId() == null) request.setId(200L); return request; });
        consumer.handleDiscountApprovalRequired(record(event("b".repeat(64), false)));
        assertThat(older.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        var capture = org.mockito.ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(repository, atLeastOnce()).save(capture.capture());
        assertThat(capture.getAllValues()).anyMatch(request -> request.getStatus() == ApprovalStatus.CANCELLED
                && "b".repeat(64).equals(request.getPricingFingerprint()));
        assertThat(capture.getAllValues()).noneMatch(request -> request.getStatus() == ApprovalStatus.PENDING);
    }
    @Test void lateLegacyEventCannotSupersedeCurrentRampBinding() throws Exception {
        ApprovalRequest current = open("b".repeat(64));
        when(inbox.claim(any(), any())).thenReturn(true);
        when(repository.findByQuoteIdAndStatus("1", ApprovalStatus.PENDING)).thenReturn(List.of(current));
        when(repository.save(any())).thenAnswer(call -> { ApprovalRequest request = call.getArgument(0);
            if (request.getId() == null) request.setId(200L); return request; });
        var legacy = new DiscountApprovalRequiredEvent("1", "10", new BigDecimal("40"), new BigDecimal("0.50"), "USD");
        consumer.handleDiscountApprovalRequired(new ConsumerRecord<>(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED,
                0, 9L, "1", json.writeValueAsString(legacy)));
        assertThat(current.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(current.getSupersededBy()).isNull();
        verify(repository, never()).save(current);
    }
    @Test void fingerprintFreePayloadOnNewTopicIsNonretryableWithoutCanonicalMutation() throws Exception {
        when(inbox.claim(any(), any())).thenReturn(true);
        var legacy = new DiscountApprovalRequiredEvent("1", "10", BigDecimal.ZERO, BigDecimal.ZERO, "USD");
        assertThatThrownBy(() -> consumer.handleDiscountApprovalRequired(record(legacy)))
                .isInstanceOf(NonRetryableEventException.class).hasMessageContaining("requires a pricing fingerprint");
        verifyNoInteractions(repository);
    }
    private ApprovalRequest open(String fingerprint) {
        ApprovalRequest request = ApprovalRequest.builder().quoteId("1").lineId("10").approvalType(ApprovalType.DISCOUNT)
                .status(ApprovalStatus.PENDING).pricingFingerprint(fingerprint).governingPeriod(1)
                .requestedDiscount(new BigDecimal("0.32")).marginDelta(BigDecimal.ZERO).currencyCode("USD").build();
        request.setId(100L); return request;
    }
    private DiscountApprovalRequiredEvent event(String fingerprint, boolean required) {
        return new DiscountApprovalRequiredEvent("1", "10", BigDecimal.ZERO, new BigDecimal("0.32"), "USD",
                fingerprint, 1, "SALES_VP", "Ramp period 1 discount threshold", required);
    }
    private ConsumerRecord<String,String> record(DiscountApprovalRequiredEvent event) throws Exception {
        return new ConsumerRecord<>(KafkaTopics.RAMP_APPROVAL_BINDING_CHANGED, 0, 7L, "1", json.writeValueAsString(event));
    }
}
