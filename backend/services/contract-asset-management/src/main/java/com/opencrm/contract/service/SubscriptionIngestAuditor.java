package com.opencrm.contract.service;

import org.springframework.stereotype.Service;

/**
 * Durable ingest rejection audit (DRG-666 AC4). Survives the rolled-back apply transaction.
 */
@Service
public class SubscriptionIngestAuditor {
    public static final String AUDIT_CODE = "SUBSCRIPTION_INGEST_REJECTED";
    public static final String REASON_UNKNOWN_MODEL = "UNKNOWN_SUBSCRIPTION_MODEL";

    public void record(String acceptedQuoteId, String auditCode, String reasonCode, String productType, String detail) {
        // RED stub — wired in the GREEN implementation.
    }
}
