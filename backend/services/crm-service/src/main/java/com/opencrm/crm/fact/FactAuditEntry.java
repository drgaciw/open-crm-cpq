package com.opencrm.crm.fact;

import java.time.Instant;

/**
 * One accept, dismiss, or automatic apply. The value is omitted: it may be an email.
 * <p>
 * TODO(BC-20-01): this is the audit hook. There is no BC-20 trail in crm-service yet.
 * {@link LoggingFactDecisionAudit} only logs. Replace that bean when the trail exists.
 * Do not drop the call.
 */
public record FactAuditEntry(
        String tenantId,
        FactSubjectType subjectType,
        String subjectId,
        Long factId,
        String fieldKey,
        FactStatus action,
        String actorId,
        Instant at
) {
}
