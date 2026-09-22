package com.opencrm.contract.domain;

/**
 * Values of {@code subscriptions.erp_sync_status} (a VARCHAR, kept as strings for compatibility).
 * <ul>
 *   <li>{@link #PENDING}: not synced; the default for subscriptions created through the API, which sync on
 *       activation ({@code SubscriptionService.activate}).</li>
 *   <li>{@link #QUEUED}: created from a quote-accepted event; the {@code ErpSyncJob} will sync and activate it
 *       (ADR-018 D6: the ERP call no longer runs inside the Kafka listener's transaction).</li>
 *   <li>{@link #IN_PROGRESS}: claimed by one {@code ErpSyncJob} run; requeued if it stays here too long.</li>
 *   <li>{@link #SYNCED}, {@link #FAILED}: outcome of the ERP call.</li>
 * </ul>
 */
public final class ErpSyncStatus {

    public static final String PENDING = "PENDING";
    public static final String QUEUED = "QUEUED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SYNCED = "SYNCED";
    public static final String FAILED = "FAILED";

    private ErpSyncStatus() {
    }
}
