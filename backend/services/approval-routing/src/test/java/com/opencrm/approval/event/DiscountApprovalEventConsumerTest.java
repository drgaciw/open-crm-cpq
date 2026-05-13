package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.repository.ApprovalRequestRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("should persist approval request with policy evaluation snapshot")
    void persistPolicyEvaluationSnapshot() throws Exception {
        DiscountApprovalRequiredEvent event = new DiscountApprovalRequiredEvent(
                "Q-001",
                "L-001",
                BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(300.00),
                "USD",
                List.of("DISCOUNT_APPROVAL_REQUIRED"),
                "{\"quoteId\":\"Q-001\",\"lineId\":\"L-001\"}",
                "{\"approvalState\":\"REQUIRES_APPROVAL\"}"
        );

        consumer.handleDiscountApprovalRequired(objectMapper.writeValueAsString(event));

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());

        ApprovalRequest saved = captor.getValue();
        assertEquals("Q-001", saved.getQuoteId());
        assertEquals("L-001", saved.getLineId());
        assertEquals(ApprovalType.DISCOUNT, saved.getApprovalType());
        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        assertEquals("DIRECTOR", saved.getApprovalTier());
        assertNotNull(saved.getSubmittedAt());
        assertEquals(
                "DISCOUNT_APPROVAL_REQUIRED",
                objectMapper.readTree(saved.getPolicyEvaluationSnapshot()).get("explanationCodes").get(0).asText()
        );
        assertEquals(
                "{\"approvalState\":\"REQUIRES_APPROVAL\"}",
                objectMapper.readTree(saved.getPolicyEvaluationSnapshot()).get("outputSnapshot").asText()
        );
    }
}
