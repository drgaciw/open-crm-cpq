package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventConsumerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ErpAdapter erpAdapter;

    private ObjectMapper objectMapper;
    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new QuoteAcceptedEventConsumer(subscriptionRepository, erpAdapter, objectMapper);
    }

    @Test
    @DisplayName("should create and activate one subscription for an accepted quote")
    void createsAndActivatesSubscription() throws Exception {
        when(subscriptionRepository.existsBySubscriptionNumber("SUB-Q-001")).thenReturn(false);
        when(subscriptionRepository.saveAndFlush(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(erpAdapter.syncToErp(any(Subscription.class))).thenReturn("ERP-123");

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).saveAndFlush(captor.capture());
        Subscription created = captor.getValue();
        assertEquals("SUB-Q-001", created.getSubscriptionNumber());
        assertEquals("Q-001", created.getQuoteId());
        assertEquals("C-001", created.getCustomerId());
        assertEquals(BigDecimal.valueOf(1000), created.getArr());
        assertEquals("USD", created.getCurrencyCode());

        verify(erpAdapter).syncToErp(created);
        assertEquals(SubscriptionStatus.ACTIVE, created.getStatus());
        assertEquals("SYNCED", created.getErpSyncStatus());
        assertEquals("ERP-123", created.getErpSubscriptionId());
    }

    @Test
    @DisplayName("should skip duplicate accepted quote events found before insert")
    void skipsExistingSubscriptionActivation() throws Exception {
        when(subscriptionRepository.existsBySubscriptionNumber("SUB-Q-001")).thenReturn(true);

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
        verify(erpAdapter, never()).syncToErp(any(Subscription.class));
    }

    @Test
    @DisplayName("should skip duplicate accepted quote events rejected by unique key")
    void skipsConcurrentDuplicateSubscriptionActivation() throws Exception {
        when(subscriptionRepository.existsBySubscriptionNumber("SUB-Q-001")).thenReturn(false, true);
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate subscription number"));

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        verify(erpAdapter, never()).syncToErp(any(Subscription.class));
    }

    @Test
    @DisplayName("should fail data integrity errors that are not duplicate accepted quote events")
    void failsNonDuplicateDataIntegrityError() throws Exception {
        when(subscriptionRepository.existsBySubscriptionNumber("SUB-Q-001")).thenReturn(false);
        when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenThrow(new DataIntegrityViolationException("not duplicate"));

        assertThrows(RuntimeException.class, () -> consumer.handleQuoteAccepted(message("Q-001", "C-001")));

        verify(erpAdapter, never()).syncToErp(any(Subscription.class));
    }

    private String message(String quoteId, String customerId) throws Exception {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent(
                quoteId,
                Instant.parse("2026-05-12T01:00:00Z"),
                customerId,
                BigDecimal.valueOf(1000),
                "USD"
        );
        return objectMapper.writeValueAsString(event);
    }
}
