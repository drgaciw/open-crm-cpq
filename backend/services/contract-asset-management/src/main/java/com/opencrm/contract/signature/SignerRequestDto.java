package com.opencrm.contract.signature;

/** One required signer, as supplied through the REST API. */
public record SignerRequestDto(String signerRef, String name, String email) {
}
