package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import org.springframework.http.HttpStatus;

/**
 * Agents do not write {@code field_value}. Person callers may. A service caller may not,
 * including when dev mode would otherwise allow an anonymous request.
 * There is no ledger write here (DRG-512 is held).
 */
public final class AgentFieldValueGuard {

    private AgentFieldValueGuard() {
    }

    public static void requireHumanWriter(CurrentActor actor) {
        if (actor.caller().filter(caller -> caller instanceof Caller.Service).isPresent()) {
            reject();
        }
        if (actor.caller().isEmpty() && !actor.unverifiedCallersAllowed()) {
            reject();
        }
    }

    public static String actorId(CurrentActor actor) {
        String id = actor.id();
        if (id == null || id.isBlank()) {
            throw new BusinessException("Current actor is missing", HttpStatus.UNAUTHORIZED, "ACTOR_REQUIRED");
        }
        return id;
    }

    private static void reject() {
        throw new BusinessException(
                "Agents cannot write field values",
                HttpStatus.FORBIDDEN,
                "AGENT_FIELD_VALUE_FORBIDDEN");
    }
}
