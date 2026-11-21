package com.opencrm.pricing.ruletools;

import com.opencrm.pricing.waterfall.PricingContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One simulate fixture plus expected financial-metric assertions (PD1-3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestCaseRequest {

    @NotBlank
    private String name;

    @NotNull
    @Valid
    private PricingContext context;

    @Builder.Default
    private Map<String, Object> expected = new LinkedHashMap<>();
}
