package com.opencrm.jev.transport;

/** Default transport: sends nothing. Merging ADR-015 code leaves runtime behaviour unchanged. */
public final class NoOpJevTransport implements JevTransport {

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public String wireFormat() {
        return "none";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public Response decide(Request request) {
        throw new IllegalStateException("NoOpJevTransport never sends; check enabled() first");
    }
}
