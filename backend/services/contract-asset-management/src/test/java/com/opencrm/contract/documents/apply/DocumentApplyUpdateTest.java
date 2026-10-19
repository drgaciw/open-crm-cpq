package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActionCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-S4-4. DoD v0.3 Core 3 and Core 4. S4 does not reprice; quantity and dates are the assertion. */
@Tag("unit")
class DocumentApplyUpdateTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final BigDecimal ORIGINAL = new BigDecimal("100.0000");
    private static final BigDecimal UPDATED = new BigDecimal("80.0000");

    @Test
    void updateFrom100To80EndsTheOldPeriodTheDayBeforeAndOpensTheNewOne() {
        LocalDate day = LocalDate.of(2026, 6, 15);
        Periods periods = applyUpdate(day, day);

        assertThat(periods.closed().getEffectiveEndDate()).isEqualTo(day.minusDays(1));
        assertThat(periods.closed().isRetired()).isFalse();
        assertThat(periods.closed().getQuantity()).isEqualByComparingTo(ORIGINAL);
        assertThat(periods.opened().getStartDate()).isEqualTo(day);
        assertThat(periods.opened().getEndDate()).isEqualTo(END);
        assertThat(periods.opened().getEffectiveEndDate()).isEqualTo(END);
        assertThat(periods.opened().getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(periods.opened().getNetMonthly()).isEqualByComparingTo("1000.00");
        assertThat(periods.bind().lineId()).isEqualTo(periods.openedLine);
        assertThat(periods.bind().periodId()).isEqualTo(periods.opened().getId());
        assertThat(periods.bind().revisionNo()).isEqualTo(1);
    }

    @Test
    void effectiveDateAtPeriodStartRetiresTheUnderflow() {
        Periods periods = applyUpdate(START, START);

        assertThat(periods.closed().getEffectiveEndDate()).isEqualTo(START.minusDays(1));
        assertThat(periods.closed().isRetired()).isTrue();
        assertThat(periods.opened().getStartDate()).isEqualTo(START);
        assertThat(periods.opened().getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(periods.bind().lineId()).isEqualTo(periods.openedLine);
    }

    @Test
    void effectiveDateOnContractEndIsASingleDay() {
        Periods periods = applyUpdate(END, END);

        assertThat(periods.closed().getEffectiveEndDate()).isEqualTo(END.minusDays(1));
        assertThat(periods.closed().isRetired()).isFalse();
        assertThat(periods.opened().getStartDate()).isEqualTo(END);
        assertThat(periods.opened().getEndDate()).isEqualTo(END);
        assertThat(periods.opened().getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(periods.bind().lineId()).isEqualTo(periods.openedLine);
    }

    private static Periods applyUpdate(LocalDate effective, LocalDate expectedStart) {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        var line = fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, effective, END, "pb-1");
        SubscriptionRatePeriod closed = fixture.openPeriod(subscription, "line-source", ORIGINAL, START, END);

        fixture.apply();

        SubscriptionRatePeriod opened = fixture.periods.stream()
                .filter(period -> period != closed)
                .findFirst()
                .orElseThrow();
        assertThat(opened.getStartDate()).isEqualTo(expectedStart);
        assertThat(fixture.binds).hasSize(1);
        return new Periods(closed, opened, line.id(), fixture.binds.get(0));
    }

    private record Periods(SubscriptionRatePeriod closed, SubscriptionRatePeriod opened, UUID openedLine,
                           ApplyFixture.LineBind bind) {
    }
}
