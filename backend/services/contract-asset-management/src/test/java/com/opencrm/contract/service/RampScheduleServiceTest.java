package com.opencrm.contract.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RampScheduleServiceTest {
    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository periods;
    RampScheduleService service;
    Subscription subscription;

    @BeforeEach void setUp() {
        service = new RampScheduleService(subscriptions, periods);
        subscription = Subscription.builder().id(42L).subscriptionNumber("SUB-RAMP").customerId("C-1")
                .currencyCode("USD").commercialRevision(7L).status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.of(2027,1,1)).endDate(LocalDate.of(2029,12,31)).build();
    }

    @ParameterizedTest @CsvSource({"2027-12-31,2000", "2028-01-01,2250", "2029-12-31,2500", "2030-01-01,0"})
    void inclusiveBoundariesReturnAcceptedHistoricalRate(String asOf, String expected) {
        when(subscriptions.findBySubscriptionNumberAndDeletedFalse("SUB-RAMP")).thenReturn(Optional.of(subscription));
        when(periods.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L))
                .thenReturn(List.of(period(1, "2027-01-01", "2027-12-31", "2000"),
                        period(2, "2028-01-01", "2028-12-31", "2250"), period(3, "2029-01-01", "2029-12-31", "2500")));
        var result = service.byNumber("SUB-RAMP", LocalDate.parse(asOf));
        assertThat(result.activeMrr()).isEqualByComparingTo(expected);
        assertThat(result.exitMrr()).isEqualByComparingTo("2500");
        assertThat(result.commercialRevision()).isEqualTo(7L);
        assertThat(result.lines().getFirst().billingAnchorDate()).isEqualTo(subscription.getStartDate());
    }

    @Test void unknownOrDeletedSubscriptionIsNotExposed() {
        when(subscriptions.findById(42L)).thenReturn(Optional.of(subscription));
        subscription.setDeleted(true);
        assertThatThrownBy(() -> service.byId(42L, LocalDate.of(2028,1,1))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.byNumber("missing", null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(periods);
    }

    @Test void legacyAggregateNeverInventsAUnitOrQuantity() {
        when(subscriptions.findById(42L)).thenReturn(Optional.of(subscription));
        var legacy = period(1, "2027-01-01", "2029-12-31", "100");
        legacy.setProvenance("LEGACY_AGGREGATE");
        when(periods.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(legacy));
        var result = service.byId(42L, LocalDate.of(2028,1,1));
        assertThat(result.lines().getFirst().periods().getFirst().netUnitPrice()).isNull();
        assertThat(result.lines().getFirst().periods().getFirst().quantity()).isNull();
        assertThat(result.lines().getFirst().provenance()).isEqualTo("LEGACY_AGGREGATE");
    }

    private SubscriptionRatePeriod period(int sequence, String start, String end, String monthly) {
        return SubscriptionRatePeriod.builder().subscriptionId(42L).acceptedLineUuid("line-1").productName("Seat")
                .sequence(sequence).startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).effectiveEndDate(LocalDate.parse(end))
                .billingAnchorDate(subscription.getStartDate()).netMonthly(new BigDecimal(monthly))
                .annualRunRate(new BigDecimal(monthly).multiply(BigDecimal.valueOf(12))).provenance("ACCEPTED_PRICED").build();
    }
}
