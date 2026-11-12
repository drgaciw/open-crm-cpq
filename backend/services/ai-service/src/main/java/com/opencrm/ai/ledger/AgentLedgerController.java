package com.opencrm.ai.ledger;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static com.opencrm.ai.ledger.LedgerTypes.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AgentLedgerController {
    private final AgentLedgerService ledger;
    public AgentLedgerController(AgentLedgerService ledger) { this.ledger=ledger; }
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Run> start(@Valid @RequestBody Start input) { return ApiResponse.success(ledger.start(tenant(),input,actor())); }
    @GetMapping("/runs/{id}")
    public ApiResponse<Run> get(@PathVariable UUID id) { return ApiResponse.success(ledger.get(tenant(),id)); }
    @PostMapping("/runs/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable UUID id) { ledger.complete(tenant(),id,actor()); return ApiResponse.success(null); }
    @PostMapping("/runs/{id}/fail")
    public ApiResponse<Void> fail(@PathVariable UUID id, @Valid @RequestBody Failure failure) {
        ledger.fail(tenant(),id,failure.errorCode(),actor()); return ApiResponse.success(null);
    }
    @PostMapping("/runs/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Action> action(@PathVariable UUID id, @Valid @RequestBody ActionInput input) {
        return ApiResponse.success(ledger.recordAction(tenant(),id,input,actor()));
    }
    @GetMapping("/runs/{id}/actions")
    public ApiResponse<List<Action>> actions(@PathVariable UUID id) { return ApiResponse.success(ledger.actions(tenant(),id)); }
    @PostMapping("/runs/{id}/audit-events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Audit> audit(@PathVariable UUID id, @Valid @RequestBody AuditInput input) {
        return ApiResponse.success(ledger.audit(tenant(),id,input.eventType(),actor()));
    }
    @GetMapping("/runs/{id}/audit-events")
    public ApiResponse<List<Audit>> audits(@PathVariable UUID id) { return ApiResponse.success(ledger.audits(tenant(),id)); }
    @GetMapping("/usage")
    public ApiResponse<Usage> usage(@RequestParam(required=false) UUID taskId) { return ApiResponse.success(ledger.usage(tenant(),taskId)); }
    private static JwtAuthenticationToken caller() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"authenticated JWT required");
        return jwt;
    }
    private static String tenant() {
        String tenant = caller().getToken().getClaimAsString("tid");
        if (tenant == null || tenant.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"tenant required");
        return tenant;
    }
    private static String actor() { return caller().getName(); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalid() { return ApiResponse.error("Invalid ledger request", "INVALID_LEDGER_REQUEST", null); }
}
