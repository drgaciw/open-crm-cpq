package com.opencrm.approval.service;

import com.opencrm.common.event.dto.ApprovalEscalatedEvent;
import com.opencrm.common.event.dto.ApprovalGrantedEvent;
import com.opencrm.common.event.dto.ApprovalRejected;

/**
 * Writes the approval-history timeline from the events approval-routing already publishes.
 * A missing recorder (tests that construct {@link ApprovalService} directly) records nothing.
 */
public interface ApprovalHistoryRecorder {

    void recordGranted(ApprovalGrantedEvent event, Long quoteVersion);

    void recordRejected(ApprovalRejected event, Long quoteVersion);

    void recordEscalated(ApprovalEscalatedEvent event, Long quoteVersion);

    static ApprovalHistoryRecorder noop() {
        return new ApprovalHistoryRecorder() {
            @Override
            public void recordGranted(ApprovalGrantedEvent event, Long quoteVersion) {
            }

            @Override
            public void recordRejected(ApprovalRejected event, Long quoteVersion) {
            }

            @Override
            public void recordEscalated(ApprovalEscalatedEvent event, Long quoteVersion) {
            }
        };
    }
}
