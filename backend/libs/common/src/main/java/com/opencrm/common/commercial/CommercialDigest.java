package com.opencrm.common.commercial;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** CAM-COMMERCIAL-1: JCS canonical bytes and SHA-256. This is not the S2 event-chain format. */
public final class CommercialDigest {

    public static final String FORMAT_ID = "CAM-COMMERCIAL-1";
    public static final int SCHEMA_VERSION = 1;

    private CommercialDigest() {
    }

    public static CanonicalBytes digest(CommercialEnvelope envelope) {
        Set<UUID> lineRefs = new HashSet<>();
        for (CommercialLine line : envelope.lines()) {
            if (!lineRefs.add(line.changeLineRef())) {
                throw new CommercialDigestException("duplicate changeLineRef");
            }
        }
        Set<Integer> units = new HashSet<>();
        for (CommercialUnit unit : envelope.units()) {
            if (!units.add(unit.unitNo())) {
                throw new CommercialDigestException("duplicate unit number");
            }
        }
        String json = JcsCanonicalizer.canonicalize(envelope.toValue());
        byte[] utf8 = json.getBytes(StandardCharsets.UTF_8);
        return new CanonicalBytes(FORMAT_ID, SCHEMA_VERSION, json, utf8, sha256(utf8));
    }

    public static void rejectDuplicateKeys(byte[] utf8) {
        JsonFactory factory = new JsonFactory();
        try (JsonParser parser = factory.createParser(utf8)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT || parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                    // Walk only. Domain decimals are strings; duplicate keys are the rejection here.
                }
            }
        } catch (CommercialDigestException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CommercialDigestException("invalid JSON: " + failure.getMessage());
        }
    }

    public static String sha256(byte[] utf8) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(utf8);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
