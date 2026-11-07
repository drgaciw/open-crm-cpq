package com.opencrm.contract.service;

import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionModel;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-666 AC4 / AC4a — unknown product type on a NEW line is rejected at ingest with
 * {@code SUBSCRIPTION_INGEST_REJECTED}/{@code UNKNOWN_SUBSCRIPTION_MODEL}, non-retryable,
 * no subscription / rate period / ledger row, and an audit entry with those exact codes.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionModelIngestRejectionTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;
    @Mock SubscriptionIngestAuditor auditor;

    AcceptedQuoteSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new AcceptedQuoteSubscriptionService(subscriptions, rates, ledger, new AcceptedRampValidator(),
                AcceptedRampValidatorTest.MAPPER, jdbc);
        service.setIngestAuditor(auditor);
        when(subscriptions.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            if (subscription.getId() == null) {
                subscription.setId(5L);
            }
            return subscription;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAAS_USAGE_METERED", "SAAS_USAGE", "SAAS_CREDITS", "ONPREM_PERPETUAL"})
    void unknownProductTypeOnNewLineIsRejectedWithAuditAndWritesNothing(String type) throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setLineItemType("SERVICE");
        line.setProductType(type);

        assertThatThrownBy(() -> service.apply(event))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining(SubscriptionIngestAuditor.AUDIT_CODE)
                .hasMessageContaining(SubscriptionIngestAuditor.REASON_UNKNOWN_MODEL);

        verify(auditor).record(eq(event.getQuoteId()), eq(SubscriptionIngestAuditor.AUDIT_CODE),
                eq(SubscriptionIngestAuditor.REASON_UNKNOWN_MODEL), eq(type), any());
        verify(subscriptions, never()).save(any());
        verify(rates, never()).saveAll(any());
        verify(ledger, never()).save(any());
    }

    @Test
    void unknownRejectionIsAllOrNothingAcrossLinesInTheSameEvent() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var good = event.getLineItems().getFirst();
        good.setRamp(null);
        good.setLineItemType("SERVICE");
        good.setProductType("SAAS_FLAT_RATE");

        var bad = QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid("11111111-1111-4111-8111-000000000299")
                .productExternalId("bad-product")
                .productName("Bad line")
                .productType("SAAS_USAGE_METERED")
                .lineItemType("SERVICE")
                .billingFrequency("MONTHLY")
                .quantity(1)
                .extendedAmount(good.getExtendedAmount())
                .build();
        event.setLineItems(java.util.List.of(good, bad));

        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class);
        verify(subscriptions, never()).save(any());
        verify(rates, never()).saveAll(any());
        verify(ledger, never()).save(any());
        verify(auditor).record(eq(event.getQuoteId()), eq(SubscriptionIngestAuditor.AUDIT_CODE),
                eq(SubscriptionIngestAuditor.REASON_UNKNOWN_MODEL), eq("SAAS_USAGE_METERED"), any());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "SAAS_FLAT_RATE, SAAS_RECURRING",
            "ONPREM_SUBSCRIPTION, ONPREM_TRANSACTIONAL",
            "SUPPORT_BASIC, SAAS_RECURRING",
            "SUPPORT_PREMIUM, SAAS_RECURRING",
            "SUPPORT_ENTERPRISE, SAAS_RECURRING"
    })
    void mappedAllowListProductSetsSubscriptionModelOnNewSale(String type, SubscriptionModel expected) throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setLineItemType("SERVICE");
        line.setProductType(type);

        service.apply(event);

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getSubscriptionModel()).isEqualTo(expected);
        verify(auditor, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void typedSaasSeatNamedSetsSaasRecurringModel() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        service.apply(event);

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getSubscriptionModel()).isEqualTo(SubscriptionModel.SAAS_RECURRING);
    }
}
