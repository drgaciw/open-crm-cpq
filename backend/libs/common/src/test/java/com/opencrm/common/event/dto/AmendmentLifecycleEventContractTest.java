package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.AmendmentLifecycleEventCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-S6-4. One contract round-trip per lifecycle DTO. Unknown fields are ignored. No payload is published.
 */
@DisplayName("Amendment lifecycle event contracts (AC-S6-4)")
public class AmendmentLifecycleEventContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-4111-8111-000000000001");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-4222-8222-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-000000000003");
    private static final UUID LIFECYCLE_CHANGE_ID = UUID.fromString("44444444-4444-4444-8444-000000000004");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-18T12:00:00Z");
    private static final String CORRELATION_ID = "corr-s6";

    public static Set<Class<?>> testedPayloads() {
        return Set.of(
                AmendmentDrafted.class,
                AmendmentSubmitted.class,
                AmendmentApproved.class,
                AmendmentExecuted.class,
                AmendmentUnitApplied.class,
                AmendmentApplied.class,
                AmendmentApplyBlocked.class,
                AmendmentWithdrawn.class);
    }

    static Stream<AmendmentLifecycleEventCatalog.Entry> entries() {
        return AmendmentLifecycleEventCatalog.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entries")
    void roundTripsAndIgnoresUnknownFields(AmendmentLifecycleEventCatalog.Entry entry) throws Exception {
        assertThat(testedPayloads()).contains(entry.payloadClass());
        Object sample = sample(entry.payloadClass());
        String json = MAPPER.writeValueAsString(sample);
        assertThat(json).doesNotContain("tenantId");
        assertThat(MAPPER.readValue(json, entry.payloadClass())).isEqualTo(sample);
        String withUnknown = json.substring(0, json.length() - 1) + ",\"futureField\":true}";
        assertThat(MAPPER.readValue(withUnknown, entry.payloadClass())).isEqualTo(sample);
    }

    private static Object sample(Class<?> type) {
        if (type == AmendmentDrafted.class) {
            return new AmendmentDrafted(DOCUMENT_ID, CONTRACT_ID, null, "DRAFT", OCCURRED_AT, CORRELATION_ID, EVENT_ID,
                    LIFECYCLE_CHANGE_ID, "AMD-PROV-1");
        }
        if (type == AmendmentSubmitted.class) {
            return new AmendmentSubmitted(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "IN_REVIEW", OCCURRED_AT, CORRELATION_ID,
                    EVENT_ID);
        }
        if (type == AmendmentApproved.class) {
            return new AmendmentApproved(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "APPROVED", OCCURRED_AT, CORRELATION_ID,
                    EVENT_ID, "approval-ref-1");
        }
        if (type == AmendmentExecuted.class) {
            return new AmendmentExecuted(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "EXECUTED", OCCURRED_AT, CORRELATION_ID,
                    EVENT_ID, 2);
        }
        if (type == AmendmentUnitApplied.class) {
            return new AmendmentUnitApplied(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "PARTIALLY_APPLIED", OCCURRED_AT,
                    CORRELATION_ID, EVENT_ID, 3);
        }
        if (type == AmendmentApplied.class) {
            return new AmendmentApplied(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "APPLIED", OCCURRED_AT, CORRELATION_ID,
                    EVENT_ID);
        }
        if (type == AmendmentApplyBlocked.class) {
            return new AmendmentApplyBlocked(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "APPLY_BLOCKED", OCCURRED_AT,
                    CORRELATION_ID, EVENT_ID, "UNIT_CONFLICT");
        }
        if (type == AmendmentWithdrawn.class) {
            return new AmendmentWithdrawn(DOCUMENT_ID, CONTRACT_ID, "AMD-1", "WITHDRAWN", OCCURRED_AT, CORRELATION_ID,
                    EVENT_ID);
        }
        throw new IllegalArgumentException("no contract sample for " + type.getName());
    }
}
