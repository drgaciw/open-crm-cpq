package com.opencrm.contract.signature.acl;

/**
 * What SendForSignature hands back: an opaque reference the adapter itself understands, and nothing else. The
 * caller stores {@code providerEnvelopeRef} and passes it back unchanged on VoidEnvelope / GetEnvelopeStatus; it
 * never inspects or parses it.
 */
public record EnvelopeHandle(String providerEnvelopeRef) {
}
