package com.opencrm.contract.service;

import com.opencrm.contract.domain.RenewalMode;
import com.opencrm.contract.domain.Subscription;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-664 AC5 — NULL / unknown {@code renewal_mode} keeps today's renewal behaviour
 * (fail-safe, no auto-roll). Only explicit {@code AUTO_ROLL} opens the gate.
 */
@Tag("unit")
class SubscriptionRenewalModePolicyTest {

    @Test
    void nullModeKeepsTodaysNoAutoRollBehaviour() {
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll((RenewalMode) null)).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll((Subscription) null)).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(
                Subscription.builder().subscriptionNumber("SUB-1").customerId("c").quoteId("q")
                        .planName("p").startDate(java.time.LocalDate.of(2027, 1, 1))
                        .endDate(java.time.LocalDate.of(2027, 12, 31)).renewalMode(null).build()))
                .isFalse();
    }

    @Test
    void manualModeDoesNotAutoRoll() {
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(RenewalMode.MANUAL)).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll("MANUAL")).isFalse();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll("manual")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "UNKNOWN", "EVERGREEN", "auto_roll", "AUTO-ROLL", "RENEW"})
    void unknownOrBlankRawTokenKeepsFailSafeNoAutoRoll(String raw) {
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(raw)).isFalse();
    }

    @Test
    void explicitAutoRollOpensTheGate() {
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(RenewalMode.AUTO_ROLL)).isTrue();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll("AUTO_ROLL")).isTrue();
        assertThat(SubscriptionRenewalModePolicy.allowsAutoRoll(
                Subscription.builder().subscriptionNumber("SUB-2").customerId("c").quoteId("q")
                        .planName("p").startDate(java.time.LocalDate.of(2027, 1, 1))
                        .endDate(java.time.LocalDate.of(2027, 12, 31))
                        .renewalMode(RenewalMode.AUTO_ROLL).build()))
                .isTrue();
    }
}
