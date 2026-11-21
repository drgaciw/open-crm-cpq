package com.opencrm.ai.workflow;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Price-Drools–compatible AI rule workflow surface under {@code /api/v1/ai/workflow}.
 * Gated by {@code opencrm.ai.agents.enabled} (default false).
 */
@RestController
@RequestMapping("/api/v1/ai/workflow")
public class RuleWorkflowController {

    private final RuleWorkflowService workflowService;
    private final boolean enabled;

    public RuleWorkflowController(
            RuleWorkflowService workflowService,
            @Value("${opencrm.ai.agents.enabled:false}") boolean enabled) {
        this.workflowService = workflowService;
        this.enabled = enabled;
    }

    @PostMapping("/create-rule")
    public ResponseEntity<RuleWorkflowResult> createRule(@Valid @RequestBody CreateRuleWorkflowRequest request) {
        if (!enabled) {
            return ResponseEntity.status(503).body(RuleWorkflowResult.failure(
                    request.businessRequirement(), disabledMessage()));
        }
        return ResponseEntity.ok(workflowService.createRule(request));
    }

    @PostMapping("/modify-rule/{ruleId}")
    public ResponseEntity<RuleWorkflowResult> modifyRule(
            @PathVariable long ruleId,
            @Valid @RequestBody ModifyRuleWorkflowRequest request) {
        if (!enabled) {
            return ResponseEntity.status(503).body(RuleWorkflowResult.failure(
                    request.modificationRequirement(), disabledMessage()));
        }
        return ResponseEntity.ok(workflowService.modifyRule(ruleId, request));
    }

    @GetMapping("/evaluate-ruleset/{ruleSetId}")
    public ResponseEntity<RuleEvaluationWorkflowResult> evaluateRuleSet(@PathVariable long ruleSetId) {
        if (!enabled) {
            return ResponseEntity.status(503).body(
                    RuleEvaluationWorkflowResult.failure(ruleSetId, disabledMessage()));
        }
        return ResponseEntity.ok(workflowService.evaluateRuleSet(ruleSetId));
    }

    @PostMapping("/optimize")
    public ResponseEntity<RuleWorkflowResult> optimize(@RequestBody OptimizeRuleWorkflowRequest request) {
        if (!enabled) {
            return ResponseEntity.status(503).body(RuleWorkflowResult.failure("optimize", disabledMessage()));
        }
        return ResponseEntity.ok(workflowService.optimize(request));
    }

    @GetMapping("/performance/{ruleId}")
    public ResponseEntity<PerformanceAnalysisResult> analyzePerformance(@PathVariable long ruleId) {
        if (!enabled) {
            return ResponseEntity.status(503).body(PerformanceAnalysisResult.failure(ruleId, disabledMessage()));
        }
        return ResponseEntity.ok(workflowService.analyzePerformance(ruleId));
    }

    private static String disabledMessage() {
        return "AI rule workflow is disabled. Set opencrm.ai.agents.enabled=true for local/offline use.";
    }
}
