package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.documents.ActionCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One change line read for apply. Pricing amounts are copied, not recomputed. */
public record ApplyLine(
        UUID id,
        int lineNo,
        int unitNo,
        ActionCode action,
        String sourceAcceptedLineUuid,
        BigDecimal endQty,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String pricebookVersion,
        String productExternalId,
        BigDecimal unitPrice) {
}
