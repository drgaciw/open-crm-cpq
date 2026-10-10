package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngineType;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.rule.RuleStatus;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.ramp.RampPeriodPricer;
import com.opencrm.pricing.ramp.RampPricingProperties;
import com.opencrm.pricing.ramp.migration.HybridMigrationPolicy;
import com.opencrm.pricing.ramp.migration.MigrationWindowProperties;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.pricing.waterfall.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

/**
 * Main orchestrator for pricing execution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PricingExecutionService {

    private final CustomerRepository customerRepository;
    private final PricingProductRepository pricingProductRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final PriceBookResolutionClient priceBookResolutionClient;
    private final PricingAuditLogRepository auditLogRepository;
    private final PricingContextValidator contextValidator;
    private final MetricsCalculator metricsCalculator;
    private final ObjectMapper objectMapper;
    private final DroolsRulesEngine droolsRulesEngine;

    /** Default off. Spring sets this from {@link RampPricingProperties}. Tests may set it directly. */
    private boolean rampEnabled;

    private int maxPeriods = 36;
    private int migrationWindowMonths = 12;

    private final RampPeriodPricer rampPeriodPricer = new RampPeriodPricer();

    @Autowired
    void applyMigrationProperties(MigrationWindowProperties properties) {
        this.migrationWindowMonths = properties.getMigrationWindowMonths();
    }

    @Autowired
    void applyRampProperties(RampPricingProperties properties) {
        if (properties == null) {
            return;
        }
        this.rampEnabled = properties.isEnabled();
        this.maxPeriods = properties.getMaxPeriods();
    }

    public void setRampEnabled(boolean rampEnabled) {
        this.rampEnabled = rampEnabled;
    }

    /**
     * Execute pricing with full audit trail.
     */
    public PricingResult execute(PricingContext context) {
        return executeInternal(context, true);
    }

    /**
     * Simulate pricing without persisting audit log.
     */
    @Transactional(readOnly = true)
    public PricingResult simulate(PricingContext context) {
        log.debug("Simulating pricing for customer: {}", context.getCustomerExternalId());
        return executeInternal(context, false);
    }

    /**
     * Simulate multiple pricing contexts in one request.
     */
    @Transactional(readOnly = true)
    public BulkPricingSimulationResponse simulateBulk(List<PricingContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new BusinessException("At least one pricing context is required", "INVALID_CONTEXT");
        }

        List<PricingResult> results = contexts.stream()
                .map(this::simulate)
                .toList();

        int successful = (int) results.stream().filter(PricingResult::isSuccess).count();
        long totalExecutionTime = results.stream()
                .map(PricingResult::getExecutionTimeMs)
                .filter(value -> value != null)
                .reduce(0L, Long::sum);

        return BulkPricingSimulationResponse.builder()
                .totalRequests(results.size())
                .successfulRequests(successful)
                .failedRequests(results.size() - successful)
                .totalExecutionTimeMs(totalExecutionTime)
                .results(results)
                .build();
    }

    /**
     * Get audit trail for a quote.
     */
    @Transactional(readOnly = true)
    public List<PricingAuditLog> getAuditTrail(String quoteExternalId) {
        log.debug("Fetching audit trail for quote: {}", quoteExternalId);
        return auditLogRepository.findByQuoteExternalIdOrderByExecutedAtDesc(quoteExternalId);
    }

    private PricingResult executeInternal(PricingContext context, boolean persistAudit) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Validate context
            contextValidator.validate(context);
            if (requestsRamp(context) && !rampEnabled) {
                throw new BusinessException(
                        "A ramp schedule cannot be priced while opencrm.pricing.ramp.enabled is false",
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "RAMP_NOT_SUPPORTED");
            }

            // 2. Look up Customer
            Customer customer = customerRepository.findByExternalIdAndDeletedFalse(context.getCustomerExternalId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer with external ID: " + context.getCustomerExternalId()));

            // 3. Find active RuleSet
            List<RuleSet> activeRuleSets = ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(
                    RuleSetStatus.ACTIVE, LocalDate.now());

            if (activeRuleSets.isEmpty()) {
                log.warn("No active rule sets found, executing with default pricing");
            }

            RuleSet ruleSet = activeRuleSets.isEmpty() ? null : activeRuleSets.get(0);

            // 4. Load active Rules if rule set exists
            List<Rule> activeRules = ruleSet != null
                    ? ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(
                            ruleSet.getId(), RuleStatus.ACTIVE)
                    : new ArrayList<>();

            // 5. Resolve price book entries (batch) for list pricing
            LinkedHashSet<String> productsToResolve = new LinkedHashSet<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                if (lineRequest.getListPriceOverride() == null) {
                    productsToResolve.add(lineRequest.getProductExternalId());
                }
            }
            ResolveBatchResponse priceBookBatch =
                    priceBookResolutionClient.resolveBatch(context.getRegion(), new ArrayList<>(productsToResolve));

            // 5b. Pre-fetch any products that failed resolution (fallback for list prices)
            // This prevents an N+1 query problem inside the getListPrice loop.
            Set<String> unresolvedProductIds = new HashSet<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                if (lineRequest.getListPriceOverride() == null) {
                    String extId = lineRequest.getProductExternalId();
                    ProductEntryResolveDto resolved = priceBookBatch.getEntries() != null
                            ? priceBookBatch.getEntries().get(extId) : null;
                    if (resolved == null || !resolved.isEntryMatched() || resolved.getListPrice() == null) {
                        unresolvedProductIds.add(extId);
                    }
                }
            }

            Map<String, PricingProduct> fallbackProducts = new HashMap<>();
            // Reusable rate classification must come from catalog data even when the price book matches.
            context.getLineItems().stream()
                    .map(PricingLineRequest::getProductExternalId).forEach(unresolvedProductIds::add);
            if (!unresolvedProductIds.isEmpty()) {
                List<PricingProduct> products = pricingProductRepository
                        .findByExternalIdInAndDeletedFalse(unresolvedProductIds);
                for (PricingProduct p : products) {
                    fallbackProducts.put(p.getExternalId(), p);
                }
            }

            HybridMigrationPolicy migrationPolicy = new HybridMigrationPolicy(migrationWindowMonths);
            HybridMigrationPolicy.Plan migrationPlan = migrationPolicy.prepare(context, fallbackProducts, priceBookBatch);

            // 6. Initialize line results with list prices
            List<PricingLineResult> lineResults = new ArrayList<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                PricingProduct authoritativeProduct = fallbackProducts.get(lineRequest.getProductExternalId());
                BigDecimal listPrice = getListPrice(lineRequest, priceBookBatch, fallbackProducts);
                List<PricedPeriod> validatedPeriods = hasSchedule(lineRequest)
                        ? rampPeriodPricer.price(lineRequest, listPrice, context.getDurationInMonths(), maxPeriods) : null;
                if (validatedPeriods != null) {
                    String actualType = authoritativeProduct != null && authoritativeProduct.getProductType() != null
                            ? authoritativeProduct.getProductType().name() : null;
                    Set<String> fixedTypes = Set.of("SAAS_SEAT_NAMED", "SAAS_SEAT_CONCURRENT", "ONPREM_SUPPORT", "HYBRID_MIGRATION_RAMP");
                    Set<String> genericKinds = Set.of("PRODUCT", "SERVICE", "SUBSCRIPTION");
                    if (actualType == null || !fixedTypes.contains(actualType)
                            || (!genericKinds.contains(Objects.toString(lineRequest.getLineItemType(), ""))
                            && !actualType.equals(lineRequest.getLineItemType()))) {
                        throw new BusinessException("Scheduled reusable rates require an authoritative fixed recurring product and matching line kind",
                                HttpStatus.UNPROCESSABLE_ENTITY, "RAMP_SCHEDULE_INVALID");
                    }
                }
                Map<String, Object> serverMetadata = new HashMap<>();
                if (lineRequest.getMetadata() != null) serverMetadata.putAll(lineRequest.getMetadata());
                serverMetadata.remove("authoritativeProductType");

                PricingLineResult lineResult = PricingLineResult.builder()
                        .productExternalId(lineRequest.getProductExternalId())
                        .lineItemType(lineRequest.getLineItemType())
                        .quantity(lineRequest.getQuantity())
                        .listPrice(listPrice)
                        .unitPrice(listPrice)
                        .discountRate(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .surchargeAmount(BigDecimal.ZERO)
                        .finalPrice(listPrice.multiply(BigDecimal.valueOf(lineRequest.getQuantity())))
                        .explanationCodes(new ArrayList<>())
                        .metadata(serverMetadata)
                        .build();

                if (hasSchedule(lineRequest)) {
                    List<PricedPeriod> periods = validatedPeriods;
                    lineResult.setPricedPeriods(periods);
                    PricedPeriod first = periods.get(0);
                    lineResult.setUnitPrice(first.getNetUnitPrice());
                    lineResult.setFinalPrice(first.getNetMonthly());
                    lineResult.setDiscountRate(first.getDiscountRate());
                    lineResult.setDiscountAmount(listPrice.subtract(first.getNetUnitPrice())
                            .multiply(BigDecimal.valueOf(first.getQuantity()))
                            .setScale(2, RoundingMode.HALF_UP));
                }

                lineResults.add(lineResult);
            }

                        // 7-10. Convert to Drools facts, build RuleExecutionContext, execute rules
            if (!activeRules.isEmpty()) {
                List<RuleDefinition> ruleDefinitions = activeRules.stream()
                    .map(rule -> RuleDefinition.builder()
                        .id(String.valueOf(rule.getId()))
                        .name(rule.getName())
                        .description(rule.getDescription())
                        .content(rule.getContent())
                        .priority(rule.getPriority())
                        .ruleType(rule.getRuleType() != null ? rule.getRuleType().name() : null)
                        .conditionGroup(rule.getConditionGroup())
                        .actionSet(rule.getActionSet())
                        .conflictPolicy(rule.getConflictPolicy())
                        .explanationCode(rule.getExplanationCode())
                        .build())
                    .toList();
                RuleExecutionContext ruleContext = RuleExecutionContext.builder()
                    .ruleSetId(ruleSet != null ? String.valueOf(ruleSet.getId()) : null)
                    .engineType(RulesEngineType.DROOLS)
                    .build();
                ruleContext.getFacts().add(customer);
                ruleContext.getFacts().add(context);
                ruleContext.getFacts().addAll(lineResults);
                try {
                    RuleExecutionResult ruleResult = droolsRulesEngine.execute(ruleContext, ruleDefinitions);
                    log.info("Executed {} rules (fired {}) for customer {}",
                        activeRules.size(), ruleResult.getRulesFired(), customer.getExternalId());
                } catch (Exception ex) {
                    log.error("Rule execution failed for customer {}: {}",
                        customer.getExternalId(), ex.getMessage(), ex);
                    throw new BusinessException("Pricing rule execution failed", "RULE_EXECUTION_ERROR");
                }
            } else {
                log.info("No active rules to execute for customer {}", customer.getExternalId());
            }
// 11. Calculate FinancialMetrics
            PricingResult result = PricingResult.builder()
                    .success(true)
                    .lineResults(lineResults)
                    .explanationCodes(new ArrayList<>())
                    .approvalState("APPROVED")
                    .build();

            migrationPolicy.apply(migrationPlan, lineResults);
            FinancialMetrics metrics = metricsCalculator.calculate(result, context.getDurationInMonths(), customer);
            result.setFinancialMetrics(metrics);
            if (requestsRamp(context)) {
                List<RuleDefinition> governanceRules = activeRules.stream().map(rule -> RuleDefinition.builder()
                        .id(String.valueOf(rule.getId())).name(rule.getName()).content(rule.getContent())
                        .priority(rule.getPriority()).ruleType(rule.getRuleType() == null ? null : rule.getRuleType().name())
                        .conditionGroup(rule.getConditionGroup()).actionSet(rule.getActionSet())
                        .conflictPolicy(rule.getConflictPolicy()).explanationCode(rule.getExplanationCode()).build()).toList();
                new com.opencrm.pricing.ramp.PeriodGovernance().evaluate(
                        context, result, droolsRulesEngine, fallbackProducts, governanceRules, migrationPlan);
            }

            // Stamp after mutable rule/governance execution; caller or configured facts cannot mint authority.
            for (PricingLineResult lineResult : lineResults) {
                Map<String, Object> metadata = new HashMap<>();
                if (lineResult.getMetadata() != null) metadata.putAll(lineResult.getMetadata());
                metadata.remove("authoritativeProductType");
                PricingProduct authoritativeProduct = fallbackProducts.get(lineResult.getProductExternalId());
                if (authoritativeProduct != null && authoritativeProduct.getProductType() != null)
                    metadata.put("authoritativeProductType", authoritativeProduct.getProductType().name());
                lineResult.setMetadata(metadata);
            }

            // 12. Build PricingWaterfall
            PricingWaterfall waterfall = buildWaterfall(lineResults);
            result.setWaterfall(waterfall);
            waterfall.setApprovalState(result.getApprovalState());

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            // 13. Persist audit log for execute mode only
            if (persistAudit) {
                persistAuditLog(context, result, ruleSet, executionTime);
            }

            return result;

        } catch (BusinessException e) {
            throw e; // Let GlobalExceptionHandler map to proper HTTP status
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during pricing execution for customer {}: {}",
                    context.getCustomerExternalId(), e.getMessage(), e);
            throw new BusinessException(
                    "An unexpected error occurred during pricing calculation",
                    "PRICING_EXECUTION_ERROR");
        }
    }

    private static boolean requestsRamp(PricingContext context) {
        return context.getLineItems().stream().anyMatch(PricingExecutionService::hasSchedule);
    }

    private static boolean hasSchedule(PricingLineRequest lineRequest) {
        return lineRequest.getSchedule() != null && !lineRequest.getSchedule().isEmpty();
    }

    private BigDecimal getListPrice(PricingLineRequest lineRequest, ResolveBatchResponse batch, Map<String, PricingProduct> fallbackProducts) {
        if (lineRequest.getListPriceOverride() != null) {
            return lineRequest.getListPriceOverride();
        }

        ProductEntryResolveDto resolved = batch.getEntries() != null
                ? batch.getEntries().get(lineRequest.getProductExternalId())
                : null;

        if (resolved != null && resolved.isEntryMatched() && resolved.getListPrice() != null) {
            return resolved.getListPrice();
        }

        PricingProduct product = fallbackProducts.get(lineRequest.getProductExternalId());
        if (product == null) {
            throw new ResourceNotFoundException("Product with external ID: " + lineRequest.getProductExternalId());
        }

        return product.getListPrice() != null ? product.getListPrice() : BigDecimal.ZERO;
    }

    private PricingWaterfall buildWaterfall(List<PricingLineResult> lineResults) {
        BigDecimal totalListPrice = lineResults.stream()
                .map(PricingLineResult::getListPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFinalPrice = lineResults.stream()
                .map(PricingLineResult::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PricingWaterfallStep> steps = new ArrayList<>();
        BigDecimal runningPeriods = BigDecimal.ZERO;
        for (PricingLineResult line : lineResults) {
            if (line.getPricedPeriods() == null) {
                continue;
            }
            for (PricedPeriod period : line.getPricedPeriods()) {
                BigDecimal amount = period.getPeriodAmount() != null ? period.getPeriodAmount() : BigDecimal.ZERO;
                runningPeriods = runningPeriods.add(amount);
                steps.add(PricingWaterfallStep.builder()
                        .stepType(WaterfallStepType.PERIOD_SELECTION)
                        .description("Period " + period.getSequence())
                        .amount(period.getGrossPeriodAmount() == null ? amount : period.getGrossPeriodAmount())
                        .runningTotal(runningPeriods.add(period.getMigrationCredit() == null ? BigDecimal.ZERO : period.getMigrationCredit()))
                        .build());
                if (period.getMigrationCredit() != null) {
                    steps.add(PricingWaterfallStep.builder().stepType(WaterfallStepType.DISCOUNT)
                            .description("One-time migration credit for period " + period.getSequence())
                            .amount(period.getMigrationCredit().negate()).runningTotal(runningPeriods)
                            .ruleId("hybrid-migration-credit").build());
                }
            }
        }
        steps.add(PricingWaterfallStep.builder()
                .stepType(WaterfallStepType.LIST_PRICE)
                .description("Sum of line list prices")
                .amount(totalListPrice)
                .runningTotal(totalListPrice)
                .build());
        steps.add(PricingWaterfallStep.builder()
                .stepType(WaterfallStepType.BASE_PRICE)
                .description("Base price")
                .amount(totalListPrice)
                .runningTotal(totalListPrice)
                .build());
        steps.add(PricingWaterfallStep.builder()
                .stepType(WaterfallStepType.NET_PRICE)
                .description("Sum of line final prices")
                .amount(totalFinalPrice)
                .runningTotal(totalFinalPrice)
                .build());

        return PricingWaterfall.builder()
                .listPrice(totalListPrice)
                .basePrice(totalListPrice)
                .netPrice(totalFinalPrice)
                .steps(steps)
                .approvalState("APPROVED")
                .explanationCodes(new ArrayList<>())
                .build();
    }

    private void persistAuditLog(PricingContext context, PricingResult result,
                                   RuleSet ruleSet, long executionTime) {
        try {
            String inputSnapshot = objectMapper.writeValueAsString(context);
            String outputSnapshot = objectMapper.writeValueAsString(result);

            PricingAuditLog auditLog = PricingAuditLog.builder()
                    .quoteExternalId(context.getQuoteExternalId())
                    .customerExternalId(context.getCustomerExternalId())
                    .ruleSetId(ruleSet != null ? ruleSet.getId() : null)
                    .inputSnapshot(inputSnapshot)
                    .outputSnapshot(outputSnapshot)
                    .executionTimeMs(executionTime)
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Persisted audit log for quote: {}", context.getQuoteExternalId());

        } catch (JsonProcessingException e) {
            log.error("AUDIT_INTEGRITY_FAILURE: Failed to serialize audit log for quote {}: {}",
                    context.getQuoteExternalId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("AUDIT_INTEGRITY_FAILURE: Failed to persist audit log for quote {}: {}",
                    context.getQuoteExternalId(), e.getMessage(), e);
            throw new BusinessException(
                    "Pricing was calculated but the audit trail could not be saved",
                    "AUDIT_PERSISTENCE_FAILED");
        }
    }
}
