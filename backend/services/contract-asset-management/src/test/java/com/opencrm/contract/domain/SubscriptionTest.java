package com.opencrm.contract.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for Subscription entity lifecycle.
 * Validates state machine transitions: PENDING → ACTIVE → SUSPENDED/CANCELLED.
 */
@DisplayName("Subscription - Lifecycle State Machine")
class SubscriptionTest {

    private Subscription subscription;

    @BeforeEach
    void setUp() {
        subscription = Subscription.builder()
                .subscriptionNumber("SUB-001")
                .customerId("C-001")
                .quoteId("Q-001")
                .status(SubscriptionStatus.PENDING)
                .planName("Enterprise Plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .renewalDate(LocalDate.now().plusYears(1).minusDays(30))
                .arr(BigDecimal.valueOf(12000.00))
                .currencyCode("USD")
                .build();
    }

    @Test
    @DisplayName("should activate a PENDING subscription")
    void activatePending() {
        subscription.activate();
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    }

    @Test
    @DisplayName("should reject activating a non-PENDING subscription")
    void rejectActivateNonPending() {
        subscription.activate();
        assertThrows(IllegalStateException.class, () -> subscription.activate());
    }

    @Test
    @DisplayName("should suspend an ACTIVE subscription")
    void suspendActive() {
        subscription.activate();
        subscription.suspend();
        assertEquals(SubscriptionStatus.SUSPENDED, subscription.getStatus());
    }

    @Test
    @DisplayName("should reject suspending a non-ACTIVE subscription")
    void rejectSuspendNonActive() {
        assertThrows(IllegalStateException.class, () -> subscription.suspend());
    }

    @Test
    @DisplayName("should cancel any subscription")
    void cancel() {
        subscription.cancel();
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"PENDING", "ACTIVE", "SUSPENDED"})
    void cancelAllowedCommercialStates(SubscriptionStatus initial) {
        subscription.setStatus(initial);
        subscription.cancel();
        assertEquals(SubscriptionStatus.CANCELLED, subscription.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"CANCELLED", "EXPIRED"})
    void rejectsCancellationOfTerminalStates(SubscriptionStatus initial) {
        subscription.setStatus(initial);
        assertThrows(IllegalStateException.class, subscription::cancel);
        assertEquals(initial, subscription.getStatus());
    }

    @Test
    @DisplayName("should use Money-compatible BigDecimal for ARR")
    void arrIsBigDecimal() {
        assertNotNull(subscription.getArr());
        assertEquals(BigDecimal.valueOf(12000.00), subscription.getArr());
    }
}
