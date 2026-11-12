package com.opencrm.contract.signature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.opencrm.contract.signature.SignatureEnvelopeStatus.COMPLETED;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.DECLINED;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.DRAFT;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.EXPIRED;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.SENT;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.VIEWED;
import static com.opencrm.contract.signature.SignatureEnvelopeStatus.VOIDED;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DRG-457: SignatureEnvelopeStatus state machine")
class SignatureEnvelopeStatusTest {

    @Test
    @DisplayName("DRAFT can only move to SENT or VOIDED")
    void draftTransitions() {
        assertThat(DRAFT.validTransitionTo(SENT)).isTrue();
        assertThat(DRAFT.validTransitionTo(VOIDED)).isTrue();
        assertThat(DRAFT.validTransitionTo(COMPLETED)).isFalse();
        assertThat(DRAFT.validTransitionTo(VIEWED)).isFalse();
    }

    @Test
    @DisplayName("SENT can reach any terminal state, or VIEWED")
    void sentTransitions() {
        assertThat(SENT.validTransitionTo(VIEWED)).isTrue();
        assertThat(SENT.validTransitionTo(COMPLETED)).isTrue();
        assertThat(SENT.validTransitionTo(VOIDED)).isTrue();
        assertThat(SENT.validTransitionTo(DECLINED)).isTrue();
        assertThat(SENT.validTransitionTo(EXPIRED)).isTrue();
        assertThat(SENT.validTransitionTo(DRAFT)).isFalse();
    }

    @Test
    @DisplayName("VIEWED can reach a terminal state but not back to DRAFT or SENT")
    void viewedTransitions() {
        assertThat(VIEWED.validTransitionTo(COMPLETED)).isTrue();
        assertThat(VIEWED.validTransitionTo(VOIDED)).isTrue();
        assertThat(VIEWED.validTransitionTo(DRAFT)).isFalse();
        assertThat(VIEWED.validTransitionTo(SENT)).isFalse();
    }

    @ParameterizedTest(name = "{0} is terminal and accepts no further transition, including to itself")
    @EnumSource(value = SignatureEnvelopeStatus.class, names = {"COMPLETED", "VOIDED", "DECLINED", "EXPIRED"})
    @DisplayName("every terminal status rejects every transition")
    void terminalStatusesAcceptNoTransition(SignatureEnvelopeStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        for (SignatureEnvelopeStatus candidate : SignatureEnvelopeStatus.values()) {
            assertThat(terminal.validTransitionTo(candidate))
                    .as("%s -> %s", terminal, candidate)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("DRAFT, SENT and VIEWED are not terminal")
    void nonTerminalStatuses() {
        assertThat(DRAFT.isTerminal()).isFalse();
        assertThat(SENT.isTerminal()).isFalse();
        assertThat(VIEWED.isTerminal()).isFalse();
    }
}
