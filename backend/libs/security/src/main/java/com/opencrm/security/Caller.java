package com.opencrm.security;

import java.util.Set;

/**
 * Who is making the current call, as its verified token says (ADR-016 decision 6). Either a person or an application
 * acting as itself; an anonymous request has no caller.
 *
 * <p>{@link #actorId()} is the value an actor field records: approver, requester, {@code createdBy}, and so on.
 */
public sealed interface Caller permits Caller.User, Caller.Service {

    /** Prefix of a service actor: {@code service:<client-id>}. */
    String SERVICE_PREFIX = "service:";

    /** The value recorded as the actor. */
    String actorId();

    /** The call's Spring authorities: {@code ROLE_*} from Entra app roles, {@code SCOPE_*} from delegated scopes. */
    Set<String> authorities();

    default boolean hasAuthority(String authority) {
        return authorities().contains(authority);
    }

    /**
     * A person. The actor id is the Entra object id ({@code oid}), which is the same in every application of the
     * tenant, so the gateway's login and an agent client record the same person the same way. {@code sub} is only a
     * fallback for tokens without {@code oid}: Entra makes it pairwise, different for each client application.
     */
    record User(String actorId, Set<String> authorities) implements Caller {
        public User {
            authorities = Set.copyOf(authorities);
        }
    }

    /** An application acting as itself, with a client-credentials token: {@code service:<client-id>}. */
    record Service(String clientId, Set<String> authorities) implements Caller {
        public Service {
            authorities = Set.copyOf(authorities);
        }

        @Override
        public String actorId() {
            return SERVICE_PREFIX + clientId;
        }
    }
}
