package com.opencrm.contract.signature.acl;

/**
 * DRG-457 / BC-06-04. Anti-Corruption Layer (ACL) boundary between the domain and an e-signature provider.
 *
 * <p>This is the whole contract: three operations (SendForSignature, VoidEnvelope, GetEnvelopeStatus) realised in
 * the story. Every method takes and returns only types declared in this {@code acl} package (plus {@code String}
 * and {@code java.time}/{@code java.util} types) — never a provider SDK class, never a provider's own JSON/XML
 * shape. An implementation is responsible for doing all of that translation on both sides: build the provider's
 * request from a {@link SignatureRequest}, and translate whatever the provider returns back into an
 * {@link EnvelopeHandle} or {@link EnvelopeStatusSnapshot} before returning. If provider specifics leak past this
 * interface, the ACL has failed at its one job.
 *
 * <p><b>Implementations.</b> {@link StubESignatureProviderAdapter} is the only implementation in this change: a
 * deterministic, local, no-network fake used by the coordination service and its tests to prove the envelope
 * lifecycle and idempotent status handling work. A real provider (DocuSign, Adobe Sign, or another) is a
 * documented extension point, not something this story wires up: a future {@code @Component} implementing this
 * same interface, selected by {@code @ConditionalOnProperty} the way {@code SpringAiLlmAdapter} (ai-service) picks
 * between its own LLM backends, holding that provider's SDK dependency, API credentials (via
 * {@code request_credential} / Spring Cloud Config, never a plain property) and HTTP calls entirely inside its own
 * class. Nothing elsewhere in the codebase — domain model, service, controller, published event — needs to change
 * for that implementation to replace the stub, which is the point of the ACL.
 */
public interface ESignatureProviderAdapter {

    /** SendForSignature: create and dispatch a new envelope. Returns the opaque handle to store and reuse. */
    EnvelopeHandle sendForSignature(SignatureRequest request);

    /** VoidEnvelope: cancel an envelope that has not yet completed. {@code reason} is for our own audit trail. */
    void voidEnvelope(String providerEnvelopeRef, String reason);

    /** GetEnvelopeStatus: poll the provider for the envelope's current status and signer evidence. */
    EnvelopeStatusSnapshot getEnvelopeStatus(String providerEnvelopeRef);
}
