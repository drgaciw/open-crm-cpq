package com.opencrm.pricingairules.api.controller.ai;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.pricingairules.ai.orchestration.AIOrchestrationService;
import com.opencrm.pricingairules.ai.orchestration.RuleCreationResult;
import com.opencrm.pricingairules.ai.orchestration.RuleEvaluationResult;
import com.opencrm.pricingairules.ai.orchestration.RuleModificationResult;
import io.micrometer.core.annotation.Timed;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * API controller for the AI orchestration workflows
 */
@RestController
@RequestMapping("/api/v1/ai/workflow")
@RequiredArgsConstructor
@Slf4j
public class AIOrchestrationController {

    private final AIOrchestrationService orchestrationService;

    /**
     * Create a rule from a business requirement using the full AI workflow
     */
    @PostMapping("/create-rule")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "ai.workflow.create", description = "Time taken to create a rule using AI workflow")
    public ResponseEntity<ApiResponse<RuleCreationResult>> createRule(@RequestBody CreateRuleRequest request) {
        log.debug("Received request to create rule from business requirement using AI workflow");

        try {
            RuleCreationResult result = orchestrationService.createRuleFromRequirement(request.getBusinessRequirement());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error creating rule using AI workflow: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Create a rule asynchronously from a business requirement
     */
    @PostMapping("/create-rule/async")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "ai.workflow.create.async", description = "Time taken to request async rule creation")
    public ResponseEntity<ApiResponse<AsyncOperationResponse>> createRuleAsync(@RequestBody CreateRuleRequest request) {
        log.debug("Received request to create rule asynchronously using AI workflow");

        try {
            CompletableFuture<RuleCreationResult> future =
                orchestrationService.createRuleAsync(request.getBusinessRequirement());

            String operationId = "create-" + System.currentTimeMillis();

            // In a real implementation, you would store the future in a map to retrieve later

            AsyncOperationResponse response = new AsyncOperationResponse();
            response.setOperationId(operationId);
            response.setStatus("PROCESSING");
            response.setMessage("Rule creation started. Check status using the operation ID.");

            return ResponseEntity.accepted().body(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error initiating async rule creation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Modify an existing rule using the AI workflow
     */
    @PostMapping("/modify-rule/{ruleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "ai.workflow.modify", description = "Time taken to modify a rule using AI workflow")
    public ResponseEntity<ApiResponse<RuleModificationResult>> modifyRule(
            @PathVariable Long ruleId,
            @RequestBody ModifyRuleRequest request) {
        log.debug("Received request to modify rule {} using AI workflow", ruleId);

        try {
            RuleModificationResult result =
                orchestrationService.modifyRule(ruleId, request.getModificationRequirement());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error modifying rule using AI workflow: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Evaluate a rule set using the AI workflow
     */
    @GetMapping("/evaluate-ruleset/{ruleSetId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "ai.workflow.evaluate", description = "Time taken to evaluate a rule set using AI workflow")
    public ResponseEntity<ApiResponse<RuleEvaluationResult>> evaluateRuleSet(@PathVariable Long ruleSetId) {
        log.debug("Received request to evaluate rule set {} using AI workflow", ruleSetId);

        try {
            RuleEvaluationResult result = orchestrationService.evaluateRuleSet(ruleSetId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error evaluating rule set using AI workflow: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Request to create a rule
     */
    @Data
    public static class CreateRuleRequest {
        private String businessRequirement;
        private String ruleType;
        private String businessDomain;
    }

    /**
     * Request to modify a rule
     */
    @Data
    public static class ModifyRuleRequest {
        private String modificationRequirement;
    }

    /**
     * Response for async operations
     */
    @Data
    public static class AsyncOperationResponse {
        private String operationId;
        private String status;
        private String message;
    }
}
