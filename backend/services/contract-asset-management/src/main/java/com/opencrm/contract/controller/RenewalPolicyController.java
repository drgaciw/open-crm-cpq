package com.opencrm.contract.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.dto.RenewalPolicyRecordDto;
import com.opencrm.contract.service.RenewalPolicyRecordService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only internal policy lookup. Tenant comes from the verified token {@code tid} claim.
 * There is no write mapping. {@code /internal/**} already requires {@code Internal.Invoke}.
 */
@RestController
public class RenewalPolicyController {

    public static final String PATH = "/internal/v1/renewal-policies";

    private final RenewalPolicyRecordService policies;

    public RenewalPolicyController(RenewalPolicyRecordService policies) {
        this.policies = policies;
    }

    @GetMapping(PATH)
    public ApiResponse<List<RenewalPolicyRecordDto>> read(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID agreementId,
            @RequestParam(required = false) UUID lineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        if (accountId == null && agreementId == null && lineId == null) {
            throw new BusinessException("At least one scope key is required", HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED");
        }
        return ApiResponse.success(policies.read(tenantFromToken(), accountId, agreementId, lineId, asOf));
    }

    private static UUID tenantFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new BusinessException("Tenant is not on the verified token", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        String tid = jwt.getToken().getClaimAsString("tid");
        try {
            if (tid == null || tid.isBlank()) {
                throw new IllegalArgumentException("missing tenant");
            }
            return UUID.fromString(tid);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Tenant is not on the verified token", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
    }
}
