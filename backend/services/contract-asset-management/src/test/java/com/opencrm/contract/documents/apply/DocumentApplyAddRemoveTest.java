package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActionCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-S4-5. DoD v0.3 Core 3 and Core 4. */
@Tag("unit")
class DocumentApplyAddRemoveTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 4, 1);

    @Test
    void addCoTermsToEnd() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 1L, START, END);
        fixture.unit(1, subscription, 1L);
        var line = fixture.line(1, ActionCode.ADD, null, new BigDecimal("3.0000"), DAY, END, "pb-1");

        fixture.apply();

        assertThat(fixture.periods).hasSize(1);
        SubscriptionRatePeriod created = fixture.periods.get(0);
        assertThat(created.getStartDate()).isEqualTo(DAY);
        assertThat(created.getEndDate()).isEqualTo(END);
        assertThat(created.getEffectiveEndDate()).isEqualTo(END);
        assertThat(created.getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(fixture.binds).containsExactly(new ApplyFixture.LineBind(created.getId(), line.id(), 1));
        assertThat(created.isRetired()).isFalse();
    }

    @Test
    void removeEndDatesSourceAndCreatesNone() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 1L, START, END);
        fixture.unit(1, subscription, 1L);
        fixture.line(1, ActionCode.REMOVE, "line-source", BigDecimal.ZERO, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openPeriod(subscription, "line-source", new BigDecimal("4.0000"), START, END);

        fixture.apply();

        assertThat(fixture.periods).containsExactly(source);
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(fixture.binds).isEmpty();
    }
}
