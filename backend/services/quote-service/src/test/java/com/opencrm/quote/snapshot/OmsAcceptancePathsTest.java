package com.opencrm.quote.snapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.client.*;
import com.opencrm.quote.event.QuoteEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class OmsAcceptancePathsTest {
    @ParameterizedTest @EnumSource(value=QuoteStatus.class, names={"SENT","DRAFT","PENDING_APPROVAL"})
    void bothPathsPersistOwnershipSnapshotAndFenceLegacyEvent(QuoteStatus status) {
        var repo = mock(QuoteRepository.class); var lines = mock(QuoteLineItemRepository.class);
        var snapshots = mock(AcceptedSnapshotRepository.class); var outbox = mock(OutboxEventPublisher.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var service = new QuoteService(repo, mock(QuoteMapper.class), mock(QuoteNumberGenerator.class),
            mock(QuoteEventPublisher.class), lines, mock(PricingServiceClient.class), mock(ProductServiceClient.class), mapper, outbox);
        var props = new OmsQuoteProperties(); props.setEnabled(true); props.setScopeId("pilot"); props.setCustomerIds(Set.of("a"));
        ReflectionTestUtils.setField(service, "acceptedSnapshotService", new AcceptedSnapshotService(snapshots,outbox,mapper,props));
        var quote = Quote.builder().id(1L).uuid(UUID.randomUUID().toString()).quoteNumber("Q1").customerExternalId("a")
            .status(status).subtotal(new BigDecimal("50.00")).totalAmount(new BigDecimal("50.00")).build();
        var line = QuoteLineItem.builder().uuid("line").quantity(1).unitPrice(new BigDecimal("50.00"))
            .extendedAmount(new BigDecimal("50.00")).build();
        quote.getLineItems().add(line);
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote)); when(repo.save(quote)).thenReturn(quote);
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        if (status == QuoteStatus.SENT) service.accept(1L); else service.acceptQuote(1L);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(quote.getTotalTax()).isEqualByComparingTo("0");
        assertThat(quote.getFulfillmentOwner()).isEqualTo("OMS"); assertThat(quote.getAcceptedRevision()).isEqualTo(1);
        verify(snapshots).save(any());
        verify(outbox).publish(eq("Quote"),eq("Q1"),eq("cpq.quote.status.accepted"),argThat(e -> e instanceof QuoteAcceptedEvent a && "OMS".equals(a.getFulfillmentOwner())));
    }
    @Test void acceptedOmsQuoteCannotBeRepriced() {
        var repo = mock(QuoteRepository.class);
        var quote = Quote.builder().id(1L).fulfillmentOwner("OMS").status(QuoteStatus.ACCEPTED).build();
        when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        var service = new QuoteService(repo, mock(QuoteMapper.class), mock(QuoteNumberGenerator.class), mock(QuoteEventPublisher.class),
            mock(QuoteLineItemRepository.class), mock(PricingServiceClient.class), mock(ProductServiceClient.class), new ObjectMapper(), mock(OutboxEventPublisher.class));
        assertThatThrownBy(() -> service.calculate(1L)).hasMessageContaining("accepted OMS");
    }
}
