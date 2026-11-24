package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DRG-443 ListPendingApprovals, re-pointed from #1071. The queue for a role contains that role's
 * pending rows and hides every other role.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-443 ListPendingApprovals")
class ListPendingApprovalsTest {

    @Mock
    private ApprovalMatrixRepository matrices;
    @Mock
    private ApprovalRequestRepository requests;
    @Mock
    private ApprovalRequestMapper mapper;
    @Mock
    private OutboxEventPublisher outbox;

    private ApprovalMatrixService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalMatrixService(matrices, requests, mapper, outbox,
                new CurrentActor("service:approval-routing", false));
        ApprovalRequest dealDesk = pending(11L, "DEAL_DESK", "Q-100");
        ApprovalRequest cfo = pending(22L, "CFO", "Q-200");
        when(requests.findByStatus(ApprovalStatus.PENDING)).thenReturn(List.of(dealDesk, cfo));
        lenient().when(mapper.toDtoList(any())).thenAnswer(invocation -> {
            List<ApprovalRequest> rows = invocation.getArgument(0);
            return rows.stream().map(row -> ApprovalRequestDto.builder()
                    .id(row.getId())
                    .quoteId(row.getQuoteId())
                    .approvalTier(row.getApprovalTier())
                    .status(row.getStatus())
                    .build()).toList();
        });
    }

    @Test
    @DisplayName("returns only the queue for that approver")
    void listPendingApprovals_returnsOnlyQueueForThatApprover() {
        assertThat(service.listPendingApprovals(QuoteApprovalTier.DEAL_DESK))
                .extracting(ApprovalRequestDto::getId, ApprovalRequestDto::getQuoteId, ApprovalRequestDto::getApprovalTier)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, "Q-100", "DEAL_DESK"));
    }

    @Test
    @DisplayName("hides other approvers' items")
    void listPendingApprovals_hidesOtherApproversItems() {
        assertThat(service.listPendingApprovals(QuoteApprovalTier.DEAL_DESK))
                .extracting(ApprovalRequestDto::getApprovalTier)
                .doesNotContain("CFO");
        assertThat(service.listPendingApprovals(QuoteApprovalTier.CFO))
                .extracting(ApprovalRequestDto::getApprovalTier)
                .doesNotContain("DEAL_DESK");
    }

    private static ApprovalRequest pending(long id, String tier, String quoteId) {
        ApprovalRequest row = ApprovalRequest.builder()
                .quoteId(quoteId)
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .approvalTier(tier)
                .requesterId("seller")
                .build();
        row.setId(id);
        return row;
    }
}
