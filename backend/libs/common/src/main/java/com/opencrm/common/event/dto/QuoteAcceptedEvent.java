package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.common.event.EventContractRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event published when a quote is accepted.
 * Topic: cpq.quote.status.accepted
 * Publisher: Quote Management
 * Subscribers: Document Generation, Contract/Asset Management, CRM (ADR-018 D8)
 *
 * <p>ADR-018 D8 added {@link DealSnapshot} and the ARR/TCV/ACV totals on {@link PricingSnapshot} so crm-service can
 * move off the Redis {@code quote:accepted} channel. Additive and {@code NON_NULL}: consumers that do not know them
 * ignore them.
 *
 * <p>{@link #lineItems} carries the accepted lines, in the quote's order, so downstream contexts (order decomposition,
 * the subscription rate schedule of ADR-023) read what was sold from the event instead of calling quote-service. It is
 * additive on version 1, and its field names match {@link QuoteAcceptedCrmMessage.LineItemSnapshot}. A payload written
 * before the field existed has no {@code lineItems} at all. The relay has never run, so its first run will publish
 * outbox rows written before this change, and they carry none. Consumers must read {@code null} as "not provided",
 * never as "no lines".
 *
 * <p>S6 adds optional {@code amendmentDocumentId} and per-line {@code applicationUnitNo}. Both are null when absent
 * and omitted from JSON. DRG-631 adds optional {@code tenantId} (Quote's interim Entra {@code tid}, per-deployment —
 * not a real customer tenant; see DRG-632 / ADR-036). Null when absent and omitted from JSON.
 * {@code EVENT_TYPE} and {@code EVENT_VERSION} stay at v1.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = {"quoteId", "acceptedAt", "customerId", "totalAmount", "currencyCode"})
public class QuoteAcceptedEvent {

    public static final String EVENT_TYPE = EventContractRegistry.QUOTE_ACCEPTED_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.QUOTE_ACCEPTED_V1.eventVersion();

    private String eventType;
    private Integer eventVersion;

    private String quoteId;
    private Instant acceptedAt;
    private String customerId;
    private BigDecimal totalAmount;
    private String currencyCode;

    private QuoteSnapshot quote;
    private CustomerSnapshot customer;
    private PricingSnapshot pricing;
    private ConfigurationSnapshot configuration;
    private TermSnapshot terms;
    private CorrelationSnapshot correlation;
    private DealSnapshot deal;
    private List<LineItemSnapshot> lineItems;
    private SubscriptionChangeSnapshot subscriptionChange;
    /** Amendment document this acceptance belongs to, when the quote was produced for one. Null on legacy events. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String amendmentDocumentId;

    /**
     * Quote tenant copied from {@code quotes.tenant_id}. Interim Entra {@code tid} (per-deployment, ADR-016 /
     * DRG-632); not a verified customer tenant. Null when the quote has no tenant (service token / dev) or on
     * payloads written before DRG-631. Omitted from JSON when null.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tenantId;

    public QuoteAcceptedEvent(
            String quoteId,
            Instant acceptedAt,
            String customerId,
            BigDecimal totalAmount,
            String currencyCode
    ) {
        this.quoteId = quoteId;
        this.acceptedAt = acceptedAt;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.currencyCode = currencyCode;
    }

    public static QuoteAcceptedEvent v1(
            QuoteSnapshot quote,
            CustomerSnapshot customer,
            PricingSnapshot pricing,
            ConfigurationSnapshot configuration,
            TermSnapshot terms,
            CorrelationSnapshot correlation
    ) {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent();
        event.setEventType(EVENT_TYPE);
        event.setEventVersion(EVENT_VERSION);
        event.setQuote(quote);
        event.setCustomer(customer);
        event.setPricing(pricing);
        event.setConfiguration(configuration);
        event.setTerms(terms);
        event.setCorrelation(correlation);
        if (quote != null) {
            event.setQuoteId(quote.getQuoteId());
            event.setAcceptedAt(quote.getAcceptedAt());
        }
        if (customer != null) {
            event.setCustomerId(customer.getCustomerId());
        }
        if (pricing != null) {
            event.setTotalAmount(pricing.getTotalAmount());
            event.setCurrencyCode(pricing.getCurrencyCode());
        }
        return event;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteSnapshot {
        private String quoteId;
        private String quoteNumber;
        private Instant acceptedAt;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSnapshot {
        private String customerId;
        private String accountName;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingSnapshot {
        private BigDecimal totalAmount;
        private String currencyCode;
        private BigDecimal arr;
        private BigDecimal tcv;
        private BigDecimal acv;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal exitArr;
    }

    /** The CRM deal the quote belongs to (ADR-018 D8); {@code dealExternalId} is the CRM {@code Deal.uuid}. */
    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealSnapshot {
        private String dealExternalId;
        private String dealType;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigurationSnapshot {
        private String configurationId;
        private String committedVersion;
        private Map<String, String> attributes;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermSnapshot {
        private Integer termMonths;
        private String startDate;
        private String endDate;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrelationSnapshot {
        private String correlationId;
        private String causationId;
        private String sourceService;
    }

    /**
     * One accepted quote line. {@code lineUuid} is the quote line's UUID and is stable across redelivery.
     * {@code unitPrice} is the priced unit price, {@code extendedAmount} is the line total after discount, and
     * {@code netPrice} is that total divided by the quantity. List order is the quote's order; {@code sortOrder} can
     * repeat.
     */
    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemSnapshot {
        private String lineUuid;
        private String productExternalId;
        private String productName;
        private String productType;
        private String lineItemType;
        private Integer quantity;
        private String unitOfMeasure;
        private BigDecimal listPrice;
        private BigDecimal unitPrice;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;
        private BigDecimal netPrice;
        private BigDecimal extendedAmount;
        private String billingFrequency;
        private Integer sortOrder;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<RampPeriodSnapshot> ramp;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String sourceSubscriptionLineUuid;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private HybridMigrationSnapshot hybridMigration;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ProrationSnapshot initialProration;
        /** Application unit on the amendment document. Null when the line is not tied to a unit. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer applicationUnitNo;
    }

    /** Accepted priced facts, never an instruction to reprice authored steps. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RampPeriodSnapshot {
        private Integer sequence;
        private Integer startMonth;
        private Integer months;
        private Integer quantity;
        private BigDecimal netUnitPrice;
        private BigDecimal netMonthly;
        private BigDecimal periodAmount;
        private BigDecimal discountRate;
        private List<String> explanationCodes;
        private BigDecimal grossPeriodAmount;
        private BigDecimal migrationCredit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubscriptionChangeSnapshot {
        private String targetSubscriptionNumber;
        private Long observedRevision;
        private String effectiveDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HybridMigrationSnapshot {
        private String pairId;
        private String role;
        private Integer startMonth;
        private Integer months;
        private BigDecimal bookValue;
    }

    /** Initial actual-day interval preceding the next full anchored billing month. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProrationSnapshot {
        private String startDate;
        private String endDate;
        private String billingPeriodStart;
        private String billingPeriodEnd;
        private Integer quantity;
        private BigDecimal netUnitPrice;
        private BigDecimal netMonthly;
        private BigDecimal periodAmount;
    }
}
