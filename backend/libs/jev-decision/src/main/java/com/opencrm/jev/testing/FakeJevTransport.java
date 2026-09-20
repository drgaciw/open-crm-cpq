package com.opencrm.jev.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.jev.transport.JevTransport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-process transport for unit tests in this library and in the services that use it. Replies are
 * scripted in order; every request is recorded so tests can assert exactly what would have been sent.
 */
public final class FakeJevTransport implements JevTransport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();

    /** @param answersJson JSON object keyed by question id */
    public FakeJevTransport reply(String answersJson) {
        try {
            script.add(JSON.readTree(answersJson));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Scripted reply is not JSON", e);
        }
        return this;
    }

    public FakeJevTransport fail(RuntimeException failure) {
        script.add(failure);
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String wireFormat() {
        return "none";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Response decide(Request request) {
        requests.add(request);
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("FakeJevTransport has no scripted reply left");
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        return new Response((JsonNode) next, null, 1);
    }
}
