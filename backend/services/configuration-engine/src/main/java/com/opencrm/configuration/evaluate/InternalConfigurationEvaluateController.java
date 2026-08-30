package com.opencrm.configuration.evaluate;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal mesh endpoint for S2B-11 stateless configuration evaluate.
 * External agents continue to call {@code POST /api/v1/configuration/validate} via gateway (F3).
 */
@RestController
@RequestMapping("/internal/v1/configurations")
@RequiredArgsConstructor
public class InternalConfigurationEvaluateController {

    private final StatelessConfigurationEvaluator evaluator;

    @PostMapping("/evaluate")
    public ApiResponse<ConfigurationValidationResult> evaluate(
            @RequestBody List<Long> selectedProductIds) {
        ConfigurationValidationResult result = evaluator.evaluate(selectedProductIds);
        String message = result.isValid()
                ? "Configuration is valid"
                : "Configuration has validation errors";
        return ApiResponse.success(result, message);
    }
}
