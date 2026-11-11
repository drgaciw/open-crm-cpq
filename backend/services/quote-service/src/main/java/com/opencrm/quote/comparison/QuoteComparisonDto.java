package com.opencrm.quote.comparison;

import com.opencrm.quote.quote.DealType;
import com.opencrm.quote.quote.QuoteStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Persisted alternatives only: comparison never recalculates prices or treats placeholder margin as evidence. */
public record QuoteComparisonDto(
        String dealExternalId, String customerExternalId, Long primaryQuoteId,
        boolean sameCurrency, List<Alternative> quotes) {

    public record Alternative(
            Long id, String quoteNumber, String name, QuoteStatus status, DealType dealType,
            String currency, String region, int durationInMonths, LocalDate startDate, LocalDate endDate,
            BigDecimal subtotal, BigDecimal totalDiscount, BigDecimal totalTax, BigDecimal totalAmount,
            BigDecimal arr, BigDecimal tcv, BigDecimal acv, boolean rampScheduleStale,
            Margin margin, List<Line> lines) {}

    /** A real cost basis has not landed. Value deliberately stays null, including when a stored snapshot says 40. */
    public record Margin(String status, BigDecimal value, String reason) {}

    public record Line(String productExternalId, String productName, String productType,
                       String lineItemType, int quantity, String unitOfMeasure, String billingFrequency) {}
}
