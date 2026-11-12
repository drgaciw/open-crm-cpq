package com.opencrm.contract.signature.acl;

import com.opencrm.contract.signature.SignatureEnvelopeStatus;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DRG-457 / BC-06-04. Local, no-network, deterministic fake of an e-sign provider. This is the only
 * {@link ESignatureProviderAdapter} implementation in this change — see that interface's Javadoc for how a real
 * provider adapter would replace it without touching anything else. It proves the envelope lifecycle and the ACL
 * boundary shape without calling out to DocuSign, Adobe Sign, or any other live service; no provider credentials
 * or SDK are involved anywhere in this class.
 *
 * <p>Rate-limited the same way {@code ErpAdapter} (this service's other ACL) is, so this ACL follows the one
 * established shape in the codebase for guarding an outbound integration call.
 *
 * <p>{@link #simulateWebhookDelivery} is not part of the {@link ESignatureProviderAdapter} interface: it is this
 * stub's own escape hatch for the one thing a real provider does that a synchronous fake cannot — push a status
 * change to us asynchronously. {@code SignatureEnvelopeWebhookController} calls it from the stub provider's webhook
 * endpoint, and the idempotency test calls it twice with the same {@code deliveryId} to prove double delivery does
 * not double-process.
 */
@Component
@Slf4j
public class StubESignatureProviderAdapter implements ESignatureProviderAdapter {

    private final Map<String, EnvelopeState> envelopes = new ConcurrentHashMap<>();

    @RateLimiter(name = "eSignatureProvider")
    @Override
    public EnvelopeHandle sendForSignature(SignatureRequest request) {
        String ref = "stub-env-" + UUID.randomUUID();
        envelopes.put(ref, new EnvelopeState(SignatureEnvelopeStatus.SENT, List.of()));
        log.info("Stub e-sign provider: envelope {} sent for aggregate {}/{}", ref, request.aggregateType(),
                request.aggregateId());
        return new EnvelopeHandle(ref);
    }

    @RateLimiter(name = "eSignatureProvider")
    @Override
    public void voidEnvelope(String providerEnvelopeRef, String reason) {
        EnvelopeState current = requireKnown(providerEnvelopeRef);
        if (current.status().isTerminal()) {
            throw new SignatureProviderException(
                    "Envelope " + providerEnvelopeRef + " is already " + current.status() + "; cannot void");
        }
        envelopes.put(providerEnvelopeRef, new EnvelopeState(SignatureEnvelopeStatus.VOIDED, current.signers()));
        log.info("Stub e-sign provider: envelope {} voided ({})", providerEnvelopeRef, reason);
    }

    /** GetEnvelopeStatus (poll path). The event id is stable for an unchanged state, so repeated polling dedupes. */
    @RateLimiter(name = "eSignatureProvider")
    @Override
    public EnvelopeStatusSnapshot getEnvelopeStatus(String providerEnvelopeRef) {
        EnvelopeState current = requireKnown(providerEnvelopeRef);
        String providerEventId = "poll:" + providerEnvelopeRef + ":" + current.status();
        return new EnvelopeStatusSnapshot(providerEventId, current.status(), current.signers());
    }

    /**
     * Test/demo-only: simulate the provider pushing a webhook that the envelope reached {@code reportedStatus}.
     * Calling this twice with the same {@code deliveryId} simulates a redelivered webhook — the returned snapshot
     * is identical both times, and {@code SignatureCoordinationService} is what makes the second delivery a no-op.
     */
    public EnvelopeStatusSnapshot simulateWebhookDelivery(String providerEnvelopeRef, String deliveryId,
            SignatureEnvelopeStatus reportedStatus, List<ProviderSignerEvidence> signers) {
        envelopes.put(providerEnvelopeRef, new EnvelopeState(reportedStatus, signers));
        return new EnvelopeStatusSnapshot(deliveryId, reportedStatus, signers);
    }

    private EnvelopeState requireKnown(String providerEnvelopeRef) {
        EnvelopeState state = envelopes.get(providerEnvelopeRef);
        if (state == null) {
            throw new SignatureProviderException("Stub e-sign provider has no envelope " + providerEnvelopeRef);
        }
        return state;
    }

    private record EnvelopeState(SignatureEnvelopeStatus status, List<ProviderSignerEvidence> signers) {
    }
}
