package com.opencrm.contract.signature;

import java.time.Instant;
import java.util.List;

/**
 * The stub provider's own webhook body shape — intentionally distinct from {@link SignatureEnvelopeDto} and from
 * anything in {@code com.opencrm.contract.signature.acl}. A real provider's webhook controller would have its own
 * payload type here, shaped however that provider sends it; {@code SignatureEnvelopeWebhookController} is what
 * keeps that shape from reaching {@code SignatureCoordinationService} by translating it first.
 */
public record StubWebhookPayload(
        String deliveryId,
        SignatureEnvelopeStatus status,
        List<StubWebhookSigner> signers) {

    public record StubWebhookSigner(String signerRef, String name, String email, Instant signedAt,
            String ipAddress, String authenticationMethod) {
    }
}
