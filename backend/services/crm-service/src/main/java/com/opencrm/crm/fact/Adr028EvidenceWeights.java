package com.opencrm.crm.fact;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ADR-028 example weights, version {@code adr-028-1}. These are a starting calibration,
 * not a measured precision report. The enrichment agent stays off until that report exists.
 */
@Component
public class Adr028EvidenceWeights implements EvidenceWeights {

    public static final String VERSION = "adr-028-1";

    private static final BigDecimal EXACT_EMAIL_MATCH = new BigDecimal("0.95");
    private static final BigDecimal SYNCED_THREAD_REPLY = new BigDecimal("0.85");
    private static final BigDecimal SIGNATURE_BLOCK = new BigDecimal("0.80");
    private static final BigDecimal CITED_WEB_ANSWER = new BigDecimal("0.40");

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public BigDecimal weight(EvidenceKind kind) {
        return switch (kind) {
            case EXACT_EMAIL_MATCH -> EXACT_EMAIL_MATCH;
            case SYNCED_THREAD_REPLY -> SYNCED_THREAD_REPLY;
            case SIGNATURE_BLOCK -> SIGNATURE_BLOCK;
            case CITED_WEB_ANSWER -> CITED_WEB_ANSWER;
        };
    }

    @Override
    public boolean primary(EvidenceKind kind) {
        return switch (kind) {
            case EXACT_EMAIL_MATCH, SYNCED_THREAD_REPLY, SIGNATURE_BLOCK -> true;
            case CITED_WEB_ANSWER -> false;
        };
    }
}
