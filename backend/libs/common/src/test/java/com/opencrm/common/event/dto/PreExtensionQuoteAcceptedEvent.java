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
 * Frozen pre-extension parser for AC-S6-1a.
 *
 * <p>Copy of {@code QuoteAcceptedEvent} as it stood on main before the optional
 * {@code amendmentDocumentId} and per-line {@code applicationUnitNo} fields. This class must not
 * gain those fields. A strict mapper feeds it an extended payload; {@code ignoreUnknown} is what
 * lets an old consumer read the new event.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = {"quoteId", "acceptedAt", "customerId", "totalAmount", "currencyCode"})
public class PreExtensionQuoteAcceptedEvent {

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

    public PreExtensionQuoteAcceptedEvent(
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

    public static PreExtensionQuoteAcceptedEvent v1(
            QuoteSnapshot quote,
            CustomerSnapshot customer,
            PricingSnapshot pricing,
            ConfigurationSnapshot configuration,
            TermSnapshot terms,
            CorrelationSnapshot correlation
    ) {
        PreExtensionQuoteAcceptedEvent event = new PreExtensionQuoteAcceptedEvent();
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
