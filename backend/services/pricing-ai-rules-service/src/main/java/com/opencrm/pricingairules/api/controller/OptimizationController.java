package com.opencrm.pricingairules.api.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.repository.OptimizationMetricsRepository;
import com.opencrm.pricingairules.domain.repository.RuleOptimizationRepository;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import com.opencrm.pricingairules.domain.repository.RuleSetRepository;
import com.opencrm.pricingairules.service.optimization.RuleOptimizationService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/optimization")
@RequiredArgsConstructor
@Slf4j
public class OptimizationController {

    private final RuleOptimizationService optimizationService;
    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleOptimizationRepository optimizationRepository;
    private final OptimizationMetricsRepository optimizationMetricsRepository;

    @GetMapping("/opportunities")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "optimization.opportunities", description = "Time taken to find optimization opportunities")
    public ResponseEntity<ApiResponse<Map<String, Object>>> findOptimizationOpportunities(@RequestParam Long ruleId) {
        log.debug("Received request to find optimization opportunities for rule {}", ruleId);

        try {
            Rule rule = ruleRepository.findById(ruleId).orElse(null);
            if (rule == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule not found"));
            }

            List<RuleOptimization> opportunities = optimizationService.findOptimizationOpportunities(rule);

            Map<String, Object> data = new HashMap<>();
            data.put("ruleId", ruleId);
            data.put("opportunitiesCount", opportunities.size());
            data.put("opportunities", opportunities.stream()
                    .map(this::mapOptimizationToResponse)
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("Error finding optimization opportunities: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/optimize-rule-set")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "optimization.rule-set", description = "Time taken to optimize a rule set")
    public ResponseEntity<ApiResponse<Map<String, Object>>> optimizeRuleSet(@RequestParam Long ruleSetId) {
        log.debug("Received request to optimize rule set {}", ruleSetId);

        try {
            RuleSet ruleSet = ruleSetRepository.findById(ruleSetId).orElse(null);
            if (ruleSet == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule set not found"));
            }

            List<RuleOptimization> optimizations = optimizationService.optimizeRuleSet(ruleSet);

            Map<String, Object> data = new HashMap<>();
            data.put("ruleSetId", ruleSetId);
            data.put("optimizationsCount", optimizations.size());
            data.put("optimizations", optimizations.stream()
                    .map(this::mapOptimizationToResponse)
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("Error optimizing rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "optimization.apply", description = "Time taken to apply an optimization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyOptimization(@RequestParam Long optimizationId) {
        log.debug("Received request to apply optimization {}", optimizationId);

        try {
            RuleOptimization optimization = optimizationRepository.findById(optimizationId).orElse(null);
            if (optimization == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Optimization not found"));
            }

            boolean applied = optimizationService.applyOptimization(optimization);

            Map<String, Object> data = new HashMap<>();
            data.put("optimizationId", optimizationId);

            String message = applied ? "Optimization applied successfully" : "Failed to apply optimization";
            return ResponseEntity.ok(ApiResponse.success(data, message));
        } catch (Exception e) {
            log.error("Error applying optimization: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/test")
    @Timed(value = "optimization.test", description = "Time taken to test an optimization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testOptimization(@RequestParam Long optimizationId) {
        log.debug("Received request to test optimization {}", optimizationId);

        try {
            RuleOptimization optimization = optimizationRepository.findById(optimizationId).orElse(null);
            if (optimization == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Optimization not found"));
            }

            double improvementPct = optimizationService.testOptimization(optimization);

            Map<String, Object> data = new HashMap<>();
            data.put("optimizationId", optimizationId);
            data.put("improvementPercentage", improvementPct);

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("Error testing optimization: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/metrics")
    @Timed(value = "optimization.metrics", description = "Time taken to get optimization metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOptimizationMetrics() {
        log.debug("Received request to get optimization metrics");

        try {
            Map<String, BigDecimal> averageImprovementByType = new HashMap<>();
            List<String> optimizationTypes = optimizationRepository.findDistinctOptimizationTypes();

            for (String type : optimizationTypes) {
                BigDecimal avgImprovement = optimizationMetricsRepository.getAverageImprovementForOptimizationType(type);
                if (avgImprovement != null) {
                    averageImprovementByType.put(type, avgImprovement);
                }
            }

            long totalOptimizations = optimizationRepository.count();
            long appliedOptimizations = optimizationRepository.countByApplied(true);

            Map<String, Object> data = new HashMap<>();
            data.put("totalOptimizations", totalOptimizations);
            data.put("appliedOptimizations", appliedOptimizations);
            data.put("averageImprovementByType", averageImprovementByType);

            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("Error getting optimization metrics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private Map<String, Object> mapOptimizationToResponse(RuleOptimization optimization) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", optimization.getId());
        result.put("ruleId", optimization.getRule() != null ? optimization.getRule().getId() : null);
        result.put("ruleName", optimization.getRule() != null ? optimization.getRule().getName() : null);
        result.put("optimizationType", optimization.getOptimizationType());
        result.put("applied", optimization.getApplied());
        result.put("performanceImprovement", optimization.getPerformanceImprovementPct());

        return result;
    }
}
