package com.opencrm.crm.duplicate;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Fail closed until trusted tenant and account authorization exists for candidate review.
 * There is deliberately no production switch for this hold.
 */
@Component
public final class DuplicateCandidateAccess {

    void requireReviewAccess() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Caller caller = SecurityUtils.getCurrentCaller().orElse(null);
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !(jwt.getToken().getClaims().get("scp") instanceof String scope) || scope.isBlank()
                || !(caller instanceof Caller.User user)
                || user.actorId() == null || user.actorId().isBlank()
                || !user.hasAuthority("ROLE_SALES_OPS_ACCOUNT_MERGE")) {
            throw new BusinessException("Candidate review requires the sales operations role",
                    HttpStatus.FORBIDDEN, "DUPLICATE_CANDIDATES_FORBIDDEN");
        }

        throw new BusinessException("Candidate review is unavailable until tenant and account authorization are ready",
                HttpStatus.SERVICE_UNAVAILABLE, "DUPLICATE_CANDIDATES_HOLD");
    }
}
