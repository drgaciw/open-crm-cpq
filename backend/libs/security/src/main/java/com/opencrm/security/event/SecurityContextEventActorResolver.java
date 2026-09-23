package com.opencrm.security.event;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;

/**
 * Attributes an event to the authenticated user when there is one, {@code user:<sub>}, and to the service otherwise,
 * {@code service:<name>} (ADR-018 D4, ADR-016).
 *
 * <p>The user comes from {@link SecurityUtils#getCurrentUser()}, which ignores anonymous callers, so a permitAll
 * request is attributed to the service rather than to {@code anonymousUser}. Schedulers and Kafka listeners have no
 * security context and are attributed to the service. Telling a service principal (a client-credentials token) apart
 * from a user, for {@code service:<client-id>}, is ADR-016 work.
 */
public class SecurityContextEventActorResolver implements EventActorResolver {

    private final String serviceActor;

    public SecurityContextEventActorResolver(String applicationName) {
        this.serviceActor = EventActorResolver.serviceActor(applicationName);
    }

    @Override
    public String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getUserId)
                .filter(userId -> !userId.isBlank())
                .map(userId -> "user:" + userId)
                .orElse(serviceActor);
    }
}
