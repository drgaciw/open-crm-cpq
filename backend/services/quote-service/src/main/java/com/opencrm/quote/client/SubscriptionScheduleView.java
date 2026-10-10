package com.opencrm.quote.client;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public class SubscriptionScheduleView {
    private String subscriptionNumber;
    private String customerId;
    private String currencyCode;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long commercialRevision;
    private List<Line> lines;
    private BigDecimal activeMrr, activeArr, exitMrr, exitArr;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public static class Line {
        private String acceptedLineUuid, productExternalId, productName, provenance;
        private String productType, lineItemType, billingFrequency;
        private LocalDate billingAnchorDate;
        private List<Period> periods;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public static class Period {
        private Integer sequence, startMonth, months, quantity;
        private LocalDate startDate, endDate;
        private LocalDate contractedEndDate;
        private BigDecimal netUnitPrice, netMonthly, periodAmount, grossPeriodAmount, migrationCredit;
        private boolean applied, retired;
    }
}
