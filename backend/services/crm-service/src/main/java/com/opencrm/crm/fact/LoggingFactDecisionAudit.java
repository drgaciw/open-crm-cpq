package com.opencrm.crm.fact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder until BC-20-01 exists.
 * TODO(BC-20-01): persist {@link FactAuditEntry} on the audit trail and stop at the log.
 */
@Slf4j
@Component
public class LoggingFactDecisionAudit implements FactDecisionAudit {

    @Override
    public void record(FactAuditEntry entry) {
        log.info(
                "Fact decision pending BC-20-01 audit trail: tenant={} subject={}:{} field={} fact={} action={} actor={}",
                entry.tenantId(),
                entry.subjectType(),
                entry.subjectId(),
                entry.fieldKey(),
                entry.factId(),
                entry.action(),
                entry.actorId());
    }
}
