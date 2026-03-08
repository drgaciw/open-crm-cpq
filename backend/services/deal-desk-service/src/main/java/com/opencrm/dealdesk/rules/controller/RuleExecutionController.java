package com.opencrm.dealdesk.rules.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.dealdesk.rules.dto.RuleExecutionResponseDto;
import com.opencrm.dealdesk.rules.service.RuleExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules/executions")
public class RuleExecutionController {

    private final RuleExecutionService ruleExecutionService;

    public RuleExecutionController(RuleExecutionService ruleExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
    }

    @PostMapping("/deals/{dealId}")
    public ResponseEntity<ApiResponse<RuleExecutionResponseDto>> executeDealRules(
            @PathVariable Long dealId,
            @RequestParam String category
    ) {
        RuleExecutionResponseDto result = ruleExecutionService.executeDealRules(dealId, category);
        return ResponseEntity.ok(ApiResponse.success(result, "Rules executed"));
    }
}
