package com.opencrm.common.event;

/**
 * Who is causing the events published on this thread, for the {@code actor} envelope header (ADR-018 D4, ADR-016).
 *
 * <p>Values are {@code user:<sub>} for an authenticated user and {@code service:<name>} otherwise. The default, from
 * {@link EventActorAutoConfiguration}, always answers {@code service:<spring.application.name>}. {@code libs/security}
 * replaces it with one that reads the security context.
 */
@FunctionalInterface
public interface EventActorResolver {

    /** The actor for an event published now, or {@code null} if unknown. */
    String currentActor();

    /** {@code service:<applicationName>}, or {@code null} without a name. */
    static String serviceActor(String applicationName) {
        return applicationName == null || applicationName.isBlank() ? null : "service:" + applicationName;
    }
}
