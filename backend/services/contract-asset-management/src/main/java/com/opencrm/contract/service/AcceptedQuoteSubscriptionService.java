package com.opencrm.contract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.asset.AssetActivation;
import com.opencrm.contract.domain.AppliedQuoteAcceptance;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Applies the immutable sale in the listener transaction without an external ERP call. */
@Service
@RequiredArgsConstructor
@Transactional
public class AcceptedQuoteSubscriptionService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRatePeriodRepository rates;
    private final AppliedQuoteAcceptanceRepository ledger;
    private final AcceptedRampValidator validator;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    /** Absent when tests construct this service directly, so those tests keep the previous subscription behaviour. */
    private AssetActivation assetActivation;

    /**
     * Additive collaborator. Spring sets it on the bean. The generated constructor is unchanged.
     */
    @Autowired(required = false)
    public void setAssetActivation(AssetActivation assetActivation) {
        this.assetActivation = assetActivation;
    }

    public void apply(QuoteAcceptedEvent event) {
        AcceptedRampValidator.require(event.getCustomerId() != null && !event.getCustomerId().isBlank(), "Accepted customer is missing");
        AcceptedRampValidator.require(event.getEventType() == null || QuoteAcceptedEvent.EVENT_TYPE.equals(event.getEventType()), "Unsupported accepted event type");
        AcceptedRampValidator.require(event.getEventVersion() == null || event.getEventVersion() == QuoteAcceptedEvent.EVENT_VERSION, "Unsupported accepted event version");
        AcceptedRampValidator.require(event.getLineItems() == null || event.getLineItems().stream().noneMatch(java.util.Objects::isNull), "Null accepted line");
        AcceptedRampValidator.require(event.getQuote() == null || event.getQuote().getQuoteId() == null || event.getQuoteId().equals(event.getQuote().getQuoteId()), "Accepted quote identities disagree");
        AcceptedRampValidator.require(event.getCustomer() == null || event.getCustomer().getCustomerId() == null || event.getCustomerId().equals(event.getCustomer().getCustomerId()), "Accepted customer identities disagree");
        String operation = event.getDeal() == null || event.getDeal().getDealType() == null ? "NEW"
                : event.getDeal().getDealType().trim().toUpperCase(Locale.ROOT);
        AcceptedRampValidator.require(List.of("NEW", "AMENDMENT", "EXPANSION", "RENEWAL").contains(operation), "Unsupported accepted deal type");
        String digest = digest(event);
        // Quote identity serializes headerless/new-header redeliveries as well as concurrent first deliveries.
        long lockId = ByteBuffer.wrap(HexFormat.of().parseHex(digestOf(event.getQuoteId()))).getLong();
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, lockId);
        AppliedQuoteAcceptance prior = ledger.findByAcceptedQuoteId(event.getQuoteId()).orElse(null);
        if (prior != null) {
            AcceptedRampValidator.require(digest.equals(prior.getCommercialDigest()), "Accepted quote identity was reused with changed commercial facts");
            return;
        }
        if (!"NEW".equals(operation)) {
            AcceptedRampValidator.require(event.getSubscriptionChange() != null
                    && event.getSubscriptionChange().getTargetSubscriptionNumber() != null
                    && !event.getSubscriptionChange().getTargetSubscriptionNumber().isBlank()
                    && event.getSubscriptionChange().getObservedRevision() != null,
                    "Accepted subscription change requires target number and observed revision");
            mutate(event, digest, operation);
            return;
        }
        AcceptedRampValidator.require(event.getSubscriptionChange() == null, "NEW sale cannot target an existing subscription");
        String number = "SUB-" + event.getQuoteId();
        if (subscriptions.existsBySubscriptionNumber(number)) {
            // Captured historical sales predate the natural replay ledger. Never create or reapply them.
            return;
        }
        boolean typedRates = validator.hasTypedRates(event);
        if (!typedRates && event.getLineItems() != null) {
            for (var line : event.getLineItems()) {
                String productType = line.getProductType();
                String kind = line.getLineItemType() == null ? "" : line.getLineItemType().trim().toUpperCase(Locale.ROOT);
                boolean nonrecurring = List.of("FEE", "TAX", "DISCOUNT").contains(kind);
                AcceptedRampValidator.require(productType == null || productType.isBlank()
                        || nonrecurring || AcceptedRampValidator.fixedProduct(productType)
                        || List.of("SAAS_FLAT_RATE", "ONPREM_SUBSCRIPTION", "SUPPORT_BASIC", "SUPPORT_PREMIUM", "SUPPORT_ENTERPRISE")
                        .contains(productType.trim().toUpperCase(Locale.ROOT)),
                        "Historical aggregate fallback cannot accept an explicit unsupported product classification");
                AcceptedRampValidator.require(nonrecurring || !"ONE_TIME".equalsIgnoreCase(line.getBillingFrequency() == null
                        ? "" : line.getBillingFrequency().trim()), "ONE_TIME accepted line cannot become a subscription");
            }
        }
        AcceptedRampValidator.Validated priced = typedRates ? validator.validate(event, null, 0) : null;
        LocalDate start = priced == null ? LocalDate.now() : priced.startDate();
        LocalDate end = priced == null ? start.plusYears(1) : priced.endDate();
        if (priced == null && event.getTerms() != null && (event.getTerms().getStartDate() != null || event.getTerms().getEndDate() != null)) {
            start = AcceptedRampValidator.date(event.getTerms().getStartDate(), "terms.startDate");
            end = AcceptedRampValidator.date(event.getTerms().getEndDate(), "terms.endDate");
            Integer months = event.getTerms().getTermMonths();
            AcceptedRampValidator.require(!end.isBefore(start) && (months == null
                    || months > 0 && start.plusMonths(months).minusDays(1).equals(end)), "Invalid explicit accepted legacy term");
        }
        BigDecimal arr = priced == null ? legacyArr(event) : priced.firstMrr().multiply(BigDecimal.valueOf(12));
        Subscription subscription = Subscription.builder().subscriptionNumber(number).customerId(event.getCustomerId())
                .quoteId(event.getQuoteId()).status(SubscriptionStatus.PENDING)
                .planName(priced == null ? legacyPlan(event) : priced.planName())
                .startDate(start).endDate(end).renewalDate(end.minusDays(30)).arr(arr)
                .mrr(arr == null ? null : AcceptedRampValidator.money(arr.divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP)))
                .currencyCode(event.getCurrencyCode()).erpSyncStatus(ErpSyncStatus.QUEUED).build();
        subscriptions.save(subscription);
        List<SubscriptionRatePeriod> periods = priced == null ? List.of(SubscriptionRatePeriod.builder()
                .acceptedLineUuid("legacy-aggregate").acceptedQuoteId(event.getQuoteId()).productName(subscription.getPlanName())
                .sequence(1).startDate(start).billingAnchorDate(start).endDate(end).effectiveEndDate(end).netMonthly(subscription.getMrr())
                .annualRunRate(arr).currencyCode(event.getCurrencyCode()).provenance("LEGACY_AGGREGATE").build()) : priced.periods();
        periods.forEach(period -> period.setSubscriptionId(subscription.getId()));
        rates.saveAll(periods);
        ledger.save(AppliedQuoteAcceptance.builder().acceptedQuoteId(event.getQuoteId()).subscriptionId(subscription.getId())
                .operation(operation).commercialDigest(digest).build());
        recordAssets(event, subscription, periods);
    }

    private void mutate(QuoteAcceptedEvent event, String digest, String operation) {
        var change = event.getSubscriptionChange();
        Subscription target = subscriptions.findByNumberForUpdate(change.getTargetSubscriptionNumber())
                .orElseThrow(() -> new NonRetryableEventException("Target subscription is missing or deleted"));
        AcceptedRampValidator.require(event.getCustomerId().equals(target.getCustomerId()), "Target subscription belongs to a different customer");
        AcceptedRampValidator.require(event.getCurrencyCode() != null && event.getCurrencyCode().equals(target.getCurrencyCode()), "Target currency mismatch");
        AcceptedRampValidator.require(change.getObservedRevision().equals(target.getCommercialRevision()), "Target commercial revision is stale");
        boolean renewal = "RENEWAL".equals(operation);
        boolean replacement = "AMENDMENT".equals(operation);
        AcceptedRampValidator.require(target.getStatus() == SubscriptionStatus.ACTIVE
                        || (!renewal && target.getStatus() == SubscriptionStatus.SUSPENDED), "Target status does not permit this subscription change");
        AcceptedRampValidator.require(validator.hasTypedRates(event), "Typed mutation requires complete accepted priced rates");
        AcceptedRampValidator.require(event.getTerms() != null, "Typed mutation requires accepted terms");
        LocalDate start = AcceptedRampValidator.date(event.getTerms().getStartDate(), "terms.startDate");
        LocalDate effective = change.getEffectiveDate() == null ? start : AcceptedRampValidator.date(change.getEffectiveDate(), "subscriptionChange.effectiveDate");
        LocalDate end = AcceptedRampValidator.date(event.getTerms().getEndDate(), "terms.endDate");
        if (renewal) {
            AcceptedRampValidator.require(start.equals(target.getEndDate().plusDays(1)) && effective.equals(start),
                    "Renewal must start immediately after the existing inclusive end");
        } else {
            AcceptedRampValidator.require(!effective.isBefore(target.getStartDate()) && !effective.isAfter(target.getEndDate()),
                    "Mutation effective date is outside target coverage");
            AcceptedRampValidator.require(end.equals(target.getEndDate()), "Mutation must co-term to the existing target");
        }
        List<SubscriptionRatePeriod> existing = rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(target.getId());
        AcceptedRampValidator.require(!existing.isEmpty(), "Target subscription lacks rate lineage");
        if (replacement) {
            var affected = event.getLineItems().stream().filter(AcceptedRampValidator::recurring).toList();
            AcceptedRampValidator.require(affected.stream().allMatch(line -> line.getSourceSubscriptionLineUuid() != null)
                            && affected.stream().map(QuoteAcceptedEvent.LineItemSnapshot::getSourceSubscriptionLineUuid).distinct().count() == affected.size(),
                    "Amendment requires unique explicit source-line replacement identities");
        }
        List<SubscriptionRatePeriod> sources = event.getLineItems().stream().filter(AcceptedRampValidator::recurring)
                .filter(line -> line.getSourceSubscriptionLineUuid() != null)
                .flatMap(line -> existing.stream().filter(period -> line.getSourceSubscriptionLineUuid().equals(period.getAcceptedLineUuid()) && !period.isRetired()))
                .toList();
        if (event.getLineItems().stream().anyMatch(line -> line.getSourceSubscriptionLineUuid() != null)) {
            List<SubscriptionRatePeriod> resolvedSources = sources;
            for (var line : event.getLineItems()) if (line.getSourceSubscriptionLineUuid() != null) {
                AcceptedRampValidator.require(resolvedSources.stream().anyMatch(period -> line.getSourceSubscriptionLineUuid().equals(period.getAcceptedLineUuid())),
                        "Source subscription line is missing or retired");
            }
        } else {
            LocalDate sourceDate = renewal ? target.getEndDate() : effective;
            sources = existing.stream().filter(period -> RampScheduleService.covers(period, sourceDate)).toList();
        }
        AcceptedRampValidator.require(!sources.isEmpty(), "Target subscription has no effective source rate");
        AcceptedRampValidator.require(sources.stream().allMatch(period -> period.getQuantity() != null
                && period.getNetUnitPrice() != null && period.getNetMonthly() != null), "Source subscription lacks known accepted unit and quantity rates");
        {
            LocalDate sourceDate = renewal ? target.getEndDate() : effective;
            for (var line : event.getLineItems()) if (AcceptedRampValidator.recurring(line) && line.getSourceSubscriptionLineUuid() != null) {
                AcceptedRampValidator.require(sources.stream().anyMatch(period -> line.getSourceSubscriptionLineUuid().equals(period.getAcceptedLineUuid())
                        && RampScheduleService.covers(period, sourceDate)), "Selected source has no effective coverage");
            }
        }
        List<LocalDate> anchors = sources.stream().map(period -> period.getBillingAnchorDate() == null
                ? target.getStartDate() : period.getBillingAnchorDate()).distinct().toList();
        AcceptedRampValidator.require(anchors.size() == 1, "Expansion source billing anchor is ambiguous");
        LocalDate anchor = renewal ? start : anchors.getFirst();
        boolean partialOnly = event.getLineItems().stream().filter(AcceptedRampValidator::recurring)
                .allMatch(line -> line.getInitialProration() != null && (line.getRamp() == null || line.getRamp().isEmpty()));
        int offset = renewal || partialOnly ? 0 : anchoredOffset(anchor, start);
        var priced = validator.validateMutation(event, anchor, offset, effective, target.getEndDate());
        for (var period : priced.periods()) {
            AcceptedRampValidator.require(existing.stream().noneMatch(old -> old.getAcceptedLineUuid().equals(period.getAcceptedLineUuid())),
                    "New accepted line reuses existing subscription line identity");
            period.setSubscriptionId(target.getId());
        }
        if (replacement) {
            var affectedIds = event.getLineItems().stream().filter(AcceptedRampValidator::recurring)
                    .map(QuoteAcceptedEvent.LineItemSnapshot::getSourceSubscriptionLineUuid).collect(java.util.stream.Collectors.toSet());
            List<SubscriptionRatePeriod> changed = new java.util.ArrayList<>();
            for (var old : existing) if (!old.isRetired() && affectedIds.contains(old.getAcceptedLineUuid()) && !old.getEffectiveEndDate().isBefore(effective)) {
                old.setEffectiveEndDate(effective.minusDays(1));
                if (!old.getStartDate().isBefore(effective)) old.setRetired(true);
                changed.add(old);
            }
            rates.saveAll(changed);
        }
        rates.saveAll(priced.periods());
        if (renewal) {
            target.setEndDate(end);
            target.setRenewalDate(end.minusDays(30));
        }
        target.setCommercialRevision(Math.addExact(target.getCommercialRevision(), 1L));
        target.setErpSyncStatus(ErpSyncStatus.QUEUED);
        target.setErpSyncClaimToken(null);
        target.setErpSyncClaimRevision(null);
        subscriptions.save(target);
        ledger.save(AppliedQuoteAcceptance.builder().acceptedQuoteId(event.getQuoteId()).subscriptionId(target.getId())
                .operation(operation).commercialDigest(digest).build());
        recordAssets(event, target, priced.periods());
    }

    private void recordAssets(QuoteAcceptedEvent event, Subscription subscription, List<SubscriptionRatePeriod> periods) {
        if (assetActivation != null) {
            assetActivation.onActivated(event, subscription, periods);
        }
    }

    private int anchoredOffset(LocalDate anchor, LocalDate start) {
        long months = java.time.temporal.ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), start.withDayOfMonth(1));
        AcceptedRampValidator.require(months >= 0 && months <= Integer.MAX_VALUE && anchor.plusMonths(months).equals(start),
                "Effective date is not a verified source billing boundary");
        return Math.toIntExact(months);
    }

    private BigDecimal legacyArr(QuoteAcceptedEvent event) {
        return event.getPricing() != null && event.getPricing().getArr() != null ? event.getPricing().getArr() : event.getTotalAmount();
    }
    private String legacyPlan(QuoteAcceptedEvent event) {
        if (event.getLineItems() == null || event.getLineItems().isEmpty()) return "Standard Plan";
        String result = String.join(", ", event.getLineItems().stream().filter(java.util.Objects::nonNull)
                .map(line -> {
                    String label = line.getProductName();
                    if (label == null || label.isBlank()) label = line.getProductExternalId();
                    AcceptedRampValidator.require(label != null && !label.isBlank(), "Accepted product name or identifier is missing");
                    return label;
                }).distinct().toList());
        return result;
    }
    private String digest(QuoteAcceptedEvent event) {
        ObjectNode tree = objectMapper.valueToTree(event);
        tree.remove(List.of("acceptedAt", "correlation", "eventType", "eventVersion"));
        if (tree.get("quote") instanceof ObjectNode quote) quote.remove("acceptedAt");
        return digestOf(canonical(tree));
    }
    private static String canonical(JsonNode value) {
        if (value.isObject()) {
            TreeMap<String, String> fields = new TreeMap<>();
            value.properties().forEach(entry -> fields.put(entry.getKey(), canonical(entry.getValue())));
            StringBuilder result = new StringBuilder("{");
            fields.forEach((key, field) -> result.append(new com.fasterxml.jackson.databind.node.TextNode(key))
                    .append(':').append(field).append(','));
            return result.append('}').toString();
        }
        if (value.isArray()) {
            StringBuilder result = new StringBuilder("[");
            value.forEach(item -> result.append(canonical(item)).append(','));
            return result.append(']').toString();
        }
        return value.isNumber() ? value.decimalValue().stripTrailingZeros().toPlainString() : value.toString();
    }
    private static String digestOf(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
