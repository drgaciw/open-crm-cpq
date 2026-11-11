package com.opencrm.audit;

/**
 * Outcome of {@link AuditTrailService#verifyChainIntegrity()}: either the whole chain recomputes cleanly, or the
 * entry where it stopped matching is reported.
 */
public record ChainIntegrityResult(boolean intact, long entriesChecked, Long brokenAtEntryId, String reason) {

    static ChainIntegrityResult intact(long entriesChecked) {
        return new ChainIntegrityResult(true, entriesChecked, null, null);
    }

    static ChainIntegrityResult broken(long entriesCheckedBeforeFailure, Long brokenAtEntryId, String reason) {
        return new ChainIntegrityResult(false, entriesCheckedBeforeFailure, brokenAtEntryId, reason);
    }
}
