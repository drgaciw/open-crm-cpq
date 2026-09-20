package com.opencrm.quote.snapshot;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;

@RestController @RequestMapping("/internal/oms/accepted-snapshots") @RequiredArgsConstructor
public class AcceptedSnapshotController {
    private final AcceptedSnapshotRepository repository;
    private final OmsQuoteProperties properties;
    public record SnapshotResponse(String payload, String snapshotHash, String holds) {}

    @GetMapping("/{snapshotId}") @Transactional(readOnly = true)
    public ApiResponse<SnapshotResponse> get(@PathVariable String snapshotId,
            @RequestParam String quoteId, @RequestParam int acceptedRevision, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)
            || !contains(jwt, "permissions", "oms:snapshot:read")) throw new AccessDeniedException("Snapshot service permission required");
        String scope = properties.getScopeId();
        if (scope == null || scope.isBlank() || !contains(jwt, "scope_ids", scope)) throw new AccessDeniedException("Snapshot scope denied");
        var snapshot = repository.lookup(snapshotId, quoteId, acceptedRevision)
            .filter(s -> scope.equals(s.getScopeId()) && contains(jwt, "account_ids", s.getCustomerId()))
            .orElseThrow(() -> new ResourceNotFoundException("Accepted snapshot"));
        return ApiResponse.success(new SnapshotResponse(snapshot.getPayload(), snapshot.getSnapshotHash(), snapshot.getHolds()));
    }
    private boolean contains(Jwt jwt, String claim, String value) {
        Object values = jwt.getClaim(claim);
        return values instanceof Collection<?> collection && value != null && collection.contains(value);
    }
}
