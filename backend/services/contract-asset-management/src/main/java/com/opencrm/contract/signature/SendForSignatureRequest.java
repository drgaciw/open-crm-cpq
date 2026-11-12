package com.opencrm.contract.signature;

import java.util.List;

/** SendForSignature API request body. Never the ACL's {@code acl.SignatureRequest} directly (ADR-006 spirit). */
public record SendForSignatureRequest(
        String aggregateType,
        String aggregateId,
        String documentTitle,
        List<SignerRequestDto> signers) {
}
