package com.opencrm.document.service;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ClauseTenant {
    public String requiredTenant() {
        return SecurityUtils.getCurrentUser().map(UserContext::getTenantId)
                .filter(t -> !t.isBlank() && t.length() <= 100)
                .orElseThrow(() -> new BusinessException("Authenticated tenant is required", HttpStatus.FORBIDDEN));
    }
    public String requiredActor() {
        return SecurityUtils.getCurrentUser().map(UserContext::getUserId)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new BusinessException("Authenticated user is required", HttpStatus.FORBIDDEN));
    }
}
