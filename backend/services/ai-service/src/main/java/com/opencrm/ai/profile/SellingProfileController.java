package com.opencrm.ai.profile;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/selling-profile")
public class SellingProfileController {
    private final SellingProfileService profiles;
    public SellingProfileController(SellingProfileService profiles) { this.profiles=profiles; }
    @GetMapping
    public ApiResponse<SellingProfile> get() { return ApiResponse.success(profiles.get(tenant()).orElse(null)); }
    @PutMapping
    public ApiResponse<SellingProfile> put(@Valid @RequestBody SellingProfile.Fields fields) {
        admin(); return ApiResponse.success(profiles.put(tenant(),fields,caller().getName()));
    }
    @PostMapping("/ingest-url")
    public ApiResponse<SellingProfile> ingest(@Valid @RequestBody SellingProfile.IngestUrl input) {
        admin(); return ApiResponse.success(profiles.ingest(tenant(),input.url(),caller().getName()));
    }
    private static JwtAuthenticationToken caller() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"authenticated JWT required");
        return jwt;
    }
    private static String tenant() {
        String tenant=caller().getToken().getClaimAsString("tid");
        if(tenant==null || tenant.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"tenant required");
        return tenant;
    }
    private static void admin() {
        if(caller().getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_WORKSPACE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"workspace admin required");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalid() { return ApiResponse.error("Invalid profile or unsafe/unavailable website", "INVALID_SELLING_PROFILE",null); }
}
