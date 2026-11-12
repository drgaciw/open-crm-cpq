package com.opencrm.contract.signature;

import com.opencrm.contract.signature.acl.EnvelopeStatusSnapshot;
import com.opencrm.contract.signature.acl.ProviderSignerEvidence;
import com.opencrm.contract.signature.acl.StubESignatureProviderAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GetEnvelopeStatus, webhook path, for the stub provider only. A real provider's webhook endpoint would live
 * alongside its own adapter implementation, with its own payload type, and would not look like this one — this
 * controller exists to prove the idempotent-webhook requirement end to end against the stub, nothing more.
 *
 * <p>{@link StubWebhookPayload} (the stub provider's wire shape) is translated here, at the edge, into an
 * {@link EnvelopeStatusSnapshot} before {@code SignatureCoordinationService} ever sees it — the same ACL boundary
 * the poll path crosses inside {@link com.opencrm.contract.signature.acl.ESignatureProviderAdapter#getEnvelopeStatus}.
 * Re-posting the same {@code deliveryId} for the same envelope is the idempotent-redelivery case this story asks
 * for: the service, not this controller, is what makes the second call a no-op.
 */
@RestController
@RequestMapping("/api/v1/signature-envelopes")
@RequiredArgsConstructor
@Tag(name = "Contract/Asset Management", description = "Stub e-sign provider webhook receiver")
public class SignatureEnvelopeWebhookController {

    private final SignatureCoordinationService coordinationService;
    private final SignatureEnvelopeRepository envelopeRepository;
    private final StubESignatureProviderAdapter stubProviderAdapter;

    @PostMapping("/{envelopeId}/webhooks/stub-provider")
    @Operation(summary = "GetEnvelopeStatus (webhook push from the stub provider)")
    public ResponseEntity<Void> receiveStubProviderWebhook(@PathVariable String envelopeId,
            @RequestBody StubWebhookPayload payload) {
        SignatureEnvelope envelope = envelopeRepository.findByUuid(envelopeId)
                .orElseThrow(() -> new SignatureEnvelopeNotFoundException(envelopeId));

        // The stub adapter doubles as "the provider" here: a real adapter would use its own provider's
        // envelope/signer identifiers to make this same translation from webhook body to ACL snapshot.
        EnvelopeStatusSnapshot snapshot = stubProviderAdapter.simulateWebhookDelivery(
                envelope.getProviderEnvelopeRef(), payload.deliveryId(), payload.status(),
                toProviderEvidence(payload));

        coordinationService.handleProviderWebhook(envelopeId, snapshot);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private List<ProviderSignerEvidence> toProviderEvidence(StubWebhookPayload payload) {
        return payload.signers().stream()
                .map(s -> new ProviderSignerEvidence(s.signerRef(), s.name(), s.email(), s.signedAt(), s.ipAddress(),
                        s.authenticationMethod()))
                .toList();
    }
}
