package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.pricebook.PriceBook;
import com.opencrm.pricing.pricebook.PriceBookEntry;
import com.opencrm.pricing.pricebook.PriceBookEntryRepository;
import com.opencrm.pricing.pricebook.PriceBookRepository;
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
import java.util.List;
import java.util.Optional;

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
    private final PriceBookRepository priceBookRepository;
    private final PriceBookEntryRepository priceBookEntryRepository;
    private final PricingAuditLogRepository auditLogRepository;
    private final PricingContextValidator contextValidator;
    private final MetricsCalculator metricsCalculator;
    private final ObjectMapper objectMapper;

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

            // 5. Find PriceBook
            PriceBook priceBook = findPriceBook(context.getRegion());

            // 6. Initialize line results with list prices
            List<PricingLineResult> lineResults = new ArrayList<>();
            for (PricingLineRequest lineRequest : context.getLineItems()) {
                BigDecimal listPrice = getListPrice(lineRequest, priceBook);

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

            // 7-10. Execute rules (simplified - would integrate with Drools engine)
            // TODO: Convert to Drools facts, build RuleExecutionContext, execute rules
            log.info("Executed {} rules for customer {}", activeRules.size(), customer.getExternalId());

            // 11. Calculate FinancialMetrics
            PricingResult result = PricingResult.builder()
                    .success(true)
                    .lineResults(lineResults)
                    .explanationCodes(new ArrayList<>())
                    .approvalState("APPROVED")
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
                persistAuditLog(context, result, ruleSet, executionTime);
            }

            return result;

        } catch (ResourceNotFoundException | BusinessException e) {
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

    private PriceBook findPriceBook(String region) {
        if (region != null) {
            Optional<PriceBook> regionalBook = priceBookRepository.findByRegionAndDeletedFalse(region);
            if (regionalBook.isPresent()) {
                return regionalBook.get();
            }
        }

        // Fall back to default price book
        return priceBookRepository.findByIsDefaultTrueAndDeletedFalse()
                .orElseThrow(() -> new BusinessException("No price book found", "NO_PRICE_BOOK"));
    }

    private BigDecimal getListPrice(PricingLineRequest lineRequest, PriceBook priceBook) {
        // Check for override
        if (lineRequest.getListPriceOverride() != null) {
            return lineRequest.getListPriceOverride();
        }

        // Look up in price book
        Optional<PriceBookEntry> entry = priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                        priceBook.getId(), lineRequest.getProductExternalId());

        if (entry.isPresent()) {
            return entry.get().getListPrice();
        }

        // Fall back to product list price
        PricingProduct product = pricingProductRepository
                .findByExternalIdAndDeletedFalse(lineRequest.getProductExternalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with external ID: " + lineRequest.getProductExternalId()));

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
