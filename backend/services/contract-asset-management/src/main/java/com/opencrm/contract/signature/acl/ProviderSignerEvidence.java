package com.opencrm.contract.signature.acl;

import java.time.Instant;

/**
 * One signer's evidence, already translated by the adapter into our shape. This is the ACL boundary's output type
 * for signer evidence — the service maps it 1:1 into the published {@code com.opencrm.common.event.dto.SignerEvidence}
 * when an envelope completes. A real provider adapter implementation is responsible for building this from whatever
 * shape its API returns (certificate XML, a JSON "tabs" structure, etc.); none of that shape is allowed past this
 * record.
 */
public record ProviderSignerEvidence(
        String signerRef,
        String name,
        String email,
        Instant signedAt,
        String ipAddress,
        String authenticationMethod) {
}
