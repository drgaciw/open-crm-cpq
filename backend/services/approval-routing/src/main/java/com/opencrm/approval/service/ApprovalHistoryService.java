package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalCaseComment;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalTimelineEntry;
import com.opencrm.approval.domain.ApprovalTimelineKind;
import com.opencrm.approval.dto.ApprovalCommentRequest;
import com.opencrm.approval.dto.ApprovalHistoryEntryDto;
import com.opencrm.approval.repository.ApprovalCaseCommentRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.repository.ApprovalTimelineRepository;
import com.opencrm.common.entity.BaseEntity;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.common.event.dto.ApprovalRejected;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Deal-desk collaboration on an approval case, and GetApprovalHistory.
 * <p>
 * The case is the approval request. Comments and replies hang off it. The timeline lists each
 * grant, rejection, escalation and comment. Grant, rejection and escalation rows are copied from
 * the {@code ApprovalGranted}, {@code ApprovalRejected} and {@code ApprovalEscalated} events in
 * the same transaction that publishes them.
 * <p>
 * Who may collaborate is the deal-desk decider set (DealDesk, SalesVP, CFO). {@code CPQ.User} and
 * {@code Admin} are refused. This service does not read a tenant column. The quote BFF scopes the
 * query by the quote's tenant before it calls here.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalHistoryService implements ApprovalHistoryRecorder {

    static final Comparator<ApprovalTimelineEntry> TIMELINE_ORDER =
            Comparator.comparing(ApprovalTimelineEntry::getOccurredAt)
                    .thenComparing(ApprovalTimelineEntry::getId);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalCaseCommentRepository commentRepository;
    private final ApprovalTimelineRepository timelineRepository;
    private final CurrentActor currentActor;

    private Clock clock = Clock.systemUTC();

    /** Tests pin when a comment happens so it can sit between decisions. */
    public void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void recordGranted(ApprovalGrantedEvent event, Long quoteVersion) {
        if (event == null) {
            return;
        }
        save(base(event.approvalId(), event.quoteId(), quoteVersion, ApprovalTimelineKind.GRANTED,
                EventContractRegistry.APPROVAL_GRANTED_V1.eventType(), event.decidedAt(), event.approverId(),
                event.comment())
                .toTier(event.approvalTier())
                .build());
    }

    @Override
    public void recordRejected(ApprovalRejected event, Long quoteVersion) {
        if (event == null) {
            return;
        }
        save(base(event.approvalId(), event.quoteId(), quoteVersion, ApprovalTimelineKind.REJECTED,
                EventContractRegistry.APPROVAL_REJECTED_V1.eventType(), event.rejectedAt(), event.approverId(),
                event.comment())
                .toTier(event.approvalTier())
                .build());
    }

    @Override
    public void recordEscalated(ApprovalEscalatedEvent event, Long quoteVersion) {
        if (event == null) {
            return;
        }
        save(base(event.approvalId(), event.quoteId(), quoteVersion, ApprovalTimelineKind.ESCALATED,
                EventContractRegistry.APPROVAL_ESCALATED_V1.eventType(), event.escalatedAt(), null, null)
                .fromTier(event.fromTier())
                .toTier(event.toTier())
                .build());
    }

    /**
     * GetApprovalHistory for every case on the quote, oldest first.
     * Same-timestamp rows keep insertion order ({@code id} ascending).
     */
    @Transactional(readOnly = true)
    public List<ApprovalHistoryEntryDto> getApprovalHistory(String quoteId) {
        author();
        if (quoteId == null || quoteId.isBlank()) {
            throw new BusinessException("quoteId is required", HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_HISTORY");
        }
        return present(timelineRepository.findByQuoteIdAndDeletedFalseOrderByOccurredAtAscIdAsc(quoteId.trim()));
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistoryEntryDto> getApprovalHistoryForCase(Long approvalId) {
        author();
        findCase(approvalId);
        return present(timelineRepository
                .findByApprovalRequestIdAndDeletedFalseOrderByOccurredAtAscIdAsc(approvalId));
    }

    public ApprovalHistoryEntryDto addComment(Long approvalId, ApprovalCommentRequest request) {
        String author = author();
        ApprovalRequest approval = findCase(approvalId);
        if (request == null || request.getBody() == null || request.getBody().isBlank()) {
            throw new BusinessException("Comment body is required", HttpStatus.BAD_REQUEST, "INVALID_COMMENT");
        }
        Long parentId = request.getParentCommentId();
        if (parentId != null) {
            ApprovalCaseComment parent = commentRepository.findByIdAndDeletedFalse(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("ApprovalCaseComment", parentId));
            if (!approval.getId().equals(parent.getApprovalRequestId())) {
                throw new BusinessException("That comment is not on this approval case",
                        HttpStatus.BAD_REQUEST, "COMMENT_NOT_ON_CASE");
            }
        }
        Instant now = clock.instant();
        ApprovalCaseComment comment = ApprovalCaseComment.builder()
                .approvalRequestId(approval.getId())
                .parentCommentId(parentId)
                .quoteId(approval.getQuoteId())
                .quoteVersion(request.getQuoteVersion())
                .authorId(author)
                .body(request.getBody().strip())
                .commentedAt(now)
                .build();
        prepare(comment, now);
        ApprovalCaseComment saved = commentRepository.save(comment);

        ApprovalTimelineEntry row = base(approval.getId(), approval.getQuoteId(), request.getQuoteVersion(),
                ApprovalTimelineKind.COMMENT, null, now, author, saved.getBody())
                .commentId(saved.getId())
                .parentCommentId(parentId)
                .build();
        return toDto(save(row));
    }

    private ApprovalRequest findCase(Long approvalId) {
        return approvalRequestRepository.findById(approvalId)
                .filter(request -> !request.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", approvalId));
    }

    private List<ApprovalHistoryEntryDto> present(List<ApprovalTimelineEntry> rows) {
        return rows.stream().sorted(TIMELINE_ORDER).map(ApprovalHistoryService::toDto).toList();
    }

    private ApprovalTimelineEntry save(ApprovalTimelineEntry row) {
        prepare(row, row.getOccurredAt());
        return timelineRepository.save(row);
    }

    private static ApprovalTimelineEntry.ApprovalTimelineEntryBuilder<?, ?> base(
            Long approvalId, String quoteId, Long quoteVersion, ApprovalTimelineKind kind, String eventName,
            Instant occurredAt, String actorId, String body) {
        return ApprovalTimelineEntry.builder()
                .approvalRequestId(approvalId)
                .quoteId(quoteId)
                .quoteVersion(quoteVersion)
                .kind(kind)
                .eventName(eventName)
                .occurredAt(occurredAt)
                .actorId(actorId)
                .body(body);
    }

    private static void prepare(BaseEntity entity, Instant when) {
        if (entity.getUuid() == null) {
            entity.setUuid(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(when == null ? Instant.now() : when);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0L);
        }
        entity.setDeleted(false);
    }

    /**
     * Deal-desk collaborators only. Checked before the case is loaded, so a refused role learns
     * nothing about the request. {@code CPQ.User} and {@code Admin} are the refused user roles.
     */
    private String author() {
        Optional<Caller> caller = currentActor.caller();
        if (caller.isEmpty()) {
            if (!currentActor.unverifiedCallersAllowed()) {
                throw new BusinessException("An authenticated user is required for deal desk collaboration",
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
            }
            return currentActor.self();
        }
        if (!(caller.get() instanceof Caller.User user)) {
            throw new BusinessException("Deal desk collaboration is done by people, not by services",
                    HttpStatus.FORBIDDEN, "COLLABORATOR_MUST_BE_A_USER");
        }
        if (!ApprovalAuthority.mayDecide(user.authorities(), "DEAL_DESK")) {
            throw new BusinessException("Deal desk case collaboration needs one of the roles "
                    + new TreeSet<>(ApprovalAuthority.decidersOf("DEAL_DESK")),
                    HttpStatus.FORBIDDEN, "COLLABORATOR_ROLE_REQUIRED");
        }
        return user.actorId();
    }

    static ApprovalHistoryEntryDto toDto(ApprovalTimelineEntry row) {
        return ApprovalHistoryEntryDto.builder()
                .id(row.getId())
                .approvalId(row.getApprovalRequestId())
                .quoteId(row.getQuoteId())
                .quoteVersion(row.getQuoteVersion())
                .commentId(row.getCommentId())
                .parentCommentId(row.getParentCommentId())
                .kind(row.getKind())
                .eventName(row.getEventName())
                .occurredAt(row.getOccurredAt())
                .actorId(row.getActorId())
                .body(row.getBody())
                .fromTier(row.getFromTier())
                .toTier(row.getToTier())
                .build();
    }
}
