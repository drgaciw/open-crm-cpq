package com.opencrm.dealdesk.rules.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.dealdesk.rules.dto.RuleDefinitionRequestDto;
import com.opencrm.dealdesk.rules.dto.RuleDefinitionResponseDto;
import com.opencrm.dealdesk.rules.service.RuleDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules/definitions")
public class RuleDefinitionController {

    private final RuleDefinitionService ruleDefinitionService;

    public RuleDefinitionController(RuleDefinitionService ruleDefinitionService) {
        this.ruleDefinitionService = ruleDefinitionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RuleDefinitionResponseDto>>> listRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(ruleDefinitionService.listRules(category, active)));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<RuleDefinitionResponseDto>>> listRulesByCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(ruleDefinitionService.listRulesByCategory(category)));
    }

    @GetMapping("/category/{category}/active")
    public ResponseEntity<ApiResponse<List<RuleDefinitionResponseDto>>> listActiveRulesByCategory(
            @PathVariable String category
    ) {
        return ResponseEntity.ok(ApiResponse.success(ruleDefinitionService.listActiveRulesByCategory(category)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleDefinitionResponseDto>> getRule(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ruleDefinitionService.getRuleById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RuleDefinitionResponseDto>> createRule(
            @Valid @RequestBody RuleDefinitionRequestDto request
    ) {
        RuleDefinitionResponseDto created = ruleDefinitionService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Rule definition created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RuleDefinitionResponseDto>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody RuleDefinitionRequestDto request
    ) {
        RuleDefinitionResponseDto updated = ruleDefinitionService.updateRule(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Rule definition updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        ruleDefinitionService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Rule definition deleted"));
    }
}
