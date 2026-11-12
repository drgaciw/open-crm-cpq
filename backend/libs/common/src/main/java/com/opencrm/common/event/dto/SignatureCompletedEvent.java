package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * DRG-457 / BC-06-04. An e-signature envelope reached COMPLETED: every required signer has signed.
 *
 * <p>Carries the envelope id and each signer's evidence ({@link SignerEvidence}), never a provider envelope id,
 * provider signer id, or any other provider-specific shape (the anti-corruption layer in
 * {@code com.opencrm.contract.signature.acl} translates those before this event is built). {@code aggregateType}
 * and {@code aggregateId} name the contract-lifecycle object (for example a contract document) the envelope was
 * sent for, so a consumer can join back to its own record without knowing anything about the signing provider.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SignatureCompletedEvent(
        String envelopeId,
        String aggregateType,
        String aggregateId,
        List<SignerEvidence> signers,
        Instant completedAt) {
}
