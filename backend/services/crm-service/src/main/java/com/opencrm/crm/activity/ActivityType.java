package com.opencrm.crm.activity;

/**
 * Timeline kinds from ADR-030. {@link #EMAIL} and {@link #MEETING} are stored so
 * mailbox ingestion can land later; the note/task API rejects creating them.
 */
public enum ActivityType {
    NOTE,
    TASK,
    EMAIL,
    MEETING
}
