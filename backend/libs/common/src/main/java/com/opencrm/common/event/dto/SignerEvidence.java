package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * One signer's evidence of having completed their part of a signature envelope (DRG-457 / BC-06-04).
 *
 * <p>Deliberately provider-agnostic: {@code signerRef} is our own identifier for the signer (an account contact id or
 * an email, whichever the caller supplied on SendForSignature), never a provider-specific signer id. The ACL adapter
 * ({@code com.opencrm.contract.signature.acl}) is responsible for translating whatever evidence shape a real e-sign
 * provider returns into this type before it ever reaches the domain model or this published event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SignerEvidence(
        String signerRef,
        String name,
        String email,
        Instant signedAt,
        String ipAddress,
        String authenticationMethod) {
}
