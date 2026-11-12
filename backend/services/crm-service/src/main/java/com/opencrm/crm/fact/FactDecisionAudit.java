package com.opencrm.crm.fact;

/**
 * Accept, dismiss, and automatic apply notify this port.
 * TODO(BC-20-01): swap the logging bean for the audit trail. Callers stay the same.
 */
public interface FactDecisionAudit {

    void record(FactAuditEntry entry);
}
