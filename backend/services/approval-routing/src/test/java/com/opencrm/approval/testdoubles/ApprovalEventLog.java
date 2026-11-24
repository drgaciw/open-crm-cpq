package com.opencrm.approval.testdoubles;

import java.util.List;

/**
 * Event seam for DRG-444. Feature re-points {@code emit} at {@code OutboxEventPublisher} with a PUBLISHED
 * contract whose name is the event {@code name} (ADR-018 D5).
 */
public interface ApprovalEventLog {

    void emit(EmittedApprovalEvent event);

    List<EmittedApprovalEvent> emitted();
}
