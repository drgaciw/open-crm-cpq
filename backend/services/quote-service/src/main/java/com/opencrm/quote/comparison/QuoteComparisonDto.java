package com.opencrm.quote.comparison;

import com.opencrm.quote.quote.QuoteStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuoteComparisonDto(String dealExternalId, Long baselineQuoteId, List<Column> quotes,
                                 List<String> unavailableFields) {
    public record Column(Long id, String quoteNumber, String name, boolean primary, QuoteStatus status,
                         String currency, BigDecimal subtotal, BigDecimal listTotal, BigDecimal discountTotal,
                         BigDecimal discountPercent, BigDecimal tax, BigDecimal grandTotal, BigDecimal netAmount,
                         BigDecimal marginAmount, BigDecimal marginPercent, List<String> billingFrequencies,
                         int termMonths, LocalDate validUntil, String paymentTerms, int lineCount,
                         Delta delta, SkuDiff skuDiff) {}
    public record Delta(boolean comparableCurrency, BigDecimal subtotal, BigDecimal discountTotal,
                        BigDecimal tax, BigDecimal grandTotal, BigDecimal grandTotalPercent) {}
    public record SkuDiff(List<String> added, List<String> removed, List<String> changed) {}
}
