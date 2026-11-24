package com.opencrm.approval.spec;

import com.opencrm.approval.testdoubles.ApprovalEscalations;
import com.opencrm.approval.testdoubles.ApprovalEventLog;
import com.opencrm.approval.testdoubles.ApprovalRequestStore;
import com.opencrm.approval.testdoubles.Escalation;
import com.opencrm.approval.testdoubles.EscalationPath;
import com.opencrm.approval.testdoubles.InMemoryApprovalEventLog;
import com.opencrm.approval.testdoubles.NotImplementedApprovalEscalations;
import com.opencrm.approval.testdoubles.PendingApproval;
import com.opencrm.approval.testdoubles.SlaWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DRG-444 red spec for Escalate along {@link EscalationPath} after {@link SlaWindow}.
 * Uses a fixed {@link Clock}. No sleeps.
 *
 * <p>Drives {@link NotImplementedApprovalEscalations}. Feature replaces the double with the real escalation
 * service and keeps these assertions. Not the D1-3 cancel cascade.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-444 ApprovalEscalation")
class ApprovalEscalationTest {

    private static final long APPROVAL_ID = 7L;
    private static final Instant OPENED_AT = Instant.parse("2026-11-23T18:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(4);

    private final SlaWindow sla = new SlaWindow(OPENED_AT, WINDOW);
    private final EscalationPath path = new EscalationPath(List.of("deal-desk", "cfo"));

    @Mock
    private ApprovalRequestStore store;

    private ApprovalEventLog events;
    private ApprovalEscalations escalations;

    @BeforeEach
    void setUp() {
        events = new InMemoryApprovalEventLog();
        escalations = new NotImplementedApprovalEscalations(store, path, sla, events);
        when(store.find(APPROVAL_ID)).thenReturn(Optional.of(
                new PendingApproval(APPROVAL_ID, "Q-42", "PENDING", path.current())));
    }

    @Test
    @DisplayName("at the SLA deadline, escalate moves to the next EscalationPath step")
    void escalateFollowsEscalationPathAfterSlaWindow() {
        Escalation result = escalations.escalate(APPROVAL_ID, clock(true));

        assertThat(result.fired()).isTrue();
        assertThat(result.assigneeId()).isEqualTo(path.nextAfter(path.current()));
    }

    @Test
    @DisplayName("one second before the SLA deadline, escalate does not fire")
    void escalateDoesNotFireBeforeSlaWindow() {
        Escalation result = escalations.escalate(APPROVAL_ID, clock(false));

        assertThat(result.fired()).isFalse();
    }

    @Test
    @DisplayName("escalation after the SLA window emits ApprovalEscalated for the next step")
    void escalateEmitsApprovalEscalated() {
        escalations.escalate(APPROVAL_ID, clock(true));

        assertThat(events.emitted()).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("ApprovalEscalated");
            assertThat(event.approvalId()).isEqualTo(APPROVAL_ID);
            assertThat(event.quoteId()).isEqualTo("Q-42");
            assertThat(event.actorId()).isEqualTo(path.nextAfter(path.current()));
        });
    }

    private Clock clock(boolean windowElapsed) {
        Instant now = windowElapsed ? sla.deadline() : sla.deadline().minusSeconds(1);
        if (sla.elapsed(now) != windowElapsed) {
            throw new IllegalStateException("SLA fixture clock does not match SlaWindow.elapsed");
        }
        return Clock.fixed(now, ZoneOffset.UTC);
    }
}
