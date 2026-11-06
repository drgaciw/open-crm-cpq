package com.opencrm.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Durable ingest rejection audit (DRG-666 AC4). Uses {@link Propagation#REQUIRES_NEW} so the
 * row survives the rolled-back apply transaction that throws {@code NonRetryableEventException}.
 */
@Service
public class SubscriptionIngestAuditor {
    public static final String AUDIT_CODE = "SUBSCRIPTION_INGEST_REJECTED";
    public static final String REASON_UNKNOWN_MODEL = "UNKNOWN_SUBSCRIPTION_MODEL";

    private final JdbcTemplate jdbc;

    public SubscriptionIngestAuditor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String acceptedQuoteId, String auditCode, String reasonCode, String productType, String detail) {
        jdbc.update("""
                INSERT INTO subscription_ingest_audits
                    (uuid, accepted_quote_id, audit_code, reason_code, product_type, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                acceptedQuoteId,
                auditCode,
                reasonCode,
                productType,
                detail == null ? null : detail.substring(0, Math.min(detail.length(), 1024)));
    }
}
