package com.opencrm.dealdesk.model.tcv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountsAndAdjustments {

    private BigDecimal volumeDiscountPercentage;
    private BigDecimal volumeDiscountAmount;
    private BigDecimal earlyPaymentDiscountPercentage;
    private BigDecimal earlyPaymentDiscountAmount;
    private BigDecimal otherDiscountAmount;
    private String otherDiscountReason;

    private BigDecimal priceAdjustmentAmount;
    private String priceAdjustmentReason;
    private LocalDate priceAdjustmentEffectiveDate;

    @Builder.Default
    private boolean approved = false;
    private String approvedBy;
    private LocalDate approvedDate;
    private String approvalNotes;

    public void validate() {
        validateDiscounts();
        validateAdjustments();
        validateApproval();
    }

    private void validateDiscounts() {
        if (volumeDiscountPercentage != null) {
            Assert.isTrue(
                    volumeDiscountPercentage.compareTo(BigDecimal.ZERO) >= 0
                            && volumeDiscountPercentage.compareTo(new BigDecimal("100")) <= 0,
                    "Volume discount percentage must be between 0 and 100"
            );
        }

        if (earlyPaymentDiscountPercentage != null) {
            Assert.isTrue(
                    earlyPaymentDiscountPercentage.compareTo(BigDecimal.ZERO) >= 0
                            && earlyPaymentDiscountPercentage.compareTo(new BigDecimal("100")) <= 0,
                    "Early payment discount percentage must be between 0 and 100"
            );
        }

        validateAmount(volumeDiscountAmount, "Volume discount amount");
        validateAmount(earlyPaymentDiscountAmount, "Early payment discount amount");
        validateAmount(otherDiscountAmount, "Other discount amount");
    }

    private void validateAdjustments() {
        if (priceAdjustmentAmount != null) {
            Assert.hasText(priceAdjustmentReason, "Price adjustment reason is required when amount is specified");
            Assert.notNull(
                    priceAdjustmentEffectiveDate,
                    "Price adjustment effective date is required when amount is specified"
            );
        }
    }

    private void validateApproval() {
        if (approved) {
            Assert.hasText(approvedBy, "Approver is required when discount is approved");
            Assert.notNull(approvedDate, "Approval date is required when discount is approved");
        }
    }

    private void validateAmount(BigDecimal amount, String fieldName) {
        if (amount != null) {
            Assert.isTrue(amount.compareTo(BigDecimal.ZERO) >= 0, fieldName + " must be non-negative");
        }
    }

    public BigDecimal getTotalDiscountAmount() {
        BigDecimal total = BigDecimal.ZERO;
        if (volumeDiscountAmount != null) {
            total = total.add(volumeDiscountAmount);
        }
        if (earlyPaymentDiscountAmount != null) {
            total = total.add(earlyPaymentDiscountAmount);
        }
        if (otherDiscountAmount != null) {
            total = total.add(otherDiscountAmount);
        }
        return total;
    }

    public BigDecimal getTotalAdjustmentAmount() {
        return priceAdjustmentAmount != null ? priceAdjustmentAmount : BigDecimal.ZERO;
    }
}
