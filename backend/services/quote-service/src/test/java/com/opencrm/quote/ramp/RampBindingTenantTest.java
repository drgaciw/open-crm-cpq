package com.opencrm.quote.ramp;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.quote.Quote;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Security H4: approval-routing refuses decisions on a row with no tenant, so every ramp binding must carry the
 * quote's tenant. The guard is the one quote-owned producer (ADR-018 D9), so it stamps the tenant for every caller.
 */
@Tag("unit")
class RampBindingTenantTest {

    private final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
    private final RampScheduleGuard guard = new RampScheduleGuard(mock(QuoteLineItemRepository.class),
            mock(QuoteRampStepRepository.class), new RampScheduleValidator(), new RampPricingProperties(),
            mock(ApprovalRoutingClient.class), outbox);

    @Test
    void bindingCarriesTheQuoteTenant() {
        Quote quote = Quote.builder().tenantId("tenant-a").build();
        quote.setId(7L);

        guard.publishBinding(quote, event());

        assertThat(published().getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void quoteWithoutTenantPublishesNoTenantSoApprovalRoutingFailsClosed() {
        Quote quote = Quote.builder().build();
        quote.setId(7L);

        guard.publishBinding(quote, event());

        assertThat(published().getTenantId()).isNull();
    }

    private DiscountApprovalRequiredEvent published() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq("Quote"), eq("7"), eq(EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1),
                payload.capture());
        return (DiscountApprovalRequiredEvent) payload.getValue();
    }

    private static DiscountApprovalRequiredEvent event() {
        return new DiscountApprovalRequiredEvent("7", "10", BigDecimal.ZERO, new BigDecimal("0.32"), "USD",
                "a".repeat(64), 1, "SALES_VP", "Ramp period 1", true);
    }
}
