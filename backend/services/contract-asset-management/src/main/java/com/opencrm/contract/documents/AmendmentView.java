package com.opencrm.contract.documents;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AmendmentView(
        UUID id,
        UUID contractId,
        String provisionalNumber,
        Integer amendmentSeq,
        String amendmentNumber,
        AmendmentDocumentStatus status,
        int version,
        String contentDigest,
        String approvalRef,
        String signatureEnvelopeRef,
        BigDecimal tcvDelta,
        BigDecimal arrDelta,
        BigDecimal oneTimeTotal,
        BigDecimal creditTotal,
        List<RevisionView> revisions,
        List<LineView> lines) {
}

