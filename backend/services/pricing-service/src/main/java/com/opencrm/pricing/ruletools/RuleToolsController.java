package com.opencrm.pricing.ruletools;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.rule.RuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flagged PD1 rule tools: conflicts, templates, test suites. Default off.
 */
@RestController
@RequestMapping("/api/v1/rules")
@Tag(name = "Rule tools", description = "Advisory rule conflict, template, and test-suite endpoints")
public class RuleToolsController {

    private final RuleConflictAnalyzer conflictAnalyzer;
    private final RuleTemplateService templateService;
    private final RuleTestSuiteService testSuiteService;
    private final boolean enabled;

    public RuleToolsController(
            RuleConflictAnalyzer conflictAnalyzer,
            RuleTemplateService templateService,
            RuleTestSuiteService testSuiteService,
            @Value("${opencrm.pricing.rule-tools.enabled:false}") boolean enabled) {
        this.conflictAnalyzer = conflictAnalyzer;
        this.templateService = templateService;
        this.testSuiteService = testSuiteService;
        this.enabled = enabled;
    }

    @GetMapping("/by-rule-set/{ruleSetId}/conflicts")
    @Operation(summary = "Analyze rule conflicts", description = "Static conflict analysis over stored DRL; never mutates rules")
    public ApiResponse<RuleConflictReportDto> conflicts(@PathVariable Long ruleSetId) {
        requireEnabled();
        return ApiResponse.success(conflictAnalyzer.analyze(ruleSetId), "Rule conflict analysis completed");
    }

    @PostMapping("/templates/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create DRAFT rule from template", description = "Generates DRL and creates a DRAFT rule; never activates the rule set")
    public ApiResponse<RuleDto> applyTemplate(
            @PathVariable String kind,
            @Valid @RequestBody RuleTemplateApplyRequest request) {
        requireEnabled();
        RuleDto created = templateService.apply(RuleTemplateKind.fromPath(kind), request);
        return ApiResponse.success(created, "Draft rule created from template");
    }

    @PostMapping("/test-suites/run")
    @Operation(summary = "Run a rule test suite", description = "Wraps pricing simulate; does not add a parallel engine")
    public ApiResponse<RuleTestSuiteResultDto> runSuite(@Valid @RequestBody RuleTestSuiteRunRequest request) {
        requireEnabled();
        return ApiResponse.success(testSuiteService.run(request), "Rule test suite completed");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(
                    "Pricing rule tools are disabled. Set opencrm.pricing.rule-tools.enabled=true for local/offline use.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FEATURE_DISABLED");
        }
    }
}
