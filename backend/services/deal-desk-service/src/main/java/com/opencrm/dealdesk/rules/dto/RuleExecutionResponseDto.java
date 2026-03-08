package com.opencrm.dealdesk.rules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionResponseDto {

    private Long dealId;
    private String category;
    private int evaluatedRules;
    private int matchedRules;
    private int firedRules;
    private List<String> matchedRuleKeys;
    private List<String> matchedRuleNames;
    private List<String> firedRuleKeys;
    private List<String> firedRuleNames;
    private Map<String, Object> facts;
    private Map<String, Object> outputs;
}
