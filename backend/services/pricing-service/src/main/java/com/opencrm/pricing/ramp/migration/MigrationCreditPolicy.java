package com.opencrm.pricing.ramp.migration;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One-time amount adjustment; recurring rates are never a credit allocation basis. */
public final class MigrationCreditPolicy {
    public Credit calculate(BigDecimal bookValue, BigDecimal grossLineTcv, BigDecimal firstGrossAmount) {
        if (bookValue == null || grossLineTcv == null || firstGrossAmount == null
                || bookValue.signum() < 0 || grossLineTcv.signum() < 0 || firstGrossAmount.signum() < 0
                || firstGrossAmount.compareTo(grossLineTcv) > 0) {
            throw new BusinessException("Invalid migration credit amounts", HttpStatus.UNPROCESSABLE_CONTENT,
                    "RAMP_SCHEDULE_INVALID");
        }
        BigDecimal requested = money(bookValue.multiply(new BigDecimal("0.30")));
        BigDecimal first = money(firstGrossAmount);
        BigDecimal effective = requested.min(money(grossLineTcv)).min(first);
        return new Credit(requested, effective, money(requested.subtract(effective)), money(first.subtract(effective)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record Credit(BigDecimal requestedCredit, BigDecimal effectiveCredit, BigDecimal unusedCredit,
                         BigDecimal adjustedPeriodAmount) {}
}
