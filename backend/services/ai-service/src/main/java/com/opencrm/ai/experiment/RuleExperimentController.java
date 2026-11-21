package com.opencrm.ai.experiment;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Price-Drools–compatible experiment surface under {@code /api/v1/ai/rules/experiments}.
 * Gated by {@code opencrm.ai.experiments.enabled} (default false). No MCP endpoints.
 */
@RestController
@RequestMapping("/api/v1/ai/rules/experiments")
public class RuleExperimentController {

    private final RuleExperimentService experimentService;
    private final boolean enabled;

    public RuleExperimentController(
            RuleExperimentService experimentService,
            @Value("${opencrm.ai.experiments.enabled:false}") boolean enabled) {
        this.experimentService = experimentService;
        this.enabled = enabled;
    }

    @PostMapping
    public ResponseEntity<ExperimentResponse> create(@Valid @RequestBody CreateExperimentRequest request) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.create(request));
    }

    @PostMapping("/{experimentId}/start")
    public ResponseEntity<ExperimentResponse> start(@PathVariable UUID experimentId) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.start(experimentId));
    }

    @PostMapping("/{experimentId}/complete")
    public ResponseEntity<ExperimentResponse> complete(
            @PathVariable UUID experimentId,
            @RequestParam(required = false) UUID winningVariantId) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.complete(experimentId, winningVariantId));
    }

    @PostMapping("/{experimentId}/apply")
    public ResponseEntity<ExperimentResponse> apply(
            @PathVariable UUID experimentId,
            @Valid @RequestBody ApplyExperimentRequest request) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.applyWinningVariant(experimentId, request));
    }

    @GetMapping
    public ResponseEntity<List<ExperimentResponse>> listAll() {
        if (!enabled) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(experimentService.listAll());
    }

    @GetMapping("/rule/{ruleId}")
    public ResponseEntity<List<ExperimentResponse>> listForRule(@PathVariable long ruleId) {
        if (!enabled) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(experimentService.listForRule(ruleId));
    }

    @GetMapping("/{experimentId}")
    public ResponseEntity<ExperimentResponse> get(@PathVariable UUID experimentId) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.get(experimentId));
    }

    @GetMapping("/{experimentId}/variants")
    public ResponseEntity<List<ExperimentResponse.VariantResponse>> variants(@PathVariable UUID experimentId) {
        if (!enabled) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(experimentService.listVariants(experimentId));
    }

    @PostMapping("/{experimentId}/variants/{variantId}/metrics")
    public ResponseEntity<ExperimentResponse> updateMetrics(
            @PathVariable UUID experimentId,
            @PathVariable UUID variantId,
            @RequestBody UpdateVariantMetricsRequest request) {
        if (!enabled) {
            return disabled();
        }
        return ResponseEntity.ok(experimentService.updateMetrics(experimentId, variantId, request));
    }

    private ResponseEntity<ExperimentResponse> disabled() {
        return ResponseEntity.status(503).body(ExperimentResponse.failure(
                "AI rule experiments are disabled. Set opencrm.ai.experiments.enabled=true for local/offline use."));
    }
}
