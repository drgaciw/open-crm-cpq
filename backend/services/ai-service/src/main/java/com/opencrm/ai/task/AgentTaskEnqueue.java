package com.opencrm.ai.task;

import java.time.Instant;

/**
 * Caller-supplied enqueue. {@code tenantId} is an opaque string, stored as text, with no format of its own.
 */
public record AgentTaskEnqueue(
        String tenantId,
        String kind,
        String subjectType,
        String subjectId,
        String reason,
        String payload,
        Integer priority,
        Integer budget,
        Integer maxAttempts,
        Instant dueAt) {
}
