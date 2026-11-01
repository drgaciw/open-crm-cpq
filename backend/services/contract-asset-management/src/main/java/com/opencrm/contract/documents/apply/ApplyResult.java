package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;

import java.util.List;

/** A committed apply or a same-digest replay. Refusals are exceptions. */
public record ApplyResult(AmendmentDocumentStatus status, boolean replayed, List<UnitOutcome> unitOutcomes) {

    public ApplyResult(AmendmentDocumentStatus status, boolean replayed) {
        this(status, replayed, null);
    }

    /**
     * One unit on the apply response. {@code outcome} is {@code APPLIED} or {@code BLOCKED}.
     * {@code BLOCKED} is the OQ5 {@code APPLY_BLOCKED} unit state; the persisted enum stays {@code BLOCKED}.
     */
    public record UnitOutcome(Integer unitId, String outcome, String reason) {
    }
}
