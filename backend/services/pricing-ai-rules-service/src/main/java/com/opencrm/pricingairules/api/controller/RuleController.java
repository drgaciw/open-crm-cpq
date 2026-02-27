package com.opencrm.pricingairules.api.controller;

import com.opencrm.common.dto.ApiResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<ApiResponse<List<RuleSetDTO>>> getAllRuleSets() {
        log.debug("Received request to get all rule sets");

        List<RuleSet> ruleSets = ruleSetRepository.findAll();
        List<RuleSetDTO> ruleSetDTOs = ruleSets.stream()
                .map(pricingMapper::toRuleSetDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(ruleSetDTOs));
    }

    @GetMapping("/sets/{id}")
    @Timed(value = "rules.get.set", description = "Time taken to get a rule set")
    public ResponseEntity<ApiResponse<RuleSetDTO>> getRuleSet(@PathVariable Long id) {
        log.debug("Received request to get rule set {}", id);

        return ruleSetRepository.findById(id)
                .map(pricingMapper::toRuleSetDTO)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Rule set not found")));
    }

    @PostMapping("/sets")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.create.set", description = "Time taken to create a rule set")
    public ResponseEntity<ApiResponse<RuleSetDTO>> createRuleSet(@RequestBody RuleSetDTO ruleSetDTO) {
        log.debug("Received request to create rule set: {}", ruleSetDTO.getName());

        try {
            RuleSet ruleSet = pricingMapper.toRuleSet(ruleSetDTO);
            RuleSet savedRuleSet = ruleSetRepository.save(ruleSet);

            // Compile rule set to verify it's valid
            boolean compiled = droolsService.compileRuleSet(savedRuleSet);
            if (!compiled) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Rule set compilation failed"));
            }

            return ResponseEntity.ok(ApiResponse.success(pricingMapper.toRuleSetDTO(savedRuleSet)));
        } catch (Exception e) {
            log.error("Error creating rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/sets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.update.set", description = "Time taken to update a rule set")
    public ResponseEntity<ApiResponse<RuleSetDTO>> updateRuleSet(@PathVariable Long id, @RequestBody RuleSetDTO ruleSetDTO) {
        log.debug("Received request to update rule set {}", id);

        try {
            RuleSet ruleSet = ruleSetRepository.findById(id).orElse(null);
            if (ruleSet == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule set not found"));
            }

            pricingMapper.updateRuleSet(ruleSet, ruleSetDTO);
            RuleSet savedRuleSet = ruleSetRepository.save(ruleSet);

            // Compile rule set to verify it's valid
            boolean compiled = droolsService.compileRuleSet(savedRuleSet);
            if (!compiled) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Rule set compilation failed"));
            }

            // Clear rule cache
            droolsService.clearRuleCache(savedRuleSet.getName());

            return ResponseEntity.ok(ApiResponse.success(pricingMapper.toRuleSetDTO(savedRuleSet)));
        } catch (Exception e) {
            log.error("Error updating rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/sets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.delete.set", description = "Time taken to delete a rule set")
    public ResponseEntity<ApiResponse<Void>> deleteRuleSet(@PathVariable Long id) {
        log.debug("Received request to delete rule set {}", id);

        try {
            RuleSet ruleSet = ruleSetRepository.findById(id).orElse(null);
            if (ruleSet == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule set not found"));
            }

            // Clear rule cache before deletion
            droolsService.clearRuleCache(ruleSet.getName());
            ruleSetRepository.delete(ruleSet);

            return ResponseEntity.ok(ApiResponse.success(null, "Rule set deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting rule set: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/sets/{ruleSetId}/rules")
    @Timed(value = "rules.get.rules", description = "Time taken to get rules for a rule set")
    public ResponseEntity<ApiResponse<List<RuleDTO>>> getRulesForRuleSet(@PathVariable Long ruleSetId) {
        log.debug("Received request to get rules for rule set {}", ruleSetId);

        RuleSet ruleSet = ruleSetRepository.findById(ruleSetId).orElse(null);
        if (ruleSet == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule set not found"));
        }

        List<Rule> rules = ruleRepository.findByRuleSet(ruleSet);
        List<RuleDTO> ruleDTOs = rules.stream()
                .map(pricingMapper::toRuleDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(ruleDTOs));
    }

    @GetMapping("/rules/{id}")
    @Timed(value = "rules.get.rule", description = "Time taken to get a rule")
    public ResponseEntity<ApiResponse<RuleDTO>> getRule(@PathVariable Long id) {
        log.debug("Received request to get rule {}", id);

        return ruleRepository.findById(id)
                .map(pricingMapper::toRuleDTO)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Rule not found")));
    }

    @PostMapping("/sets/{ruleSetId}/rules")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.create.rule", description = "Time taken to create a rule")
    public ResponseEntity<ApiResponse<RuleDTO>> createRule(@PathVariable Long ruleSetId, @RequestBody RuleDTO ruleDTO) {
        log.debug("Received request to create rule for rule set {}", ruleSetId);

        try {
            RuleSet ruleSet = ruleSetRepository.findById(ruleSetId).orElse(null);
            if (ruleSet == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule set not found"));
            }

            Rule rule = pricingMapper.toRule(ruleDTO);
            rule.setRuleSet(ruleSet);
            Rule savedRule = ruleRepository.save(rule);

            // Compile rule to verify it's valid
            boolean compiled = droolsService.compileRule(savedRule);
            if (!compiled) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Rule compilation failed"));
            }

            // Clear rule cache
            droolsService.clearRuleCache(ruleSet.getName());

            return ResponseEntity.ok(ApiResponse.success(pricingMapper.toRuleDTO(savedRule)));
        } catch (Exception e) {
            log.error("Error creating rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.update.rule", description = "Time taken to update a rule")
    public ResponseEntity<ApiResponse<RuleDTO>> updateRule(@PathVariable Long id, @RequestBody RuleDTO ruleDTO) {
        log.debug("Received request to update rule {}", id);

        try {
            Rule rule = ruleRepository.findById(id).orElse(null);
            if (rule == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule not found"));
            }

            pricingMapper.updateRule(rule, ruleDTO);
            Rule savedRule = ruleRepository.save(rule);

            // Compile rule to verify it's valid
            boolean compiled = droolsService.compileRule(savedRule);
            if (!compiled) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Rule compilation failed"));
            }

            // Clear rule cache
            droolsService.clearRuleCache(savedRule.getRuleSet().getName());

            return ResponseEntity.ok(ApiResponse.success(pricingMapper.toRuleDTO(savedRule)));
        } catch (Exception e) {
            log.error("Error updating rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.delete.rule", description = "Time taken to delete a rule")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        log.debug("Received request to delete rule {}", id);

        try {
            Rule rule = ruleRepository.findById(id).orElse(null);
            if (rule == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule not found"));
            }

            String ruleSetName = rule.getRuleSet().getName();
            ruleRepository.delete(rule);
            droolsService.clearRuleCache(ruleSetName);

            return ResponseEntity.ok(ApiResponse.success(null, "Rule deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/rules/{id}/validate")
    @Timed(value = "rules.validate", description = "Time taken to validate a rule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateRule(@PathVariable Long id) {
        log.debug("Received request to validate rule {}", id);

        try {
            Rule rule = ruleRepository.findById(id).orElse(null);
            if (rule == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Rule not found"));
            }

            boolean valid = droolsService.compileRule(rule);

            Map<String, Object> data = new HashMap<>();
            data.put("valid", valid);

            if (valid) {
                return ResponseEntity.ok(ApiResponse.success(data, "Rule is valid"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Rule is invalid"));
            }
        } catch (Exception e) {
            log.error("Error validating rule: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "rules.cache.clear", description = "Time taken to clear rule cache")
    public ResponseEntity<ApiResponse<Void>> clearRuleCache(@RequestParam String ruleSetName) {
        log.debug("Received request to clear rule cache for rule set {}", ruleSetName);

        try {
            droolsService.clearRuleCache(ruleSetName);
            return ResponseEntity.ok(ApiResponse.success(null, "Rule cache cleared successfully"));
        } catch (Exception e) {
            log.error("Error clearing rule cache: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
