package com.opencrm.common.commercial;

/** UTF-8 JCS bytes and their lowercase SHA-256 hex digest. */
public record CanonicalBytes(
        String formatId,
        int schemaVersion,
        String canonicalJson,
        byte[] utf8,
        String sha256Hex) {
}
