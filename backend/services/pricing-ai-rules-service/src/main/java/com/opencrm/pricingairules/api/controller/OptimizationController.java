package com.opencrm.pricingairules.api.controller;

import com.opencrm.pricingairules.domain.model.OptimizationMetrics;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.repository.OptimizationMetricsRepository;
import com.opencrm.pricingairules.domain.repository.RuleOptimizationRepository;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import com.opencrm.pricingairules.domain.repository.RuleSetRepository;
import com.opencrm.pricingairules.service.optimization.RuleOptimizationService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<Map<String, Object>> findOptimizationOpportunities(@RequestParam Long ruleId) {
        log.debug("Received request to find optimization opportunities for rule {}", ruleId);
        
        try {
            return ruleRepository.findById(ruleId)
                    .map(rule -> {
                        List<RuleOptimization> opportunities = optimizationService.findOptimizationOpportunities(rule);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("ruleId", ruleId);
                        response.put("opportunitiesCount", opportunities.size());
                        response.put("opportunities", opportunities.stream()
                                .map(this::mapOptimizationToResponse)
                                .collect(Collectors.toList()));
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error finding optimization opportunities: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error finding optimization opportunities: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/optimize-rule-set")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "optimization.rule-set", description = "Time taken to optimize a rule set")
    public ResponseEntity<Map<String, Object>> optimizeRuleSet(@RequestParam Long ruleSetId) {
        log.debug("Received request to optimize rule set {}", ruleSetId);
        
        try {
            return ruleSetRepository.findById(ruleSetId)
                    .map(ruleSet -> {
                        List<RuleOptimization> optimizations = optimizationService.optimizeRuleSet(ruleSet);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("ruleSetId", ruleSetId);
                        response.put("optimizationsCount", optimizations.size());
                        response.put("optimizations", optimizations.stream()
                                .map(this::mapOptimizationToResponse)
                                .collect(Collectors.toList()));
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error optimizing rule set: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error optimizing rule set: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "optimization.apply", description = "Time taken to apply an optimization")
    public ResponseEntity<Map<String, Object>> applyOptimization(@RequestParam Long optimizationId) {
        log.debug("Received request to apply optimization {}", optimizationId);
        
        try {
            return optimizationRepository.findById(optimizationId)
                    .map(optimization -> {
                        boolean applied = optimizationService.applyOptimization(optimization);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", applied);
                        response.put("optimizationId", optimizationId);
                        response.put("message", applied 
                                ? "Optimization applied successfully" 
                                : "Failed to apply optimization");
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error applying optimization: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error applying optimization: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/test")
    @Timed(value = "optimization.test", description = "Time taken to test an optimization")
    public ResponseEntity<Map<String, Object>> testOptimization(@RequestParam Long optimizationId) {
        log.debug("Received request to test optimization {}", optimizationId);
        
        try {
            return optimizationRepository.findById(optimizationId)
                    .map(optimization -> {
                        double improvementPct = optimizationService.testOptimization(optimization);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("optimizationId", optimizationId);
                        response.put("improvementPercentage", improvementPct);
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error testing optimization: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error testing optimization: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/metrics")
    @Timed(value = "optimization.metrics", description = "Time taken to get optimization metrics")
    public ResponseEntity<Map<String, Object>> getOptimizationMetrics() {
        log.debug("Received request to get optimization metrics");
        
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Get average improvement by optimization type
            Map<String, BigDecimal> averageImprovementByType = new HashMap<>();
            List<String> optimizationTypes = optimizationRepository.findDistinctOptimizationTypes();
            
            for (String type : optimizationTypes) {
                BigDecimal avgImprovement = optimizationMetricsRepository.getAverageImprovementForOptimizationType(type);
                if (avgImprovement != null) {
                    averageImprovementByType.put(type, avgImprovement);
                }
            }
            
            // Get total optimization counts
            long totalOptimizations = optimizationRepository.count();
            long appliedOptimizations = optimizationRepository.countByApplied(true);
            
            response.put("totalOptimizations", totalOptimizations);
            response.put("appliedOptimizations", appliedOptimizations);
            response.put("averageImprovementByType", averageImprovementByType);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting optimization metrics: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting optimization metrics: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
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
