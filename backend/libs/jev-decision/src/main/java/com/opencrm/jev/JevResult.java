package com.opencrm.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.jev.answer.JevAnswer;

import java.util.Map;
import java.util.Optional;

/**
 * Outcome of one decision request. Always returned, never thrown: advisory code must not be able to
 * break the operation it advises.
 *
 * @param answers validated answers by question id; empty unless status is OK or ABSTAINED
 * @param confidence weakest answer confidence, or 0 when there are no answers
 * @param errorClass simple class name of the failure, for the decision log; null on success
 */
public record JevResult(
        Status status,
        Map<String, JevAnswer> answers,
        double confidence,
        long latencyMs,
        int attempts,
        JsonNode usage,
        String errorClass,
        String model,
        String rubricVersion,
        String transport,
        String wireFormat) {

    public enum Status {
        OK,
        /** Globally off, use case off, or the transport is the no-op. */
        DISABLED,
        /** Valid answers, but below the use case minimum confidence. Log it; do not surface it. */
        ABSTAINED,
        /** The upstream replied but the answer broke the contract. */
        INVALID,
        ERROR,
        TIMEOUT,
        /** The daily call budget is spent. */
        BUDGET
    }

    public JevResult {
        answers = Map.copyOf(answers);
    }

    public boolean usable() {
        return status == Status.OK;
    }

    public <T extends JevAnswer> Optional<T> answer(String questionId, Class<T> type) {
        JevAnswer answer = answers.get(questionId);
        return type.isInstance(answer) ? Optional.of(type.cast(answer)) : Optional.empty();
    }
}
