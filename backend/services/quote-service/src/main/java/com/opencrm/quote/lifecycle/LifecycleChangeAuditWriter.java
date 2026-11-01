package com.opencrm.quote.lifecycle;

/** Writes one rejection row. The implementation commits that write on its own. */
public interface LifecycleChangeAuditWriter {

    void record(LifecycleChangeAuditEntry entry);
}
