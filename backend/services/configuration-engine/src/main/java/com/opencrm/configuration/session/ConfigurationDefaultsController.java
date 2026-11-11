package com.opencrm.configuration.session;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfigurationDefaultsController {
    private final ConfigurationDefaultsService defaultsService;
    private final CurrentActor currentActor;

    @PostMapping("/api/v1/configurations/{id}/defaults")
    @Operation(summary = "Apply catalog defaults to missing required selections atomically",
            description = "Preserves seller selections. Returns defaultedSelectionKeys so defaults remain distinguishable on reload.")
    public ApiResponse<ConfigurationSessionDto> applyDefaults(@PathVariable Long id) {
        return ApiResponse.success(defaultsService.applyDefaults(id, currentActor.id()), "Configuration defaults applied");
    }
}
