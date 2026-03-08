package com.opencrm.dealdesk.model.tcv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepricingTriggers {

    private String id;

    private String priceRuleId;
    private String priceRuleSetId;

    private boolean commercialCredit;
    private boolean allCredit;

    private BigDecimal averagePaymentThresholdAmount;
    private BigDecimal comCreditPercentageThreshold;
    private BigDecimal allCreditPercentageThreshold;
    private BigDecimal debitPercentageThreshold;
    private BigDecimal durbinRegPercentageThreshold;
    private BigDecimal unregDebitPercentageThreshold;

    private BigDecimal creditPercentageAdjustment;
    private BigDecimal debitPercentageAdjustment;
    private BigDecimal durbinRegPercentageAdjustment;
    private BigDecimal unregDebitPercentageAdjustment;

    @Builder.Default
    private boolean averagePaymentAbove = true;
    @Builder.Default
    private boolean comCreditPercentageAbove = true;
    @Builder.Default
    private boolean allCreditPercentageAbove = true;
    @Builder.Default
    private boolean debitPercentageAbove = false;
    @Builder.Default
    private boolean durbinRegPercentageAbove = false;
    @Builder.Default
    private boolean unregDebitPercentageAbove = true;

    private boolean syncedWithCPQ;
    private LocalDateTime lastSyncTimestamp;
    private String lastSyncError;

    public BigDecimal getCreditPercentageThreshold() {
        Assert.isTrue(commercialCredit || allCredit, "Either commercial credit or all credit must be selected");
        if (commercialCredit) {
            Assert.notNull(comCreditPercentageThreshold, "Commercial credit percentage threshold cannot be null");
            return comCreditPercentageThreshold;
        }
        Assert.notNull(allCreditPercentageThreshold, "All credit percentage threshold cannot be null");
        return allCreditPercentageThreshold;
    }

    public boolean isCreditPercentageAbove() {
        Assert.isTrue(commercialCredit || allCredit, "Either commercial credit or all credit must be selected");
        return commercialCredit ? comCreditPercentageAbove : allCreditPercentageAbove;
    }

    public BigDecimal getCreditPercentageAdjustment() {
        return creditPercentageAdjustment != null ? creditPercentageAdjustment : BigDecimal.ZERO;
    }

    public void validateThresholds() {
        Assert.notNull(averagePaymentThresholdAmount, "Average payment threshold amount cannot be null");
        Assert.notNull(debitPercentageThreshold, "Debit percentage threshold cannot be null");
        Assert.notNull(durbinRegPercentageThreshold, "Durbin regulated percentage threshold cannot be null");
        Assert.notNull(unregDebitPercentageThreshold, "Unregulated debit percentage threshold cannot be null");

        Assert.isTrue(
                averagePaymentThresholdAmount.compareTo(BigDecimal.ZERO) > 0,
                "Average payment threshold amount must be positive"
        );

        validatePercentageRange(debitPercentageThreshold, "Debit percentage threshold");
        validatePercentageRange(durbinRegPercentageThreshold, "Durbin regulated percentage threshold");
        validatePercentageRange(unregDebitPercentageThreshold, "Unregulated debit percentage threshold");

        if (commercialCredit) {
            validatePercentageRange(comCreditPercentageThreshold, "Commercial credit percentage threshold");
        }
        if (allCredit) {
            validatePercentageRange(allCreditPercentageThreshold, "All credit percentage threshold");
        }
    }

    private void validatePercentageRange(BigDecimal percentage, String fieldName) {
        Assert.notNull(percentage, fieldName + " cannot be null");
        Assert.isTrue(
                percentage.compareTo(BigDecimal.ZERO) >= 0 && percentage.compareTo(new BigDecimal("100")) <= 0,
                fieldName + " must be between 0 and 100"
        );
    }

    public void validateWithCPQ(String quoteId) {
        // Placeholder for CPQ validation hooks.
    }

    public void syncToCPQ(String quoteId) {
        // Placeholder for CPQ sync hooks.
    }
}
