package com.opencrm.configuration.session;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
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
    public ApiResponse<ConfigurationSessionDto> create(
            @RequestParam String productId,
            @RequestParam String userId) {
        ConfigurationSessionDto session = sessionService.create(productId, userId);
        return ApiResponse.success(session, "Configuration session created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get configuration session by ID")
    public ApiResponse<ConfigurationSessionDto> getById(@PathVariable Long id) {
        return ApiResponse.success(sessionService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List configuration sessions by user and status")
    public ApiResponse<List<ConfigurationSessionDto>> listByUser(
            @RequestParam String userId,
            @RequestParam(defaultValue = "IN_PROGRESS") ConfigurationStatus status) {
        return ApiResponse.success(sessionService.getByUserAndStatus(userId, status));
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

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a valid configuration session")
    public ApiResponse<ConfigurationSessionDto> complete(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.complete(id);
        return ApiResponse.success(session, "Configuration completed");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a configuration session")
    public ApiResponse<ConfigurationSessionDto> cancel(@PathVariable Long id) {
        ConfigurationSessionDto session = sessionService.cancel(id);
        return ApiResponse.success(session, "Configuration cancelled");
    }
}
