package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingMatrixSubmitRequest;
import com.opencrm.quote.quote.ApprovalState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.quote.QuoteTenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Quote-service BFF for the approval matrix (ADR-013). Approval rows are written only by approval-routing.
 * This class does not touch the legacy local {@code approval_requests} table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalMatrixBff {

    private static final List<String> TIER_RANK = List.of("DEAL_DESK", "SALES_VP", "CFO");

    private final QuoteRepository quoteRepository;
    private final ApprovalRoutingClient approvalRoutingClient;

    @Transactional
    public List<RoutingApprovalDto> submitForApproval(Long quoteId, MatrixApprovalSubmitRequest request) {
        String tenantId = currentTenant();
        Quote quote = quoteRepository.findByIdAndTenantIdAndDeletedFalse(quoteId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.IN_REVIEW
                && quote.getStatus() != QuoteStatus.PENDING_APPROVAL) {
            throw new BusinessException("Quote must be in DRAFT or IN_REVIEW to submit for approval",
                    "INVALID_STATUS_TRANSITION");
        }
        List<RoutingMatrixSubmitRequest.Breach> breaches = request.getBreaches() == null
                ? List.of()
                : request.getBreaches().stream()
                .map(row -> RoutingMatrixSubmitRequest.Breach.builder()
                        .breachType(row.getBreachType())
                        .actual(row.getActual())
                        .limit(row.getLimit())
                        .amount(row.getAmount())
                        .clauseCode(row.getClauseCode())
                        .build())
                .toList();
        List<RoutingApprovalDto> created = approvalRoutingClient.submitMatrix(RoutingMatrixSubmitRequest.builder()
                .quoteId(String.valueOf(quoteId))
                .quoteVersion(String.valueOf(quote.getVersion() == null ? 0L : quote.getVersion()))
                .verdict(request.getVerdict())
                .quoteTenantId(quote.getTenantId())
                .matrixId(request.getMatrixId())
                .breaches(breaches)
                .build());
        boolean pending = created.stream().anyMatch(row -> "PENDING".equals(row.getStatus()));
        if (pending) {
            quote.setStatus(QuoteStatus.PENDING_APPROVAL);
            quote.setApprovalState(ApprovalState.PENDING);
            quote.setApprovalTier(highestPendingTier(created));
            quoteRepository.save(quote);
        }
        log.info("Quote {} matrix submission forwarded to approval-routing ({} requests)", quoteId, created.size());
        return created;
    }

    @Transactional(readOnly = true)
    public List<RoutingApprovalDto> listPendingApprovals(String tier) {
        currentTenant();
        return approvalRoutingClient.listInbox(tier);
    }

    private static String currentTenant() {
        String tenantId = QuoteTenantResolver.currentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException("A tenant claim is required", HttpStatus.UNAUTHORIZED, "TENANT_REQUIRED");
        }
        return tenantId;
    }

    private static String highestPendingTier(List<RoutingApprovalDto> created) {
        return created.stream()
                .filter(row -> "PENDING".equals(row.getStatus()))
                .map(RoutingApprovalDto::getApprovalTier)
                .filter(TIER_RANK::contains)
                .max(Comparator.comparingInt(TIER_RANK::indexOf))
                .orElse(null);
    }
}
