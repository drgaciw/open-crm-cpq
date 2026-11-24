package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalCaseComment;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalTimelineEntry;
import com.opencrm.approval.domain.ApprovalTimelineKind;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.dto.ApprovalCommentRequest;
import com.opencrm.approval.dto.ApprovalHistoryEntryDto;
import com.opencrm.approval.repository.ApprovalCaseCommentRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.repository.ApprovalTimelineRepository;
import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.common.event.dto.ApprovalRejected;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

@DisplayName("GetApprovalHistory timeline")
class ApprovalHistoryServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

    private final ApprovalRequestRepository approvals = mock(ApprovalRequestRepository.class);
    private final ApprovalCaseCommentRepository comments = mock(ApprovalCaseCommentRepository.class);
    private final ApprovalTimelineRepository timeline = mock(ApprovalTimelineRepository.class);
    private final ApprovalHistoryService history = new ApprovalHistoryService(
            approvals, comments, timeline, new CurrentActor("service:approval-routing", false));

    @BeforeEach
    void caseExists() {
        ApprovalRequest approval = ApprovalRequest.builder()
                .quoteId("42")
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .requesterId("seller")
                .approvalTier("DEAL_DESK")
                .build();
        approval.setId(7L);
        when(approvals.findById(7L)).thenReturn(Optional.of(approval));
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("timeline is oldest first, and the same timestamp keeps id order")
    void ordersByTimeThenIdAndKeepsLinks() {
        TestCallers.signInUser("desk-1", "DealDesk");
        when(timeline.findByQuoteIdAndDeletedFalseOrderByOccurredAtAscIdAsc("42")).thenReturn(List.of(
                row(30L, T0.plusSeconds(30), ApprovalTimelineKind.GRANTED, "ApprovalGranted", "ship it", 5L, null, null,
                        null, "DEAL_DESK"),
                row(12L, T0.plusSeconds(10), ApprovalTimelineKind.COMMENT, null, "try 12 percent", 4L, 100L, null,
                        null, null),
                row(11L, T0.plusSeconds(10), ApprovalTimelineKind.ESCALATED, "ApprovalEscalated", null, 2L, null, null,
                        "DEAL_DESK", "SALES_VP"),
                row(40L, T0.plusSeconds(40), ApprovalTimelineKind.COMMENT, null, "agreed", 4L, 101L, 100L, null, null),
                row(20L, T0.plusSeconds(20), ApprovalTimelineKind.REJECTED, "ApprovalRejected", "revise", 3L, null, null,
                        null, "DEAL_DESK")));

        List<ApprovalHistoryEntryDto> timelineRows = history.getApprovalHistory("42");

        assertEquals(List.of(11L, 12L, 20L, 30L, 40L),
                timelineRows.stream().map(ApprovalHistoryEntryDto::getId).toList());
        ApprovalHistoryEntryDto escalation = timelineRows.get(0);
        assertEquals(ApprovalTimelineKind.ESCALATED, escalation.getKind());
        assertEquals("ApprovalEscalated", escalation.getEventName());
        assertEquals("DEAL_DESK", escalation.getFromTier());
        assertEquals("SALES_VP", escalation.getToTier());
        assertEquals(2L, escalation.getQuoteVersion());
        assertNull(escalation.getCommentId());

        ApprovalHistoryEntryDto comment = timelineRows.get(1);
        assertEquals(ApprovalTimelineKind.COMMENT, comment.getKind());
        assertNull(comment.getEventName());
        assertEquals(100L, comment.getCommentId());
        assertNull(comment.getParentCommentId());
        assertEquals(4L, comment.getQuoteVersion());
        assertEquals("try 12 percent", comment.getBody());

        ApprovalHistoryEntryDto rejection = timelineRows.get(2);
        assertEquals("ApprovalRejected", rejection.getEventName());
        assertEquals("revise", rejection.getBody());
        assertEquals(3L, rejection.getQuoteVersion());

        ApprovalHistoryEntryDto grant = timelineRows.get(3);
        assertEquals("ApprovalGranted", grant.getEventName());
        assertEquals("ship it", grant.getBody());
        assertEquals(5L, grant.getQuoteVersion());
        assertEquals(7L, grant.getApprovalId());

        ApprovalHistoryEntryDto reply = timelineRows.get(4);
        assertEquals(101L, reply.getCommentId());
        assertEquals(100L, reply.getParentCommentId());
        assertEquals("agreed", reply.getBody());
    }

    @Test
    @DisplayName("a comment and its reply land on the timeline with quote and thread links")
    void commentAndReplyRecordLinks() {
        TestCallers.signInUser("desk-1", "DealDesk");
        when(comments.save(any(ApprovalCaseComment.class))).thenAnswer(invocation -> {
            ApprovalCaseComment comment = invocation.getArgument(0);
            comment.setId(comment.getParentCommentId() == null ? 100L : 101L);
            return comment;
        });
        when(timeline.save(any(ApprovalTimelineEntry.class))).thenAnswer(invocation -> {
            ApprovalTimelineEntry row = invocation.getArgument(0);
            row.setId(row.getParentCommentId() == null ? 12L : 40L);
            return row;
        });
        when(comments.findByIdAndDeletedFalse(100L)).thenAnswer(invocation -> {
            ApprovalCaseComment parent = ApprovalCaseComment.builder()
                    .approvalRequestId(7L)
                    .quoteId("42")
                    .authorId("desk")
                    .body("try 12 percent")
                    .commentedAt(T0)
                    .build();
            parent.setId(100L);
            return Optional.of(parent);
        });

        ApprovalHistoryEntryDto first = history.addComment(7L, ApprovalCommentRequest.builder()
                .body("  try 12 percent ")
                .quoteVersion(4L)
                .build());
        ApprovalHistoryEntryDto reply = history.addComment(7L, ApprovalCommentRequest.builder()
                .body("agreed")
                .parentCommentId(100L)
                .quoteVersion(4L)
                .build());

        assertEquals(ApprovalTimelineKind.COMMENT, first.getKind());
        assertEquals(100L, first.getCommentId());
        assertNull(first.getParentCommentId());
        assertEquals(4L, first.getQuoteVersion());
        assertEquals("try 12 percent", first.getBody());
        assertEquals("42", first.getQuoteId());
        assertEquals(101L, reply.getCommentId());
        assertEquals(100L, reply.getParentCommentId());
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"CPQ.User", "Admin"})
    @DisplayName("each refused role gets 403 and the timeline is not read or written")
    void refusedRoleCannotReadOrComment(String role) {
        TestCallers.signInUser("someone", role);

        assertRefused(() -> history.getApprovalHistory("42"));
        assertRefused(() -> history.getApprovalHistoryForCase(7L));
        assertRefused(() -> history.addComment(7L, ApprovalCommentRequest.builder().body("no").quoteVersion(1L).build()));

        verify(timeline, never()).findByQuoteIdAndDeletedFalseOrderByOccurredAtAscIdAsc(any());
        verify(timeline, never()).save(any());
        verify(comments, never()).save(any());
        verify(approvals, never()).findById(any());
    }

    @Test
    @DisplayName("a recorded grant, rejection and escalation keep the event name and quote version")
    void recordsEventsAsTimelineRows() {
        history.recordGranted(new ApprovalGrantedEvent(7L, "42", "mgr", "DEAL_DESK", "ship it", T0), 5L);
        history.recordRejected(new ApprovalRejected(7L, "42", "mgr", "DEAL_DESK", "revise", T0.plusSeconds(1)), 3L);
        history.recordEscalated(new ApprovalEscalatedEvent(
                7L, "42", "DEAL_DESK", "SALES_VP", T0.plusSeconds(2), T0.plusSeconds(3)), 2L);

        ArgumentCaptor<ApprovalTimelineEntry> saved = ArgumentCaptor.forClass(ApprovalTimelineEntry.class);
        verify(timeline, times(3)).save(saved.capture());
        assertEquals(List.of("ApprovalGranted", "ApprovalRejected", "ApprovalEscalated"),
                saved.getAllValues().stream().map(ApprovalTimelineEntry::getEventName).toList());
        assertEquals(List.of(5L, 3L, 2L),
                saved.getAllValues().stream().map(ApprovalTimelineEntry::getQuoteVersion).toList());
        assertEquals(List.of(ApprovalTimelineKind.GRANTED, ApprovalTimelineKind.REJECTED, ApprovalTimelineKind.ESCALATED),
                saved.getAllValues().stream().map(ApprovalTimelineEntry::getKind).toList());
        assertEquals("DEAL_DESK", saved.getAllValues().get(2).getFromTier());
        assertEquals("SALES_VP", saved.getAllValues().get(2).getToTier());
    }

    private static void assertRefused(Runnable action) {
        BusinessException refused = assertThrows(BusinessException.class, action::run);
        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
        assertEquals("COLLABORATOR_ROLE_REQUIRED", refused.getErrorCode());
    }

    private static ApprovalTimelineEntry row(Long id, Instant when, ApprovalTimelineKind kind, String eventName,
            String body, Long quoteVersion, Long commentId, Long parentCommentId, String fromTier, String toTier) {
        ApprovalTimelineEntry row = ApprovalTimelineEntry.builder()
                .approvalRequestId(7L)
                .quoteId("42")
                .quoteVersion(quoteVersion)
                .commentId(commentId)
                .parentCommentId(parentCommentId)
                .kind(kind)
                .eventName(eventName)
                .occurredAt(when)
                .actorId("desk")
                .body(body)
                .fromTier(fromTier)
                .toTier(toTier)
                .build();
        row.setId(id);
        return row;
    }
}
