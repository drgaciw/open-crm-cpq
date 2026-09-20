package com.opencrm.ai.selection.catalog;

import com.opencrm.ai.selection.SelectionTypes.PriceRates;
import com.opencrm.ai.selection.SelectionTypes.Usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** USD accounting. Every attempt, including failed retries, belongs in the batch. */
public final class CostCalculator {
    public BigDecimal usageCost(PriceRates prices, Usage usage) {
        // Unknown mandatory rates remain ineligible even when a provider reports a total.
        amount(prices.prompt()); amount(prices.completion()); amount(prices.request());
        if (prices.cacheRead() != null) amount(prices.cacheRead());
        if (prices.cacheWrite() != null) amount(prices.cacheWrite());
        long uncached = usage.inputTokens() - usage.cacheReadTokens() - usage.cacheWriteTokens();
        BigDecimal calculated = prices.prompt().multiply(BigDecimal.valueOf(uncached))
                .add(prices.completion().multiply(BigDecimal.valueOf(usage.outputTokens())))
                .add(prices.request().multiply(BigDecimal.valueOf(usage.requests())))
                .add(cacheCharge(prices.cacheRead(), usage.cacheReadTokens()))
                .add(cacheCharge(prices.cacheWrite(), usage.cacheWriteTokens()));
        // Provider accounting may include overhead unknown to token estimates. Never understate it.
        return usage.reportedCost() == null ? calculated : calculated.max(usage.reportedCost());
    }

    public BigDecimal batchCost(PriceRates prices, List<Usage> attempts, BigDecimal evaluatorOverhead) {
        amount(evaluatorOverhead);
        return attempts.stream().map(usage -> usageCost(prices, usage))
                .reduce(evaluatorOverhead, BigDecimal::add);
    }

    public BigDecimal costPerSuccess(PriceRates prices, List<Usage> attempts,
            BigDecimal evaluatorOverhead, int successes) {
        if (successes <= 0 || successes > attempts.size()) {
            throw new IllegalArgumentException("Successful tasks must be positive and cannot exceed attempts");
        }
        return batchCost(prices, attempts, evaluatorOverhead)
                .divide(BigDecimal.valueOf(successes), 18, RoundingMode.CEILING);
    }

    private static BigDecimal cacheCharge(BigDecimal rate, long tokens) {
        if (tokens == 0) return BigDecimal.ZERO;
        amount(rate);
        return rate.multiply(BigDecimal.valueOf(tokens));
    }

    private static void amount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Unknown or negative cost");
    }
}
