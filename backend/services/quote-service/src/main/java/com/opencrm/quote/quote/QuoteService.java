package com.opencrm.quote.quote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventCorrelation;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingFinancialMetricsView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final CurrentActor currentActor;

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

    /**
     * Pass a quote's review. A quote waiting on a tiered approval ({@code PENDING_APPROVAL}) is decided through that
     * approval in approval-routing, which checks the approver's role (ADR-016 decision 7), never here.
     *
     * <p>Passing a review releases the quote to be sent, so it is a controlled step: {@link ReviewAuthority} says who
     * may do it, and the quote's creator may not pass their own quote (ADR-016, owner decision 1).
     */
    public QuoteDto approve(Long id) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        authorizeReview(quote, true);
        refuseWhileRouted(quote);
        if (quote.getStatus() != QuoteStatus.IN_REVIEW) {
            throw new BusinessException("Quote must be in review to approve", "INVALID_STATUS_TRANSITION");
        }

        quote.setStatus(QuoteStatus.APPROVED);
        quote.setApprovalState(ApprovalState.APPROVED);
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} approved", saved.getQuoteNumber());
        return quoteMapper.toDto(saved);
    }

    /**
     * Fail a quote's review. Like {@link #approve}, it needs a reviewer role and a quote waiting on a tiered approval is
     * decided there. Failing your own quote is allowed: it only holds the quote back.
     */
    public QuoteDto reject(Long id, String reason) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", id));

        authorizeReview(quote, false);
        refuseWhileRouted(quote);
        if (quote.getStatus() != QuoteStatus.IN_REVIEW) {
            throw new BusinessException("Quote must be in review to reject", "INVALID_STATUS_TRANSITION");
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

    /**
     * Who may pass or fail a review (ADR-016, owner decision 1). Refusals are 401 without a verified caller, and 403 for
     * a service, a person without a reviewer role, or the creator passing their own quote. A quote with no recorded
     * creator, made before actors were recorded (ADR-016 step 4), has no four-eyes check but still needs the role.
     */
    private void authorizeReview(Quote quote, boolean passing) {
        Optional<Caller> caller = currentActor.caller();
        if (caller.isEmpty()) {
            if (!currentActor.unverifiedCallersAllowed()) {
                throw new BusinessException("An authenticated user is required to review quotes",
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
            }
            log.warn("dev mode: quote {} reviewed without a verified user", quote.getQuoteNumber());
            return;
        }
        if (!(caller.get() instanceof Caller.User user)) {
            throw new BusinessException("Quote reviews are decided by people, not by services",
                    HttpStatus.FORBIDDEN, "REVIEWER_MUST_BE_A_USER");
        }
        if (!ReviewAuthority.mayReview(user.authorities())) {
            log.warn("Refused review of quote {} by {}: none of {}", quote.getQuoteNumber(), user.actorId(),
                    ReviewAuthority.REVIEWERS);
            throw new BusinessException("Reviewing a quote needs the Deal Desk, Sales VP or CFO role",
                    HttpStatus.FORBIDDEN, "REVIEWER_ROLE_REQUIRED");
        }
        if (passing && user.actorId().equals(quote.getCreatedBy())) {
            log.warn("Refused self-review of quote {} by its creator {}", quote.getQuoteNumber(), user.actorId());
            throw new BusinessException("The creator of a quote cannot pass its review",
                    HttpStatus.FORBIDDEN, "SELF_REVIEW");
        }
    }

    /**
     * Deciding a {@code PENDING_APPROVAL} quote here would skip approval-routing's role check and leave its approval
     * open (ADR-016 decision 7, ADR-013 single writer).
     */
    private static void refuseWhileRouted(Quote quote) {
        if (quote.getStatus() == QuoteStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Quote " + quote.getQuoteNumber() + " is waiting on a tiered approval; decide that approval instead",
                    HttpStatus.CONFLICT,
                    "APPROVAL_PENDING"
            );
        }
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
        // ADR-018 D8 dual run: best-effort Redis publish after commit; the outbox row below is the reliable channel.
        // D9: this is the only emitter of cpq.quote.status.accepted (the unwired acceptQuote path was removed).
        eventPublisher.publishQuoteAccepted(QuoteAcceptedCrmMessageFactory.from(saved, lineItems));

        QuoteAcceptedEvent kafkaEvent = QuoteAcceptedKafkaEventFactory.from(saved, lineItems);
        // ADR-018 D4: the envelope headers carry the same correlation as the payload's snapshot (the quote UUID).
        QuoteAcceptedEvent.CorrelationSnapshot correlation = kafkaEvent.getCorrelation();
        try (EventCorrelation.Scope ignored =
                     EventCorrelation.begin(correlation.getCorrelationId(), correlation.getCausationId())) {
            outboxEventPublisher.publish(
                    "Quote",
                    saved.getQuoteNumber(),
                    EventContractRegistry.QUOTE_ACCEPTED_V1,
                    kafkaEvent
            );
        }

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

        List<QuoteLineItem> sourceLines =
                quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        List<QuoteLineItem> clonedLines = new ArrayList<>(sourceLines.size());
        for (QuoteLineItem source : sourceLines) {
            // Commercial snapshot only — skip pricingExplanationCode / pricingWaterfallJson
            QuoteLineItem copy = QuoteLineItem.builder()
                    .quote(saved)
                    .productExternalId(source.getProductExternalId())
                    .productName(source.getProductName())
                    .productType(source.getProductType())
                    .lineItemType(source.getLineItemType())
                    .quantity(source.getQuantity())
                    .unitOfMeasure(source.getUnitOfMeasure())
                    .listPrice(source.getListPrice())
                    .unitPrice(source.getUnitPrice())
                    .discountRate(source.getDiscountRate())
                    .discountAmount(source.getDiscountAmount())
                    .netPrice(source.getNetPrice())
                    .extendedAmount(source.getExtendedAmount())
                    .billingFrequency(source.getBillingFrequency())
                    .sortOrder(source.getSortOrder())
                    .notes(source.getNotes())
                    .metadata(source.getMetadata())
                    .build();
            clonedLines.add(copy);
        }

        if (!clonedLines.isEmpty()) {
            quoteLineItemRepository.saveAll(clonedLines);
        }

        // Same rollup as QuoteLineItemService.recalculateQuoteTotals — do not call Quote.recalculateTotals()
        BigDecimal subtotal = clonedLines.stream()
                .map(i -> i.getExtendedAmount() != null ? i.getExtendedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDiscount = clonedLines.stream()
                .map(i -> i.getDiscountAmount() != null ? i.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        saved.setSubtotal(subtotal);
        saved.setTotalDiscount(totalDiscount);
        saved.setTotalAmount(subtotal.subtract(totalDiscount));
        saved = quoteRepository.save(saved);

        saved.getLineItems().addAll(clonedLines);

        log.info("Cloned quote {} to {} with {} line items",
                original.getQuoteNumber(), saved.getQuoteNumber(), clonedLines.size());
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
        PricingExecuteView pricingData = fetchPricingData(pricingContext);
        Map<String, ArrayDeque<PricingLineResultView>> pricingResultByLine =
                indexLineResults(pricingData.getLineResults());

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

    private void updateQuoteFromPricingData(Quote quote, PricingExecuteView pricingData) {
        PricingFinancialMetricsView financialMetrics = pricingData.getFinancialMetrics();
        if (financialMetrics != null) {
            quote.setArr(decimalOrFallback(financialMetrics.getArr(), quote.getArr()));
            quote.setTcv(decimalOrFallback(financialMetrics.getTcv(), quote.getTcv()));
            quote.setAcv(decimalOrFallback(financialMetrics.getAcv(), quote.getAcv()));
        }

        String approvalState = pricingData.getApprovalState();
        if (approvalState != null && !approvalState.isBlank()) {
            try {
                quote.setApprovalState(ApprovalState.valueOf(approvalState));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown approval state from pricing service: {}", approvalState);
            }
        }

        quote.setApprovalTier(pricingData.getApprovalTier());

        JsonNode waterfall = pricingData.getWaterfall();
        if (waterfall != null && !waterfall.isMissingNode() && !waterfall.isNull()) {
            try {
                quote.setPricingWaterfallJson(objectMapper.writeValueAsString(waterfall));
            } catch (JsonProcessingException ex) {
                log.warn("Unable to serialize pricing waterfall for quote {}: {}", quote.getQuoteNumber(), ex.getMessage());
            }
        }
    }

    private record PricingTotals(BigDecimal subtotal, BigDecimal totalDiscount, BigDecimal totalAmount) {}

    private PricingTotals applyPricingToLineItems(
            List<QuoteLineItem> lineItems,
            Map<String, ArrayDeque<PricingLineResultView>> pricingResultByLine) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (QuoteLineItem lineItem : lineItems) {
            String key = lineKey(lineItem.getProductExternalId(), lineItem.getLineItemType());
            ArrayDeque<PricingLineResultView> queue = pricingResultByLine.get(key);
            PricingLineResultView lineResult = queue != null ? queue.pollFirst() : null;

            if (lineResult == null) {
                throw new BusinessException(
                        "Pricing result missing for line item " + lineItem.getProductExternalId(),
                        "PRICING_RESULT_MISMATCH"
                );
            }

            BigDecimal quantity = BigDecimal.valueOf(lineItem.getQuantity());
            BigDecimal listPrice = decimalOrFallback(lineResult.getListPrice(), lineItem.getListPrice());
            BigDecimal unitPrice = decimalOrFallback(lineResult.getUnitPrice(), listPrice);
            BigDecimal lineDiscount = decimalOrFallback(lineResult.getDiscountAmount(), BigDecimal.ZERO);
            BigDecimal lineFinal = decimalOrFallback(
                    lineResult.getFinalPrice(),
                    unitPrice.multiply(quantity)
            );
            BigDecimal netUnit = quantity.signum() > 0
                    ? lineFinal.divide(quantity, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            lineItem.setListPrice(listPrice);
            lineItem.setUnitPrice(unitPrice);
            lineItem.setDiscountRate(decimalOrFallback(lineResult.getDiscountRate(), BigDecimal.ZERO));
            lineItem.setDiscountAmount(lineDiscount);
            lineItem.setNetPrice(netUnit);
            lineItem.setExtendedAmount(lineFinal);

            List<String> explanationCodes = lineResult.getExplanationCodes();
            if (explanationCodes != null && !explanationCodes.isEmpty()) {
                lineItem.setPricingExplanationCode(explanationCodes.getFirst());
            } else {
                lineItem.setPricingExplanationCode(null);
            }

            subtotal = subtotal.add(listPrice.multiply(quantity));
            totalDiscount = totalDiscount.add(lineDiscount);
            totalAmount = totalAmount.add(lineFinal);
        }

        return new PricingTotals(subtotal, totalDiscount, totalAmount);
    }

    private PricingExecuteView fetchPricingData(Map<String, Object> pricingContext) {
        ApiResponse<PricingExecuteView> pricingApiResponse;
        try {
            pricingApiResponse = pricingServiceClient.executePricing(pricingContext);
        } catch (RestClientException ex) {
            throw new BusinessException(
                    "Failed to call pricing service: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "PRICING_SERVICE_UNAVAILABLE"
            );
        }

        if (pricingApiResponse == null || !pricingApiResponse.isSuccess()) {
            String message = pricingApiResponse != null && pricingApiResponse.getMessage() != null
                    ? pricingApiResponse.getMessage()
                    : "Pricing execution failed";
            throw new BusinessException(message, "PRICING_EXECUTION_FAILED");
        }

        PricingExecuteView pricingData = pricingApiResponse.getData();
        if (pricingData == null || !pricingData.isSuccess()) {
            String message = pricingData != null && pricingData.getErrorMessage() != null
                    ? pricingData.getErrorMessage()
                    : "Pricing execution failed";
            throw new BusinessException(message, "PRICING_EXECUTION_FAILED");
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

    private Map<String, ArrayDeque<PricingLineResultView>> indexLineResults(
            List<PricingLineResultView> lineResults) {
        Map<String, ArrayDeque<PricingLineResultView>> indexed = new HashMap<>();
        if (lineResults == null) {
            return indexed;
        }

        for (PricingLineResultView lineResult : lineResults) {
            String key = lineKey(lineResult.getProductExternalId(), lineResult.getLineItemType());
            indexed.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(lineResult);
        }
        return indexed;
    }

    private String lineKey(String productExternalId, String lineItemType) {
        return (Objects.toString(productExternalId, "") + "::" + Objects.toString(lineItemType, "")).toLowerCase();
    }

    private BigDecimal decimalOrFallback(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }
}
