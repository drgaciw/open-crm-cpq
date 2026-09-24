package com.opencrm.security;

import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * The actor to record for what happens now (ADR-016 decision 6), auto-configured by
 * {@link OpenCrmActorAutoConfiguration}:
 * <ul>
 *   <li>the verified caller, a person ({@code <oid>}) or a service ({@code service:<client-id>});</li>
 *   <li>otherwise this service itself, {@link #self()}: Kafka consumers, schedulers, async work and anything else
 *       without a caller. It replaces the literal {@code "system"} actors.</li>
 * </ul>
 * Background work is deliberately attributed to the service, not to a user who may have triggered it earlier: the
 * security context is not propagated to other threads. The user is recorded where they act, on the request thread.
 */
public class CurrentActor {

    private final String self;
    private final boolean unverifiedCallersAllowed;

    /**
     * @param self                     this service's actor, {@code service:<client-id or name>}
     * @param unverifiedCallersAllowed whether a request without a verified caller may still act; true only in
     *                                 {@code dev} mode, where the chain permits everyone
     */
    public CurrentActor(String self, boolean unverifiedCallersAllowed) {
        if (self == null || self.isBlank()) {
            throw new IllegalArgumentException("A service actor is required");
        }
        this.self = self;
        this.unverifiedCallersAllowed = unverifiedCallersAllowed;
    }

    /** The verified caller of the current request, if any. */
    public Optional<Caller> caller() {
        return SecurityUtils.getCurrentCaller();
    }

    /** The caller's actor id, or {@link #self()} when there is no caller. */
    public String id() {
        return caller().map(Caller::actorId).orElse(self);
    }

    /** This service as an actor: {@code service:<client-id>}, or {@code service:<application-name>} locally. */
    public String self() {
        return self;
    }

    /**
     * Whether an action that needs a verified person may proceed without one. Only {@code dev} mode says yes; there
     * the action is recorded as {@link #self()}.
     */
    public boolean unverifiedCallersAllowed() {
        return unverifiedCallersAllowed;
    }

    /**
     * This service's own actor. In AKS it is the workload identity's client id ({@code opencrm.security.client
     * .client-id}, else {@code AZURE_CLIENT_ID}), which is also how other services record this one when it calls them
     * with a service token. Without a service identity (local runs) it is {@code spring.application.name}.
     */
    public static String selfActor(Environment environment) {
        for (String key : new String[]{"opencrm.security.client.client-id", "AZURE_CLIENT_ID",
                "spring.application.name"}) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return Caller.SERVICE_PREFIX + value.trim();
            }
        }
        return Caller.SERVICE_PREFIX + "unnamed";
    }
}
