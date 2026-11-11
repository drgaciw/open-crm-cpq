package com.opencrm.contract.service;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-663: QuoteAcceptedEvent.customerId is a String external id — never parse into
 * subscriptions.customer_account_id at ingest. Typed key stays NULL until a uuid account source exists.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcceptedQuoteCustomerAccountIngestTest {

    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;

    AcceptedQuoteSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new AcceptedQuoteSubscriptionService(subscriptions, rates, ledger, new AcceptedRampValidator(),
                AcceptedRampValidatorTest.MAPPER, jdbc);
        when(subscriptions.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            if (subscription.getId() == null) {
                subscription.setId(5L);
            }
            return subscription;
        });
    }

    @Test
    void ingestLeavesCustomerAccountIdNullEvenWhenCustomerIdLooksLikeUuid() throws Exception {
        QuoteAcceptedEvent event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.getLineItems().getFirst().setRamp(null);
        event.getLineItems().getFirst().setProductType(null);
        String uuidLooking = UUID.randomUUID().toString();
        event.setCustomerId(uuidLooking);
        if (event.getCustomer() != null) {
            event.getCustomer().setCustomerId(uuidLooking);
        }

        service.apply(event);

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getCustomerId()).isEqualTo(uuidLooking);
        assertThat(captured.getValue().getCustomerAccountId()).isNull();
    }
}
