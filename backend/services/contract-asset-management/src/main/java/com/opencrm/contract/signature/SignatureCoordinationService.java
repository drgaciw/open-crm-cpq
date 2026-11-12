package com.opencrm.contract.signature;

import com.opencrm.common.event.dto.SignatureCompletedEvent;
import com.opencrm.common.event.dto.SignerEvidence;
import com.opencrm.contract.signature.acl.ESignatureProviderAdapter;
import com.opencrm.contract.signature.acl.EnvelopeHandle;
import com.opencrm.contract.signature.acl.EnvelopeStatusSnapshot;
import com.opencrm.contract.signature.acl.ProviderSignerEvidence;
import com.opencrm.contract.signature.acl.SignatureRequest;
import com.opencrm.contract.signature.acl.SignerRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * DRG-457 / BC-06-04. Application service coordinating SendForSignature / VoidEnvelope / GetEnvelopeStatus behind
 * the {@link ESignatureProviderAdapter} ACL. Owns the envelope state machine ({@link SignatureEnvelopeStatus}) and
 * the idempotency guard described in {@link SignatureEnvelopeEvent}: {@link #handleProviderWebhook} and
 * {@link #getEnvelopeStatus} both funnel through {@link #applyStatusReport}, which claims the
 * {@code (envelopeId, providerEventId)} row before touching the envelope or publishing anything, so a redelivered
 * webhook or a repeated poll of an unchanged state is a safe no-op rather than a double transition or a double
 * {@code SignatureCompleted} publish.
 */
@Service
public class SignatureCoordinationService {

    private final SignatureEnvelopeRepository envelopeRepository;
    private final SignatureEnvelopeEventRepository eventRepository;
    private final ESignatureProviderAdapter providerAdapter;
    private final SignatureEventPublisher eventPublisher;

    public SignatureCoordinationService(SignatureEnvelopeRepository envelopeRepository,
            SignatureEnvelopeEventRepository eventRepository, ESignatureProviderAdapter providerAdapter,
            SignatureEventPublisher eventPublisher) {
        this.envelopeRepository = envelopeRepository;
        this.eventRepository = eventRepository;
        this.providerAdapter = providerAdapter;
        this.eventPublisher = eventPublisher;
    }

    /** SendForSignature. Creates the envelope row, then dispatches through the ACL adapter. */
    @Transactional
    public SignatureEnvelope sendForSignature(String aggregateType, String aggregateId, String documentTitle,
            List<SignerRequest> signers) {
        SignatureEnvelope envelope = envelopeRepository.save(SignatureEnvelope.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .status(SignatureEnvelopeStatus.DRAFT)
                .build());

        EnvelopeHandle handle = providerAdapter.sendForSignature(
                new SignatureRequest(aggregateType, aggregateId, documentTitle, signers));

        envelope.setProviderEnvelopeRef(handle.providerEnvelopeRef());
        envelope.setStatus(SignatureEnvelopeStatus.SENT);
        envelope.setSentAt(Instant.now());
        return envelopeRepository.save(envelope);
    }

    /** VoidEnvelope. Refuses a terminal envelope (ADR-457: a provider specific error code never crosses the ACL). */
    @Transactional
    public SignatureEnvelope voidEnvelope(String envelopeUuid, String reason) {
        SignatureEnvelope envelope = requireEnvelope(envelopeUuid);
        if (envelope.getStatus().isTerminal()) {
            throw new InvalidSignatureTransitionException(envelope.getStatus(), SignatureEnvelopeStatus.VOIDED);
        }
        providerAdapter.voidEnvelope(envelope.getProviderEnvelopeRef(), reason);
        envelope.setStatus(SignatureEnvelopeStatus.VOIDED);
        envelope.setVoidReason(reason);
        return envelopeRepository.save(envelope);
    }

    /**
     * GetEnvelopeStatus, poll path. A terminal envelope is not re-polled — it is already final, so this returns it
     * unchanged rather than calling the provider again.
     */
    @Transactional
    public SignatureEnvelope getEnvelopeStatus(String envelopeUuid) {
        SignatureEnvelope envelope = requireEnvelope(envelopeUuid);
        if (envelope.getStatus().isTerminal()) {
            return envelope;
        }
        EnvelopeStatusSnapshot snapshot = providerAdapter.getEnvelopeStatus(envelope.getProviderEnvelopeRef());
        return applyStatusReport(envelope, snapshot, "poll");
    }

    /** GetEnvelopeStatus, webhook path. {@code snapshot} is already ACL-translated by the calling adapter. */
    @Transactional
    public SignatureEnvelope handleProviderWebhook(String envelopeUuid, EnvelopeStatusSnapshot snapshot) {
        SignatureEnvelope envelope = requireEnvelope(envelopeUuid);
        return applyStatusReport(envelope, snapshot, "webhook");
    }

    private SignatureEnvelope applyStatusReport(SignatureEnvelope envelope, EnvelopeStatusSnapshot snapshot,
            String eventType) {
        if (!claimEvent(envelope.getId(), snapshot.providerEventId(), eventType)) {
            // Already processed this exact provider event (redelivered webhook, or an unchanged poll result
            // whose providerEventId the adapter derives deterministically from the reported state): no-op.
            return envelope;
        }
        if (envelope.getStatus().isTerminal() || envelope.getStatus() == snapshot.status()) {
            // Either a late-arriving report after the envelope already reached a terminal state through another
            // channel (e.g. webhook completed it while a poll was in flight), or a status re-affirmation with no
            // actual change (e.g. polling finds the envelope still SENT). The ledger row above still records
            // that we saw it, but there is nothing to transition or publish either way.
            return envelope;
        }
        if (!envelope.getStatus().validTransitionTo(snapshot.status())) {
            throw new InvalidSignatureTransitionException(envelope.getStatus(), snapshot.status());
        }

        envelope.setStatus(snapshot.status());
        if (snapshot.status() == SignatureEnvelopeStatus.COMPLETED) {
            envelope.setCompletedAt(Instant.now());
            SignatureEnvelope saved = envelopeRepository.save(envelope);
            eventPublisher.publishCompleted(saved, toCompletedEvent(saved, snapshot));
            return saved;
        }
        return envelopeRepository.save(envelope);
    }

    /**
     * Claims {@code (envelopeId, providerEventId)} by inserting the ledger row and forcing the unique constraint
     * to be checked now, in this transaction. Returns {@code false} without side effects when another delivery of
     * the same provider event already holds the claim — the real idempotency guarantee is the database
     * constraint, not this check.
     */
    private boolean claimEvent(Long envelopeId, String providerEventId, String eventType) {
        try {
            eventRepository.saveAndFlush(SignatureEnvelopeEvent.builder()
                    .envelopeId(envelopeId)
                    .providerEventId(providerEventId)
                    .eventType(eventType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    /** The ACL-to-published-language translation: {@link ProviderSignerEvidence} never reaches the outbox as-is. */
    private SignatureCompletedEvent toCompletedEvent(SignatureEnvelope envelope, EnvelopeStatusSnapshot snapshot) {
        List<SignerEvidence> signers = snapshot.signers().stream()
                .map(this::toPublishedSignerEvidence)
                .toList();
        return new SignatureCompletedEvent(envelope.getUuid(), envelope.getAggregateType(), envelope.getAggregateId(),
                signers, envelope.getCompletedAt());
    }

    private SignerEvidence toPublishedSignerEvidence(ProviderSignerEvidence provider) {
        return new SignerEvidence(provider.signerRef(), provider.name(), provider.email(), provider.signedAt(),
                provider.ipAddress(), provider.authenticationMethod());
    }

    private SignatureEnvelope requireEnvelope(String envelopeUuid) {
        return envelopeRepository.findByUuid(envelopeUuid)
                .orElseThrow(() -> new SignatureEnvelopeNotFoundException(envelopeUuid));
    }
}
