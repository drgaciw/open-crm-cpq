package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload published on Redis channel {@code quote:accepted} when a quote is accepted.
 * Consumers: {@code crm-service} ({@code QuoteAcceptedEventListener}).
 * <p>
 * Distinct from {@link QuoteAcceptedEvent}, which is used for Kafka ({@code QUOTE_STATUS_ACCEPTED})
 * and has a narrower contract for downstream subscribers (document-generation, contract-asset).
 * Do not merge or unify these DTOs without coordinated releases for those consumers.
 * <p>
 * <b>Versioning:</b> Treat additive JSON fields as backward-compatible; renaming/removing fields
 * requires a coordinated bump and publisher/consumer rollout.
 * <p>
 * <b>Field semantics (CRM listener):</b>
 * <ul>
 *   <li>{@code quoteNumber} — Human-readable quote id (required for logging/fallback keys).</li>
 *   <li>{@code quoteUuid} — Stable quote id; preferred for idempotency when present.</li>
 *   <li>{@code customerExternalId} — Customer reference from CPQ (optional CRM-side).</li>
 *   <li>{@code dealExternalId} — <b>Required for CRM:</b> deal UUID string ({@code Deal.uuid}); listener skips update if blank.</li>
 *   <li>{@code dealType} — Optional classification.</li>
 *   <li>{@code totalAmount}, {@code arr}, {@code tcv}, {@code acv} — Commercial totals as published by quote-service.</li>
 *   <li>{@code currency} — ISO currency code.</li>
 *   <li>{@code durationInMonths} — Contract duration.</li>
 *   <li>{@code acceptedAt} — Acceptance timestamp.</li>
 * </ul>
 * <p>
 * <b>Idempotency (Redis):</b> Keys {@code crm:quote-accepted:processed:*} (see {@code QuoteAcceptedIdempotency}).
 * CRM claims with SET NX, retains the key only after a successful deal update, and deletes it when the deal
 * is missing or apply fails (so retries are not poisoned). For ultimate idempotency across Redis loss,
 * consider a DB column on deals (e.g. last winning quote UUID) in a future change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAcceptedCrmMessage {

    @Builder.Default
    private String schemaVersion = "QuoteAccepted.v1";
    private String quoteNumber;
    private String quoteUuid;
    private String customerExternalId;
    private String dealExternalId;
    private String dealType;
    private BigDecimal totalAmount;
    private BigDecimal arr;
    private BigDecimal tcv;
    private BigDecimal acv;
    private String currency;
    private int durationInMonths;
    private Instant acceptedAt;
    private CustomerSnapshot customer;
    private DealSnapshot deal;
    private ConfigurationSnapshot configuration;
    private PricingSnapshot pricing;
    private TermsSnapshot terms;
    private List<LineItemSnapshot> lineItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSnapshot {
        private String customerExternalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DealSnapshot {
        private String dealExternalId;
        private String dealType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigurationSnapshot {
        private String region;
        private String subscriptionType;
        private String metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingSnapshot {
        private BigDecimal subtotal;
        private BigDecimal totalDiscount;
        private BigDecimal totalTax;
        private BigDecimal totalAmount;
        private BigDecimal arr;
        private BigDecimal tcv;
        private BigDecimal acv;
        private String currency;
        private String approvalState;
        private String approvalTier;
        private String pricingWaterfallJson;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermsSnapshot {
        private int durationInMonths;
        private LocalDate startDate;
        private LocalDate endDate;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemSnapshot {
        private String lineUuid;
        private String productExternalId;
        private String productName;
        private String productType;
        private String lineItemType;
        private int quantity;
        private String unitOfMeasure;
        private BigDecimal listPrice;
        private BigDecimal unitPrice;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;
        private BigDecimal netPrice;
        private BigDecimal extendedAmount;
        private String billingFrequency;
        private int sortOrder;
        private String pricingExplanationCode;
        private String pricingWaterfallJson;
        private String metadata;
    }
}
