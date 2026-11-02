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
 * AC-651-1 through AC-651-4. A missing start date blocks the line before any write.
 * Amounts on a dated line are copied, never repriced.
 */
@Tag("unit")
class DocumentApplyNullStartDateTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
    private static final BigDecimal ORIGINAL = new BigDecimal("100.0000");
    private static final BigDecimal UPDATED = new BigDecimal("80.0000");
    private static final String START_DATE_MISSING = "START_DATE_MISSING";
    private static final String BLOCK_DETAIL =
            "apply:START_DATE_MISSING:the change line or source period has no start date";

    @Test
    void addWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.ADD);
    }

    @Test
    void updateWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.UPDATE);
    }

    @Test
    void removeWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.REMOVE);
    }

    @Test
    void upgradeWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.UPGRADE);
    }

    @Test
    void swapWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.SWAP);
    }

    @Test
    void terminateWithNullEffectiveFromIsBlocked() {
        assertNullEffectiveFromBlocks(ActionCode.TERMINATE);
    }

    @Test
    void nullSourceStartDateBlocksWithoutLedger() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openPeriod(subscription, "line-source", ORIGINAL, START, END);
        source.setStartDate(null);

        ApplyResult result = fixture.apply();

        assertBlocked(fixture, result, subscription, 5L);
        assertThat(fixture.periods).containsExactly(source);
        assertThat(fixture.binds).isEmpty();
        assertThat(source.getStartDate()).isNull();
        assertThat(source.getEffectiveEndDate()).isEqualTo(END);
        assertThat(source.isRetired()).isFalse();
        assertThat(source.getQuantity()).isEqualByComparingTo(ORIGINAL);
        assertThat(source.getNetUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");
    }

    @Test
    void datedUpdateStillCopiesAmountsAndDoesNotReprice() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 5L, START, END);
        fixture.unit(1, subscription, 5L);
        fixture.line(1, ActionCode.UPDATE, "line-source", UPDATED, DAY, END, "pb-1");
        SubscriptionRatePeriod source = fixture.openPeriod(subscription, "line-source", ORIGINAL, START, END);

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(subscription.getCommercialRevision()).isEqualTo(6L);
        assertThat(source.getEffectiveEndDate()).isEqualTo(DAY.minusDays(1));
        assertThat(source.isRetired()).isFalse();
        assertThat(source.getQuantity()).isEqualByComparingTo(ORIGINAL);
        assertThat(source.getNetUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");
        SubscriptionRatePeriod opened = fixture.periods.stream()
                .filter(period -> period != source)
                .findFirst()
                .orElseThrow();
        assertThat(opened.getStartDate()).isEqualTo(DAY);
        assertThat(opened.getQuantity()).isEqualByComparingTo(UPDATED);
        assertThat(opened.getNetUnitPrice()).isEqualByComparingTo("10.0000");
        assertThat(opened.getNetMonthly()).isEqualByComparingTo("1000.00");
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(1);
    }

    @Test
    void replayAfterNullStartBlockIsIdempotent() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, null, END, "pb-1");

        ApplyResult first = fixture.apply();
        int events = fixture.events.size();
        ApplyResult second = fixture.apply();

        assertBlocked(fixture, first, subscription, 3L);
        assertThat(fixture.periods).isEmpty();
        assertThat(second.replayed()).isTrue();
        assertThat(second.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(second.unitOutcomes()).isEqualTo(first.unitOutcomes());
        assertThat(fixture.events).hasSize(events);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isZero();
        assertThat(fixture.ledger).isEmpty();
        assertThat(subscription.getCommercialRevision()).isEqualTo(3L);
    }

    private static void assertNullEffectiveFromBlocks(ActionCode action) {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 4L, START, END);
        fixture.unit(1, subscription, 4L);
        boolean needsSource = action != ActionCode.ADD;
        fixture.line(1, action, needsSource ? "line-source" : null, new BigDecimal("2.0000"), null, END, "pb-1");
        SubscriptionRatePeriod source = needsSource
                ? fixture.openPeriod(subscription, "line-source", ORIGINAL, START, END)
                : null;

        ApplyResult result = fixture.apply();

        assertBlocked(fixture, result, subscription, 4L);
        assertThat(fixture.binds).isEmpty();
        if (source == null) {
            assertThat(fixture.periods).isEmpty();
        } else {
            assertThat(fixture.periods).containsExactly(source);
            assertThat(source.getStartDate()).isEqualTo(START);
            assertThat(source.getEffectiveEndDate()).isEqualTo(END);
            assertThat(source.isRetired()).isFalse();
            assertThat(source.getQuantity()).isEqualByComparingTo(ORIGINAL);
            assertThat(source.getNetUnitPrice()).isEqualByComparingTo("10.0000");
            assertThat(source.getNetMonthly()).isEqualByComparingTo("1000.00");
        }
    }

    private static void assertBlocked(ApplyFixture fixture, ApplyResult result, Subscription subscription,
                                      long revision) {
        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(result.unitOutcomes()).containsExactly(
                new ApplyResult.UnitOutcome(1, ApplicationUnitStatus.BLOCKED.name(), START_DATE_MISSING));
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(0).getBlockReason()).isEqualTo(START_DATE_MISSING);
        assertThat(fixture.ledger).isEmpty();
        assertThat(subscription.getCommercialRevision()).isEqualTo(revision);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isZero();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.events)
                .filteredOn(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.reasonCode()).isEqualTo(START_DATE_MISSING);
                    assertThat(event.payloadRef()).isEqualTo(BLOCK_DETAIL);
                });
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "APPLY_BLOCKED").count())
                .isEqualTo(1);
    }
}
