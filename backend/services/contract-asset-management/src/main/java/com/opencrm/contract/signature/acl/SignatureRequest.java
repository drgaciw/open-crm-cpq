package com.opencrm.contract.signature.acl;

import java.util.List;

/**
 * SendForSignature request, in our own shape only. The adapter translates this into whatever request shape a real
 * e-sign provider's API wants; nothing upstream of the adapter (service, controller, domain model) ever builds a
 * provider-specific request.
 */
public record SignatureRequest(
        String aggregateType,
        String aggregateId,
        String documentTitle,
        List<SignerRequest> signers) {
}
