package com.opencrm.quote.quote;

import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStepRepository;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.quote.ramp.RampScheduleValidator;

import static org.mockito.Mockito.mock;

/** Guard with the ramp flag off, for existing QuoteService unit tests. */
public final class RampScheduleTestSupport {

    private RampScheduleTestSupport() {
    }

    public static RampScheduleGuard guard(QuoteLineItemRepository lineItems) {
        return guard(lineItems, org.mockito.Mockito.mock(com.opencrm.common.event.OutboxEventPublisher.class));
    }

    public static RampScheduleGuard guard(QuoteLineItemRepository lineItems, com.opencrm.common.event.OutboxEventPublisher outbox) {
        return new RampScheduleGuard(
                lineItems,
                mock(QuoteRampStepRepository.class),
                new RampScheduleValidator(),
                new RampPricingProperties(), org.mockito.Mockito.mock(com.opencrm.quote.client.ApprovalRoutingClient.class), outbox);
    }
}
