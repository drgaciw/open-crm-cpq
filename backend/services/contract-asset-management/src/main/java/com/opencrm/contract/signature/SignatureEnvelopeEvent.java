package com.opencrm.contract.signature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import java.time.Instant;

/**
 * DRG-457 / BC-06-04. Idempotency ledger row: one per {@code (envelopeId, providerEventId)} a webhook delivery or
 * poll result has claimed, written in the same transaction as the status transition and outbox publish it guards.
 * The database-level unique constraint — not application logic alone — is what makes a redelivered webhook or a
 * repeated poll a safe no-op instead of a double-processed event; see {@code SignatureCoordinationService}.
 */
@Entity
@Table(name = "signature_envelope_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_signature_envelope_event", columnNames = {"envelope_id", "provider_event_id"}),
        indexes = @Index(name = "idx_signature_envelope_event_envelope", columnList = "envelope_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureEnvelopeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_id", nullable = false)
    private Long envelopeId;

    /** The provider's event/delivery id for a webhook, or a synthetic id derived from the poll result for a poll. */
    @Column(name = "provider_event_id", nullable = false, length = 200)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();
}
