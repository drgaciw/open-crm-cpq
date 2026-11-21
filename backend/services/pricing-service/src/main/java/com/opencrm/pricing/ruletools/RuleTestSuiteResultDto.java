package com.opencrm.pricing.ruletools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestSuiteResultDto {

    private String suiteName;
    private int passedTests;
    private int totalTests;
    private double passRate;
    @Builder.Default
    private List<RuleTestCaseResultDto> cases = new ArrayList<>();
}
