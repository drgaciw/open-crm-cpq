package com.opencrm.contract.service;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.AppliedQuoteAcceptance;
import com.opencrm.contract.domain.RenewalMode;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DRG-664 AC4 / AC5 — quote-accepted renewal math is unchanged when {@code renewal_mode}
 * is null or unknown; the policy is read and fail-safes to no auto-roll.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionRenewalModeNullKeepsRenewalMathTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;

    AcceptedQuoteSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new AcceptedQuoteSubscriptionService(subscriptions, rates, ledger, new AcceptedRampValidator(),
                AcceptedRampValidatorTest.MAPPER, jdbc);
        when(subscriptions.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void nullRenewalModeKeepsQuoteRenewalEndDateMathAndDoesNotAutoRoll() throws Exception {
        var event = AcceptedQuoteSubscriptionServiceTest.expansion();
        event.getDeal().setDealType("RENEWAL");
        event.getTerms().setStartDate("2030-01-01");
        event.getTerms().setEndDate("2030-12-31");
        event.getTerms().setTermMonths(12);
        var line = event.getLineItems().getFirst();
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        line.setQuantity(50);
        line.setExtendedAmount(new BigDecimal("2700"));
        line.setRamp(List.of(QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(1).startMonth(1).months(12)
                .quantity(50).netUnitPrice(new BigDecimal("54")).netMonthly(new BigDecimal("2700"))
                .periodAmount(new BigDecimal("32400")).build()));
        event.setTotalAmount(new BigDecimal("2700"));
        event.getPricing().setTotalAmount(event.getTotalAmount());
        event.getPricing().setTcv(new BigDecimal("32400"));
        event.getPricing().setAcv(new BigDecimal("32400"));
        event.getPricing().setArr(new BigDecimal("32400"));
        event.getPricing().setExitArr(new BigDecimal("32400"));

        Subscription target = Subscription.builder().id(42L).subscriptionNumber("SUB-OLD")
                .customerId(event.getCustomerId()).quoteId("old-quote").planName("Named seat")
                .startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2029, 12, 31))
                .status(SubscriptionStatus.ACTIVE).currencyCode("USD").commercialRevision(3L)
                .renewalMode(null)
                .mrr(new BigDecimal("2250")).arr(new BigDecimal("27000")).build();
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        SubscriptionRatePeriod oldExit = SubscriptionRatePeriod.builder().subscriptionId(42L)
                .acceptedLineUuid("11111111-1111-4111-8111-000000000201").sequence(3)
                .startDate(LocalDate.of(2029, 1, 1)).endDate(LocalDate.of(2029, 12, 31))
                .effectiveEndDate(LocalDate.of(2029, 12, 31))
                .billingAnchorDate(LocalDate.of(2027, 1, 1)).quantity(new BigDecimal("50"))
                .netUnitPrice(new BigDecimal("50")).netMonthly(new BigDecimal("2500"))
                .annualRunRate(new BigDecimal("30000")).applied(true).build();
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L))
                .thenReturn(List.of(oldExit));
        AtomicReference<AppliedQuoteAcceptance> saved = new AtomicReference<>();
        when(ledger.findByAcceptedQuoteId(any())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(ledger.save(any())).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get();
        });

        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(target)).isFalse();

        service.apply(event);

        // Today's renewal math: end advances to the accepted term end — not an auto-roll invent.
        assertThat(target.getEndDate()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThat(target.getRenewalDate()).isEqualTo(LocalDate.of(2030, 12, 1));
        assertThat(target.getRenewalMode()).isNull();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(target.getRenewalMode())).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll("GARBAGE")).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(RenewalMode.MANUAL)).isFalse();
    }
}
