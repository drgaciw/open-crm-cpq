package com.opencrm.pricingairules.service.pricing;

import com.opencrm.pricingairules.domain.model.Customer;
import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.repository.QuoteRepository;
import com.opencrm.pricingairules.domain.repository.RuleSetRepository;
import com.opencrm.pricingairules.service.drools.DroolsService;
import com.opencrm.pricingairules.service.metrics.FinancialMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final DroolsService droolsService;
    private final FinancialMetricsService financialMetricsService;
    private final RuleSetRepository ruleSetRepository;
    private final QuoteRepository quoteRepository;

    private static final String DEFAULT_RULE_SET = "StandardPricing";

    @Override
    @Transactional
    public Quote calculatePricing(Quote quote) {
        // Find the best rule set for this quote's customer
        RuleSet ruleSet = findBestRuleSet(quote.getCustomer());
        return calculatePricing(quote, ruleSet.getName());
    }

    @Override
    @Transactional
    public Quote calculatePricing(Quote quote, String ruleSetName) {
        log.debug("Calculating pricing for quote {} using rule set {}", quote.getExternalId(), ruleSetName);

        // Save the quote first to ensure it has an ID
        if (quote.getId() == null) {
            quote = quoteRepository.save(quote);
        }

        // Evaluate rules
        Quote pricedQuote = droolsService.evaluateQuote(quote, ruleSetName);

        // Calculate financial metrics
        pricedQuote = financialMetricsService.calculateFinancialMetrics(pricedQuote);

        // Set status to priced
        pricedQuote.setStatus("PRICED");

        // Save and return the updated quote
        return quoteRepository.save(pricedQuote);
    }

    @Override
    @Transactional
    public List<Quote> batchCalculatePricing(List<Quote> quotes) {
        log.debug("Batch calculating pricing for {} quotes", quotes.size());

        // Save all quotes first to ensure they have IDs
        quotes = quoteRepository.saveAll(quotes);

        // Group quotes by customer type for more efficient processing
        Map<String, List<Quote>> quotesByCustomerType = quotes.stream()
                .collect(Collectors.groupingBy(q -> q.getCustomer().getType()));

        // Process each group with the appropriate rule set
        List<Quote> pricedQuotes = quotesByCustomerType.entrySet().stream()
                .flatMap(entry -> {
                    String customerType = entry.getKey();
                    List<Quote> customerQuotes = entry.getValue();

                    // Find the best rule set for this customer type
                    String ruleSetName = findRuleSetNameForCustomerType(customerType);

                    // Create a map of quotes by ID for batch processing
                    Map<String, Quote> quotesMap = customerQuotes.stream()
                            .collect(Collectors.toMap(
                                    q -> q.getExternalId(),
                                    q -> q));

                    // Batch evaluate quotes with Drools
                    Map<String, Quote> pricedQuotesMap = droolsService.evaluateQuotes(quotesMap, ruleSetName);

                    // Calculate financial metrics for each quote
                    return pricedQuotesMap.values().stream()
                            .map(priced -> {
                                Quote withMetrics = financialMetricsService.calculateFinancialMetrics(priced);
                                withMetrics.setStatus("PRICED");
                                return withMetrics;
                            });
                })
                .collect(Collectors.toList());

        // Save all updated quotes
        return quoteRepository.saveAll(pricedQuotes);
    }

    @Override
    @Transactional
    public Quote calculatePricingWithContext(Quote quote, Map<String, Object> additionalContext) {
        log.debug("Calculating pricing for quote {} with additional context", quote.getExternalId());

        // Find the best rule set for this quote's customer
        RuleSet ruleSet = findBestRuleSet(quote.getCustomer());
        String ruleSetName = ruleSet.getName();

        // Save the quote first to ensure it has an ID
        if (quote.getId() == null) {
            quote = quoteRepository.save(quote);
        }

        // Evaluate rules with additional context
        Quote pricedQuote = droolsService.evaluateQuote(quote, ruleSetName, additionalContext);

        // Calculate financial metrics
        pricedQuote = financialMetricsService.calculateFinancialMetrics(pricedQuote);

        // Set status to priced
        pricedQuote.setStatus("PRICED");

        // Save and return the updated quote
        return quoteRepository.save(pricedQuote);
    }

    @Override
    @Cacheable(value = "ruleSetCache", key = "#customer.type")
    public RuleSet findBestRuleSet(Customer customer) {
        log.debug("Finding best rule set for customer {}", customer.getExternalId());

        // Find active rule sets applicable to this customer
        ZonedDateTime now = ZonedDateTime.now();
        List<RuleSet> applicableRuleSets = ruleSetRepository
                .findByStatusAndEffectiveDateBeforeAndExpirationDateAfter("ACTIVE", now, now);

        if (applicableRuleSets.isEmpty()) {
            // Fall back to default rule set
            return ruleSetRepository.findByNameAndVersion(DEFAULT_RULE_SET, "latest")
                    .orElseThrow(() -> new IllegalStateException("Default rule set not found"));
        }

        // Select the rule set based on customer attributes
        // This is a simplified implementation - in a real system, this would be more
        // sophisticated
        String customerType = customer.getType();

        // Try to find a rule set specifically for this customer type
        return applicableRuleSets.stream()
                .filter(rs -> rs.getName().contains(customerType))
                .findFirst()
                .orElse(applicableRuleSets.get(0)); // Fall back to the first applicable rule set
    }

    @Override
    @Transactional
    public int recalculateAffectedQuotes(String ruleSetName) {
        log.debug("Recalculating quotes affected by changes to rule set {}", ruleSetName);

        // Find all quotes that were priced using this rule set
        // This is a simplified implementation - in a real system, you would track which
        // rule set was used for each quote
        List<Quote> affectedQuotes = quoteRepository.findByStatus("PRICED");

        if (affectedQuotes.isEmpty()) {
            return 0;
        }

        // Recalculate pricing for each quote
        int count = 0;
        for (Quote quote : affectedQuotes) {
            calculatePricing(quote, ruleSetName);
            count++;
        }

        log.debug("Recalculated pricing for {} quotes", count);
        return count;
    }

    private String findRuleSetNameForCustomerType(String customerType) {
        // This is a simplified implementation - in a real system, this would be more
        // sophisticated
        switch (customerType) {
            case "ENTERPRISE":
                return "EnterprisePricing";
            case "SMB":
                return "SMBPricing";
            case "STARTUP":
                return "StartupPricing";
            case "EDUCATION":
                return "EducationPricing";
            case "NON_PROFIT":
                return "NonProfitPricing";
            default:
                return DEFAULT_RULE_SET;
        }
    }
}
