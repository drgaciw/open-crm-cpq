package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;

/** A committed apply or a same-digest replay. Refusals are exceptions. */
public record ApplyResult(AmendmentDocumentStatus status, boolean replayed) {
}
