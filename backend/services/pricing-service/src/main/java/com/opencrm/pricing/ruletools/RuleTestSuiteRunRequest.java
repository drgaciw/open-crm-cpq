package com.opencrm.pricing.ruletools;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Named suite and/or explicit simulate fixtures (PD1-3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestSuiteRunRequest {

    private String suiteName;

    @Valid
    @Builder.Default
    private List<RuleTestCaseRequest> cases = new ArrayList<>();
}
