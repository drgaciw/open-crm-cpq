package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.ramp.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Service-owned target validation and reachable continuation authoring; no mutation of the subscription here. */
@Service @RequiredArgsConstructor
public class QuoteContinuationService {
    private final SubscriptionScheduleClient schedules;
    private final QuoteRepository quotes;
    private final QuoteLineItemRepository lines;
    private final QuoteNumberGenerator numbers;
    private final QuoteMapper mapper;
    private final RampPricingProperties properties;
    private final RampScheduleAttacher attacher;
    private final ObjectMapper json;

    @Transactional(readOnly=true)
    public QuoteContinuationPolicy.Plan preview(QuoteContinuationRequest request) {
        requireEnabled();
        return QuoteContinuationPolicy.plan(request,schedules.byNumber(request.getTargetSubscriptionNumber()));
    }

    @Transactional
    public QuoteDto author(QuoteContinuationRequest request) {
        requireEnabled();
        var target=schedules.byNumber(request.getTargetSubscriptionNumber());
        var plan=QuoteContinuationPolicy.plan(request,target);
        var source=QuoteContinuationPolicy.source(request,target);
        var effective=request.getEffectiveDate() == null ? plan.startDate() : request.getEffectiveDate();
        Quote quote=Quote.builder().name(request.getName() == null ? request.getOperation()+" "+target.getSubscriptionNumber() : request.getName())
                .quoteNumber(numbers.generate()).customerExternalId(target.getCustomerId()).currency(target.getCurrencyCode())
                .dealType(dealType(request.getOperation())).targetSubscriptionNumber(target.getSubscriptionNumber())
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
        if (!Objects.equals(quote.getTargetSubscriptionNumber(),request.getTargetSubscriptionNumber())
                || !Objects.equals(quote.getCustomerExternalId(),request.getCustomerExternalId())
                || !Objects.equals(quote.getCurrency(),request.getCurrency()) || !Objects.equals(quote.getObservedRevision(),request.getObservedRevision())
                || quote.getDealType() != dealType(request.getOperation())) throw invalid("Continuation commercial identities have changed; author a fresh continuation");
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
        return plan;
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
