package com.opencrm.pricing.ruletools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleTestCaseResultDto {

    private String name;
    private boolean passed;
    private String errorMessage;
    @Builder.Default
    private Map<String, Object> expected = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> actual = new LinkedHashMap<>();
}
