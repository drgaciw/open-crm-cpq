package com.opencrm.contract.signature;

import java.util.EnumSet;
import java.util.Set;

/**
 * DRG-457 / BC-06-04. Provider-agnostic lifecycle of a signature envelope.
 *
 * <p>This is the state machine that makes {@code GetEnvelopeStatus} idempotent under duplicate delivery: a webhook
 * or poll result is applied through {@link #validTransitionTo(SignatureEnvelopeStatus)}, so a stale or repeated
 * result that would move the envelope backwards, or past a terminal state, is rejected rather than silently
 * re-applied. Combined with the {@code signature_envelope_events} dedupe ledger (one row per
 * {@code (envelope_id, provider_event_id)}), a redelivered webhook neither double-transitions the envelope nor
 * double-publishes {@code SignatureCompleted}.
 */
public enum SignatureEnvelopeStatus {
    /** Created locally, not yet sent to the provider. */
    DRAFT,
    /** Sent to the provider; awaiting signers. */
    SENT,
    /** At least one signer has opened the envelope, not yet fully signed. */
    VIEWED,
    /** Terminal: every required signer has signed. */
    COMPLETED,
    /** Terminal: voided before completion (VoidEnvelope). */
    VOIDED,
    /** Terminal: a signer explicitly declined to sign. */
    DECLINED,
    /** Terminal: the provider's signing window elapsed before completion. */
    EXPIRED;

    private static final Set<SignatureEnvelopeStatus> TERMINAL =
            EnumSet.of(COMPLETED, VOIDED, DECLINED, EXPIRED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Whether this status may move to {@code next}. A terminal status never moves again (including to itself),
     * which is what makes re-applying the same terminal result from a redelivered webhook or a repeated poll safe
     * to just no-op rather than an error: callers check {@link #isTerminal()} first and skip the transition instead
     * of calling this for an already-terminal envelope.
     */
    public boolean validTransitionTo(SignatureEnvelopeStatus next) {
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case DRAFT -> next == SENT || next == VOIDED;
            case SENT -> next == VIEWED || next == COMPLETED || next == VOIDED || next == DECLINED
                    || next == EXPIRED;
            case VIEWED -> next == COMPLETED || next == VOIDED || next == DECLINED || next == EXPIRED;
            default -> false;
        };
    }
}
