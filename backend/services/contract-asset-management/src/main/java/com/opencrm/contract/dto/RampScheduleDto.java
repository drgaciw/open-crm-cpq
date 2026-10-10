package com.opencrm.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.contract.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Public read facts, with absent legacy unit prices kept absent. */
public record RampScheduleDto(String subscriptionNumber, String customerId, String currencyCode,
        SubscriptionStatus status, LocalDate startDate, LocalDate endDate, Long commercialRevision,
        List<Line> lines, BigDecimal activeMrr, BigDecimal activeArr, BigDecimal exitMrr, BigDecimal exitArr) {
    public record Line(String acceptedLineUuid, String productExternalId, String productName, String provenance,
                       String productType, String lineItemType, String billingFrequency,
                       LocalDate billingAnchorDate, List<Period> periods) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Period(Integer sequence, Integer startMonth, Integer months, LocalDate startDate, LocalDate endDate,
            LocalDate contractedEndDate, BigDecimal quantity, BigDecimal netUnitPrice, BigDecimal netMonthly,
            BigDecimal periodAmount, BigDecimal grossPeriodAmount, BigDecimal migrationCredit, boolean applied,
            boolean retired) {}
}
