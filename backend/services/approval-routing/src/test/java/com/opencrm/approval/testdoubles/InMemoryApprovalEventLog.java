package com.opencrm.approval.testdoubles;

import java.util.ArrayList;
import java.util.List;

/** Test fixture. Starts empty. The not-implemented doubles never call {@link #emit}. */
public final class InMemoryApprovalEventLog implements ApprovalEventLog {

    private final List<EmittedApprovalEvent> events = new ArrayList<>();

    @Override
    public void emit(EmittedApprovalEvent event) {
        events.add(event);
    }

    @Override
    public List<EmittedApprovalEvent> emitted() {
        return List.copyOf(events);
    }
}
