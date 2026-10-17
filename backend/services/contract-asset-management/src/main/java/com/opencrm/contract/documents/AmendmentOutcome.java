package com.opencrm.contract.documents;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record AmendmentOutcome(AmendmentView view, boolean replayed) {
}
