package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * S2B-11 stateless configuration evaluate, on two paths with two audiences (ADR-016 step 2):
 * <ul>
 *   <li>{@code POST /api/v1/configurations/evaluate} for users and agents. The gateway's
 *       {@code /api/v1/configuration/validate} shim routes here (F3), with the user's token;</li>
 *   <li>{@code POST /internal/v1/configurations/evaluate} for services, with a service token carrying
 *       {@code Internal.Invoke}. quote-service's S2D-3a preflight calls it directly.</li>
 * </ul>
 * Before step 2 the gateway rewrote browser traffic onto the internal path, which cannot work once {@code /internal/**}
 * refuses user tokens.
 */
@RestController
@RequiredArgsConstructor
public class ConfigurationEvaluateController {

    private final StatelessConfigurationEvaluator evaluator;

    @PostMapping({"/api/v1/configurations/evaluate", "/internal/v1/configurations/evaluate"})
    public ApiResponse<ConfigurationValidationResult> evaluate(@RequestBody List<Long> selectedProductIds) {
        ConfigurationValidationResult result = evaluator.evaluate(selectedProductIds);
        String message = result.isValid()
                ? "Configuration is valid"
                : "Configuration has validation errors";
        return ApiResponse.success(result, message);
    }
}
