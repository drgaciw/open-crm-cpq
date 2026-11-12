package com.opencrm.crm.fact;

import java.math.BigDecimal;

/**
 * ADR-028 D-03, D-04 and D-05. The band, the principal, and human ownership decide the row.
 * Contradiction overrides the score, including scores that would otherwise be dropped.
 */
public final class FactDispositionDecider {

    static final BigDecimal VERIFIED_AT = new BigDecimal("0.85");
    static final BigDecimal PROBABLE_AT = new BigDecimal("0.55");
    static final BigDecimal POSSIBLE_AT = new BigDecimal("0.30");

    private FactDispositionDecider() {
    }

    public static FactDisposition decide(
            BigDecimal score,
            boolean primarySource,
            boolean sensitive,
            boolean contradicting,
            FactPrincipalKind principal,
            boolean humanOwnsField) {
        if (score == null) {
            throw new IllegalArgumentException("score is required");
        }
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
        FactBand raw = rawBand(score, primarySource);
        if (contradicting) {
            return new FactDisposition(true, FactStatus.UNRESOLVED, raw, false, false);
        }
        FactBand effective = sensitive ? demote(raw) : raw;
        if (effective == null) {
            return FactDisposition.notStored();
        }
        return switch (effective) {
            case VERIFIED -> verified(principal, humanOwnsField);
            case PROBABLE -> FactDisposition.proposal(FactBand.PROBABLE, humanOwnsField);
            case POSSIBLE -> new FactDisposition(true, FactStatus.PROVENANCE, FactBand.POSSIBLE, false, humanOwnsField);
        };
    }

    private static FactDisposition verified(FactPrincipalKind principal, boolean humanOwnsField) {
        return switch (principal) {
            case AGENT -> humanOwnsField
                    ? FactDisposition.proposal(FactBand.VERIFIED, true)
                    : new FactDisposition(true, FactStatus.APPLIED, FactBand.VERIFIED, true, false);
            case HUMAN -> FactDisposition.proposal(FactBand.VERIFIED, humanOwnsField);
        };
    }

    /**
     * Score at or above 0.85 without a primary source stays PROBABLE. It is not VERIFIED.
     */
    private static FactBand rawBand(BigDecimal score, boolean primarySource) {
        if (score.compareTo(VERIFIED_AT) >= 0 && primarySource) {
            return FactBand.VERIFIED;
        }
        if (score.compareTo(PROBABLE_AT) >= 0) {
            return FactBand.PROBABLE;
        }
        if (score.compareTo(POSSIBLE_AT) >= 0) {
            return FactBand.POSSIBLE;
        }
        return null;
    }

    /** Sensitive fields need one band higher, so each band steps down. POSSIBLE is not stored. */
    private static FactBand demote(FactBand band) {
        if (band == null) {
            return null;
        }
        return switch (band) {
            case VERIFIED -> FactBand.PROBABLE;
            case PROBABLE -> FactBand.POSSIBLE;
            case POSSIBLE -> null;
        };
    }
}
