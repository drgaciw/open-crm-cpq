package com.opencrm.security.event;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;

/**
 * The {@code actor} envelope header of an event (ADR-018 D4, ADR-016 decision 6):
 * <ul>
 *   <li>{@code user:<oid>} for a person;</li>
 *   <li>{@code service:<client-id>} for another service calling with a service token;</li>
 *   <li>this service, {@link CurrentActor#self()}, when there is no caller: Kafka listeners, schedulers, and
 *       anonymous requests on a permitAll chain.</li>
 * </ul>
 */
public class SecurityContextEventActorResolver implements EventActorResolver {

    private final CurrentActor currentActor;

    public SecurityContextEventActorResolver(CurrentActor currentActor) {
        this.currentActor = currentActor;
    }

    @Override
    public String currentActor() {
        return currentActor.caller()
                .map(caller -> caller instanceof Caller.User user ? "user:" + user.actorId() : caller.actorId())
                .orElse(currentActor.self());
    }
}
