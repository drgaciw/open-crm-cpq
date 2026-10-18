package com.opencrm.pricing.renewal;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal preview of a renewal price. Not on the gateway. {@code Internal.Invoke} is the existing service
 * authority; this controller grants no new role. Any application that already holds that authority can call,
 * because no existing authority names quote-service alone.
 */
@RestController
public class RenewalResolveController {

    public static final String PATH = "/internal/v1/pricing/renewals/resolve";

    private final RenewalResolveService service;

    public RenewalResolveController(RenewalResolveService service) {
        this.service = service;
    }

    @PostMapping(PATH)
    @PreAuthorize("hasAuthority('ROLE_INTERNAL_INVOKE')")
    public ApiResponse<RenewalResolveResponse> resolve(@Valid @RequestBody RenewalResolveRequest request) {
        return ApiResponse.success(service.resolve(request));
    }
}
