package com.opencrm.contract.signature.acl;

/** One required signer on a SendForSignature request. {@code signerRef} is our own identifier (e.g. a contact id). */
public record SignerRequest(String signerRef, String name, String email) {
}
