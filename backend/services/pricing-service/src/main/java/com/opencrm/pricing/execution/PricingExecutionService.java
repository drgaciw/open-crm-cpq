package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.dto.PriceCalculatedEvent;
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
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.pricing.waterfall.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
    private final OutboxEventPublisher outboxEventPublisher;

    private static final BigDecimal APPROVAL_THRESHOLD_PERCENT = BigDecimal.valueOf(20);

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

            // 6. Initialize line results with list prices
            List<PricingLineResult> lineResults = new ArrayList<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                BigDecimal listPrice = getListPrice(lineRequest, priceBookBatch);

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
                        .metadata(lineRequest.getMetadata())
                        .build();

                applyRequestedDiscount(lineRequest, lineResult);
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
                    .explanationCodes(explanationCodes(lineResults))
                    .approvalState(requiresDiscountApproval(lineResults) ? "PENDING_APPROVAL" : "APPROVED")
                    .build();

            FinancialMetrics metrics = metricsCalculator.calculate(result, context.getDurationInMonths());
            result.setFinancialMetrics(metrics);

            // 12. Build PricingWaterfall
            PricingWaterfall waterfall = buildWaterfall(lineResults);
            result.setWaterfall(waterfall);

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            // 13. Persist audit log for execute mode only
            if (persistAudit) {
                EventSnapshots snapshots = buildSnapshots(context, result);
                persistAuditLog(context, result, ruleSet, executionTime, snapshots);
                publishPricingEvents(context, result, snapshots);
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

    private BigDecimal getListPrice(PricingLineRequest lineRequest, ResolveBatchResponse batch) {
        if (lineRequest.getListPriceOverride() != null) {
            return lineRequest.getListPriceOverride();
        }

        ProductEntryResolveDto resolved = batch.getEntries() != null
                ? batch.getEntries().get(lineRequest.getProductExternalId())
                : null;

        if (resolved != null && resolved.isEntryMatched() && resolved.getListPrice() != null) {
            return resolved.getListPrice();
        }

        PricingProduct product = pricingProductRepository
                .findByExternalIdAndDeletedFalse(lineRequest.getProductExternalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with external ID: " + lineRequest.getProductExternalId()));

        return product.getListPrice() != null ? product.getListPrice() : BigDecimal.ZERO;
    }

    private void applyRequestedDiscount(PricingLineRequest lineRequest, PricingLineResult lineResult) {
        lineResult.getExplanationCodes().add(
                lineRequest.getListPriceOverride() != null ? "LIST_PRICE_OVERRIDE" : "LIST_PRICE_APPLIED");

        BigDecimal discountRate = metadataDecimal(lineRequest.getMetadata(), "discountRate");
        if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal grossPrice = lineResult.getListPrice()
                .multiply(BigDecimal.valueOf(lineResult.getQuantity()));
        BigDecimal discountAmount = grossPrice
                .multiply(discountRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        lineResult.setDiscountRate(discountRate);
        lineResult.setDiscountAmount(discountAmount);
        lineResult.setFinalPrice(grossPrice.subtract(discountAmount));

        if (discountRate.compareTo(APPROVAL_THRESHOLD_PERCENT) > 0) {
            lineResult.getExplanationCodes().add("DISCOUNT_APPROVAL_REQUIRED");
        }
    }

    private BigDecimal metadataDecimal(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return null;
        }

        Object value = metadata.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string) {
            return new BigDecimal(string);
        }
        return null;
    }

    private List<String> explanationCodes(List<PricingLineResult> lineResults) {
        return lineResults.stream()
                .flatMap(line -> line.getExplanationCodes().stream())
                .distinct()
                .toList();
    }

    private boolean requiresDiscountApproval(List<PricingLineResult> lineResults) {
        return lineResults.stream()
                .anyMatch(line -> line.getDiscountRate() != null
                        && line.getDiscountRate().compareTo(APPROVAL_THRESHOLD_PERCENT) > 0);
    }

    private PricingWaterfall buildWaterfall(List<PricingLineResult> lineResults) {
        List<PricingWaterfallStep> steps = new ArrayList<>();

        BigDecimal totalListPrice = lineResults.stream()
                .map(PricingLineResult::getListPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFinalPrice = lineResults.stream()
                .map(PricingLineResult::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PricingWaterfall.builder()
                .listPrice(totalListPrice)
                .basePrice(totalListPrice)
                .netPrice(totalFinalPrice)
                .steps(steps)
                .approvalState("APPROVED")
                .explanationCodes(new ArrayList<>())
                .build();
    }

    private EventSnapshots buildSnapshots(PricingContext context, PricingResult result) {
        try {
            return new EventSnapshots(
                    deterministicSnapshotMapper().writeValueAsString(context),
                    deterministicSnapshotMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.error("AUDIT_INTEGRITY_FAILURE: Failed to serialize snapshots for quote {}: {}",
                    context.getQuoteExternalId(), e.getMessage(), e);
            throw new BusinessException(
                    "Pricing was calculated but deterministic snapshots could not be serialized",
                    "SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private void persistAuditLog(PricingContext context, PricingResult result,
                                   RuleSet ruleSet, long executionTime, EventSnapshots snapshots) {
        try {
            PricingAuditLog auditLog = PricingAuditLog.builder()
                    .quoteExternalId(context.getQuoteExternalId())
                    .customerExternalId(context.getCustomerExternalId())
                    .ruleSetId(ruleSet != null ? ruleSet.getId() : null)
                    .inputSnapshot(snapshots.inputSnapshot())
                    .outputSnapshot(snapshots.outputSnapshot())
                    .executionTimeMs(executionTime)
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Persisted audit log for quote: {}", context.getQuoteExternalId());

        } catch (Exception e) {
            log.error("AUDIT_INTEGRITY_FAILURE: Failed to persist audit log for quote {}: {}",
                    context.getQuoteExternalId(), e.getMessage(), e);
            throw new BusinessException(
                    "Pricing was calculated but the audit trail could not be saved",
                    "AUDIT_PERSISTENCE_FAILED");
        }
    }

    private void publishPricingEvents(PricingContext context, PricingResult result, EventSnapshots snapshots) {
        outboxEventPublisher.publish(
                "PricingExecution",
                context.getQuoteExternalId(),
                KafkaTopics.PRICE_CALCULATED,
                new PriceCalculatedEvent(
                        context.getQuoteExternalId(),
                        context.getCustomerExternalId(),
                        result.getExplanationCodes(),
                        snapshots.inputSnapshot(),
                        snapshots.outputSnapshot()));

        for (PricingLineResult line : result.getLineResults()) {
            if (line.getDiscountRate() == null
                    || line.getDiscountRate().compareTo(APPROVAL_THRESHOLD_PERCENT) <= 0) {
                continue;
            }

            outboxEventPublisher.publish(
                    "PricingExecution",
                    context.getQuoteExternalId(),
                    KafkaTopics.DISCOUNT_APPROVAL_REQUIRED,
                    new DiscountApprovalRequiredEvent(
                            context.getQuoteExternalId(),
                            line.getProductExternalId(),
                            line.getDiscountRate(),
                            line.getDiscountAmount(),
                            context.getCurrency(),
                            line.getExplanationCodes(),
                            snapshots.inputSnapshot(),
                            snapshots.outputSnapshot()));
        }
    }

    private ObjectMapper deterministicSnapshotMapper() {
        return objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private record EventSnapshots(String inputSnapshot, String outputSnapshot) {
    }
}
