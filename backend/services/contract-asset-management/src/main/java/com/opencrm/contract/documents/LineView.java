package com.opencrm.contract.documents;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LineView(
        UUID changeLineRef,
        int revisionNo,
        String productExternalId,
        String pricebookVersion,
        String configurationHash,
        String policyDecisionId,
        BusinessCategory businessCategory) {
}

