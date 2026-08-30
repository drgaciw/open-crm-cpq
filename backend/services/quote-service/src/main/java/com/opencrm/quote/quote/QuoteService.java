package com.opencrm.quote.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final QuoteMapper quoteMapper;
    private final QuoteNumberGenerator quoteNumberGenerator;
    private final QuoteEventPublisher eventPublisher;
    private final QuoteLineItemRepository quoteLineItemRepository;
    private final PricingServiceClient pricingServiceClient;
    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    private static final Set<QuoteStatus> EDITABLE_STATUSES = Set.of(QuoteStatus.DRAFT, QuoteStatus.REJECTED);

    public QuoteDto create(QuoteCreateRequest request) {
        log.debug("Creating quote for customer: {}", request.getCustomerExternalId());

        Quote quote = quoteMapper.toEntity(request);
        quote.setQuoteNumber(quoteNumberGenerator.generate());

        if (request.getDealType() == null) {
            quote.setDealType(DealType.NEW);
        }
        if (request.getDurationInMonths() == null) {
            quote.setDurationInMonths(12);
        }

        Quote saved = quoteRepository.save(quote);
        log.info("Created quote: {}", saved.getQuoteNumber());

        return quoteMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public QuoteDto getById(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        return quoteMapper.toDto(quote);
    }

    @Transactional(readOnly = true)
    public QuoteDto getByQuoteNumber(String quoteNumber) {
        Quote quote = quoteRepository.findByQuoteNumberAndDeletedFalse(quoteNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Quote with number: " + quoteNumber));
        return quoteMapper.toDto(quote);
    }

    public QuoteDto update(Long id, QuoteUpdateRequest request) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (!EDITABLE_STATUSES.contains(quote.getStatus())) {
            throw new BusinessException(
                    "Quote can only be edited in DRAFT or REJECTED status. Current: " + quote.getStatus(),
                    "QUOTE_NOT_EDITABLE"
            );
        }

        quoteMapper.updateEntity(request, quote);
        Quote updated = quoteRepository.save(quote);
        log.info("Updated quote: {}", updated.getQuoteNumber());

        return quoteMapper.toDto(updated);
    }

    public void delete(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new BusinessException("Cannot delete an accepted quote", "QUOTE_ACCEPTED_NO_DELETE");
        }

        quote.softDelete();
        quoteRepository.save(quote);
        log.info("Deleted quote: {}", quote.getQuoteNumber());
    }

    @Transactional(readOnly = true)
    public Page<QuoteDto> list(Pageable pageable, QuoteStatus status, DealType dealType, String customerExternalId) {
        Specification<Quote> spec = QuoteSpecifications.notDeleted();

        if (status != null) {
            spec = spec.and(QuoteSpecifications.hasStatus(status));
        }
        if (dealType != null) {
            spec = spec.and(QuoteSpecifications.hasDealType(dealType));
        }
        if (customerExternalId != null) {
            spec = spec.and(QuoteSpecifications.hasCustomerExternalId(customerExternalId));
        }

        Page<Quote> quotes = quoteRepository.findAll(spec, pageable);
        return quotes.map(quoteMapper::toDto);
    }

    public QuoteDto submitForReview(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException("Only draft quotes can be submitted for review", "INVALID_STATUS_TRANSITION");
        }

        if (quote.getLineItems().isEmpty()) {
            throw new BusinessException("Quote must have at least one line item", "QUOTE_NO_LINE_ITEMS");
        }

        assertConfigurationPreflight(quote.getLineItems());

        quote.setStatus(QuoteStatus.IN_REVIEW);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} submitted for review", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    /**
     * S2D-3a CONFIGURATION_ONLY preflight: gateway validate on line product IDs.
     * Blocks submit when {@code valid=false}; no inline REQUIRES/EXCLUDES in quote-service.
     */
    private void assertConfigurationPreflight(List<QuoteLineItem> lineItems) {
        List<Long> productIds = resolveLineProductIds(lineItems);
        ConfigurationValidationResult result = productServiceClient.validateConfiguration(productIds);
        if (result.isValid()) {
            return;
        }

        List<ConfigurationViolation> violations =
                result.getViolations() == null ? List.of() : result.getViolations();
        String detail = violations.stream()
                .map(v -> {
                    String description = v.getDescription() != null ? v.getDescription() : "constraint violated";
                    String type = v.getRuleType() != null ? v.getRuleType() : "UNKNOWN";
                    return type + ": " + description;
                })
                .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = "Configuration is invalid";
        }
        throw new BusinessException(
                "Quote configuration preflight failed: " + detail,
                "CONFIGURATION_PREFLIGHT_FAILED"
        );
    }

    private List<Long> resolveLineProductIds(List<QuoteLineItem> lineItems) {
        Set<Long> ids = new LinkedHashSet<>();
        for (QuoteLineItem item : lineItems) {
            String externalId = item.getProductExternalId();
            if (externalId == null || externalId.isBlank()) {
                throw new BusinessException(
                        "Line item is missing productExternalId required for configuration preflight",
                        "QUOTE_INVALID_PRODUCT_ID"
                );
            }
            try {
                ids.add(Long.parseLong(externalId.trim()));
            } catch (NumberFormatException ex) {
                throw new BusinessException(
                        "Line productExternalId must be a numeric product id for configuration preflight: "
                                + externalId,
                        "QUOTE_INVALID_PRODUCT_ID"
                );
            }
        }
        return List.copyOf(ids);
    }

    public QuoteDto approve(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.PENDING_APPROVAL && quote.getStatus() != QuoteStatus.IN_REVIEW) {
            throw new BusinessException("Quote must be in review or pending approval to approve", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.APPROVED);
        quote.setApprovalState(ApprovalState.APPROVED);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} approved", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    public QuoteDto reject(Long id, String reason) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.PENDING_APPROVAL && quote.getStatus() != QuoteStatus.IN_REVIEW) {
            throw new BusinessException("Quote must be in review or pending approval to reject", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.REJECTED);
        quote.setApprovalState(ApprovalState.REJECTED);
        if (reason != null) {
            quote.setNotes(quote.getNotes() != null ? quote.getNotes() + "\nRejection: " + reason : "Rejection: " + reason);
        }
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} rejected", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    public QuoteDto send(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw new BusinessException("Only approved quotes can be sent", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.SENT);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} sent", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    public QuoteDto accept(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new BusinessException("Only sent quotes can be accepted", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(Instant.now());
        quote.recalculateTotals();
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} accepted", saved.getQuoteNumber());

        List<QuoteLineItem> lineItems = quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        eventPublisher.publishQuoteAccepted(QuoteAcceptedCrmMessageFactory.from(saved, lineItems));

        QuoteAcceptedEvent kafkaEvent = QuoteAcceptedKafkaEventFactory.from(saved, lineItems);
        outboxEventPublisher.publish(
                "Quote",
                saved.getQuoteNumber(),
                KafkaTopics.QUOTE_STATUS_ACCEPTED,
                kafkaEvent
        );

        return quoteMapper.toDto(saved);
    }

    public QuoteDto expire(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new BusinessException("Only sent quotes can expire", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.EXPIRED);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} expired", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    public QuoteDto cancel(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new BusinessException("Cannot cancel an accepted quote", "CANNOT_CANCEL_ACCEPTED");
        }

        quote.setStatus(QuoteStatus.CANCELLED);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} cancelled", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    public QuoteDto clone(Long id) {
        Quote original = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        Quote cloned = Quote.builder()
                .quoteNumber(quoteNumberGenerator.generate())
                .name(original.getName() + " (Copy)")
                .description(original.getDescription())
                .customerExternalId(original.getCustomerExternalId())
                .dealExternalId(original.getDealExternalId())
                .status(QuoteStatus.DRAFT)
                .dealType(original.getDealType())
                .subscriptionType(original.getSubscriptionType())
                .currency(original.getCurrency())
                .region(original.getRegion())
                .durationInMonths(original.getDurationInMonths())
                .startDate(original.getStartDate())
                .endDate(original.getEndDate())
                .notes(original.getNotes())
                .build();

        Quote saved = quoteRepository.save(cloned);
        log.info("Cloned quote {} to {}", original.getQuoteNumber(), saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    /**
     * Calculate quote pricing by invoking pricing-service and persisting results.
     */
    public QuoteDto calculate(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        List<QuoteLineItem> lineItems = quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        if (lineItems.isEmpty()) {
            throw new BusinessException("Quote must have at least one line item", "QUOTE_NO_LINE_ITEMS");
        }

        Map<String, Object> pricingContext = buildPricingContext(quote, lineItems);
        JsonNode pricingData = fetchPricingData(pricingContext);
        Map<String, ArrayDeque<JsonNode>> pricingResultByLine = indexLineResults(pricingData.path("lineResults"));

        PricingTotals totals = applyPricingToLineItems(lineItems, pricingResultByLine);
        quoteLineItemRepository.saveAll(lineItems);

        quote.setSubtotal(totals.subtotal());
        quote.setTotalDiscount(totals.totalDiscount());
        quote.setTotalAmount(totals.totalAmount());

        updateQuoteFromPricingData(quote, pricingData);

        Quote saved = quoteRepository.save(quote);
        log.info("Calculated pricing for quote {}", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    private void updateQuoteFromPricingData(Quote quote, JsonNode pricingData) {
        JsonNode financialMetrics = pricingData.path("financialMetrics");
        quote.setArr(decimalValue(financialMetrics.path("arr"), quote.getArr()));
        quote.setTcv(decimalValue(financialMetrics.path("tcv"), quote.getTcv()));
        quote.setAcv(decimalValue(financialMetrics.path("acv"), quote.getAcv()));

        String approvalState = pricingData.path("approvalState").asText(null);
        if (approvalState != null && !approvalState.isBlank()) {
            try {
                quote.setApprovalState(ApprovalState.valueOf(approvalState));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown approval state from pricing service: {}", approvalState);
            }
        }

        quote.setApprovalTier(pricingData.path("approvalTier").asText(null));

        JsonNode waterfall = pricingData.path("waterfall");
        if (!waterfall.isMissingNode() && !waterfall.isNull()) {
            try {
                quote.setPricingWaterfallJson(objectMapper.writeValueAsString(waterfall));
            } catch (JsonProcessingException ex) {
                log.warn("Unable to serialize pricing waterfall for quote {}: {}", quote.getQuoteNumber(), ex.getMessage());
            }
        }
    }

    private record PricingTotals(BigDecimal subtotal, BigDecimal totalDiscount, BigDecimal totalAmount) {}

    private PricingTotals applyPricingToLineItems(List<QuoteLineItem> lineItems, Map<String, ArrayDeque<JsonNode>> pricingResultByLine) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (QuoteLineItem lineItem : lineItems) {
            String key = lineKey(lineItem.getProductExternalId(), lineItem.getLineItemType());
            ArrayDeque<JsonNode> queue = pricingResultByLine.get(key);
            JsonNode lineResult = queue != null ? queue.pollFirst() : null;

            if (lineResult == null) {
                throw new BusinessException(
                        "Pricing result missing for line item " + lineItem.getProductExternalId(),
                        "PRICING_RESULT_MISMATCH"
                );
            }

            BigDecimal quantity = BigDecimal.valueOf(lineItem.getQuantity());
            BigDecimal listPrice = decimalValue(lineResult.path("listPrice"), lineItem.getListPrice());
            BigDecimal unitPrice = decimalValue(lineResult.path("unitPrice"), listPrice);
            BigDecimal lineDiscount = decimalValue(lineResult.path("discountAmount"), BigDecimal.ZERO);
            BigDecimal lineFinal = decimalValue(
                    lineResult.path("finalPrice"),
                    unitPrice.multiply(quantity)
            );
            BigDecimal netUnit = quantity.signum() > 0
                    ? lineFinal.divide(quantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            lineItem.setListPrice(listPrice);
            lineItem.setUnitPrice(unitPrice);
            lineItem.setDiscountRate(decimalValue(lineResult.path("discountRate"), BigDecimal.ZERO));
            lineItem.setDiscountAmount(lineDiscount);
            lineItem.setNetPrice(netUnit);
            lineItem.setExtendedAmount(lineFinal);

            JsonNode explanationCodes = lineResult.path("explanationCodes");
            if (explanationCodes.isArray() && !explanationCodes.isEmpty()) {
                lineItem.setPricingExplanationCode(explanationCodes.get(0).asText());
            } else {
                lineItem.setPricingExplanationCode(null);
            }

            subtotal = subtotal.add(listPrice.multiply(quantity));
            totalDiscount = totalDiscount.add(lineDiscount);
            totalAmount = totalAmount.add(lineFinal);
        }

        return new PricingTotals(subtotal, totalDiscount, totalAmount);
    }

    private JsonNode fetchPricingData(Map<String, Object> pricingContext) {
        JsonNode pricingApiResponse;
        try {
            pricingApiResponse = pricingServiceClient.executePricing(pricingContext);
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call pricing service: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "PRICING_SERVICE_UNAVAILABLE"
            );
        }

        JsonNode wrapperSuccessNode = pricingApiResponse.path("success");
        if (!wrapperSuccessNode.asBoolean(false)) {
            throw new BusinessException(
                    pricingApiResponse.path("message").asText("Pricing execution failed"),
                    "PRICING_EXECUTION_FAILED"
            );
        }

        JsonNode pricingData = pricingApiResponse.path("data");
        if (!pricingData.path("success").asBoolean(false)) {
            throw new BusinessException(
                    pricingData.path("errorMessage").asText("Pricing execution failed"),
                    "PRICING_EXECUTION_FAILED"
            );
        }

        return pricingData;
    }

    private Map<String, Object> buildPricingContext(Quote quote, List<QuoteLineItem> lineItems) {
        List<Map<String, Object>> contextLineItems = lineItems.stream()
                .map(item -> {
                    Map<String, Object> line = new HashMap<>();
                    line.put("productExternalId", item.getProductExternalId());
                    line.put("lineItemType", item.getLineItemType());
                    line.put("quantity", item.getQuantity());
                    return line;
                })
                .toList();

        Map<String, Object> context = new HashMap<>();
        context.put("customerExternalId", quote.getCustomerExternalId());
        context.put("quoteExternalId", quote.getQuoteNumber());
        context.put("lineItems", contextLineItems);
        context.put("region", quote.getRegion());
        context.put("currency", quote.getCurrency());
        context.put("durationInMonths", quote.getDurationInMonths());
        context.put("subscriptionType", quote.getSubscriptionType());
        return context;
    }

    private Map<String, ArrayDeque<JsonNode>> indexLineResults(JsonNode lineResults) {
        Map<String, ArrayDeque<JsonNode>> indexed = new HashMap<>();
        if (!lineResults.isArray()) {
            return indexed;
        }

        for (JsonNode lineResult : lineResults) {
            String key = lineKey(
                    lineResult.path("productExternalId").asText(null),
                    lineResult.path("lineItemType").asText(null)
            );
            indexed.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(lineResult);
        }
        return indexed;
    }

    private String lineKey(String productExternalId, String lineItemType) {
        return (Objects.toString(productExternalId, "") + "::" + Objects.toString(lineItemType, "")).toLowerCase();
    }

    private BigDecimal decimalValue(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }

        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
    
    /**
     * Accept a quote and publish event via Transactional Outbox.
     * Ensures synchronized totals before acceptance.
     */
    public QuoteDto acceptQuote(Long id) {
        log.debug("Accepting quote ID: {}", id);
        
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        
        // Recalculate totals to ensure synchronization
        quote.recalculateTotals();
        
        // Use state machine method
        quote.accept();
        
        Quote saved = quoteRepository.save(quote);
        
        // Publish event via Transactional Outbox
        com.opencrm.common.event.dto.QuoteAcceptedEvent event = 
                new com.opencrm.common.event.dto.QuoteAcceptedEvent(
                        saved.getQuoteNumber(),
                        saved.getAcceptedAt(),
                        saved.getCustomerExternalId(),
                        saved.getTotalAmount(),
                        saved.getCurrency()
                );
        
        outboxEventPublisher.publish(
                "Quote",
                saved.getQuoteNumber(),
                KafkaTopics.QUOTE_STATUS_ACCEPTED,
                event
        );
        
        log.info("Accepted quote: {}", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }
}
