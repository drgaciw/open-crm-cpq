package com.opencrm.pricingairules.service.drools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricingairules.domain.model.Product;
import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.model.QuoteLineItem;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.repository.RuleExecutionMetricsRepository;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import com.opencrm.pricingairules.domain.repository.RuleSetRepository;
import com.opencrm.pricingairules.service.drools.dto.DroolsRuleExecutionMetrics;
import com.opencrm.pricingairules.service.drools.dto.RuleCacheMetrics;
import com.opencrm.pricingairules.service.drools.dto.RuleDeploymentResult;
import com.opencrm.pricingairules.service.drools.dto.RuleExecutionMetrics;
import com.opencrm.pricingairules.service.drools.dto.RuleSetMetadata;
import com.opencrm.pricingairules.service.drools.dto.RuleValidationResult;
import com.opencrm.pricingairules.service.drools.dto.ValidationError;
import com.opencrm.pricingairules.service.optimization.RuleOptimizationService;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngine;
import com.opencrm.rules.engine.RulesEngineType;
import com.opencrm.rules.model.CustomerFact;
import com.opencrm.rules.model.ProductFact;
import com.opencrm.rules.model.QuoteFact;
import com.opencrm.rules.model.QuoteLineItemFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class DroolsServiceImpl implements DroolsService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String VERSION_LATEST = "latest";
    private static final String DEFAULT_VERSION = "latest";
    private static final String TEMP_RULE_FILE = "src/main/resources/temp_rule.drl";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;");

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleExecutionMetricsRepository metricsRepository;
    private final RuleOptimizationService ruleOptimizationService;
    private final DroolsMetricsInterceptor metricsInterceptor;
    private final RulesEngine rulesEngine;
    private final DroolsRuleCompiler droolsRuleCompiler;
    private final ObjectMapper objectMapper;

    private final KieServices kieServices = KieServices.Factory.get();

    @Override
    public Quote evaluateQuote(Quote quote, String ruleSetName) {
        return evaluateQuote(quote, ruleSetName, Map.of());
    }

    @Override
    public Quote evaluateQuote(Quote quote, String ruleSetName, Map<String, Object> additionalFacts) {
        RuleSet ruleSet = findRuleSetByNameAndVersion(ruleSetName, VERSION_LATEST);
        long startTime = System.currentTimeMillis();

        try {
            QuoteFact quoteFact = toQuoteFact(quote);
            List<Object> facts = new ArrayList<>();
            facts.add(quoteFact);
            addLineItemFacts(quote, quoteFact, facts);
            facts.addAll(additionalFacts.values());

            RuleExecutionContext context = RuleExecutionContext.builder()
                    .ruleSetId(ruleSet.getName())
                    .ruleSetVersion(ruleSet.getVersion())
                    .engineType(RulesEngineType.DROOLS)
                    .facts(facts)
                    .globals(Map.of("logFinancialMetric", new FinancialMetricLogger()))
                    .build();

            List<RuleDefinition> rules = buildActiveRuleDefinitions(ruleSet);
            RuleExecutionResult result = rulesEngine.execute(context, rules);

            applyResultsToQuote(quote, result, quoteFact);

            long executionTimeMs = System.currentTimeMillis() - startTime;
            saveExecutionMetrics(ruleSet, quote, executionTimeMs, true, null);
            metricsInterceptor.recordRuleExecution(ruleSet.getName(), executionTimeMs);
            return quote;
        } catch (Exception e) {
            long executionTimeMs = System.currentTimeMillis() - startTime;
            saveExecutionMetrics(ruleSet, quote, executionTimeMs, false, e.getMessage());
            log.error("Failed to evaluate quote {} using rule set {}: {}",
                    quote.getExternalId(), ruleSetName, e.getMessage(), e);
            throw new RuntimeException("Failed to evaluate rules: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Quote> evaluateQuotes(Map<String, Quote> quotes, String ruleSetName) {
        Map<String, Quote> result = new HashMap<>();
        quotes.forEach((k, v) -> result.put(k, evaluateQuote(v, ruleSetName)));
        return result;
    }

    @Override
    public RuleDeploymentResult deployRules(String ruleContent) {
        RuleValidationResult validation = validateRule(ruleContent);
        if (!validation.isValid()) {
            return RuleDeploymentResult.builder()
                    .status(RuleDeploymentResult.DeploymentStatus.FAILED)
                    .message("Rule validation failed")
                    .validationErrors(validation.getErrors())
                    .build();
        }

        final String packageName = Optional.ofNullable(extractPackageName(ruleContent))
                .orElse("dynamic.pricing.rules");

        RuleSet ruleSet = ruleSetRepository.findByName(packageName)
                .orElseGet(() -> {
                    RuleSet rs = new RuleSet();
                    rs.setName(packageName);
                    rs.setVersion(DEFAULT_VERSION);
                    rs.setStatus(STATUS_ACTIVE);
                    rs.setEffectiveDate(ZonedDateTime.now());
                    rs.setExpirationDate(ZonedDateTime.now().plusYears(10));
                    return ruleSetRepository.save(rs);
                });

        Rule rule = new Rule();
        rule.setRuleSet(ruleSet);
        rule.setName("Rule_" + System.currentTimeMillis());
        rule.setDescription("Dynamically deployed rule");
        rule.setRuleType("PRICING");
        rule.setContent(ruleContent);
        rule.setPriority(0);
        rule.setStatus(STATUS_ACTIVE);
        rule = ruleRepository.save(rule);

        clearRuleCache(ruleSet.getName());
        return RuleDeploymentResult.builder()
                .id(rule.getId().toString())
                .status(RuleDeploymentResult.DeploymentStatus.DEPLOYED)
                .message("Rules deployed successfully")
                .build();
    }

    @Override
    public RuleDeploymentResult updateRules(String ruleContent, String version) {
        RuleDeploymentResult deploy = deployRules(ruleContent);
        if (deploy.getStatus() != RuleDeploymentResult.DeploymentStatus.DEPLOYED) {
            return deploy;
        }
        return RuleDeploymentResult.builder()
                .id(deploy.getId())
                .status(deploy.getStatus())
                .message("Rules updated for version " + version)
                .validationErrors(deploy.getValidationErrors())
                .build();
    }

    @Override
    public void undeployRules(String version) {
        ruleSetRepository.findByName(version).ifPresent(ruleSet -> {
            List<Rule> rules = ruleRepository.findByRuleSet(ruleSet);
            for (Rule rule : rules) {
                rule.setStatus(STATUS_INACTIVE);
                ruleRepository.save(rule);
            }
            clearRuleCache(ruleSet.getName());
        });
    }

    @Override
    public RuleValidationResult validateRules(String ruleContent) {
        return validateRule(ruleContent);
    }

    @Override
    public RuleValidationResult validateRuleSet(String ruleSetId) {
        try {
            RuleSet ruleSet = findRuleSetByName(ruleSetId);
            List<RuleDefinition> rules = buildActiveRuleDefinitions(ruleSet);
            if (rules.isEmpty()) {
                return RuleValidationResult.builder()
                        .valid(false)
                        .errors(List.of(ValidationError.builder()
                                .severity(ValidationError.ErrorSeverity.ERROR)
                                .message("No active rules found for rule set")
                                .build()))
                        .build();
            }
            droolsRuleCompiler.compileRules(rules);
            return RuleValidationResult.builder().valid(true).build();
        } catch (Exception e) {
            return RuleValidationResult.builder()
                    .valid(false)
                    .errors(List.of(ValidationError.builder()
                            .severity(ValidationError.ErrorSeverity.ERROR)
                            .message("Rule set validation failed: " + e.getMessage())
                            .build()))
                    .build();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T executeRules(String ruleSetId, Map<String, Object> facts) {
        RuleSet ruleSet = findRuleSetByNameAndVersion(ruleSetId, VERSION_LATEST);
        RuleExecutionContext context = RuleExecutionContext.builder()
                .ruleSetId(ruleSet.getName())
                .ruleSetVersion(ruleSet.getVersion())
                .engineType(RulesEngineType.DROOLS)
                .facts(new ArrayList<>(facts.values()))
                .globals(Map.of("logFinancialMetric", new FinancialMetricLogger()))
                .build();

        rulesEngine.execute(context, buildActiveRuleDefinitions(ruleSet));
        return (T) facts;
    }

    @Override
    public <T> List<T> executeBatchRules(String ruleSetId, List<Map<String, Object>> facts) {
        List<T> results = new ArrayList<>();
        for (Map<String, Object> factMap : facts) {
            results.add(executeRules(ruleSetId, factMap));
        }
        return results;
    }

    @Override
    public RuleSetMetadata getRuleSetMetadata(String ruleSetId) {
        RuleSet ruleSet = findRuleSetByName(ruleSetId);
        List<Rule> rules = ruleRepository.findByRuleSetAndStatus(ruleSet, STATUS_ACTIVE);
        return RuleSetMetadata.builder()
                .id(ruleSet.getId() != null ? ruleSet.getId().toString() : null)
                .name(ruleSet.getName())
                .version(ruleSet.getVersion())
                .status(toRuleSetStatus(ruleSet.getStatus()))
                .createdAt(ruleSet.getCreatedAt())
                .updatedAt(ruleSet.getUpdatedAt())
                .author(ruleSet.getCreatedBy())
                .ruleCount((long) rules.size())
                .build();
    }

    @Override
    public List<RuleSetMetadata> listRuleSets() {
        List<RuleSetMetadata> list = new ArrayList<>();
        for (RuleSet ruleSet : ruleSetRepository.findAll()) {
            List<Rule> rules = ruleRepository.findByRuleSetAndStatus(ruleSet, STATUS_ACTIVE);
            list.add(RuleSetMetadata.builder()
                    .id(ruleSet.getId() != null ? ruleSet.getId().toString() : null)
                    .name(ruleSet.getName())
                    .version(ruleSet.getVersion())
                    .status(toRuleSetStatus(ruleSet.getStatus()))
                    .createdAt(ruleSet.getCreatedAt())
                    .updatedAt(ruleSet.getUpdatedAt())
                    .author(ruleSet.getCreatedBy())
                    .ruleCount((long) rules.size())
                    .build());
        }
        return list;
    }

    @Override
    public void reloadRuleSet(String ruleSetId) {
        clearRuleCache(ruleSetId);
        validateRuleSet(ruleSetId);
    }

    @Override
    public boolean compileRule(Rule rule) {
        try {
            String content = resolveOptimizedContent(rule);
            droolsRuleCompiler.compileFromDrlString(content);
            return true;
        } catch (Exception e) {
            log.warn("Rule compile failed for {}: {}", rule.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean compileRuleSet(RuleSet ruleSet) {
        try {
            List<RuleDefinition> rules = buildActiveRuleDefinitions(ruleSet);
            if (rules.isEmpty()) {
                return false;
            }
            droolsRuleCompiler.compileRules(rules);
            return true;
        } catch (Exception e) {
            log.warn("Rule set compile failed for {}: {}", ruleSet.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    @CacheEvict(value = "ruleCache", key = "#ruleSetName")
    public void clearRuleCache(String ruleSetName) {
        rulesEngine.invalidateCache(ruleSetName);
        log.debug("Cleared rule cache for {}", ruleSetName);
    }

    @Override
    public RuleExecutionMetrics getRuleExecutionMetrics(String ruleSetId) {
        List<DroolsRuleExecutionMetrics> metrics = metricsInterceptor.getMetricsForRule(ruleSetId);
        if (metrics == null || metrics.isEmpty()) {
            return RuleExecutionMetrics.builder().ruleName(ruleSetId).build();
        }

        long total = 0L;
        long last = 0L;
        for (DroolsRuleExecutionMetrics m : metrics) {
            if (m.getExecutionTimeMs() != null) {
                long current = Math.round(m.getExecutionTimeMs());
                total += current;
                last = current;
            }
        }

        return RuleExecutionMetrics.builder()
                .ruleName(ruleSetId)
                .executionCount(metrics.size())
                .totalExecutionTime(total)
                .lastExecutionTime(last)
                .build();
    }

    @Override
    public RuleCacheMetrics getRuleCacheMetrics(String ruleSetId) {
        return RuleCacheMetrics.builder()
                .size(0L)
                .capacity(1000L)
                .hitRate(0.0)
                .missRate(1.0)
                .evictions(0L)
                .loadTime(0L)
                .evictionPolicy("ENGINE_MANAGED")
                .ttl(3600000L)
                .build();
    }

    @Override
    public RuleValidationResult validateRule(String ruleContent) {
        try {
            KieFileSystem kfs = kieServices.newKieFileSystem();
            kfs.write(TEMP_RULE_FILE, ruleContent);
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();
            return toValidationResult(kieBuilder.getResults());
        } catch (Exception e) {
            return RuleValidationResult.builder()
                    .valid(false)
                    .errors(List.of(ValidationError.builder()
                            .severity(ValidationError.ErrorSeverity.ERROR)
                            .message("Validation error: " + e.getMessage())
                            .build()))
                    .build();
        }
    }

    @Override
    public RuleDeploymentResult deployTemporaryRule(String ruleId, String ruleContent) {
        RuleValidationResult validationResult = validateRule(ruleContent);
        return RuleDeploymentResult.builder()
                .id(ruleId)
                .status(validationResult.isValid()
                        ? RuleDeploymentResult.DeploymentStatus.DEPLOYED
                        : RuleDeploymentResult.DeploymentStatus.FAILED)
                .message(validationResult.isValid()
                        ? "Temporary rule deployed successfully"
                        : "Failed to deploy temporary rule")
                .validationErrors(validationResult.getErrors())
                .build();
    }

    @Override
    public void undeployTemporaryRule(String ruleId) {
        log.debug("Temporary rule {} undeployed (no-op)", ruleId);
    }

    private RuleSet findRuleSetByNameAndVersion(String name, String version) {
        return ruleSetRepository.findByNameAndVersion(name, version)
                .orElseGet(() -> ruleSetRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Rule set not found: " + name)));
    }

    private RuleSet findRuleSetByName(String name) {
        return ruleSetRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Rule set not found: " + name));
    }

    private List<RuleDefinition> buildActiveRuleDefinitions(RuleSet ruleSet) {
        List<Rule> activeRules = ruleRepository.findByRuleSetAndStatus(ruleSet, STATUS_ACTIVE);
        List<RuleDefinition> defs = new ArrayList<>(activeRules.size());
        for (Rule rule : activeRules) {
            defs.add(RuleDefinition.builder()
                    .id(rule.getId() != null ? rule.getId().toString() : null)
                    .name(rule.getName())
                    .description(rule.getDescription())
                    .content(resolveOptimizedContent(rule))
                    .priority(rule.getPriority() != null ? rule.getPriority() : 0)
                    .ruleType(rule.getRuleType())
                    .engineType("DROOLS")
                    .build());
        }
        return defs;
    }

    private String resolveOptimizedContent(Rule rule) {
        return ruleOptimizationService.optimizeRule(rule, rule.getContent());
    }

    private void addLineItemFacts(Quote quote, QuoteFact quoteFact, List<Object> facts) {
        if (quote.getLineItems() == null) {
            return;
        }

        for (QuoteLineItem lineItem : quote.getLineItems()) {
            QuoteLineItemFact lineFact = QuoteLineItemFact.builder()
                    .externalId(lineItem.getId() != null ? String.valueOf(lineItem.getId()) : null)
                    .lineItemType(lineItem.getLineItemType())
                    .productExternalId(lineItem.getProduct() != null ? lineItem.getProduct().getExternalId() : null)
                    .quantity(lineItem.getQuantity() != null ? lineItem.getQuantity() : 0)
                    .listPrice(lineItem.getListPrice())
                    .unitPrice(lineItem.getUnitPrice())
                    .discountRate(lineItem.getDiscountRate())
                    .finalPrice(lineItem.getFinalPrice())
                    .quote(quoteFact)
                    .build();
            copyJsonMetadata(lineItem.getMetadata(), lineFact.getMetadata());
            facts.add(lineFact);

            Product product = lineItem.getProduct();
            if (product != null) {
                ProductFact productFact = ProductFact.builder()
                        .externalId(product.getExternalId())
                        .name(product.getName())
                        .productType(product.getProductType())
                        .category(product.getCategory())
                        .listPrice(product.getListPrice())
                        .currency(product.getCurrency())
                        .build();
                facts.add(productFact);
            }
        }
    }

    private QuoteFact toQuoteFact(Quote quote) {
        CustomerFact customerFact = null;
        if (quote.getCustomer() != null) {
            customerFact = CustomerFact.builder()
                    .externalId(quote.getCustomer().getExternalId())
                    .name(quote.getCustomer().getName())
                    .type(quote.getCustomer().getType())
                    .segment(quote.getCustomer().getSegment())
                    .region(quote.getCustomer().getRegion())
                    .industry(quote.getCustomer().getIndustry())
                    .churnScore(quote.getCustomer().getChurnScore() != null
                            ? (int) Math.round(quote.getCustomer().getChurnScore())
                            : null)
                    .build();
        }

        QuoteFact quoteFact = QuoteFact.builder()
                .externalId(quote.getExternalId())
                .subscriptionType(quote.getSubscriptionType())
                .durationInMonths(quote.getDurationInMonths() != null ? quote.getDurationInMonths() : 0)
                .basePrice(quote.getBasePrice())
                .finalPrice(quote.getFinalPrice())
                .currency(quote.getCurrency())
                .customer(customerFact)
                .build();

        copyJsonMetadata(quote.getMetadata(), quoteFact.getMetadata());
        if (quote.getMonthlyPrice() != null) {
            quoteFact.setDecimal("monthlyPrice", quote.getMonthlyPrice());
        }
        if (quote.getARR() != null) {
            quoteFact.setDecimal("arr", quote.getARR());
        }
        if (quote.getTCV() != null) {
            quoteFact.setDecimal("tcv", quote.getTCV());
        }
        if (quote.getACV() != null) {
            quoteFact.setDecimal("acv", quote.getACV());
        }
        if (quote.getCLV() != null) {
            quoteFact.setDecimal("clv", quote.getCLV());
        }
        if (quote.getUserCount() != null) {
            quoteFact.setInt("userCount", quote.getUserCount());
        }
        if (quote.getExpectedDuration() != null) {
            quoteFact.setInt("expectedDuration", quote.getExpectedDuration());
        }
        if (quote.getIsMultiRegion() != null) {
            quoteFact.setBoolean("isMultiRegion", quote.getIsMultiRegion());
        }
        if (quote.getHasCustomSupport() != null) {
            quoteFact.setBoolean("hasCustomSupport", quote.getHasCustomSupport());
        }
        if (quote.getPriceMultiplier() != null) {
            quoteFact.setDecimal("priceMultiplier", quote.getPriceMultiplier());
        }
        return quoteFact;
    }

    private void applyResultsToQuote(Quote quote, RuleExecutionResult executionResult, QuoteFact originalQuoteFact) {
        QuoteFact quoteFact = originalQuoteFact;
        for (Object fact : executionResult.getModifiedFacts()) {
            if (fact instanceof QuoteFact qf) {
                quoteFact = qf;
                break;
            }
        }

        quote.setBasePrice(quoteFact.getBasePrice());
        quote.setFinalPrice(quoteFact.getFinalPrice());
        quote.setARR(quoteFact.getDecimal("arr"));
        quote.setTCV(quoteFact.getDecimal("tcv"));
        quote.setACV(quoteFact.getDecimal("acv"));
        quote.setCLV(quoteFact.getDecimal("clv"));
        if (quoteFact.getDecimal("priceMultiplier") != null) {
            quote.setPriceMultiplier(quoteFact.getDecimal("priceMultiplier"));
        }
        if (quoteFact.getDecimal("minimumCommitment") != null) {
            quote.setMetadata(mergeMetadataNode(quote.getMetadata(), Map.of(
                    "minimumCommitment", quoteFact.getDecimal("minimumCommitment"),
                    "explanationCode", quoteFact.getString("explanationCode"),
                    "rulesFired", executionResult.getRulesFired()
            )));
        } else {
            quote.setMetadata(mergeMetadataNode(quote.getMetadata(), Map.of(
                    "explanationCode", quoteFact.getString("explanationCode"),
                    "rulesFired", executionResult.getRulesFired()
            )));
        }
    }

    private void copyJsonMetadata(JsonNode node, Map<String, Object> target) {
        if (node == null || node.isNull() || !node.isObject()) {
            return;
        }
        Map<String, Object> map = objectMapper.convertValue(node, Map.class);
        if (map != null) {
            target.putAll(map);
        }
    }

    private JsonNode mergeMetadataNode(JsonNode existing, Map<String, Object> additions) {
        Map<String, Object> merged = new HashMap<>();
        if (existing != null && existing.isObject()) {
            Map<String, Object> current = objectMapper.convertValue(existing, Map.class);
            if (current != null) {
                merged.putAll(current);
            }
        }
        additions.forEach((k, v) -> {
            if (v != null) {
                merged.put(k, v);
            }
        });
        return objectMapper.valueToTree(merged);
    }

    private void saveExecutionMetrics(RuleSet ruleSet, Quote quote, long executionTimeMs, boolean success, String errorMessage) {
        com.opencrm.pricingairules.domain.model.RuleExecutionMetrics metrics =
                com.opencrm.pricingairules.domain.model.RuleExecutionMetrics.builder()
                        .ruleSet(ruleSet)
                        .quote(quote)
                        .executionTimeMs(executionTimeMs)
                        .success(success)
                        .errorMessage(errorMessage)
                        .build();
        metricsRepository.save(metrics);
    }

    private RuleValidationResult toValidationResult(Results results) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationError> warnings = new ArrayList<>();
        List<ValidationError> infos = new ArrayList<>();

        for (Message message : results.getMessages()) {
            ValidationError item = ValidationError.builder()
                    .message(message.getText())
                    .line(message.getLine())
                    .column(message.getColumn())
                    .severity(toSeverity(message.getLevel()))
                    .build();
            switch (item.getSeverity()) {
                case ERROR -> errors.add(item);
                case WARNING -> warnings.add(item);
                case INFO -> infos.add(item);
            }
        }

        return RuleValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .infos(infos)
                .build();
    }

    private ValidationError.ErrorSeverity toSeverity(Message.Level level) {
        if (level == Message.Level.ERROR) {
            return ValidationError.ErrorSeverity.ERROR;
        }
        if (level == Message.Level.WARNING) {
            return ValidationError.ErrorSeverity.WARNING;
        }
        return ValidationError.ErrorSeverity.INFO;
    }

    private RuleSetMetadata.RuleSetStatus toRuleSetStatus(String status) {
        if (status == null) {
            return RuleSetMetadata.RuleSetStatus.INACTIVE;
        }
        try {
            return RuleSetMetadata.RuleSetStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return RuleSetMetadata.RuleSetStatus.INACTIVE;
        }
    }

    private String extractPackageName(String ruleContent) {
        Matcher matcher = PACKAGE_PATTERN.matcher(ruleContent);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Slf4j
    public static class FinancialMetricLogger {
        public void log(Quote quote, String metricName, Object value) {
            log.debug("Drools metric {}={} for quote {}", metricName, value,
                    quote != null ? quote.getExternalId() : "unknown");
        }
    }
}
