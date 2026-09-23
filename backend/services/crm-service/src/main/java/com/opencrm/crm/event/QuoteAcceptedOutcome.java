package com.opencrm.crm.event;

import java.util.Locale;

/** What happened to one quote acceptance on one channel (ADR-018 D8 dual run). */
public enum QuoteAcceptedOutcome {
    /** This delivery marked the deal WON. */
    APPLIED,
    /** The acceptance had already been applied, by the other channel or an earlier delivery. */
    DUPLICATE,
    /** The deal does not exist (yet); nothing was claimed, so a redelivery can still apply it. */
    DEAL_NOT_FOUND,
    /** The quote has no CRM deal; nothing to do. */
    NO_DEAL,
    /** Processing threw. On Kafka the error handler retries it; on Redis it is lost. */
    FAILED;

    /** Metric tag value, e.g. {@code deal-not-found}. */
    public String tag() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
