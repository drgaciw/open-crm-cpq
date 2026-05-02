package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

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
 * Reprocessing after a failed persistence path may require deleting the key in ops runbooks; for ultimate
 * idempotency across Redis loss, consider a DB column on deals (e.g. last winning quote UUID) in a future change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteAcceptedCrmMessage {

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
}
