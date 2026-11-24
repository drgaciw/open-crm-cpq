package com.opencrm.approval.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The quote version {@code EvaluateQuote} compares with commercial policy. Percent fields are percentages
 * (25 means 25%), not fractions. A null measurement means the quote version does not carry that fact.
 */
public record QuoteVersionFacts(
        String quoteId,
        Long quoteVersion,
        String tenantId,
        String segment,
        String productFamily,
        String region,
        BigDecimal discountPercent,
        BigDecimal marginPercent,
        Integer termMonths,
        List<String> clauseCodes,
        LocalDate asOf) {

    public QuoteVersionFacts {
        clauseCodes = clauseCodes == null ? List.of() : List.copyOf(clauseCodes);
    }
}
