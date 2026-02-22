package com.opencrm.pricingairules.api.controller;

import com.opencrm.pricingairules.api.dto.*;
import com.opencrm.pricingairules.api.mapper.PricingMapper;
import com.opencrm.pricingairules.domain.model.Customer;
import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.service.metrics.FinancialMetricsService;
import com.opencrm.pricingairules.service.pricing.PricingService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Slf4j
public class PricingController {

    private final PricingService pricingService;
    private final FinancialMetricsService financialMetricsService;
    private final PricingMapper pricingMapper;
    
    @PostMapping("/evaluate")
    @Timed(value = "pricing.evaluate", description = "Time taken to evaluate pricing")
    public ResponseEntity<PricingResponseDTO> evaluatePricing(@RequestBody PricingRequestDTO request) {
        log.debug("Received pricing evaluation request: {}", request.getQuote().getExternalId());

        // Convert DTO to domain model
        Quote quote = pricingMapper.toQuote(request.getQuote());

        // Evaluate pricing
        Quote pricedQuote;
        if (request.getRuleSetName() != null && !request.getRuleSetName().isEmpty()) {
            pricedQuote = pricingService.calculatePricing(quote, request.getRuleSetName());
        } else if (request.getAdditionalContext() != null && !request.getAdditionalContext().isEmpty()) {
            pricedQuote = pricingService.calculatePricingWithContext(quote, request.getAdditionalContext());
        } else {
            pricedQuote = pricingService.calculatePricing(quote);
        }

        // Get financial metrics (placeholder maps for now)
        Map<String, Object> financialMetrics = new HashMap<>();
        Map<String, Object> pricingDetails = new HashMap<>();

        // Convert domain model back to DTO
        QuoteDTO quoteDTO = pricingMapper.toQuoteDTO(pricedQuote);

        // Build response
        PricingResponseDTO response = PricingResponseDTO.builder()
                .quote(quoteDTO)
                .ruleSetName(request.getRuleSetName())
                .financialMetrics(financialMetrics)
                .pricingDetails(pricingDetails)
                .success(true)
                .message("Pricing calculated successfully")
                .build();

        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/batch")
    @Timed(value = "pricing.batch", description = "Time taken to batch evaluate pricing")
    public ResponseEntity<BatchPricingResponseDTO> batchEvaluatePricing(@RequestBody BatchPricingRequestDTO request) {
        log.debug("Received batch pricing evaluation request for {} quotes", request.getQuotes().size());

        // Convert DTOs to domain models
        List<Quote> quotes = request.getQuotes().stream()
                .map(pricingMapper::toQuote)
                .collect(Collectors.toList());

        // Batch evaluate pricing
        List<Quote> pricedQuotes = pricingService.batchCalculatePricing(quotes);

        // Convert domain models back to DTOs
        List<QuoteDTO> quoteDTOs = pricedQuotes.stream()
                .map(pricingMapper::toQuoteDTO)
                .collect(Collectors.toList());

        // Build response
        BatchPricingResponseDTO response = BatchPricingResponseDTO.builder()
                .quotes(quoteDTOs)
                .ruleSetName(request.getRuleSetName())
                .processedCount(pricedQuotes.size())
                .successCount(pricedQuotes.size())
                .failureCount(0)
                .success(true)
                .message("Batch pricing calculated successfully")
                .build();

        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/metrics")
    @Timed(value = "pricing.metrics", description = "Time taken to retrieve pricing metrics")
    public ResponseEntity<Map<String, Map<String, Object>>> getAggregatedMetrics() {
        log.debug("Received request to get aggregated metrics");

        Map<String, Map<String, BigDecimal>> metrics = financialMetricsService.getAggregatedMetrics();

        // Convert BigDecimal to Object for the response
        Map<String, Map<String, Object>> result = new HashMap<>();
        metrics.forEach((key, value) -> {
            Map<String, Object> converted = new HashMap<>();
            value.forEach(converted::put);
            result.put(key, converted);
        });

        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/recalculate")
    @Timed(value = "pricing.recalculate", description = "Time taken to recalculate pricing")
    public ResponseEntity<Map<String, Object>> recalculateAffectedQuotes(@RequestParam String ruleSetName) {
        log.debug("Received request to recalculate quotes affected by rule set {}", ruleSetName);

        int count = pricingService.recalculateAffectedQuotes(ruleSetName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("recalculatedCount", count);
        response.put("message", "Recalculated pricing for " + count + " quotes");

        return ResponseEntity.ok(response);
    }
}
