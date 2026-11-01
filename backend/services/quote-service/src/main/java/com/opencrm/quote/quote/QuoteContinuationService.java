package com.opencrm.quote.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.ramp.*;
import com.opencrm.security.CurrentActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Service-owned target validation and reachable continuation authoring; no mutation of the subscription here. */
@Service
public class QuoteContinuationService {
    private final SubscriptionScheduleClient schedules;
    private final QuoteRepository quotes;
    private final QuoteLineItemRepository lines;
    private final QuoteNumberGenerator numbers;
    private final QuoteMapper mapper;
    private final RampPricingProperties properties;
    private final RampScheduleAttacher attacher;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final CurrentActor actor;
    private final RestClient scheduleHttp;
    private AssetRenewalDrafts assetRenewals;

    public QuoteContinuationService(SubscriptionScheduleClient schedules, QuoteRepository quotes,
                                    QuoteLineItemRepository lines, QuoteNumberGenerator numbers, QuoteMapper mapper,
                                    RampPricingProperties properties, RampScheduleAttacher attacher, ObjectMapper json,
                                    JdbcTemplate jdbc, CurrentActor actor,
                                    @Qualifier("subscriptionScheduleRestClient") RestClient scheduleHttp) {
        this.schedules = schedules;
        this.quotes = quotes;
        this.lines = lines;
        this.numbers = numbers;
        this.mapper = mapper;
        this.properties = properties;
        this.attacher = attacher;
        this.json = json;
        this.jdbc = jdbc;
        this.actor = actor;
        this.scheduleHttp = scheduleHttp;
    }

    @Autowired(required = false)
    void setAssetRenewalClients(AssetReadClient assets, RenewalPolicyClient policies) {
        this.assetRenewals = new AssetRenewalDrafts(assets, policies, schedules, quotes, lines, numbers, mapper,
                attacher, json, jdbc, actor);
    }

    /** Append-only commercial decision for a draft this service opened from renewal eligibility. */
    static final String SYSTEM_RENEWAL_DRAFT = "SYSTEM_RENEWAL_DRAFT";
    private static final String RENEWAL_EXIT_RATE = "RENEWAL_EXIT_RATE";
    static final String AUDIT_INSERT = """
            INSERT INTO commercial_decision_audit (
                quote_id, quote_number, decision_kind, decided_by, policy_id, operation, mode,
                subscription_id, event_id, inputs_json, outputs_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    @Transactional(readOnly=true)
    public QuoteContinuationPolicy.Plan preview(QuoteContinuationRequest request) {
        requireEnabled();
        if (fromActiveAssets(request)) return assetRenewals().preview(request);
        return QuoteContinuationPolicy.plan(request,schedules.byNumber(request.getTargetSubscriptionNumber()));
    }

    @Transactional
    public QuoteDto author(QuoteContinuationRequest request) {
        requireEnabled();
        if (fromActiveAssets(request)) return assetRenewals().author(request);
        var target=schedules.byNumber(request.getTargetSubscriptionNumber());
        var plan=QuoteContinuationPolicy.plan(request,target);
        var source=QuoteContinuationPolicy.source(request,target);
        var effective=request.getEffectiveDate() == null ? plan.startDate() : request.getEffectiveDate();
        Quote quote=Quote.builder().name(request.getName() == null ? request.getOperation()+" "+target.getSubscriptionNumber() : request.getName())
                .quoteNumber(numbers.generate()).customerExternalId(target.getCustomerId()).currency(target.getCurrencyCode())
                .dealType(dealType(request.getOperation())).continuationOperation(request.getOperation())
                .targetSubscriptionNumber(target.getSubscriptionNumber())
                .observedRevision(target.getCommercialRevision()).subscriptionEffectiveDate(effective)
                .durationInMonths(plan.termMonths()).startDate(plan.startDate()).endDate(plan.endDate())
                .continuationRequestJson(write(request)).rampScheduleStale(true).build();
        quote=quotes.save(quote);
        BigDecimal initial=plan.initialProration() == null ? plan.periods().getFirst().unitPrice() : plan.initialProration().getNetUnitPrice();
        QuoteLineItem line=QuoteLineItem.builder().quote(quote).sourceSubscriptionLineUuid(source.getAcceptedLineUuid())
                .productExternalId(source.getProductExternalId()).productName(source.getProductName()).lineItemType(source.getLineItemType()).productType(source.getProductType())
                .billingFrequency(source.getBillingFrequency()).quantity(request.getQuantity()).listPrice(initial).unitPrice(initial).netPrice(initial)
                .extendedAmount(initial.multiply(BigDecimal.valueOf(request.getQuantity())))
                .initialProrationJson(plan.initialProration() == null ? null : write(plan.initialProration())).build();
        if (!plan.periods().isEmpty()) {
            var steps=plan.periods().stream().map(p -> RampStepDto.builder().sequence(p.sequence()).startMonth(p.startMonth()).months(p.months())
                    .quantity(p.quantity()).priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(p.unitPrice()).build()).toList();
            var prepared=attacher.prepare(quote,LineItemCreateRequest.builder().productExternalId(source.getProductExternalId())
                    .lineItemType(line.getLineItemType()).billingFrequency(source.getBillingFrequency()).quantity(request.getQuantity()).listPrice(initial)
                    .schedule(RampScheduleDto.builder().steps(steps).build()).build());
            attacher.attach(line,prepared);
        }
        line=lines.save(line);quote.getLineItems().add(line);
        return mapper.toDto(quote);
    }

    /**
     * Opens one draft RENEWAL continuation for an eligibility event. Exit quantity and exit unit rate, zero uplift,
     * twelve months, starting the day after the target's inclusive end. {@code renewalDate} on the event is the
     * notice date, not the quote start. A live renewal quote for that window is returned unchanged.
     *
     * <p>No draft-quote event is published: the registry has no such contract, and this change does not add one.
     * The commercial decision is the {@code commercial_decision_audit} row written in this transaction.
     */
    @Transactional
    public QuoteDto draftRenewal(SubscriptionRenewalEligibleEvent event, String subscriptionNumber, String eventId,
                                 String dedupeKey) {
        if (event == null || event.getSubscriptionId() == null || event.getSubscriptionId().isBlank())
            throw new NonRetryableEventException("Renewal eligibility event lacks subscriptionId");
        SubscriptionScheduleView schedule = scheduleFor(event, subscriptionNumber);
        SubscriptionScheduleView.Line source = renewalSource(schedule);
        int quantity = exitQuantity(source);
        QuoteContinuationRequest request = QuoteContinuationRequest.builder()
                .operation(QuoteContinuationRequest.Operation.RENEWAL)
                .targetSubscriptionNumber(schedule.getSubscriptionNumber())
                .sourceSubscriptionLineUuid(source.getAcceptedLineUuid())
                .observedRevision(schedule.getCommercialRevision())
                .customerExternalId(schedule.getCustomerId())
                .currency(schedule.getCurrencyCode())
                .quantity(quantity)
                .build();
        QuoteContinuationPolicy.Plan plan = QuoteContinuationPolicy.plan(request, schedule);
        Optional<Quote> existing = liveRenewal(schedule.getSubscriptionNumber(), plan.startDate());
        if (existing.isPresent()) return mapper.toDto(existing.get());
        QuoteDto draft = author(request);
        if (draft.getId() == null) throw new IllegalStateException("Renewal draft was not persisted");
        recordDecision(draft, event, schedule, source, plan, eventId, dedupeKey, quantity);
        return draft;
    }

    private SubscriptionScheduleView scheduleFor(SubscriptionRenewalEligibleEvent event, String subscriptionNumber) {
        if (subscriptionNumber != null && !subscriptionNumber.isBlank()) return schedules.byNumber(subscriptionNumber);
        String id = event.getSubscriptionId().trim();
        if (id.chars().allMatch(Character::isDigit)) return readById(id);
        return schedules.byNumber(id);
    }

    /** RenewalScheduler publishes the numeric primary key. The internal schedule route is by number only. */
    private SubscriptionScheduleView readById(String id) {
        try {
            String raw = scheduleHttp.get().uri("/api/v1/subscriptions/{id}/ramp-schedule", id).retrieve().body(String.class);
            ApiResponse<SubscriptionScheduleView> response = json.readValue(raw, new TypeReference<>() {});
            if (response == null || !response.isSuccess() || response.getData() == null)
                throw new NonRetryableEventException("Subscription " + id + " has no ramp schedule");
            return response.getData();
        } catch (NonRetryableEventException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError())
                throw new NonRetryableEventException("Subscription " + id + " schedule was rejected: " + ex.getStatusCode(), ex);
            throw unavailable();
        } catch (Exception ex) {
            throw unavailable();
        }
    }

    private SubscriptionScheduleView.Line renewalSource(SubscriptionScheduleView schedule) {
        List<SubscriptionScheduleView.Line> matches = schedule.getLines() == null ? List.of() : schedule.getLines().stream()
                .filter(Objects::nonNull)
                .filter(line -> "ACCEPTED_PRICED".equals(line.getProvenance()))
                .filter(line -> line.getPeriods() != null && line.getPeriods().stream().anyMatch(period -> period != null && !period.isRetired()))
                .filter(line -> AcceptedRampSnapshots.recurring(QuoteLineItem.builder().lineItemType(line.getLineItemType())
                        .productType(line.getProductType()).billingFrequency(line.getBillingFrequency()).build()))
                .toList();
        if (matches.size() != 1)
            throw new NonRetryableEventException("Renewal draft needs exactly one accepted recurring source line; found " + matches.size());
        return matches.getFirst();
    }

    private int exitQuantity(SubscriptionScheduleView.Line source) {
        List<SubscriptionScheduleView.Period> open = source.getPeriods().stream().filter(period -> period != null && !period.isRetired())
                .sorted(Comparator.comparing(SubscriptionScheduleView.Period::getEndDate, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        Integer quantity = open.isEmpty() ? null : open.getLast().getQuantity();
        if (quantity == null || quantity < 1)
            throw new NonRetryableEventException("Renewal draft source has no positive exit quantity");
        return quantity;
    }

    private Optional<Quote> liveRenewal(String subscriptionNumber, LocalDate start) {
        PageRequest page = PageRequest.of(0, 50, Sort.by("id"));
        while (true) {
            var candidates = quotes.findAll((root, query, cb) -> cb.and(
                cb.isFalse(root.get("deleted")),
                cb.equal(root.get("dealType"), DealType.RENEWAL),
                cb.or(cb.equal(root.get("continuationOperation"), QuoteContinuationRequest.Operation.RENEWAL),
                        cb.isNull(root.get("continuationOperation"))),
                cb.equal(root.get("targetSubscriptionNumber"), subscriptionNumber),
                cb.equal(root.get("startDate"), start),
                root.get("status").in(QuoteStatus.DRAFT, QuoteStatus.IN_REVIEW, QuoteStatus.PENDING_APPROVAL,
                        QuoteStatus.APPROVED, QuoteStatus.SENT, QuoteStatus.ACCEPTED)),
                page);
            var existing = candidates.stream()
                    .filter(quote -> continuationOperation(quote) == QuoteContinuationRequest.Operation.RENEWAL)
                    .findFirst();
            if (existing.isPresent() || !candidates.hasNext()) return existing;
            page = page.next();
        }
    }

    /** Old writers can leave the column null after V1_12; only explicit persisted JSON identifies them. */
    private QuoteContinuationRequest.Operation continuationOperation(Quote quote) {
        if (quote.getContinuationOperation() != null) return quote.getContinuationOperation();
        if (quote.getContinuationRequestJson() == null) return null;
        try {
            return json.readValue(quote.getContinuationRequestJson(), QuoteContinuationRequest.class).getOperation();
        } catch (Exception ex) {
            return null;
        }
    }

    private void recordDecision(QuoteDto draft, SubscriptionRenewalEligibleEvent event, SubscriptionScheduleView schedule,
                                SubscriptionScheduleView.Line source, QuoteContinuationPolicy.Plan plan, String eventId,
                                String dedupeKey, int quantity) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("subscriptionId", event.getSubscriptionId());
        inputs.put("renewalDate", event.getRenewalDate() == null ? null : event.getRenewalDate().toString());
        inputs.put("currentPlan", event.getCurrentPlan());
        inputs.put("usageBand", event.getUsageBand());
        inputs.put("subscriptionNumber", schedule.getSubscriptionNumber());
        inputs.put("observedRevision", schedule.getCommercialRevision());
        inputs.put("sourceSubscriptionLineUuid", source.getAcceptedLineUuid());
        inputs.put("exitQuantity", quantity);
        inputs.put("exitUnitPrice", plan.periods().getFirst().unitPrice().toPlainString());
        inputs.put("customerExternalId", schedule.getCustomerId());
        inputs.put("currency", schedule.getCurrencyCode());
        inputs.put("eventId", eventId);
        inputs.put("dedupeKey", dedupeKey);
        inputs.put("upliftPercent", "0");
        inputs.put("termMonths", plan.termMonths());
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("quoteId", draft.getId());
        outputs.put("quoteNumber", draft.getQuoteNumber());
        outputs.put("dealType", DealType.RENEWAL.name());
        outputs.put("status", QuoteStatus.DRAFT.name());
        outputs.put("startDate", plan.startDate().toString());
        outputs.put("endDate", plan.endDate().toString());
        outputs.put("termMonths", plan.termMonths());
        outputs.put("quantity", quantity);
        outputs.put("unitPrice", plan.periods().getFirst().unitPrice().toPlainString());
        int inserted = jdbc.update(AUDIT_INSERT, draft.getId(), draft.getQuoteNumber(), SYSTEM_RENEWAL_DRAFT, actor.self(),
                "QuoteContinuationPolicy", QuoteContinuationRequest.Operation.RENEWAL.name(), RENEWAL_EXIT_RATE,
                event.getSubscriptionId(), eventId, write(inputs), write(outputs));
        if (inserted != 1) throw new IllegalStateException("Renewal commercial decision was not recorded");
    }

    private BusinessException unavailable() {
        return new BusinessException("Cannot read the target subscription schedule; retry or choose a valid target",
                HttpStatus.BAD_GATEWAY, "SUBSCRIPTION_SCHEDULE_UNAVAILABLE");
    }

    /** Reload target at calculate/accept; retained source identity never authorizes replacement except AMENDMENT. */
    public QuoteContinuationPolicy.Plan validate(Quote quote,List<QuoteLineItem> items) {
        if (quote.getDealType() == DealType.NEW) {
            if (quote.getTargetSubscriptionNumber() != null) throw invalid("A new quote cannot target an existing subscription");
            return null;
        }
        requireEnabled();
        if (quote.getTargetSubscriptionNumber() == null || quote.getContinuationRequestJson() == null)
            throw invalid("Use typed continuation authoring with a target subscription, source line and observed revision");
        QuoteContinuationRequest request;
        try { request=json.readValue(quote.getContinuationRequestJson(),QuoteContinuationRequest.class); }
        catch (Exception ex) { throw invalid("Unreadable continuation authoring context"); }
        if (fromActiveAssets(request)) return assetRenewals().validate(quote, items, request);
        if (!Objects.equals(quote.getTargetSubscriptionNumber(),request.getTargetSubscriptionNumber())
                || !Objects.equals(quote.getCustomerExternalId(),request.getCustomerExternalId())
                || !Objects.equals(quote.getCurrency(),request.getCurrency()) || !Objects.equals(quote.getObservedRevision(),request.getObservedRevision())
                || quote.getDealType() != dealType(request.getOperation())
                || (quote.getContinuationOperation() != null && quote.getContinuationOperation() != request.getOperation()))
            throw invalid("Continuation commercial identities have changed; author a fresh continuation");
        var target=schedules.byNumber(request.getTargetSubscriptionNumber());
        var plan=QuoteContinuationPolicy.plan(request,target);
        var source=QuoteContinuationPolicy.source(request,target);
        var effective=request.getEffectiveDate() == null ? plan.startDate() : request.getEffectiveDate();
        if (!Objects.equals(quote.getSubscriptionEffectiveDate(),effective) || !Objects.equals(quote.getStartDate(),plan.startDate())
                || !Objects.equals(quote.getEndDate(),plan.endDate()) || quote.getDurationInMonths() != plan.termMonths())
            throw invalid("Continuation dates or term have changed; author a fresh continuation");
        List<QuoteLineItem> recurring=items.stream().filter(l -> !l.isDeleted()).filter(AcceptedRampSnapshots::recurring).toList();
        if (recurring.size() != 1 || items.stream().filter(l -> !l.isDeleted()).count() != 1)
            throw invalid("This continuation must retain only its one validated recurring source line; additional lines require a separate quote");
        QuoteLineItem line=recurring.getFirst();
        if (!Objects.equals(line.getSourceSubscriptionLineUuid(),source.getAcceptedLineUuid())
                || !Objects.equals(line.getProductExternalId(),source.getProductExternalId()) || line.getQuantity() != request.getQuantity()
                || !Objects.equals(source.getBillingFrequency(),line.getBillingFrequency()) || !Objects.equals(source.getLineItemType(),line.getLineItemType())
                || !Objects.equals(source.getProductType(),line.getProductType()) || line.getHybridMigrationJson() != null)
            throw invalid("Continuation source, quantity or product has changed; author a fresh continuation");
        var authored=RampScheduleViews.activeSteps(line);
        if (authored.size() != plan.periods().size()) throw invalid("Continuation schedule has changed; author a fresh continuation");
        for (int i=0;i<authored.size();i++) {
            var actual=authored.get(i);var expected=plan.periods().get(i);
            if (actual.getSequence() != expected.sequence() || actual.getStartMonth() != expected.startMonth()
                    || actual.getMonths() != expected.months() || actual.getQuantity() != expected.quantity()
                    || !"ABSOLUTE".equals(actual.getPriceBasis()) || actual.getUnitPrice() == null
                    || actual.getUnitPrice().compareTo(expected.unitPrice()) != 0)
                throw invalid("Continuation rates must match the current accepted source schedule");
        }
        if ((plan.initialProration() == null) != (line.getInitialProrationJson() == null))
            throw invalid("Continuation initial interval has changed; author a fresh continuation");
        if (plan.initialProration() != null) {
            com.opencrm.common.event.dto.QuoteAcceptedEvent.ProrationSnapshot actual;
            try { actual=json.readValue(line.getInitialProrationJson(),com.opencrm.common.event.dto.QuoteAcceptedEvent.ProrationSnapshot.class); }
            catch(Exception ex) {throw invalid("Unreadable continuation initial interval");}
            var expected=plan.initialProration();
            if (!Objects.equals(actual.getStartDate(),expected.getStartDate()) || !Objects.equals(actual.getEndDate(),expected.getEndDate())
                    || !Objects.equals(actual.getBillingPeriodStart(),expected.getBillingPeriodStart()) || !Objects.equals(actual.getBillingPeriodEnd(),expected.getBillingPeriodEnd())
                    || !Objects.equals(actual.getQuantity(),expected.getQuantity()) || !sameAmount(actual.getNetUnitPrice(),expected.getNetUnitPrice())
                    || !sameAmount(actual.getNetMonthly(),expected.getNetMonthly()) || !sameAmount(actual.getPeriodAmount(),expected.getPeriodAmount()))
                throw invalid("Continuation initial interval differs from the validated target rates");
        }
        // Repair only after every identity, revision, date and price check has passed.
        if (quote.getContinuationOperation() == null) quote.setContinuationOperation(request.getOperation());
        return plan;
    }
    private boolean fromActiveAssets(QuoteContinuationRequest request) {
        return request != null && request.getRenewalSourceMode() == QuoteContinuationRequest.RenewalSourceMode.FROM_ACTIVE_ASSETS;
    }
    private AssetRenewalDrafts assetRenewals() {
        if (assetRenewals == null) throw new IllegalStateException("Asset renewal reads are not configured");
        return assetRenewals;
    }
    private boolean sameAmount(BigDecimal left,BigDecimal right) {return left != null && right != null && left.compareTo(right)==0;}
    private void requireEnabled() { if (!properties.isEnabled()) throw RampScheduleException.notSupported(); }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw invalid("Cannot persist continuation context"); }
    }
    private DealType dealType(QuoteContinuationRequest.Operation operation) {
        if (operation == null) throw invalid("A typed continuation operation is required");
        return switch(operation) { case AMENDMENT -> DealType.AMENDMENT; case EXPANSION -> DealType.EXPANSION; case RENEWAL,EXTENSION -> DealType.RENEWAL; };
    }
    private BusinessException invalid(String message) { return new BusinessException(message,"SUBSCRIPTION_CONTINUATION_INVALID"); }
}
