package com.opencrm.configuration.session;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for configuration session management.
 * Constraint solving, configuration validation, and session-safe configuration states.
 */
@RestController
@RequestMapping("/api/v1/configurations")
@RequiredArgsConstructor
@Tag(name = "Configuration Engine", description = "Configuration session and constraint solving endpoints")
public class ConfigurationSessionController {

    private final ConfigurationSessionService sessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new configuration session")
    public ApiResponse<ConfigurationSession> create(
            @RequestParam String productId,
            @RequestParam String userId) {
        ConfigurationSession session = sessionService.create(productId, userId);
        return ApiResponse.success(session, "Configuration session created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get configuration session by ID")
    public ApiResponse<ConfigurationSession> getById(@PathVariable Long id) {
        return ApiResponse.success(sessionService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List configuration sessions by user and status")
    public ApiResponse<List<ConfigurationSession>> listByUser(
            @RequestParam String userId,
            @RequestParam(defaultValue = "IN_PROGRESS") ConfigurationStatus status) {
        return ApiResponse.success(sessionService.getByUserAndStatus(userId, status));
    }

    @PostMapping("/{id}/selections")
    @Operation(summary = "Add or update a selection in the configuration")
    public ApiResponse<ConfigurationSession> addSelection(
            @PathVariable Long id,
            @RequestParam String optionKey,
            @RequestParam String optionValue) {
        ConfigurationSession session = sessionService.addSelection(id, optionKey, optionValue);
        return ApiResponse.success(session, "Selection added");
    }

    @DeleteMapping("/{id}/selections/{optionKey}")
    @Operation(summary = "Remove a selection from the configuration")
    public ApiResponse<ConfigurationSession> removeSelection(
            @PathVariable Long id,
            @PathVariable String optionKey) {
        ConfigurationSession session = sessionService.removeSelection(id, optionKey);
        return ApiResponse.success(session, "Selection removed");
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate the current configuration")
    public ApiResponse<ConfigurationSession> validate(@PathVariable Long id) {
        ConfigurationSession session = sessionService.validate(id);
        return ApiResponse.success(session, session.isValid() ? "Configuration is valid" : "Configuration has errors");
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a valid configuration session")
    public ApiResponse<ConfigurationSession> complete(@PathVariable Long id) {
        ConfigurationSession session = sessionService.complete(id);
        return ApiResponse.success(session, "Configuration completed");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a configuration session")
    public ApiResponse<ConfigurationSession> cancel(@PathVariable Long id) {
        ConfigurationSession session = sessionService.cancel(id);
        return ApiResponse.success(session, "Configuration cancelled");
    }
}
