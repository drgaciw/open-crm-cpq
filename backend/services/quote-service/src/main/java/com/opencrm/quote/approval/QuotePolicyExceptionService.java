package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingPolicyExceptionDto;
import com.opencrm.quote.client.RoutingRecordPolicyExceptionRequest;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import com.opencrm.security.web.EntraRoleAuthorities;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Quote-service BFF for {@code RecordException} (ADR-013). The only write is the relay to approval-routing.
 * This service does not insert an approval row and does not persist the exception locally.
 */
@Service
@RequiredArgsConstructor
public class QuotePolicyExceptionService {

    private static final Set<String> RECORDERS = Set.of(
            EntraRoleAuthorities.authorityFor("DealDesk"),
            EntraRoleAuthorities.authorityFor("SalesVP"),
            EntraRoleAuthorities.authorityFor("CFO"));

    private final QuoteRepository quoteRepository;
    private final ApprovalRoutingClient approvalRoutingClient;

    @Transactional(readOnly = true)
    public PolicyExceptionResponse record(Long quoteId, RecordQuotePolicyExceptionRequest request) {
        requireRecorder();
        String tenant = requireTenant();
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        if (quote.getTenantId() != null && !tenant.equals(quote.getTenantId())) {
            throw new BusinessException(
                    "Quote tenant does not match the caller", HttpStatus.FORBIDDEN, "TENANT_MISMATCH");
        }
        if (request.getQuoteVersion() == null || quote.getVersion() == null
                || !request.getQuoteVersion().equals(quote.getVersion())) {
            throw new BusinessException(
                    "An exception binds the current quote version",
                    HttpStatus.CONFLICT,
                    "QUOTE_VERSION_MISMATCH");
        }
        RoutingPolicyExceptionDto recorded = approvalRoutingClient.recordPolicyException(
                RoutingRecordPolicyExceptionRequest.builder()
                        .quoteId(String.valueOf(quote.getId()))
                        .quoteVersion(quote.getVersion())
                        .policyId(request.getPolicyId())
                        .justification(request.getJustification())
                        .expiresAt(request.getExpiresAt())
                        .tenantId(quote.getTenantId() != null ? quote.getTenantId() : tenant)
                        .build());
        return new PolicyExceptionResponse(
                recorded.getId(),
                quote.getId(),
                recorded.getQuoteVersion(),
                recorded.getPolicyId(),
                recorded.getJustification(),
                recorded.getExpiresAt(),
                recorded.getRecordedBy());
    }

    private static void requireRecorder() {
        Caller caller = SecurityUtils.getCurrentCaller()
                .orElseThrow(() -> new BusinessException(
                        "A deal desk user must record the exception", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED"));
        if (!(caller instanceof Caller.User user)) {
            throw new BusinessException(
                    "A deal desk user must record the exception", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
        if (RECORDERS.stream().noneMatch(user.authorities()::contains)) {
            throw new BusinessException(
                    "Recording a policy exception needs DealDesk, SalesVP, or CFO",
                    HttpStatus.FORBIDDEN,
                    "EXCEPTION_ROLE_REQUIRED");
        }
    }

    private static String requireTenant() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getTenantId)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElseThrow(() -> new BusinessException(
                        "Tenant identity is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED"));
    }
}
