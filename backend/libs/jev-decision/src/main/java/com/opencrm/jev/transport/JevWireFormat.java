package com.opencrm.jev.transport;

/** Request and response envelope spoken by the configured upstream. */
public enum JevWireFormat {

    /**
     * TypeSafe native and OpenRouter Decisions: {@code {model, state (JSON text), questions}} in,
     * {@code {answers, usage}} out.
     */
    NATIVE,

    /**
     * AutoJev REST: {@code {model, state (object), questions}} in, {@code {code, message, data}} out. The
     * shape of {@code data} is taken from the published docs and is unverified against a live call.
     */
    AUTOJEV,

    /**
     * An authenticated facade we own (for example the jev-worker Cloudflare Worker):
     * {@code {state (object), questions}} in, {@code {answers, usage}} out. The facade pins the model.
     */
    FACADE
}
