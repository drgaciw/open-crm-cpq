package com.opencrm.crm.fact;

/**
 * Closed evidence kinds for ADR-028. The model names a kind. It does not send a weight or a score.
 * Weights live in {@link Adr028EvidenceWeights}.
 */
public enum EvidenceKind {
    /** Exact email match on a profile the subject controls. Primary source. */
    EXACT_EMAIL_MATCH,
    /** The person replied on a synced thread. Primary source. */
    SYNCED_THREAD_REPLY,
    /** A signature block on a message attributed to the person states the value. Primary source. */
    SIGNATURE_BLOCK,
    /** A cited web answer. Not a primary source. */
    CITED_WEB_ANSWER
}
