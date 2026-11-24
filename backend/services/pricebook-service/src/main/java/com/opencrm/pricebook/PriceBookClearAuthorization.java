package com.opencrm.pricebook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Clearing a price book's region or expiration date is an Admin action.
 * A PUT that does not set either clear flag keeps the baseline authenticated rule.
 */
public final class PriceBookClearAuthorization {

    static final String ADMIN = EntraRoleAuthorities.authorityFor("Admin");

    private PriceBookClearAuthorization() {
    }

    public static void requireAdminWhenClearing(PriceBookUpdateRequest request) {
        if (request == null || !clears(request)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN.equals(authority.getAuthority()));
        if (!admin) {
            throw new BusinessException(
                    "Clearing a price book region or expiration date requires the Admin role",
                    HttpStatus.FORBIDDEN,
                    "PRICE_BOOK_CLEAR_FORBIDDEN");
        }
    }

    private static boolean clears(PriceBookUpdateRequest request) {
        return Boolean.TRUE.equals(request.getClearRegion())
                || Boolean.TRUE.equals(request.getClearExpirationDate());
    }
}
