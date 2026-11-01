package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.AssetReadClient;
import com.opencrm.quote.client.InstalledAssetView;
import com.opencrm.quote.client.RenewalPolicyClient;
import com.opencrm.quote.client.RenewalPolicyView;
import com.opencrm.quote.client.SubscriptionScheduleClient;
import com.opencrm.quote.client.SubscriptionScheduleView;
import com.opencrm.quote.lineitem.LineItemCreateRequest;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.RampPriceBasis;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleDto;
import com.opencrm.quote.ramp.RampStepDto;
import com.opencrm.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Renewal lines taken from the account's active assets as of the target subscription's term end.
 * Prices each line with {@link RenewalPolicyClient} rungs applied by {@link QuoteContinuationPolicy}.
 */
final class AssetRenewalDrafts {

    static final String SOURCE_MODE = "FROM_ACTIVE_ASSETS";
    static final String DECISION_KIND = "RENEWAL_FROM_ACTIVE_ASSETS";

    private final AssetReadClient assets;
    private final RenewalPolicyClient policies;
    private final SubscriptionScheduleClient schedules;
    private final QuoteRepository quotes;
    private final QuoteLineItemRepository lines;
    private final QuoteNumberGenerator numbers;
    private final QuoteMapper mapper;
    private final RampScheduleAttacher attacher;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final CurrentActor actor;

    AssetRenewalDrafts(AssetReadClient assets, RenewalPolicyClient policies, SubscriptionScheduleClient schedules,
                       QuoteRepository quotes, QuoteLineItemRepository lines, QuoteNumberGenerator numbers,
                       QuoteMapper mapper, RampScheduleAttacher attacher, ObjectMapper json, JdbcTemplate jdbc,
                       CurrentActor actor) {
        this.assets = assets;
        this.policies = policies;
        this.schedules = schedules;
        this.quotes = quotes;
        this.lines = lines;
        this.numbers = numbers;
        this.mapper = mapper;
        this.attacher = attacher;
        this.json = json;
        this.jdbc = jdbc;
        this.actor = actor;
    }

    QuoteContinuationPolicy.Plan preview(QuoteContinuationRequest request) {
        return price(request).plan();
    }

    QuoteDto author(QuoteContinuationRequest request) {
        Priced priced = price(request);
        SubscriptionScheduleView schedule = priced.schedule();
        QuoteContinuationPolicy.Plan plan = priced.plan();
        QuoteContinuationRequest stored = storedRequest(request, schedule, priced);
        Quote quote = Quote.builder()
                .name(request.getName() == null || request.getName().isBlank()
                        ? "RENEWAL " + schedule.getSubscriptionNumber() : request.getName())
                .quoteNumber(numbers.generate())
                .customerExternalId(schedule.getCustomerId())
                .currency(schedule.getCurrencyCode())
                .status(QuoteStatus.DRAFT)
                .dealType(DealType.RENEWAL)
                .continuationOperation(QuoteContinuationRequest.Operation.RENEWAL)
                .targetSubscriptionNumber(schedule.getSubscriptionNumber())
                .observedRevision(schedule.getCommercialRevision())
                .subscriptionEffectiveDate(plan.startDate())
                .durationInMonths(plan.termMonths())
                .startDate(plan.startDate())
                .endDate(plan.endDate())
                .continuationRequestJson(write(stored))
                .rampScheduleStale(true)
                .build();
        quote = quotes.save(quote);
        int sort = 0;
        for (PricedLine pricedLine : priced.lines()) {
            quote.getLineItems().add(saveLine(quote, pricedLine, sort));
            sort++;
        }
        QuoteDto draft = mapper.toDto(quote);
        if (draft.getId() == null) {
            throw new IllegalStateException("Renewal draft was not persisted");
        }
        recordDecision(draft, priced, stored);
        return draft;
    }

    QuoteContinuationPolicy.Plan validate(Quote quote, List<QuoteLineItem> items, QuoteContinuationRequest stored) {
        if (stored.getRenewalSourceMode() != QuoteContinuationRequest.RenewalSourceMode.FROM_ACTIVE_ASSETS) {
            throw invalid("Continuation source mode has changed; author a fresh continuation");
        }
        Priced priced = price(stored);
        QuoteContinuationPolicy.Plan plan = priced.plan();
        if (!Objects.equals(quote.getTargetSubscriptionNumber(), priced.schedule().getSubscriptionNumber())
                || !Objects.equals(quote.getCustomerExternalId(), priced.schedule().getCustomerId())
                || !Objects.equals(quote.getCurrency(), priced.schedule().getCurrencyCode())
                || !Objects.equals(quote.getObservedRevision(), priced.schedule().getCommercialRevision())
                || quote.getDealType() != DealType.RENEWAL
                || (quote.getContinuationOperation() != null
                    && quote.getContinuationOperation() != QuoteContinuationRequest.Operation.RENEWAL)
                || !Objects.equals(quote.getSubscriptionEffectiveDate(), plan.startDate())
                || !Objects.equals(quote.getStartDate(), plan.startDate())
                || !Objects.equals(quote.getEndDate(), plan.endDate())
                || quote.getDurationInMonths() != plan.termMonths()) {
            throw invalid("Continuation commercial identities have changed; author a fresh continuation");
        }
        if (!priced.assetIds().equals(stored.getRenewalAssetIds())) {
            throw invalid("Active assets changed; author a fresh continuation");
        }
        List<QuoteLineItem> live = items == null ? List.of() : items.stream().filter(line -> !line.isDeleted()).toList();
        List<QuoteLineItem> recurring = live.stream().filter(AcceptedRampSnapshots::recurring).toList();
        if (recurring.size() != priced.lines().size() || live.size() != priced.lines().size()) {
            throw invalid("This continuation must retain the active-asset lines it was authored with");
        }
        for (int i = 0; i < recurring.size(); i++) {
            assertLine(recurring.get(i), priced.lines().get(i));
        }
        if (quote.getContinuationOperation() == null) {
            quote.setContinuationOperation(QuoteContinuationRequest.Operation.RENEWAL);
        }
        return plan;
    }

    private Priced price(QuoteContinuationRequest request) {
        if (request == null || request.getOperation() != QuoteContinuationRequest.Operation.RENEWAL
                || request.getRenewalSourceMode() != QuoteContinuationRequest.RenewalSourceMode.FROM_ACTIVE_ASSETS) {
            throw invalid("From active assets is a renewal source mode");
        }
        if (request.getUpliftPercent() != null) {
            throw invalid("Renewal from active assets prices through the renewal policy");
        }
        if (request.getTargetSubscriptionNumber() == null || request.getTargetSubscriptionNumber().isBlank()) {
            throw invalid("A target subscription is required");
        }
        SubscriptionScheduleView schedule = schedules.byNumber(request.getTargetSubscriptionNumber());
        if (schedule == null || schedule.getEndDate() == null || schedule.getCustomerId() == null
                || schedule.getCustomerId().isBlank()) {
            throw invalid("Target subscription has no term end to read assets against");
        }
        LocalDate asOf = schedule.getEndDate();
        UUID policyAccount = request.getRenewalPolicyAccountId() != null
                ? request.getRenewalPolicyAccountId() : uuidOrNull(schedule.getCustomerId());
        UUID policyAgreement = request.getRenewalPolicyAgreementId();
        if (policyAccount == null && policyAgreement == null) {
            throw invalid("Renewal policy needs an account or agreement id");
        }
        List<InstalledAssetView> selected = select(assets.installedBase(schedule.getCustomerId(), asOf), schedule, request);
        Map<String, List<RenewalPolicyView>> rungsByLine = new HashMap<>();
        List<PricedLine> pricedLines = new ArrayList<>();
        List<QuoteContinuationPolicy.Period> periods = new ArrayList<>();
        BigDecimal tcv = BigDecimal.ZERO;
        for (InstalledAssetView asset : selected) {
            UUID lineKey = uuidOrNull(asset.contractLineId());
            String cacheKey = String.valueOf(policyAccount) + "|" + policyAgreement + "|" + lineKey;
            List<RenewalPolicyView> rungs = rungsByLine.computeIfAbsent(cacheKey,
                    ignored -> policies.fetchRungs(policyAccount, policyAgreement, lineKey, asOf));
            RenewalPolicyView policy = selectPolicy(rungs);
            if (policy == null) {
                throw unresolved("Renewal policy did not resolve a mode");
            }
            int quantity = wholeQuantity(asset);
            QuoteContinuationRequest lineRequest = QuoteContinuationRequest.builder()
                    .operation(QuoteContinuationRequest.Operation.RENEWAL)
                    .targetSubscriptionNumber(schedule.getSubscriptionNumber())
                    .sourceSubscriptionLineUuid(asset.contractLineId())
                    .observedRevision(schedule.getCommercialRevision())
                    .customerExternalId(schedule.getCustomerId())
                    .currency(schedule.getCurrencyCode())
                    .quantity(quantity)
                    .termMonths(request.getTermMonths())
                    .effectiveDate(request.getEffectiveDate())
                    .upliftPercent(upliftOf(policy))
                    .build();
            QuoteContinuationPolicy.Plan linePlan = QuoteContinuationPolicy.plan(lineRequest, schedule);
            SubscriptionScheduleView.Line source = QuoteContinuationPolicy.source(lineRequest, schedule);
            pricedLines.add(new PricedLine(asset, source, quantity, linePlan, policy));
            periods.add(linePlan.periods().getFirst());
            tcv = tcv.add(linePlan.tcv());
        }
        QuoteContinuationPolicy.Plan first = pricedLines.getFirst().plan();
        QuoteContinuationPolicy.Plan plan = new QuoteContinuationPolicy.Plan(
                first.startDate(), first.endDate(), first.termMonths(), List.copyOf(periods), null, tcv);
        return new Priced(schedule, asOf, policyAccount, List.copyOf(pricedLines), plan);
    }

    private List<InstalledAssetView> select(List<InstalledAssetView> installed, SubscriptionScheduleView schedule,
                                            QuoteContinuationRequest request) {
        java.util.Set<String> contractLines = new java.util.HashSet<>();
        if (schedule.getLines() != null) {
            for (SubscriptionScheduleView.Line line : schedule.getLines()) {
                if (line != null && line.getAcceptedLineUuid() != null && !line.getAcceptedLineUuid().isBlank()) {
                    contractLines.add(line.getAcceptedLineUuid());
                }
            }
        }
        String onlyLine = request.getContractLineId() == null || request.getContractLineId().isBlank()
                ? null : request.getContractLineId();
        List<InstalledAssetView> selected = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (InstalledAssetView asset : installed == null ? List.<InstalledAssetView>of() : installed) {
            if (asset == null || !installedNow(asset.status())) {
                continue;
            }
            if (asset.accountId() != null && !asset.accountId().equals(schedule.getCustomerId())) {
                continue;
            }
            if (asset.contractLineId() == null || !contractLines.contains(asset.contractLineId())) {
                continue;
            }
            if (onlyLine != null && !onlyLine.equals(asset.contractLineId())) {
                continue;
            }
            if (asset.assetId() == null || asset.assetId().isBlank() || !seen.add(asset.assetId())) {
                throw invalid("Active asset is missing a unique id");
            }
            selected.add(asset);
        }
        if (selected.isEmpty()) {
            throw invalid("No active assets to renew as of the term end");
        }
        selected.sort(Comparator.comparing(InstalledAssetView::assetId));
        return selected;
    }

    private static boolean installedNow(String status) {
        return "ACTIVE".equals(status) || "AMENDED".equals(status);
    }

    private static RenewalPolicyView selectPolicy(List<RenewalPolicyView> rungs) {
        RenewalPolicyView best = null;
        int bestRank = Integer.MAX_VALUE;
        if (rungs == null) {
            return null;
        }
        for (RenewalPolicyView rung : rungs) {
            if (rung == null || rung.scopeKind() == null) {
                continue;
            }
            int rank = switch (rung.scopeKind()) {
                case "LINE" -> 0;
                case "AGREEMENT" -> 1;
                case "ACCOUNT" -> 2;
                default -> 9;
            };
            if (rank > 2) {
                continue;
            }
            if (best == null || rank < bestRank || (rank == bestRank && rung.policyVersion() > best.policyVersion())) {
                best = rung;
                bestRank = rank;
            }
        }
        return best;
    }

    private static BigDecimal upliftOf(RenewalPolicyView policy) {
        if (policy.mode() == null) {
            throw unresolved("Renewal policy mode is missing");
        }
        return switch (policy.mode()) {
            case "SAME" -> BigDecimal.ZERO;
            case "UPLIFT_PCT" -> {
                if (policy.upliftPercentPoints() == null) {
                    throw unresolved("Renewal policy uplift percent is missing");
                }
                yield policy.upliftPercentPoints();
            }
            default -> throw new BusinessException(
                    "Mode " + policy.mode() + " is outside this slice",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "MODE_OUT_OF_SLICE");
        };
    }

    private static int wholeQuantity(InstalledAssetView asset) {
        BigDecimal quantity = asset.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw invalid("Asset " + asset.assetId() + " has no positive quantity");
        }
        try {
            return quantity.intValueExact();
        } catch (ArithmeticException ex) {
            throw invalid("Asset " + asset.assetId() + " quantity is not a whole number");
        }
    }

    private QuoteLineItem saveLine(Quote quote, PricedLine pricedLine, int sort) {
        QuoteContinuationPolicy.Period period = pricedLine.plan().periods().getFirst();
        BigDecimal unit = period.unitPrice();
        SubscriptionScheduleView.Line source = pricedLine.source();
        QuoteLineItem line = QuoteLineItem.builder()
                .quote(quote)
                .sourceSubscriptionLineUuid(source.getAcceptedLineUuid())
                .productExternalId(source.getProductExternalId())
                .productName(source.getProductName())
                .lineItemType(source.getLineItemType())
                .productType(source.getProductType())
                .billingFrequency(source.getBillingFrequency())
                .quantity(pricedLine.quantity())
                .listPrice(unit)
                .unitPrice(unit)
                .netPrice(unit)
                .extendedAmount(unit.multiply(BigDecimal.valueOf(pricedLine.quantity())))
                .sortOrder(sort)
                .metadata(write(Map.of("assetId", pricedLine.asset().assetId(), "renewalSourceMode", SOURCE_MODE)))
                .build();
        var prepared = attacher.prepare(quote, LineItemCreateRequest.builder()
                .productExternalId(source.getProductExternalId())
                .lineItemType(line.getLineItemType())
                .billingFrequency(source.getBillingFrequency())
                .quantity(pricedLine.quantity())
                .listPrice(unit)
                .schedule(RampScheduleDto.builder().steps(List.of(RampStepDto.builder()
                        .sequence(period.sequence())
                        .startMonth(period.startMonth())
                        .months(period.months())
                        .quantity(period.quantity())
                        .priceBasis(RampPriceBasis.ABSOLUTE)
                        .unitPrice(unit)
                        .build())).build())
                .build());
        attacher.attach(line, prepared);
        return lines.save(line);
    }

    private QuoteContinuationRequest storedRequest(QuoteContinuationRequest request, SubscriptionScheduleView schedule,
                                                   Priced priced) {
        return QuoteContinuationRequest.builder()
                .operation(QuoteContinuationRequest.Operation.RENEWAL)
                .renewalSourceMode(QuoteContinuationRequest.RenewalSourceMode.FROM_ACTIVE_ASSETS)
                .targetSubscriptionNumber(schedule.getSubscriptionNumber())
                .observedRevision(schedule.getCommercialRevision())
                .customerExternalId(schedule.getCustomerId())
                .currency(schedule.getCurrencyCode())
                .effectiveDate(request.getEffectiveDate())
                .termMonths(request.getTermMonths())
                .name(request.getName())
                .contractLineId(request.getContractLineId())
                .renewalPolicyAccountId(priced.policyAccountId())
                .renewalPolicyAgreementId(request.getRenewalPolicyAgreementId())
                .renewalAssetIds(priced.assetIds())
                .build();
    }

    private void assertLine(QuoteLineItem line, PricedLine expected) {
        QuoteContinuationPolicy.Period period = expected.plan().periods().getFirst();
        SubscriptionScheduleView.Line source = expected.source();
        if (!Objects.equals(line.getSourceSubscriptionLineUuid(), source.getAcceptedLineUuid())
                || !Objects.equals(line.getProductExternalId(), source.getProductExternalId())
                || line.getQuantity() != expected.quantity()
                || !Objects.equals(source.getBillingFrequency(), line.getBillingFrequency())
                || !Objects.equals(source.getLineItemType(), line.getLineItemType())
                || !Objects.equals(source.getProductType(), line.getProductType())
                || line.getUnitPrice() == null
                || line.getUnitPrice().compareTo(period.unitPrice()) != 0
                || !expected.asset().assetId().equals(assetIdOf(line))) {
            throw invalid("Continuation source, quantity or product has changed; author a fresh continuation");
        }
        var authored = com.opencrm.quote.ramp.RampScheduleViews.activeSteps(line);
        if (authored.size() != 1) {
            throw invalid("Continuation schedule has changed; author a fresh continuation");
        }
        var actual = authored.getFirst();
        if (actual.getSequence() != period.sequence() || actual.getStartMonth() != period.startMonth()
                || actual.getMonths() != period.months() || actual.getQuantity() != period.quantity()
                || !"ABSOLUTE".equals(actual.getPriceBasis()) || actual.getUnitPrice() == null
                || actual.getUnitPrice().compareTo(period.unitPrice()) != 0) {
            throw invalid("Continuation rates must match the renewal policy");
        }
    }

    private String assetIdOf(QuoteLineItem line) {
        if (line.getMetadata() == null) {
            return null;
        }
        try {
            JsonNode node = json.readTree(line.getMetadata());
            return node.path("assetId").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private void recordDecision(QuoteDto draft, Priced priced, QuoteContinuationRequest stored) {
        List<Map<String, Object>> lineFacts = new ArrayList<>();
        List<String> policyIds = new ArrayList<>();
        for (PricedLine line : priced.lines()) {
            String policyId = line.policy().id() == null ? null : line.policy().id().toString();
            if (policyId != null && !policyIds.contains(policyId)) {
                policyIds.add(policyId);
            }
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("assetId", line.asset().assetId());
            fact.put("contractLineId", line.asset().contractLineId());
            fact.put("quantity", line.quantity());
            fact.put("policyId", policyId);
            fact.put("mode", line.policy().mode());
            fact.put("unitPrice", line.plan().periods().getFirst().unitPrice().toPlainString());
            lineFacts.add(fact);
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("sourceMode", SOURCE_MODE);
        inputs.put("assetIds", priced.assetIds());
        inputs.put("asOf", priced.asOf().toString());
        inputs.put("accountId", priced.schedule().getCustomerId());
        inputs.put("subscriptionNumber", priced.schedule().getSubscriptionNumber());
        inputs.put("policyAccountId", priced.policyAccountId() == null ? null : priced.policyAccountId().toString());
        inputs.put("lines", lineFacts);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("quoteId", draft.getId());
        outputs.put("quoteNumber", draft.getQuoteNumber());
        outputs.put("sourceMode", SOURCE_MODE);
        outputs.put("assetIds", priced.assetIds());
        outputs.put("dealType", DealType.RENEWAL.name());
        outputs.put("status", QuoteStatus.DRAFT.name());
        outputs.put("startDate", priced.plan().startDate().toString());
        outputs.put("endDate", priced.plan().endDate().toString());
        String policyId = policyIds.size() == 1 ? policyIds.getFirst() : "MULTIPLE";
        int inserted = jdbc.update(QuoteContinuationService.AUDIT_INSERT, draft.getId(), draft.getQuoteNumber(),
                DECISION_KIND, actor.self(), policyId, QuoteContinuationRequest.Operation.RENEWAL.name(), SOURCE_MODE,
                priced.schedule().getSubscriptionNumber(), null, write(inputs), write(outputs));
        if (inserted != 1) {
            throw new IllegalStateException("Renewal commercial decision was not recorded");
        }
        if (stored.getRenewalAssetIds() == null) {
            throw new IllegalStateException("Renewal asset ids were not recorded");
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw invalid("Cannot persist continuation context");
        }
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT, "SUBSCRIPTION_CONTINUATION_INVALID");
    }

    private static BusinessException unresolved(String message) {
        return new BusinessException(message, HttpStatus.UNPROCESSABLE_CONTENT, "UNRESOLVED_RENEWAL_MODE");
    }

    private record PricedLine(InstalledAssetView asset, SubscriptionScheduleView.Line source, int quantity,
                              QuoteContinuationPolicy.Plan plan, RenewalPolicyView policy) {
    }

    private record Priced(SubscriptionScheduleView schedule, LocalDate asOf, UUID policyAccountId,
                          List<PricedLine> lines, QuoteContinuationPolicy.Plan plan) {
        List<String> assetIds() {
            return lines.stream().map(line -> line.asset().assetId()).toList();
        }
    }
}
