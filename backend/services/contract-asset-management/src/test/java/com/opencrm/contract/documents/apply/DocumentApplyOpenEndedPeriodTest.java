package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-624-1 and AC-624-2. DoD v0.3 item 3 (tests) and item 4 (date boundaries; amounts copied, not repriced).
 * A null effective end is open-ended coverage and still matches Update and Remove.
 */
@Tag("unit")
class DocumentApplyOpenEndedPeriodTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
    private static final BigDecimal ORIGINAL = new BigDecimal("100.0000");
    private static final BigDecimal UPDATED = new BigDecimal("80.0000");

    @Test
    void updateOpenEndedSourceAppliesAndClosesTheDayBefore() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openEndedPeriod(subscription, "line-source", ORIGINAL, START, END);

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(1);
        assertThat(subscription.getCommercialRevision()).isEqualTo(6L);
        assertThat(source.getEndDate()).isEqualTo(END);
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(source.getQuantity()).isEqualByComparingTo(ORIGINAL);
        assertThat(source.getNetUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");

        SubscriptionRatePeriod opened = opened(fixture, source);
        assertThat(opened.getStartDate()).isEqualTo(DAY);
        assertThat(opened.getEndDate()).isEqualTo(END);
        assertThat(opened.getEffectiveEndDate()).isEqualTo(END);
        assertThat(opened.getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(opened.getNetUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(opened.getNetMonthly()).isEqualByComparingTo("1000.00");
        assertThat(fixture.binds).hasSize(1);
        assertThat(fixture.binds.get(0).periodId()).isEqualTo(opened.getId());
        assertThat(fixture.binds.get(0).revisionNo()).isEqualTo(1);
    }

    @Test
    void removeOpenEndedSourceAppliesAndClosesTheDayBefore() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 1L, START, END);
        fixture.unit(1, subscription, 1L);
        fixture.line(1, ActionCode.REMOVE, "line-source", BigDecimal.ZERO, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openEndedPeriod(
                subscription, "line-source", new BigDecimal("4.0000"), START, END);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.periods).containsExactly(source);
        assertThat(fixture.binds).isEmpty();
        assertThat(source.getEndDate()).isEqualTo(END);
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(source.getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");
        assertThat(subscription.getCommercialRevision()).isEqualTo(2L);
    }

    @Test
    void nullEffectiveEndMatchesWhenSoldEndIsAlreadyPast() {
        LocalDate soldEnd = LocalDate.of(2026, 2, 1);
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openEndedPeriod(subscription, "line-source", ORIGINAL, START, soldEnd);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(source.getEndDate()).isEqualTo(soldEnd);
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");
        SubscriptionRatePeriod opened = opened(fixture, source);
        assertThat(opened.getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(opened.getNetMonthly()).isEqualByComparingTo("1000.00");
        assertThat(opened.getNetUnitPrice()).isEqualByComparingTo("10.0000");
    }

    @Test
    void effectiveDateAtOpenEndedStartRetiresTheSource() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, START, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openEndedPeriod(subscription, "line-source", ORIGINAL, START, END);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(source.getEffectiveEndDate()).isEqualTo(START.minusDays(1));
        assertThat(source.isRetired()).isTrue();
        assertThat(source.getQuantity()).isEqualByComparingTo(ORIGINAL);
        assertThat(opened(fixture, source).getStartDate()).isEqualTo(START);
        assertThat(opened(fixture, source).getQuantity()).isEqualByComparingTo(UPDATED);
    }

    @Test
    void retiredOpenEndedSourceStillBlocksWithV4() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openEndedPeriod(subscription, "line-source", ORIGINAL, START, END);
        source.setRetired(true);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).containsExactly(source);
        assertThat(source.getEffectiveEndDate()).isNull();
        assertThat(source.isRetired()).isTrue();
        assertThat(fixture.events).anyMatch(event -> "V4".equals(event.reasonCode()));
        assertThat(subscription.getCommercialRevision()).isEqualTo(2L);
    }

    @Test
    void boundedSourceThatEndedBeforeTheEffectiveDateStillBlocksWithV4() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.REMOVE, "line-source", BigDecimal.ZERO, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openPeriod(subscription, "line-source", ORIGINAL, START, END);
        source.setEffectiveEndDate(DAY.minusDays(1));

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.ledger).isEmpty();
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(fixture.events).anyMatch(event -> "V4".equals(event.reasonCode()));
    }

    private static SubscriptionRatePeriod opened(ApplyFixture fixture, SubscriptionRatePeriod source) {
        return fixture.periods.stream()
                .filter(period -> period != source)
                .findFirst()
                .orElseThrow();
    }
}
