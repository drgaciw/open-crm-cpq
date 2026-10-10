package com.opencrm.pricing.ramp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.opencrm.common.exception.BusinessException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MigrationCreditPolicyTest {
    private final MigrationCreditPolicy policy = new MigrationCreditPolicy();

    @Test
    void firstPeriodCapNeverSpillsTheUnusedCreditIntoLaterPeriods() {
        var credit = policy.calculate(amount("6000"), amount("3000"), amount("1000"));
        assertThat(credit.requestedCredit()).isEqualByComparingTo("1800.00");
        assertThat(credit.effectiveCredit()).isEqualByComparingTo("1000.00");
        assertThat(credit.unusedCredit()).isEqualByComparingTo("800.00");
        assertThat(credit.adjustedPeriodAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void bookValueTenThousandCreditsThreeThousandExactlyOnce() {
        var credit = policy.calculate(amount("10000"), amount("12000"), amount("12000"));
        assertThat(credit.effectiveCredit()).isEqualByComparingTo("3000.00");
        assertThat(credit.adjustedPeriodAmount()).isEqualByComparingTo("9000.00");
        assertThat(credit.unusedCredit()).isEqualByComparingTo("0.00");
    }

    @Test
    void decimalBookValueUsesHalfUpMoneyRounding() {
        var credit = policy.calculate(amount("10000.05"), amount("12000"), amount("12000"));
        assertThat(credit.requestedCredit()).isEqualByComparingTo("3000.02");
        assertThat(credit.adjustedPeriodAmount()).isEqualByComparingTo("8999.98");
    }

    @Test
    void zeroGrossAndZeroBookValueNeverProduceANegativeAmount() {
        assertThat(policy.calculate(amount("10000"), amount("0"), amount("0")).adjustedPeriodAmount())
                .isEqualByComparingTo("0.00");
        assertThat(policy.calculate(amount("0"), amount("12000"), amount("12000")).effectiveCredit())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void negativeOrInconsistentCommercialInputsFailBeforeAdjustment() {
        assertThatThrownBy(() -> policy.calculate(amount("-1"), amount("3000"), amount("1000")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RAMP_SCHEDULE_INVALID");
        assertThatThrownBy(() -> policy.calculate(amount("6000"), amount("500"), amount("1000")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RAMP_SCHEDULE_INVALID");
    }

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
