package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.rules.PricingRuleEngineOrchestrator;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final QuoteLevelMetricsPolicy quoteLevelMetricsPolicy;
    private final PricingExecutionEventPublisher pricingExecutionEventPublisher;
    private final ObjectMapper objectMapper;
    private final PricingRuleEngineOrchestrator pricingRuleEngineOrchestrator;

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
            if (!unresolvedProductIds.isEmpty()) {
                List<PricingProduct> products = pricingProductRepository
                        .findByExternalIdInAndDeletedFalse(unresolvedProductIds);
                for (PricingProduct p : products) {
                    fallbackProducts.put(p.getExternalId(), p);
                }
            }

            // 6. Initialize line results with list prices
            List<PricingLineResult> lineResults = new ArrayList<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                BigDecimal listPrice = getListPrice(lineRequest, priceBookBatch, fallbackProducts);

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

                lineResults.add(lineResult);
            }

            // 7-10. Hybrid OpenL + Drools execution via shared rule engine port
            if (!activeRules.isEmpty()) {
                RuleExecutionContext ruleContext = RuleExecutionContext.builder()
                        .ruleSetId(ruleSet != null ? String.valueOf(ruleSet.getId()) : null)
                        .build();
                ruleContext.getFacts().add(customer);
                ruleContext.getFacts().add(context);
                ruleContext.getFacts().addAll(lineResults);
                try {
                    RuleExecutionResult ruleResult = pricingRuleEngineOrchestrator.execute(
                            ruleSet, activeRules, ruleContext);
                    log.info("Executed {} hybrid rules (fired {}, trace entries {}) for customer {}",
                            activeRules.size(),
                            ruleResult.getRulesFired(),
                            ruleResult.getTrace() != null ? ruleResult.getTrace().size() : 0,
                            customer.getExternalId());
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

            FinancialMetrics metrics = metricsCalculator.calculate(result, context.getDurationInMonths(), customer);
            result.setFinancialMetrics(metrics);

            result = quoteLevelMetricsPolicy.apply(result, context.getDurationInMonths() != null
                    ? context.getDurationInMonths()
                    : 12);

            // 12. Build PricingWaterfall
            PricingWaterfall waterfall = buildWaterfall(lineResults);
            result.setWaterfall(waterfall);

            long executionTime = System.currentTimeMillis() - startTime;
            result.setExecutionTimeMs(executionTime);

            String pricebookVersion = priceBookBatch.getPriceBookId() != null
                    ? "pb-" + priceBookBatch.getPriceBookId()
                    : null;
            String rulesetVersion = ruleSet != null ? "rs-" + ruleSet.getId() : null;

            // 13. Persist audit log for execute mode only
            if (persistAudit) {
                persistAuditLog(context, result, ruleSet, executionTime, pricebookVersion, rulesetVersion);
                pricingExecutionEventPublisher.publishOutcomes(context, result, pricebookVersion, rulesetVersion);
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

    private void persistAuditLog(
            PricingContext context,
            PricingResult result,
            RuleSet ruleSet,
            long executionTime,
            String pricebookVersion,
            String rulesetVersion
    ) {
        try {
            String inputSnapshot = objectMapper.writeValueAsString(context);
            String outputSnapshot = objectMapper.writeValueAsString(result);
            String metricsSnapshot = result.getFinancialMetrics() != null
                    ? objectMapper.writeValueAsString(result.getFinancialMetrics())
                    : null;

            PricingAuditLog auditLog = PricingAuditLog.builder()
                    .quoteExternalId(context.getQuoteExternalId())
                    .customerExternalId(context.getCustomerExternalId())
                    .ruleSetId(ruleSet != null ? ruleSet.getId() : null)
                    .correlationId(context.getCorrelationId())
                    .pricebookVersion(pricebookVersion)
                    .rulesetVersion(rulesetVersion)
                    .metricsSnapshot(metricsSnapshot)
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
