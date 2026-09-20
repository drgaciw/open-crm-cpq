package com.opencrm.jev.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.state.JevState;

import java.util.List;

/**
 * How a decision request reaches a Jev upstream. ADR-015 leaves the production upstream open, so callers
 * depend on this port and the default implementation does nothing.
 */
public interface JevTransport {

    /** Recorded on every decision row. */
    String name();

    /** Wire format label recorded on every decision row; "none" when nothing is sent. */
    String wireFormat();

    /** False means no request will ever be made; the client short-circuits to DISABLED. */
    boolean enabled();

    /** @throws com.opencrm.jev.error.JevException on any failure */
    Response decide(Request request);

    record Request(String useCase, String model, JevState state, List<JevQuestion> questions) {
        public Request {
            questions = List.copyOf(questions);
        }
    }

    /**
     * @param answers object keyed by question id, not yet validated
     * @param usage token or cost accounting if the upstream reports it, else null
     * @param attempts HTTP attempts made, including the successful one
     */
    record Response(JsonNode answers, JsonNode usage, int attempts) {
    }
}
