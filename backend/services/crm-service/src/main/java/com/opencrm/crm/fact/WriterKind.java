package com.opencrm.crm.fact;

/**
 * Who owns the value when the fact is the current one. A human owner is never overwritten
 * by an agent (ADR-028 D-04).
 */
public enum WriterKind {
    HUMAN,
    AGENT
}
