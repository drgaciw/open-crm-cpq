package com.opencrm.contract.documents.apply;

import com.opencrm.common.commercial.CommercialDigest;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Unit replay digest via {@link CommercialDigest}. This is not a second digest implementation. */
public final class DocumentApplyDigest {

    private DocumentApplyDigest() {
    }

    public static String unitDigest(UUID documentId, AmendmentApplicationUnit unit, List<ApplyLine> lines) {
        StringBuilder raw = new StringBuilder();
        raw.append(documentId).append('|').append(unit.getUnitNo()).append('|')
                .append(unit.getObservedCommercialRevision()).append('|').append(unit.getTargetRef());
        lines.stream().sorted(Comparator.comparingInt(ApplyLine::lineNo)).forEach(line -> raw
                .append('|').append(line.lineNo())
                .append('|').append(line.action())
                .append('|').append(line.endQty() == null ? "" : line.endQty().toPlainString())
                .append('|').append(line.effectiveFrom())
                .append('|').append(line.effectiveTo())
                .append('|').append(line.pricebookVersion())
                .append('|').append(line.sourceAcceptedLineUuid())
                .append('|').append(line.productExternalId()));
        return CommercialDigest.sha256(raw.toString().getBytes(StandardCharsets.UTF_8));
    }
}
