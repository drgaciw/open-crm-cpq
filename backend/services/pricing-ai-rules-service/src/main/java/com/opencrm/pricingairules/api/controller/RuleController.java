package com.opencrm.pricingairules.api.controller;

import com.opencrm.pricingairules.api.dto.RuleDTO;
import com.opencrm.pricingairules.api.dto.RuleSetDTO;
import com.opencrm.pricingairules.api.mapper.PricingMapper;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import com.opencrm.pricingairules.domain.repository.RuleSetRepository;
import com.opencrm.pricingairules.service.drools.DroolsService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Slf4j
public class RuleController {

    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final DroolsService droolsService;
    private final PricingMapper pricingMapper;
    
    @GetMapping("/sets")
    @Timed(value = "rules.get.sets", description = "Time taken to get all rule sets")
    public ResponseEntity<List<RuleSetDTO>> getAllRuleSets() {
        log.debug("Received request to get all rule sets");
        
        List<RuleSet> ruleSets = ruleSetRepository.findAll();
        List<RuleSetDTO> ruleSetDTOs = ruleSets.stream()
                .map(pricingMapper::toRuleSetDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ruleSetDTOs);
    }
    
    @GetMapping("/sets/{id}")
    @Timed(value = "rules.get.set", description = "Time taken to get a rule set")
    public ResponseEntity<RuleSetDTO> getRuleSet(@PathVariable Long id) {
        log.debug("Received request to get rule set {}", id);
        
        return ruleSetRepository.findById(id)
                .map(pricingMapper::toRuleSetDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/sets")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.create.set", description = "Time taken to create a rule set")
    public ResponseEntity<RuleSetDTO> createRuleSet(@RequestBody RuleSetDTO ruleSetDTO) {
        log.debug("Received request to create rule set: {}", ruleSetDTO.getName());
        
        try {
            RuleSet ruleSet = pricingMapper.toRuleSet(ruleSetDTO);
            ruleSet.setCreatedAt(ZonedDateTime.now());
            ruleSet.setUpdatedAt(ZonedDateTime.now());
            
            RuleSet savedRuleSet = ruleSetRepository.save(ruleSet);
            
            // Compile rule set to verify it's valid
            boolean compiled = droolsService.compileRuleSet(savedRuleSet);
            if (!compiled) {
                return ResponseEntity.badRequest().build();
            }
            
            return ResponseEntity.ok(pricingMapper.toRuleSetDTO(savedRuleSet));
        } catch (Exception e) {
            log.error("Error creating rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/sets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.update.set", description = "Time taken to update a rule set")
    public ResponseEntity<RuleSetDTO> updateRuleSet(@PathVariable Long id, @RequestBody RuleSetDTO ruleSetDTO) {
        log.debug("Received request to update rule set {}", id);
        
        try {
            return ruleSetRepository.findById(id)
                    .map(ruleSet -> {
                        pricingMapper.updateRuleSet(ruleSet, ruleSetDTO);
                        ruleSet.setUpdatedAt(ZonedDateTime.now());
                        
                        RuleSet savedRuleSet = ruleSetRepository.save(ruleSet);
                        
                        // Compile rule set to verify it's valid
                        boolean compiled = droolsService.compileRuleSet(savedRuleSet);
                        if (!compiled) {
                            return ResponseEntity.badRequest().<RuleSetDTO>build();
                        }
                        
                        // Clear rule cache
                        droolsService.clearRuleCache(savedRuleSet.getName());
                        
                        return ResponseEntity.ok(pricingMapper.toRuleSetDTO(savedRuleSet));
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error updating rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/sets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.delete.set", description = "Time taken to delete a rule set")
    public ResponseEntity<Map<String, Object>> deleteRuleSet(@PathVariable Long id) {
        log.debug("Received request to delete rule set {}", id);
        
        try {
            return ruleSetRepository.findById(id)
                    .map(ruleSet -> {
                        // Clear rule cache before deletion
                        droolsService.clearRuleCache(ruleSet.getName());
                        
                        ruleSetRepository.delete(ruleSet);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "Rule set deleted successfully");
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error deleting rule set: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting rule set: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/sets/{ruleSetId}/rules")
    @Timed(value = "rules.get.rules", description = "Time taken to get rules for a rule set")
    public ResponseEntity<List<RuleDTO>> getRulesForRuleSet(@PathVariable Long ruleSetId) {
        log.debug("Received request to get rules for rule set {}", ruleSetId);
        
        return ruleSetRepository.findById(ruleSetId)
                .map(ruleSet -> {
                    List<Rule> rules = ruleRepository.findByRuleSet(ruleSet);
                    List<RuleDTO> ruleDTOs = rules.stream()
                            .map(pricingMapper::toRuleDTO)
                            .collect(Collectors.toList());
                    
                    return ResponseEntity.ok(ruleDTOs);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/rules/{id}")
    @Timed(value = "rules.get.rule", description = "Time taken to get a rule")
    public ResponseEntity<RuleDTO> getRule(@PathVariable Long id) {
        log.debug("Received request to get rule {}", id);
        
        return ruleRepository.findById(id)
                .map(pricingMapper::toRuleDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/sets/{ruleSetId}/rules")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.create.rule", description = "Time taken to create a rule")
    public ResponseEntity<RuleDTO> createRule(@PathVariable Long ruleSetId, @RequestBody RuleDTO ruleDTO) {
        log.debug("Received request to create rule for rule set {}", ruleSetId);
        
        try {
            return ruleSetRepository.findById(ruleSetId)
                    .map(ruleSet -> {
                        Rule rule = pricingMapper.toRule(ruleDTO);
                        rule.setRuleSet(ruleSet);
                        rule.setCreatedAt(ZonedDateTime.now());
                        rule.setUpdatedAt(ZonedDateTime.now());
                        
                        Rule savedRule = ruleRepository.save(rule);
                        
                        // Compile rule to verify it's valid
                        boolean compiled = droolsService.compileRule(savedRule);
                        if (!compiled) {
                            return ResponseEntity.badRequest().<RuleDTO>build();
                        }
                        
                        // Clear rule cache
                        droolsService.clearRuleCache(ruleSet.getName());
                        
                        return ResponseEntity.ok(pricingMapper.toRuleDTO(savedRule));
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error creating rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.update.rule", description = "Time taken to update a rule")
    public ResponseEntity<RuleDTO> updateRule(@PathVariable Long id, @RequestBody RuleDTO ruleDTO) {
        log.debug("Received request to update rule {}", id);
        
        try {
            return ruleRepository.findById(id)
                    .map(rule -> {
                        pricingMapper.updateRule(rule, ruleDTO);
                        rule.setUpdatedAt(ZonedDateTime.now());
                        
                        Rule savedRule = ruleRepository.save(rule);
                        
                        // Compile rule to verify it's valid
                        boolean compiled = droolsService.compileRule(savedRule);
                        if (!compiled) {
                            return ResponseEntity.badRequest().<RuleDTO>build();
                        }
                        
                        // Clear rule cache
                        droolsService.clearRuleCache(savedRule.getRuleSet().getName());
                        
                        return ResponseEntity.ok(pricingMapper.toRuleDTO(savedRule));
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error updating rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.delete.rule", description = "Time taken to delete a rule")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        log.debug("Received request to delete rule {}", id);
        
        try {
            return ruleRepository.findById(id)
                    .map(rule -> {
                        String ruleSetName = rule.getRuleSet().getName();
                        
                        // Delete the rule
                        ruleRepository.delete(rule);
                        
                        // Clear rule cache
                        droolsService.clearRuleCache(ruleSetName);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("message", "Rule deleted successfully");
                        
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error deleting rule: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting rule: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/rules/{id}/validate")
    @Timed(value = "rules.validate", description = "Time taken to validate a rule")
    public ResponseEntity<Map<String, Object>> validateRule(@PathVariable Long id) {
        log.debug("Received request to validate rule {}", id);
        
        try {
            return ruleRepository.findById(id)
                    .map(rule -> {
                        boolean valid = droolsService.compileRule(rule);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("valid", valid);
                        
                        if (valid) {
                            response.put("message", "Rule is valid");
                            return ResponseEntity.ok(response);
                        } else {
                            response.put("message", "Rule is invalid");
                            return ResponseEntity.badRequest().body(response);
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error validating rule: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Error validating rule: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.cache.clear", description = "Time taken to clear rule cache")
    public ResponseEntity<Map<String, Object>> clearRuleCache(@RequestParam String ruleSetName) {
        log.debug("Received request to clear rule cache for rule set {}", ruleSetName);
        
        try {
            droolsService.clearRuleCache(ruleSetName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Rule cache cleared successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing rule cache: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error clearing rule cache: " + e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
}
