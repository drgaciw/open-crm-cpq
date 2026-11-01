package com.opencrm.contract.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.documents.apply.ApplyCommand;
import com.opencrm.contract.documents.apply.ApplyResult;
import com.opencrm.contract.documents.apply.DocumentApplyOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal apply. {@code /internal/**} already requires {@code Internal.Invoke}.
 * Tenant is the verified token {@code tid}, not an {@code X-Tenant-Id} header.
 * The route is not on the gateway and is not rate limited.
 */
@RestController
public class DocumentApplyController {

    public static final String PATH = "/internal/v1/amendments/{amendmentId}:apply";

    private final DocumentApplyOrchestrator orchestrator;

    @Autowired
    public DocumentApplyController(DocumentApplyOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(PATH)
    public ApiResponse<ApplyView> apply(@PathVariable UUID amendmentId,
                                        @RequestBody(required = false) ApplyPins body,
                                        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ApplyResult result = orchestrator.apply(new ApplyCommand(amendmentId, tenantOrNull(authentication),
                caller(authentication), authorities(authentication), correlationId, pins(body)));
        return ApiResponse.success(new ApplyView(amendmentId, result.status().name(), result.replayed(),
                unitOutcomes(result.unitOutcomes())));
    }

    private static List<ApplyView.UnitOutcome> unitOutcomes(List<ApplyResult.UnitOutcome> units) {
        if (units == null) {
            return null;
        }
        return units.stream()
                .map(unit -> new ApplyView.UnitOutcome(unit.unitId(), unit.outcome(), unit.reason()))
                .toList();
    }

    private static UUID tenantOrNull(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        String tid = jwt.getToken().getClaimAsString("tid");
        if (tid == null || tid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }

    private static String authorities(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private static Map<Integer, String> pins(ApplyPins body) {
        Map<Integer, String> pins = new LinkedHashMap<>();
        if (body == null || body.pins() == null) {
            return pins;
        }
        for (ApplyPins.Pin pin : body.pins()) {
            if (pin == null || pin.unitNo() == null || pin.unitNo() < 1) {
                throw new BusinessException("unitNo must be a positive integer", HttpStatus.BAD_REQUEST, "VALIDATION_REJECTED");
            }
            if (pin.pricebookVersion() == null || pin.pricebookVersion().isBlank() || pin.pricebookVersion().length() > 64) {
                throw new BusinessException("pricebookVersion must be 1 to 64 characters", HttpStatus.BAD_REQUEST, "VALIDATION_REJECTED");
            }
            if (pins.put(pin.unitNo(), pin.pricebookVersion()) != null) {
                throw new BusinessException("duplicate pin for unit " + pin.unitNo(), HttpStatus.BAD_REQUEST, "VALIDATION_REJECTED");
            }
        }
        return pins;
    }

    public record ApplyPins(List<Pin> pins) {
        public record Pin(Integer unitNo, String pricebookVersion) {
        }
    }

    public record ApplyView(UUID documentId, String status, boolean replayed, List<UnitOutcome> unitOutcomes) {

        public ApplyView(UUID documentId, String status, boolean replayed) {
            this(documentId, status, replayed, null);
        }

        /** Additive unit row. {@code outcome} is {@code APPLIED} or {@code BLOCKED}. */
        public record UnitOutcome(Integer unitId, String outcome, String reason) {
        }
    }
}
