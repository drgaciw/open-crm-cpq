package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.ApprovalState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Quote BFF for approvals (S2C-12 / Wave G4).
 * <p>
 * Single-writer: approval <em>records</em> are created/updated in approval-routing.
 * This service only projects {@link Quote} status / approvalState / approvalTier.
 * Dual-read GETs may fall back to legacy local {@code approval_requests} rows.
 * <p>
 * Identity and authority (ADR-016 decisions 6 and 7): the quote BFF sends no requester or approver. It relays the
 * user's token, and approval-routing records that user and checks that their role may decide the tier. A legacy local
 * approval, one approval-routing does not know, is history only: deciding it is refused, because it would bypass that
 * check.
 */
@Slf4j
@Service
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final QuoteRepository quoteRepository;
    private final ApprovalRoutingClient approvalRoutingClient;
    private final boolean legacyLocalReadEnabled;

    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            QuoteRepository quoteRepository,
            ApprovalRoutingClient approvalRoutingClient,
            @Value("${approvals.legacy-local-read-enabled:true}") boolean legacyLocalReadEnabled) {
        this.approvalRepository = approvalRepository;
        this.quoteRepository = quoteRepository;
        this.approvalRoutingClient = approvalRoutingClient;
        this.legacyLocalReadEnabled = legacyLocalReadEnabled;
    }

    public ApprovalRequestDto submitForApproval(Long quoteId, ApprovalSubmitRequest request) {
        log.debug("Submitting quote {} for approval at tier {} via approval-routing", quoteId, request.getTier());

        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (quote.getStatus() != QuoteStatus.IN_REVIEW && quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException(
                    "Quote must be in DRAFT or IN_REVIEW to submit for approval",
                    "INVALID_STATUS_TRANSITION"
            );
        }

        RoutingApprovalDto remote = approvalRoutingClient.submit(RoutingApprovalSubmitRequest.builder()
                .quoteId(String.valueOf(quoteId))
                .tier(request.getTier().name())
                .reason("Quote approval submitted via BFF")
                .build());

        // Single-writer: do NOT persist a local ApprovalRequest on submit.
        quote.setStatus(QuoteStatus.PENDING_APPROVAL);
        quote.setApprovalState(ApprovalState.PENDING);
        quote.setApprovalTier(request.getTier().name());
        quoteRepository.save(quote);

        log.info("Quote {} submitted for {} approval (routing id={})",
                quote.getQuoteNumber(), request.getTier(), remote.getId());
        return toDtoFromRouting(remote);
    }

    public ApprovalRequestDto approveRequest(Long approvalId, ApprovalResponseRequest request) {
        try {
            RoutingApprovalDto remote = approvalRoutingClient.approve(approvalId);
            applyQuoteDecision(remote.getQuoteId(), QuoteStatus.APPROVED, ApprovalState.APPROVED);
            log.info("Approval {} approved via routing for quote {} by {}",
                    approvalId, remote.getQuoteId(), remote.getApproverId());
            return toDtoFromRouting(remote);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    public ApprovalRequestDto rejectRequest(Long approvalId, ApprovalResponseRequest request) {
        String comments = request != null ? request.getComments() : null;
        try {
            RoutingApprovalDto remote = approvalRoutingClient.reject(approvalId, comments);
            applyQuoteDecision(remote.getQuoteId(), QuoteStatus.REJECTED, ApprovalState.REJECTED);
            log.info("Approval {} rejected via routing for quote {} by {}",
                    approvalId, remote.getQuoteId(), remote.getApproverId());
            return toDtoFromRouting(remote);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    /**
     * An approval approval-routing does not know. A legacy local row is readable history but cannot be decided here:
     * that would skip approval-routing's role check (ADR-016 decision 7) and write a second store (ADR-013).
     */
    private RuntimeException notInRouting(Long approvalId, ResourceNotFoundException notFound) {
        if (legacyLocalReadEnabled && approvalRepository.findByIdAndDeletedFalse(approvalId).isPresent()) {
            log.warn("Refused a decision on legacy local approval {}: it predates approval-routing", approvalId);
            return new BusinessException(
                    "Approval " + approvalId + " predates approval-routing and can no longer be decided; "
                            + "submit the quote for approval again",
                    HttpStatus.CONFLICT,
                    "LEGACY_APPROVAL_READ_ONLY"
            );
        }
        return notFound;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getByQuoteId(Long quoteId) {
        List<ApprovalRequestDto> result = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        try {
            for (RoutingApprovalDto remote : approvalRoutingClient.getByQuoteId(quoteId)) {
                if (!String.valueOf(quoteId).equals(remote.getQuoteId())) {
                    continue;
                }
                ApprovalRequestDto dto = toDtoFromRouting(remote);
                if (dto.getId() != null) {
                    seenIds.add(dto.getId());
                }
                result.add(dto);
            }
        } catch (BusinessException ex) {
            log.warn("Dual-read: routing history unavailable for quote {}: {}", quoteId, ex.getMessage());
            if (!legacyLocalReadEnabled) {
                throw ex;
            }
        }

        if (legacyLocalReadEnabled) {
            for (ApprovalRequest local : approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(quoteId)) {
                if (local.getId() != null && seenIds.contains(local.getId())) {
                    continue;
                }
                // Prefer routing when both stores have a PENDING for the same quote+tier.
                if (local.getStatus() == ApprovalStatus.PENDING
                        && result.stream().anyMatch(r ->
                        r.getStatus() == ApprovalStatus.PENDING
                                && r.getTier() == local.getTier()
                                && quoteId.equals(r.getQuoteId()))) {
                    continue;
                }
                result.add(toDto(local));
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovals() {
        List<ApprovalRequestDto> result = new ArrayList<>();
        Set<String> routingQuoteKeys = new HashSet<>();

        try {
            for (RoutingApprovalDto remote : approvalRoutingClient.getPending()) {
                Optional<ApprovalTier> tier = mapQuoteTier(remote.getApprovalTier());
                if (tier.isEmpty()) {
                    // Skip non-quote tiers (e.g. MANAGER discount Kafka path) from agent BFF queue.
                    continue;
                }
                ApprovalRequestDto dto = toDtoFromRouting(remote);
                result.add(dto);
                if (remote.getQuoteId() != null) {
                    routingQuoteKeys.add(remote.getQuoteId() + "|" + tier.get().name());
                }
            }
        } catch (BusinessException ex) {
            log.warn("Dual-read: routing pending unavailable: {}", ex.getMessage());
            if (!legacyLocalReadEnabled) {
                throw ex;
            }
        }

        if (legacyLocalReadEnabled) {
            for (ApprovalRequest local : approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING)) {
                String key = local.getQuoteId() + "|" + local.getTier().name();
                if (routingQuoteKeys.contains(key)) {
                    continue;
                }
                result.add(toDto(local));
            }
        }

        return result;
    }

    private void applyQuoteDecision(String quoteIdRaw, QuoteStatus status, ApprovalState state) {
        Long quoteId = parseQuoteId(quoteIdRaw);
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        quote.setStatus(status);
        quote.setApprovalState(state);
        quoteRepository.save(quote);
    }

    private static Long parseQuoteId(String quoteIdRaw) {
        if (quoteIdRaw == null || quoteIdRaw.isBlank()) {
            throw new BusinessException("Routing approval missing quoteId", "INVALID_ROUTING_APPROVAL");
        }
        try {
            return Long.parseLong(quoteIdRaw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "Routing approval quoteId is not numeric: " + quoteIdRaw,
                    "INVALID_ROUTING_QUOTE_ID"
            );
        }
    }

    private ApprovalRequestDto toDtoFromRouting(RoutingApprovalDto remote) {
        Long quoteId = null;
        if (remote.getQuoteId() != null) {
            try {
                quoteId = Long.parseLong(remote.getQuoteId().trim());
            } catch (NumberFormatException ignored) {
                // Non-numeric routing quoteIds (discount Kafka) stay null in BFF DTO.
            }
        }

        ApprovalTier tier = mapQuoteTier(remote.getApprovalTier()).orElse(null);
        ApprovalStatus status = mapStatus(remote.getStatus());

        Instant respondedAt = remote.getApprovedAt() != null ? remote.getApprovedAt() : remote.getRejectedAt();
        String comments = remote.getRejectionReason() != null ? remote.getRejectionReason() : remote.getReason();
        if (status == ApprovalStatus.SUPERSEDED) {
            // Nobody responded: routing replaced it. Say by what, so the history reads without the reason column.
            respondedAt = remote.getSupersededAt();
            comments = "Superseded by request " + remote.getSupersededBy();
        }

        return ApprovalRequestDto.builder()
                .id(remote.getId())
                .uuid(remote.getUuid())
                .quoteId(quoteId)
                .tier(tier)
                .status(status)
                .requestedBy(remote.getRequesterId())
                .approver(remote.getApproverId())
                .comments(comments)
                .requestedAt(remote.getSubmittedAt())
                .respondedAt(respondedAt)
                .supersededBy(status == ApprovalStatus.SUPERSEDED ? remote.getSupersededBy() : null)
                .createdAt(remote.getCreatedAt())
                .build();
    }

    private static Optional<ApprovalTier> mapQuoteTier(String approvalTier) {
        if (approvalTier == null || approvalTier.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ApprovalTier.valueOf(approvalTier.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static ApprovalStatus mapStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(normalized) || "CANCELED".equals(normalized)) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ApprovalRequestDto toDto(ApprovalRequest a) {
        return ApprovalRequestDto.builder()
                .id(a.getId())
                .uuid(a.getUuid())
                .quoteId(a.getQuoteId())
                .tier(a.getTier())
                .status(a.getStatus())
                .requestedBy(a.getRequestedBy())
                .approver(a.getApprover())
                .comments(a.getComments())
                .requestedAt(a.getRequestedAt())
                .respondedAt(a.getRespondedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
