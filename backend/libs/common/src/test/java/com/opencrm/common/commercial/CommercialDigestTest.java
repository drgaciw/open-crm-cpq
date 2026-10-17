package com.opencrm.common.commercial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAM-COMMERCIAL-1. The golden string is the closed projection: explicit nulls, decimal strings,
 * RFC 8785 key order, SHA-256 of the UTF-8 bytes. S2 event hashing is a different format.
 */
class CommercialDigestTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DOCUMENT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String GOLDEN = "{\"actionDate\":\"2026-06-01\",\"arrDelta\":null,"
            + "\"backdatingReasonCode\":null,\"coTermDate\":null,\"contractEffectiveDate\":\"2026-06-01\","
            + "\"creditTotal\":null,\"currency\":\"USD\",\"customerAcceptanceDate\":null,"
            + "\"documentId\":\"22222222-2222-4222-8222-222222222222\",\"formatId\":\"CAM-COMMERCIAL-1\","
            + "\"lines\":[],\"observedScheduleRevision\":null,\"oneTimeTotal\":null,\"revisionNo\":1,"
            + "\"schemaVersion\":1,\"serviceActivationDate\":null,\"sourceInputDigest\":null,"
            + "\"sourceInputRef\":\"caller-snap\",\"tcvDelta\":null,"
            + "\"tenantId\":\"11111111-1111-4111-8111-111111111111\",\"units\":[]}";

    @Test
    void emptyUnpricedEnvelopeMatchesTheGoldenCanonicalFormAndHash() {
        CanonicalBytes digest = CommercialDigest.digest(CommercialEnvelope.unpriced(
                TENANT, DOCUMENT, 1, "USD",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), "caller-snap"));

        assertThat(digest.formatId()).isEqualTo("CAM-COMMERCIAL-1");
        assertThat(digest.schemaVersion()).isEqualTo(1);
        assertThat(digest.canonicalJson()).isEqualTo(GOLDEN);
        assertThat(digest.utf8()).isEqualTo(GOLDEN.getBytes(StandardCharsets.UTF_8));
        assertThat(digest.sha256Hex()).matches("^[0-9a-f]{64}$");
        assertThat(digest.sha256Hex()).isEqualTo(CommercialDigest.sha256(GOLDEN.getBytes(StandardCharsets.UTF_8)));
        assertThat(digest.canonicalJson()).doesNotContain(
                "approvalRef", "signatureEnvelopeRef", "contentDigest", "status", "version");
    }

    @Test
    void moneyAndQuantitiesAreNormalizedDecimalStringsAndLinesSortByChangeLineRef() {
        UUID later = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID earlier = UUID.fromString("00000000-0000-4000-8000-000000000001");
        CommercialEnvelope envelope = CommercialEnvelope.unpriced(
                        TENANT, DOCUMENT, 1, "USD",
                        LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), "caller-snap")
                .withTotals(new BigDecimal("-0.0"), new BigDecimal("10.50"), new BigDecimal("0.10"), BigDecimal.ZERO)
                .withLine(line(later))
                .withLine(line(earlier))
                .withUnit(2)
                .withUnit(1);

        String json = CommercialDigest.digest(envelope).canonicalJson();

        assertThat(json).contains("\"tcvDelta\":\"0\"");
        assertThat(json).contains("\"arrDelta\":\"10.5\"");
        assertThat(json).contains("\"oneTimeTotal\":\"0.1\"");
        assertThat(json).contains("\"creditTotal\":\"0\"");
        assertThat(json).contains("\"deltaQty\":\"0.5\"");
        assertThat(json.indexOf(earlier.toString())).isLessThan(json.indexOf(later.toString()));
        assertThat(json.indexOf("\"unitNo\":1")).isLessThan(json.indexOf("\"unitNo\":2"));
    }

    @Test
    void duplicateLineOrUnitIdentityIsRejected() {
        UUID ref = UUID.fromString("00000000-0000-4000-8000-000000000001");
        CommercialEnvelope duplicatedLine = CommercialEnvelope.unpriced(
                        TENANT, DOCUMENT, 1, "USD",
                        LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), "caller-snap")
                .withLine(line(ref))
                .withLine(line(ref));
        assertThatThrownBy(() -> CommercialDigest.digest(duplicatedLine))
                .isInstanceOf(CommercialDigestException.class);

        CommercialEnvelope duplicatedUnit = CommercialEnvelope.unpriced(
                        TENANT, DOCUMENT, 1, "USD",
                        LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), "caller-snap")
                .withUnit(1)
                .withUnit(1);
        assertThatThrownBy(() -> CommercialDigest.digest(duplicatedUnit))
                .isInstanceOf(CommercialDigestException.class);
    }

    @Test
    void duplicateJsonKeysUnpairedSurrogatesAndNonfiniteNumbersAreRejected() {
        byte[] duplicates = "{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> CommercialDigest.rejectDuplicateKeys(duplicates))
                .isInstanceOf(CommercialDigestException.class);

        assertThatThrownBy(() -> CommercialEnvelope.unpriced(
                TENANT, DOCUMENT, 1, "USD",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), "bad-\uD800"))
                .isInstanceOf(CommercialDigestException.class);

        assertThatThrownBy(() -> DecimalStrings.normalize("NaN")).isInstanceOf(CommercialDigestException.class);
        assertThatThrownBy(() -> DecimalStrings.normalize("Infinity")).isInstanceOf(CommercialDigestException.class);
    }

    private static CommercialLine line(UUID changeLineRef) {
        return CommercialLine.structural(
                changeLineRef, 1, "ADD", "UPSELL", "SUBSCRIPTION",
                new BigDecimal("1.00"), new BigDecimal("0.50"), new BigDecimal("1.50"),
                1, 0L);
    }
}
