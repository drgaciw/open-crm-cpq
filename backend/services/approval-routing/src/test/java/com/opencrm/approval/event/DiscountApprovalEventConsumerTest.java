package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.service.ApprovalRoutingOwnership;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Discount Approval Event Consumer")
class DiscountApprovalEventConsumerTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DiscountApprovalEventConsumer consumer;

    @Test
    @DisplayName("creates approval-routing owned discount approval requests")
    void createsDiscountApprovalRequests() throws Exception {
        DiscountApprovalRequiredEvent event = new DiscountApprovalRequiredEvent(
                "Q-55",
                "LINE-1",
                new BigDecimal("25.0"),
                new BigDecimal("500.00"),
                "USD"
        );

        consumer.handleDiscountApprovalRequired(objectMapper.writeValueAsString(event));

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        ApprovalRequest saved = captor.getValue();

        assertEquals("Q-55", saved.getQuoteId());
        assertEquals(ApprovalType.DISCOUNT, saved.getApprovalType());
        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        assertEquals("Discount exceeds standard threshold", saved.getReason());
    }
}
