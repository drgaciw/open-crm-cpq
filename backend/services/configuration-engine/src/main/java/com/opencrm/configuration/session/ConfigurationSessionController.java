package com.opencrm.configuration.session;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for configuration session management.
 * Constraint solving, configuration validation, and session-safe configuration states.
 * API boundary exposes {@link ConfigurationSessionDto} only — never the JPA entity.
 * <p>
 * A session belongs to the authenticated caller; no endpoint takes a user id (ADR-016 decision 6).
 */
@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
@Tag(name = "Configuration Engine", description = "Configuration session and constraint solving endpoints")
public class ConfigurationSessionController {

    private final ConfigurationSessionService sessionService;
    private final CurrentActor currentActor;

    /**
     * StartSession (DRG-424 / BC-02-01): against a catalog version and a product or a bundle — exactly
     * one of {@code productId}/{@code bundleId} is required. {@code catalogVersion} is optional and
     * recorded as given. Existing callers that pass only {@code productId} are unaffected.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a configuration session for the authenticated caller, against a catalog "
            + "version and a product or a bundle (StartSession)")
    public ApiResponse<ConfigurationSessionDto> create(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String bundleId,
            @RequestParam(required = false) String catalogVersion) {
        ConfigurationSessionDto session =
                sessionService.create(productId, bundleId, catalogVersion, currentActor.id());
        return ApiResponse.success(session, "Configuration session created");
    }

    /** GetSessionState (DRG-424 / BC-02-01): selections, open violations, and available options. */
    @GetMapping("/{id}")
    @Operation(summary = "Get configuration session state: selections, open violations, and available "
            + "options (GetSessionState)")
    public ApiResponse<ConfigurationSessionDto> getById(@PathVariable Long id) {
        return ApiResponse.success(sessionService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List the authenticated caller's configuration sessions by status")
    public ApiResponse<List<ConfigurationSessionDto>> listMine(
            @RequestParam(defaultValue = "IN_PROGRESS") ConfigurationStatus status) {
        return ApiResponse.success(sessionService.getByUserAndStatus(currentActor.id(), status));
    }

    @PostMapping("/{id}/selections")
    @Operation(summary = "Add or update a selection in the configuration")
    public ApiResponse<ConfigurationSessionDto> addSelection(
            @PathVariable Long id,
            @RequestParam String optionKey,
            @RequestParam String optionValue) {
        ConfigurationSessionDto session = sessionService.addSelection(id, optionKey, optionValue);
        return ApiResponse.success(session, "Selection added");
    }

    @DeleteMapping("/{id}/selections/{optionKey}")
    @Operation(summary = "Remove a selection from the configuration")
    public ApiResponse<ConfigurationSessionDto> removeSelection(
            @PathVariable Long id,
            @PathVariable String optionKey) {
        ConfigurationSessionDto session = sessionService.removeSelection(id, optionKey);
        return ApiResponse.success(session, "Selection removed");
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate the current configuration")
    public ApiResponse<ConfigurationSessionDto> validate(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.validate(id);
        return ApiResponse.success(session, session.isValid() ? "Configuration is valid" : "Configuration has errors");
    }

    /** Rejects (409) a session started against a bundle — committed-bundle events are not defined yet (DRG-424). */
    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a valid configuration session (not yet supported for a bundle-started session)")
    public ApiResponse<ConfigurationSessionDto> complete(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.complete(id);
        return ApiResponse.success(session, "Configuration completed");
    }

    /**
     * FreezeConfiguration (DRG-428 / BC-02-05): ValidateConfiguration then FreezeConfiguration computes
     * the deterministic ConfigurationHash and makes the session immutable. Rejects a bundle-started
     * session (409), matching {@code /complete}'s restriction.
     */
    @PostMapping("/{id}/freeze")
    @Operation(summary = "Freeze a valid configuration session and compute its ConfigurationHash (FreezeConfiguration)")
    public ApiResponse<ConfigurationSessionDto> freeze(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.freeze(id);
        return ApiResponse.success(session, "Configuration frozen");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a configuration session")
    public ApiResponse<ConfigurationSessionDto> cancel(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.cancel(id);
        return ApiResponse.success(session, "Configuration cancelled");
    }
}
