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
import com.opencrm.quote.client.PricedPeriodView;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingFinancialMetricsView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemDto;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.RampReasons;
import com.opencrm.quote.ramp.RampScheduleException;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.quote.ramp.RampScheduleViews;
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
    private final RampScheduleGuard rampScheduleGuard;
    private QuoteContinuationService continuationService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setContinuationService(QuoteContinuationService continuationService) {
        this.continuationService = continuationService;
    }

    private static final Set<QuoteStatus> EDITABLE_STATUSES = Set.of(QuoteStatus.DRAFT, QuoteStatus.REJECTED);

    public QuoteDto create(QuoteCreateRequest request) {
        log.debug("Creating quote for customer: {}", request.getCustomerExternalId());

        Quote quote = quoteMapper.toEntity(request);
        quote.setQuoteNumber(quoteNumberGenerator.generate());

        applyOpportunityDealType(request, quote);
        if (request.getDurationInMonths() == null) {
            quote.setDurationInMonths(12);
        }
        quote.setTenantId(QuoteTenantResolver.currentTenantId());

        Quote saved = quoteRepository.save(quote);
        log.info("Created quote: {}", saved.getQuoteNumber());

        return quoteMapper.toDto(saved);
    }

    /**
     * Opportunity-to-quote handoff for deal type.
     *
     * <p>A CRM type is translated by {@link CrmQuoteDealTypeMapping}. Unknown CRM values fail.
     * When the request also carries a quote {@link DealType} that disagrees with the table, creation
     * fails ({@code CRM_DEAL_TYPE_CONFLICT}). When the CRM type is omitted, an explicit quote type is
     * kept; if that is omitted too, the documented default is {@link DealType#NEW}.
     */
    private void applyOpportunityDealType(QuoteCreateRequest request, Quote quote) {
        if (request.getCrmDealType() != null) {
            DealType mapped = CrmQuoteDealTypeMapping.toQuoteDealType(request.getCrmDealType());
            if (request.getDealType() != null && request.getDealType() != mapped) {
                throw new BusinessException(
                        "Quote deal type " + request.getDealType()
                                + " conflicts with CRM deal type mapping to " + mapped,
                        "CRM_DEAL_TYPE_CONFLICT");
            }
            quote.setDealType(mapped);
            return;
        }
        if (request.getDealType() == null) {
            quote.setDealType(DealType.NEW);
        }
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

        if (!EDITABLE_STATUSES.contains(quote.getStatus()) && !(quote.getStatus() == QuoteStatus.PENDING_APPROVAL
                && com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote) != null)) {
            throw new BusinessException(
                    "Quote can only be edited in DRAFT or REJECTED status. Current: " + quote.getStatus(),
                    "QUOTE_NOT_EDITABLE"
            );
        }

        int previousDuration = quote.getDurationInMonths();
        String previousCurrency = quote.getCurrency();
        String previousRegion = quote.getRegion();
        String previousAuthoring = com.opencrm.quote.ramp.RampPricingSnapshot.authoringFingerprint(quote,
                quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id));
        String previousSubscriptionType = quote.getSubscriptionType();
        java.time.LocalDate previousStart = quote.getStartDate();
        java.time.LocalDate previousEnd = quote.getEndDate();
        quoteMapper.updateEntity(request, quote);
        rampScheduleGuard.markIfContextChanged(quote, previousDuration, previousCurrency, previousRegion);
        if (!Objects.equals(previousStart, quote.getStartDate()) || !Objects.equals(previousEnd, quote.getEndDate())
                || !Objects.equals(previousSubscriptionType, quote.getSubscriptionType())) {
            List<QuoteLineItem> lines = quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
            if (lines.stream().anyMatch(RampScheduleViews::hasSteps)) quote.setRampScheduleStale(true);
        }
        rampScheduleGuard.invalidateOnEdit(quote, previousAuthoring);
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

        rampScheduleGuard.refuseIfStale(quote);
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
        // REJECTED is editable; callers use this version for the next schedule write.
        quoteRepository.flush();
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

        rampScheduleGuard.requireApproved(quote);
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

        rampScheduleGuard.requireApproved(quote);
        List<QuoteLineItem> lineItems = quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        validateContinuation(quote,lineItems);

        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(Instant.now());
        // Acceptance freezes the totals the customer accepted. Quote.recalculateTotals() re-derives them from
        // unitPrice and adds a fixed 10% tax, so the published total would no longer match the priced lines in
        // QuoteAccepted.lineItems. Like clone(), do not call it here.
        Quote saved = quoteRepository.save(quote);
        log.info("Quote {} accepted", saved.getQuoteNumber());

        QuoteAcceptedEvent kafkaEvent = QuoteAcceptedKafkaEventFactory.from(saved, lineItems);
        // ADR-018 D8 dual run: best-effort Redis publish after commit; the outbox row below is the reliable channel.
        // D9: this is the only emitter of cpq.quote.status.accepted (the unwired acceptQuote path was removed).
        eventPublisher.publishQuoteAccepted(QuoteAcceptedCrmMessageFactory.from(saved, lineItems));

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
                .targetSubscriptionNumber(original.getTargetSubscriptionNumber())
                .observedRevision(original.getObservedRevision())
                .subscriptionEffectiveDate(original.getSubscriptionEffectiveDate())
                .continuationRequestJson(original.getContinuationRequestJson())
                .continuationOperation(original.getContinuationOperation())
                .tenantId(QuoteTenantResolver.currentTenantId())
                .notes(original.getNotes())
                .rampScheduleStale(original.isRampScheduleStale())
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
                    .sourceSubscriptionLineUuid(source.getSourceSubscriptionLineUuid())
                    .hybridMigrationJson(source.getHybridMigrationJson())
                    .initialProrationJson(source.getInitialProrationJson())
                    .rampEscalatorStartPrice(source.getRampEscalatorStartPrice())
                    .rampEscalatorMode(source.getRampEscalatorMode())
                    .rampEscalatorAmount(source.getRampEscalatorAmount())
                    .rampEscalatorEveryMonths(source.getRampEscalatorEveryMonths())
                    .rampEscalatorCompounding(source.getRampEscalatorCompounding())
                    .build();
            copyRampSteps(source, copy);
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
        if (clonedLines.stream().anyMatch(RampScheduleViews::hasSteps) || saved.getTargetSubscriptionNumber() != null) saved.setRampScheduleStale(true);
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

        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new BusinessException("Accepted quote pricing is immutable; create a new version",
                    HttpStatus.CONFLICT, "QUOTE_NOT_EDITABLE");
        }
        String previousFingerprint = com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote);
        boolean previouslyRequired = com.opencrm.quote.ramp.RampPricingSnapshot.read(quote.getPricingWaterfallJson()).hasNonNull("rampGovernance");
        ApprovalState previousApproval = quote.getApprovalState();
        List<QuoteLineItem> lineItems = quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(id);
        if (lineItems.isEmpty()) {
            throw new BusinessException("Quote must have at least one line item", "QUOTE_NO_LINE_ITEMS");
        }

        QuoteContinuationPolicy.Plan continuation = validateContinuation(quote,lineItems);
        PricingExecuteView partialPolicy = continuation != null && continuation.initialProration() != null
                ? priceInitialInterval(quote,lineItems,continuation) : null;
        if (continuation != null && continuation.periods().isEmpty()) {
            applyPartialOnly(quote,lineItems.getFirst(),continuation,partialPolicy);
            quote.setRampScheduleStale(false);
            bindRampSnapshot(quote,lineItems,previousFingerprint,previousApproval,previouslyRequired);
            quoteLineItemRepository.saveAll(lineItems);
            // Flush the calculated root before mapping so the returned optimistic version is current.
            Quote saved = quoteRepository.save(quote);
            quoteRepository.flush();
            return quoteMapper.toDto(saved);
        }

        Map<String, Object> pricingContext = buildPricingContext(quote, lineItems);
        PricingExecuteView pricingData = fetchPricingData(pricingContext);
        Map<String, ArrayDeque<PricingLineResultView>> pricingResultByLine =
                indexLineResults(pricingData.getLineResults());
        refuseUnpricedSchedules(lineItems, pricingResultByLine);
        if (continuation != null) verifyContinuationPeriods(continuation,pricingData,lineItems);

        Map<Long, List<PricedPeriodView>> pricedByLineId = new HashMap<>();
        PricingTotals totals = applyPricingToLineItems(lineItems, pricingResultByLine, pricedByLineId);
        quoteLineItemRepository.saveAll(lineItems);

        quote.setSubtotal(totals.subtotal());
        quote.setTotalDiscount(totals.totalDiscount());
        quote.setTotalAmount(totals.totalAmount());

        updateQuoteFromPricingData(quote, pricingData);
        if (partialPolicy != null) applyInitialInterval(quote,lineItems,continuation,partialPolicy);
        quote.setRampScheduleStale(false);
        bindRampSnapshot(quote, lineItems, previousFingerprint, previousApproval, previouslyRequired);

        // The response feeds subsequent version-checked extension; map the flushed version.
        Quote saved = quoteRepository.save(quote);
        quoteRepository.flush();
        log.info("Calculated pricing for quote {}", saved.getQuoteNumber());
        QuoteDto dto = quoteMapper.toDto(saved);
        stampRampView(dto, saved, pricedByLineId);
        return dto;
    }

    private void updateQuoteFromPricingData(Quote quote, PricingExecuteView pricingData) {
        PricingFinancialMetricsView financialMetrics = pricingData.getFinancialMetrics();
        if (financialMetrics != null) {
            quote.setArr(decimalOrFallback(financialMetrics.getArr(), quote.getArr()));
            quote.setTcv(decimalOrFallback(financialMetrics.getTcv(), quote.getTcv()));
            quote.setAcv(decimalOrFallback(financialMetrics.getAcv(), quote.getAcv()));
            quote.setExitArr(decimalOrFallback(financialMetrics.getExitArr(), quote.getExitArr()));
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
            Map<String, ArrayDeque<PricingLineResultView>> pricingResultByLine,
            Map<Long, List<PricedPeriodView>> pricedByLineId) {
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
            List<PricedPeriodView> periods = lineResult.getPricedPeriods();
            Object authoritativeType = lineResult.getMetadata() == null ? null : lineResult.getMetadata().get("authoritativeProductType");
            if (periods != null && !periods.isEmpty() && !AcceptedRampSnapshots.matchesAuthority(lineItem,authoritativeType))
                throw new BusinessException("Ramp pricing product classification is unverified or conflicts with the quote", "RAMP_PRICING_SNAPSHOT_INVALID");
            if (periods != null && !periods.isEmpty()) {
                try {
                    Map<String, Object> snapshot = new HashMap<>();
                    snapshot.put("authoritativeProductType",authoritativeType);
                    snapshot.put("pricedPeriods", periods);
                    snapshot.put("rampPolicyContext", lineResult.getMetadata().get("rampPolicyContext"));
                    if (lineResult.getMetadata().get("migrationCreditAudit") != null)
                        snapshot.put("migrationCreditAudit",lineResult.getMetadata().get("migrationCreditAudit"));
                    if (lineResult.getMetadata() != null && lineResult.getMetadata().get("rampGovernance") != null) {
                        snapshot.put("rampGovernance", lineResult.getMetadata().get("rampGovernance"));
                    }
                    lineItem.setPricingWaterfallJson(objectMapper.writeValueAsString(snapshot));
                } catch (JsonProcessingException ex) {
                    throw new BusinessException("Cannot persist ramp pricing", "RAMP_PRICING_SNAPSHOT_INVALID");
                }
            } else {
                try {
                    lineItem.setPricingWaterfallJson(authoritativeType instanceof String
                            ? objectMapper.writeValueAsString(Map.of("authoritativeProductType",authoritativeType)) : null);
                } catch (JsonProcessingException ex) { throw new BusinessException("Cannot persist pricing product authority", "RAMP_PRICING_SNAPSHOT_INVALID"); }
            }
            if (lineItem.getId() != null && periods != null && !periods.isEmpty()) {
                pricedByLineId.put(lineItem.getId(), List.copyOf(periods));
            }

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

    private void bindRampSnapshot(Quote quote, List<QuoteLineItem> lines,
                                  String previousFingerprint, ApprovalState previousApproval, boolean previouslyRequired) {
        if (lines.stream().noneMatch(RampScheduleViews::hasSteps) && !previouslyRequired && quote.getTargetSubscriptionNumber() == null) return;
        com.opencrm.quote.ramp.RampGovernanceDto governing = null;
        String selectedTier = null;
        for (QuoteLineItem line : lines) {
            if (!RampScheduleViews.hasSteps(line) && line.getInitialProrationJson() == null) continue;
            JsonNode candidate = com.opencrm.quote.ramp.RampPricingSnapshot.read(line.getPricingWaterfallJson()).get("rampGovernance");
            if (candidate == null || candidate.isNull()) continue;
            String candidateTier = candidate.path("approvalTier").asText();
            com.fasterxml.jackson.databind.node.ObjectNode copied = (com.fasterxml.jackson.databind.node.ObjectNode) candidate.deepCopy();
            copied.remove("approvalTier");
            com.opencrm.quote.ramp.RampGovernanceDto value = objectMapper.convertValue(copied, com.opencrm.quote.ramp.RampGovernanceDto.class);
            value.setGoverningLineItemId(line.getId());
            boolean anyStepDown = value.isStepDown() || (governing != null && governing.isStepDown());
            List<String> allReasons = new ArrayList<>(value.getReasons());
            if (governing != null) allReasons.addAll(governing.getReasons());
            if (governing == null || (candidateTier.equals(quote.getApprovalTier()) && !candidateTier.equals(selectedTier))
                    || (candidateTier.equals(selectedTier) && value.getDiscountPercent().compareTo(governing.getDiscountPercent()) > 0)) {
                governing = value;
                selectedTier = candidateTier;
            }
            governing.setStepDown(anyStepDown);
            governing.setReasons(allReasons.stream().distinct().toList());
        }
        String fingerprint = com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote, lines);
        long distinctPairs=lines.stream().map(com.opencrm.quote.ramp.RampPricingSnapshot::hybridMigration)
                .filter(Objects::nonNull).map(com.opencrm.quote.ramp.HybridMigrationContext::pairId).distinct().count();
        if (governing != null && distinctPairs > 1) governing.setHybridMigration(null);
        com.fasterxml.jackson.databind.node.ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("waterfall", com.opencrm.quote.ramp.RampPricingSnapshot.read(quote.getPricingWaterfallJson()));
        snapshot.put("pricingFingerprint", fingerprint);
        if (governing != null) snapshot.set("rampGovernance", objectMapper.valueToTree(governing));
        try { quote.setPricingWaterfallJson(objectMapper.writeValueAsString(snapshot)); }
        catch (JsonProcessingException ex) { throw new BusinessException("Cannot bind ramp approval", "RAMP_PRICING_SNAPSHOT_INVALID"); }
        if (Objects.equals(fingerprint, previousFingerprint)) {
            quote.setApprovalState(previousApproval);
            return;
        }
        if (governing != null) {
            quote.setApprovalState(ApprovalState.PENDING);
            com.opencrm.common.event.dto.DiscountApprovalRequiredEvent event = new com.opencrm.common.event.dto.DiscountApprovalRequiredEvent(
                    String.valueOf(quote.getId()), String.valueOf(governing.getGoverningLineItemId()),
                    BigDecimal.ZERO, governing.getDiscountPercent().divide(BigDecimal.valueOf(100)), quote.getCurrency(),
                    fingerprint, governing.getGoverningPeriod(), quote.getApprovalTier(),
                    "Ramp period " + governing.getGoverningPeriod() + ": " + String.join(", ", governing.getReasons()));
            rampScheduleGuard.publishBinding(event);
        } else if (previouslyRequired) {
            QuoteLineItem line = lines.stream().filter(RampScheduleViews::hasSteps).findFirst().orElse(lines.getFirst());
            com.opencrm.common.event.dto.DiscountApprovalRequiredEvent retired = new com.opencrm.common.event.dto.DiscountApprovalRequiredEvent(
                    String.valueOf(quote.getId()), String.valueOf(line.getId()), BigDecimal.ZERO, BigDecimal.ZERO,
                    quote.getCurrency(), fingerprint, 1,
                    "DEAL_DESK", "Ramp policy cleared; previous approval snapshot retired");
            retired.setApprovalRequired(false);
            rampScheduleGuard.publishBinding(retired);
        }
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
            throw pricingEnvelopeFailure(pricingApiResponse);
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

    /**
     * Flag-off is 422 {@code RAMP_NOT_SUPPORTED} with no success body. A line the engine
     * will not price is 422 {@code RAMP_SCHEDULE_INVALID} and must not be relabeled as
     * the empty-period failure.
     */
    private RuntimeException pricingEnvelopeFailure(ApiResponse<PricingExecuteView> pricingApiResponse) {
        if (pricingApiResponse == null) {
            return new BusinessException("Pricing execution failed", "PRICING_EXECUTION_FAILED");
        }
        String code = pricingApiResponse.getErrorCode();
        String message = pricingApiResponse.getMessage();
        if (RampReasons.NOT_SUPPORTED.equals(code)) {
            return RampScheduleException.notSupported();
        }
        if (RampReasons.SCHEDULE_INVALID.equals(code)
                && RampReasons.NOT_ALLOWED_FOR_LINE_TYPE.equals(message)) {
            return RampScheduleException.invalid(List.of(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE));
        }
        String fallback = message != null ? message : "Pricing execution failed";
        return new BusinessException(fallback, "PRICING_EXECUTION_FAILED");
    }

    private void copyRampSteps(QuoteLineItem source, QuoteLineItem copy) {
        if (source.getRampSteps() == null) {
            return;
        }
        if (copy.getRampSteps() == null) {
            copy.setRampSteps(new ArrayList<>());
        }
        for (QuoteRampStep step : source.getRampSteps()) {
            if (step == null || step.isDeleted()) {
                continue;
            }
            QuoteRampStep cloned = QuoteRampStep.builder()
                    .lineItem(copy)
                    .sequence(step.getSequence())
                    .startMonth(step.getStartMonth())
                    .months(step.getMonths())
                    .quantity(step.getQuantity())
                    .priceBasis(step.getPriceBasis())
                    .unitPrice(step.getUnitPrice())
                    .discountFraction(step.getDiscountFraction())
                    .build();
            copy.getRampSteps().add(cloned);
        }
    }

    /**
     * Flat lines send product, type, and quantity. A line with steps also sends
     * {@code billingFrequency} and {@code schedule}. Pricing refuses a schedule
     * that has no recurring frequency.
     */
    private Map<String, Object> buildPricingContext(Quote quote, List<QuoteLineItem> lineItems) {
        List<Map<String, Object>> contextLineItems = lineItems.stream()
                .map(this::toPricingLine)
                .toList();

        Map<String, Object> context = new HashMap<>();
        context.put("customerExternalId", quote.getCustomerExternalId());
        context.put("quoteExternalId", quote.getQuoteNumber());
        context.put("lineItems", contextLineItems);
        context.put("region", quote.getRegion());
        context.put("currency", quote.getCurrency());
        context.put("durationInMonths", quote.getDurationInMonths());
        context.put("subscriptionType", quote.getSubscriptionType());
        // Quote DealType is already the CRM mapping result (CrmQuoteDealTypeMapping). Pricing
        // matches the stored name; churn stays on the pricing customer, not this request.
        if (quote.getDealType() != null) {
            context.put("metadata", Map.of("dealType", quote.getDealType().name()));
        }
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

    private Map<String, Object> toPricingLine(QuoteLineItem item) {
        Map<String, Object> line = new HashMap<>();
        line.put("productExternalId", item.getProductExternalId());
        line.put("lineItemType", item.getLineItemType());
        line.put("quantity", item.getQuantity());
        if (item.getHybridMigrationJson() != null) {
            line.put("metadata", Map.of("hybridMigration",com.opencrm.quote.ramp.RampPricingSnapshot.hybridMigration(item)));
        }
        List<QuoteRampStep> steps = RampScheduleViews.activeSteps(item);
        if (!steps.isEmpty()) {
            line.put("billingFrequency", item.getBillingFrequency());
            line.put("schedule", steps.stream().map(this::toPricingStep).toList());
        }
        return line;
    }

    private QuoteContinuationPolicy.Plan validateContinuation(Quote quote,List<QuoteLineItem> lines) {
        if (quote.getDealType() == DealType.NEW && quote.getTargetSubscriptionNumber() == null) return null;
        if (continuationService == null) throw new BusinessException("Typed subscription continuation validation is unavailable","SUBSCRIPTION_CONTINUATION_INVALID");
        return continuationService.validate(quote,lines);
    }

    /** A real positive-month policy evaluation; this period is never authored or emitted as sold coverage. */
    private PricingExecuteView priceInitialInterval(Quote quote,List<QuoteLineItem> lines,QuoteContinuationPolicy.Plan plan) {
        QuoteLineItem line=lines.stream().filter(AcceptedRampSnapshots::recurring).findFirst().orElseThrow();
        var partial=plan.initialProration();
        Map<String,Object> context=buildPricingContext(quote,lines);
        Map<String,Object> policyLine=new HashMap<>();
        policyLine.put("productExternalId",line.getProductExternalId());policyLine.put("lineItemType",line.getLineItemType());
        policyLine.put("quantity",partial.getQuantity());policyLine.put("billingFrequency",line.getBillingFrequency());
        policyLine.put("schedule",List.of(Map.of("sequence",1,"startMonth",1,"months",1,"quantity",partial.getQuantity(),
                "priceBasis","ABSOLUTE","unitPrice",partial.getNetUnitPrice())));
        context.put("lineItems",List.of(policyLine));context.put("durationInMonths",1);
        PricingExecuteView result=fetchPricingData(context);
        var matches=result.getLineResults() == null ? List.<PricingLineResultView>of() : result.getLineResults();
        if (matches.size() != 1 || matches.getFirst().getPricedPeriods() == null || matches.getFirst().getPricedPeriods().size() != 1
                || matches.getFirst().getMetadata() == null || !Boolean.TRUE.equals(matches.getFirst().getMetadata().get("rampGovernanceEvaluated")))
            throw RampScheduleException.notSupported();
        var evaluated=matches.getFirst().getPricedPeriods().getFirst();
        if (!Objects.equals(matches.getFirst().getProductExternalId(),line.getProductExternalId())
                || !Objects.equals(matches.getFirst().getLineItemType(),line.getLineItemType())
                || !AcceptedRampSnapshots.matchesAuthority(line,matches.getFirst().getMetadata().get("authoritativeProductType"))
                || !Objects.equals(evaluated.getQuantity(),partial.getQuantity()) || !Objects.equals(evaluated.getMonths(),1)
                || !Objects.equals(evaluated.getStartMonth(),1) || !Objects.equals(evaluated.getSequence(),1)
                || evaluated.getNetUnitPrice() == null || evaluated.getNetUnitPrice().compareTo(partial.getNetUnitPrice()) != 0
                || evaluated.getNetMonthly() == null || evaluated.getNetMonthly().compareTo(partial.getNetMonthly()) != 0
                || evaluated.getPeriodAmount() == null || evaluated.getPeriodAmount().compareTo(partial.getNetMonthly()) != 0
                || evaluated.getGrossPeriodAmount() != null || evaluated.getMigrationCredit() != null)
            throw new BusinessException("Current pricing policy cannot preserve the accepted partial unit rate; author a fresh commercial change","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
        return result;
    }

    private void applyPartialOnly(Quote quote,QuoteLineItem line,QuoteContinuationPolicy.Plan plan,PricingExecuteView policy) {
        var result=policy.getLineResults().getFirst();
        line.setPricingWaterfallJson(null);
        quote.setPricingWaterfallJson(null);
        quote.setApprovalState(ApprovalState.NONE);quote.setApprovalTier(null);
        line.setListPrice(result.getListPrice());
        quote.setTcv(BigDecimal.ZERO);quote.setAcv(BigDecimal.ZERO);quote.setExitArr(plan.initialProration().getNetMonthly().multiply(BigDecimal.valueOf(12)));
        quote.setSubtotal(result.getListPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        quote.setTotalTax(BigDecimal.ZERO);
        applyInitialInterval(quote,List.of(line),plan,policy);
    }

    private void verifyContinuationPeriods(QuoteContinuationPolicy.Plan plan,PricingExecuteView pricing,List<QuoteLineItem> items) {
        List<QuoteLineItem> recurring = items.stream().filter(line -> !line.isDeleted()).filter(AcceptedRampSnapshots::recurring).toList();
        if (recurring.size() > 1) {
            verifyEachContinuationLine(plan, pricing, recurring);
            return;
        }
        QuoteLineItem line=items.stream().filter(AcceptedRampSnapshots::recurring).findFirst().orElseThrow();
        List<PricingLineResultView> matches=pricing.getLineResults().stream().filter(r -> Objects.equals(r.getProductExternalId(),line.getProductExternalId())
                && Objects.equals(r.getLineItemType(),line.getLineItemType())).toList();
        if (matches.size() != 1 || matches.getFirst().getPricedPeriods() == null || matches.getFirst().getPricedPeriods().size() != plan.periods().size())
            throw new BusinessException("Current pricing cannot preserve the inherited continuation schedule","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
        for(int i=0;i<plan.periods().size();i++) {
            var expected=plan.periods().get(i);var actual=matches.getFirst().getPricedPeriods().get(i);
            if (actual == null || !Objects.equals(actual.getSequence(),expected.sequence()) || !Objects.equals(actual.getStartMonth(),expected.startMonth())
                    || !Objects.equals(actual.getMonths(),expected.months()) || !Objects.equals(actual.getQuantity(),expected.quantity())
                    || actual.getNetUnitPrice() == null || actual.getNetUnitPrice().compareTo(expected.unitPrice()) != 0
                    || actual.getGrossPeriodAmount() != null || actual.getMigrationCredit() != null)
                throw new BusinessException("Current pricing changes the inherited continuation unit rate; author a fresh commercial change","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
        }
    }

    /** One priced period per asset line. The single-line ramp path stays in {@link #verifyContinuationPeriods}. */
    private void verifyEachContinuationLine(QuoteContinuationPolicy.Plan plan, PricingExecuteView pricing,
                                            List<QuoteLineItem> recurring) {
        if (pricing == null || pricing.getLineResults() == null || plan == null || plan.periods() == null
                || plan.periods().size() != recurring.size()) {
            throw new BusinessException("Current pricing cannot preserve the inherited continuation schedule","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
        }
        Map<String, ArrayDeque<PricingLineResultView>> indexed = indexLineResults(pricing.getLineResults());
        for (int i = 0; i < recurring.size(); i++) {
            QuoteLineItem line = recurring.get(i);
            QuoteContinuationPolicy.Period expected = plan.periods().get(i);
            ArrayDeque<PricingLineResultView> queue = indexed.get(lineKey(line.getProductExternalId(), line.getLineItemType()));
            PricingLineResultView match = queue == null || queue.isEmpty() ? null : queue.removeFirst();
            if (match == null || match.getPricedPeriods() == null || match.getPricedPeriods().size() != 1) {
                throw new BusinessException("Current pricing cannot preserve the inherited continuation schedule","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
            }
            PricedPeriodView actual = match.getPricedPeriods().getFirst();
            if (actual == null || !Objects.equals(actual.getSequence(), expected.sequence())
                    || !Objects.equals(actual.getStartMonth(), expected.startMonth())
                    || !Objects.equals(actual.getMonths(), expected.months())
                    || !Objects.equals(actual.getQuantity(), expected.quantity())
                    || actual.getNetUnitPrice() == null || actual.getNetUnitPrice().compareTo(expected.unitPrice()) != 0
                    || actual.getGrossPeriodAmount() != null || actual.getMigrationCredit() != null) {
                throw new BusinessException("Current pricing changes the inherited continuation unit rate; author a fresh commercial change","SUBSCRIPTION_CONTINUATION_RATE_CHANGED");
            }
        }
    }

    private void applyInitialInterval(Quote quote,List<QuoteLineItem> lines,QuoteContinuationPolicy.Plan plan,PricingExecuteView policy) {
        QuoteLineItem line=lines.stream().filter(AcceptedRampSnapshots::recurring).findFirst().orElseThrow();
        var partial=plan.initialProration();var result=policy.getLineResults().getFirst();
        BigDecimal previousMonthly=line.getExtendedAmount() == null ? BigDecimal.ZERO : line.getExtendedAmount();
        BigDecimal previousDiscount=line.getDiscountAmount() == null ? BigDecimal.ZERO : line.getDiscountAmount();
        line.setUnitPrice(partial.getNetUnitPrice());line.setNetPrice(partial.getNetUnitPrice());line.setExtendedAmount(partial.getNetMonthly());
        line.setDiscountRate(result.getPricedPeriods().getFirst().getDiscountRate());
        line.setDiscountAmount(result.getDiscountAmount() == null ? BigDecimal.ZERO : result.getDiscountAmount());
        quote.setTotalAmount(plan.periods().isEmpty() ? partial.getNetMonthly() : quote.getTotalAmount().subtract(previousMonthly).add(partial.getNetMonthly()));
        quote.setTotalDiscount(plan.periods().isEmpty() ? line.getDiscountAmount() : quote.getTotalDiscount().subtract(previousDiscount).add(line.getDiscountAmount()));
        quote.setArr(partial.getNetMonthly().multiply(BigDecimal.valueOf(12)));
        quote.setTcv(quote.getTcv().add(partial.getPeriodAmount()));
        quote.setAcv(quote.getTcv().multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(quote.getDurationInMonths()),2,RoundingMode.HALF_UP));
        var snapshot=(com.fasterxml.jackson.databind.node.ObjectNode)com.opencrm.quote.ramp.RampPricingSnapshot.read(line.getPricingWaterfallJson()).deepCopy();
        snapshot.set("authoritativeProductType",objectMapper.valueToTree(result.getMetadata().get("authoritativeProductType")));
        snapshot.set("initialProrationPolicyContext",objectMapper.valueToTree(result.getMetadata().get("rampPolicyContext")));
        JsonNode partialGovernance=objectMapper.valueToTree(result.getMetadata().get("rampGovernance"));
        if (partialGovernance != null && partialGovernance.isObject()) {
            var copied=(com.fasterxml.jackson.databind.node.ObjectNode)partialGovernance.deepCopy();copied.put("governingPeriod",0);
            snapshot.set("initialProrationGovernance",copied);
            snapshot.set("rampGovernance",mergeGovernance(snapshot.get("rampGovernance"),copied));
        }
        JsonNode governing=snapshot.get("rampGovernance");
        if (governing != null && governing.isObject()) {
            String tier=governing.path("approvalTier").asText(null);
            if (tierRank(tier)>tierRank(quote.getApprovalTier())) {quote.setApprovalTier(tier);quote.setApprovalState(ApprovalState.PENDING);}
        }
        try {
            line.setInitialProrationJson(objectMapper.writeValueAsString(partial));
            line.setPricingWaterfallJson(objectMapper.writeValueAsString(snapshot));
        } catch(JsonProcessingException ex) { throw new BusinessException("Cannot persist verified partial pricing","RAMP_PRICING_SNAPSHOT_INVALID"); }
    }

    private JsonNode mergeGovernance(JsonNode full,JsonNode partial) {
        if (full == null || full.isNull()) return partial;
        boolean selectPartial=tierRank(partial.path("approvalTier").asText(null))>tierRank(full.path("approvalTier").asText(null))
                || (tierRank(partial.path("approvalTier").asText(null))==tierRank(full.path("approvalTier").asText(null))
                && partial.path("discountPercent").decimalValue().compareTo(full.path("discountPercent").decimalValue())>0);
        var merged=(com.fasterxml.jackson.databind.node.ObjectNode)(selectPartial ? partial : full).deepCopy();
        Set<String> reasons=new LinkedHashSet<>();
        if (full.path("reasons").isArray()) full.path("reasons").forEach(r -> reasons.add(r.asText()));
        if (partial.path("reasons").isArray()) partial.path("reasons").forEach(r -> reasons.add(r.asText()));
        merged.set("reasons",objectMapper.valueToTree(reasons));merged.put("stepDown",full.path("stepDown").asBoolean() || partial.path("stepDown").asBoolean());
        return merged;
    }
    private int tierRank(String tier) { return tier == null ? 0 : switch(tier) {case "CFO" -> 3;case "SALES_VP" -> 2;case "DEAL_DESK" -> 1;default -> throw new BusinessException("Unknown current pricing approval tier","RAMP_PRICING_SNAPSHOT_INVALID");}; }

    private Map<String, Object> toPricingStep(QuoteRampStep step) {
        Map<String, Object> row = new HashMap<>();
        row.put("sequence", step.getSequence());
        row.put("startMonth", step.getStartMonth());
        row.put("months", step.getMonths());
        row.put("quantity", step.getQuantity());
        row.put("priceBasis", step.getPriceBasis());
        if (step.getUnitPrice() != null) {
            row.put("unitPrice", step.getUnitPrice());
        }
        if (step.getDiscountFraction() != null) {
            row.put("discountFraction", step.getDiscountFraction());
        }
        return row;
    }

    /**
     * A sent schedule with no period results is refused before any total is saved.
     */
    private void refuseUnpricedSchedules(
            List<QuoteLineItem> lineItems,
            Map<String, ArrayDeque<PricingLineResultView>> pricingResultByLine) {
        Map<String, ArrayDeque<PricingLineResultView>> pending = new HashMap<>();
        for (Map.Entry<String, ArrayDeque<PricingLineResultView>> entry : pricingResultByLine.entrySet()) {
            pending.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
        }
        for (QuoteLineItem lineItem : lineItems) {
            if (!RampScheduleViews.hasSteps(lineItem)) {
                continue;
            }
            String key = lineKey(lineItem.getProductExternalId(), lineItem.getLineItemType());
            ArrayDeque<PricingLineResultView> queue = pending.get(key);
            PricingLineResultView lineResult = queue != null ? queue.pollFirst() : null;
            List<PricedPeriodView> periods = lineResult != null ? lineResult.getPricedPeriods() : null;
            if (periods == null || periods.isEmpty() || lineResult.getMetadata() == null
                    || !Boolean.TRUE.equals(lineResult.getMetadata().get("rampGovernanceEvaluated"))) {
                throw RampScheduleException.notSupported();
            }
        }
    }

    private void stampRampView(
            QuoteDto dto,
            Quote quote,
            Map<Long, List<PricedPeriodView>> pricedByLineId) {
        if (dto == null) {
            return;
        }
        dto.setArr(quote.getArr());
        dto.setTcv(quote.getTcv());
        dto.setAcv(quote.getAcv());
        dto.setExitArr(quote.getExitArr());
        if (dto.getLineItems() == null || pricedByLineId.isEmpty()) {
            return;
        }
        for (QuoteLineItemDto lineDto : dto.getLineItems()) {
            if (lineDto.getId() == null) {
                continue;
            }
            List<PricedPeriodView> periods = pricedByLineId.get(lineDto.getId());
            if (periods != null && !periods.isEmpty()) {
                lineDto.setPricedPeriods(periods);
            }
        }
    }
}
