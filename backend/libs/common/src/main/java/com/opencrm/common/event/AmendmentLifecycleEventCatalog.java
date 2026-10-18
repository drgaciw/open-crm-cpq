package com.opencrm.common.event;

import com.opencrm.common.event.dto.AmendmentApplied;
import com.opencrm.common.event.dto.AmendmentApplyBlocked;
import com.opencrm.common.event.dto.AmendmentApproved;
import com.opencrm.common.event.dto.AmendmentDrafted;
import com.opencrm.common.event.dto.AmendmentExecuted;
import com.opencrm.common.event.dto.AmendmentSubmitted;
import com.opencrm.common.event.dto.AmendmentUnitApplied;
import com.opencrm.common.event.dto.AmendmentWithdrawn;

import java.util.List;
import java.util.Objects;

/**
 * RESERVED schemas for the eight S6 amendment lifecycle events.
 *
 * <p>An entry here plus its DTO plus its contract test is what "registered" means for AC-S6-4.
 * Nothing in this catalog is published, and none of these types are in {@link EventContractRegistry}.
 * There is no Kafka topic and no producer.
 *
 * <p>S3 document statuses OUT_FOR_SIGNATURE, REJECTED and VOID have no event in this list. The field
 * lists are a draft for CPQ Decision to score against the S3 names.
 */
public final class AmendmentLifecycleEventCatalog {

    private AmendmentLifecycleEventCatalog() {
    }

    /** Whether a lifecycle contract has a producer. This catalog has none. */
    public enum Publication {
        RESERVED,
        PUBLISHED
    }

    /**
     * One reserved lifecycle contract. No topic: these events are not on the bus.
     *
     * @param documentStatus the S3 {@code AmendmentDocumentStatus} name this event reports
     */
    public record Entry(String eventType, int eventVersion, Class<?> payloadClass, String documentStatus,
                        Publication publication) {

        public Entry {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(payloadClass, "payloadClass");
            Objects.requireNonNull(documentStatus, "documentStatus");
            Objects.requireNonNull(publication, "publication");
            if (eventVersion < 1) {
                throw new IllegalArgumentException("eventVersion must be at least 1");
            }
        }
    }

    public static final Entry AMENDMENT_DRAFTED =
            reserved("AmendmentDrafted", AmendmentDrafted.class, "DRAFT");
    public static final Entry AMENDMENT_SUBMITTED =
            reserved("AmendmentSubmitted", AmendmentSubmitted.class, "IN_REVIEW");
    public static final Entry AMENDMENT_APPROVED =
            reserved("AmendmentApproved", AmendmentApproved.class, "APPROVED");
    public static final Entry AMENDMENT_EXECUTED =
            reserved("AmendmentExecuted", AmendmentExecuted.class, "EXECUTED");
    public static final Entry AMENDMENT_UNIT_APPLIED =
            reserved("AmendmentUnitApplied", AmendmentUnitApplied.class, "PARTIALLY_APPLIED");
    public static final Entry AMENDMENT_APPLIED =
            reserved("AmendmentApplied", AmendmentApplied.class, "APPLIED");
    public static final Entry AMENDMENT_APPLY_BLOCKED =
            reserved("AmendmentApplyBlocked", AmendmentApplyBlocked.class, "APPLY_BLOCKED");
    public static final Entry AMENDMENT_WITHDRAWN =
            reserved("AmendmentWithdrawn", AmendmentWithdrawn.class, "WITHDRAWN");

    private static final List<Entry> ALL = List.of(
            AMENDMENT_DRAFTED,
            AMENDMENT_SUBMITTED,
            AMENDMENT_APPROVED,
            AMENDMENT_EXECUTED,
            AMENDMENT_UNIT_APPLIED,
            AMENDMENT_APPLIED,
            AMENDMENT_APPLY_BLOCKED,
            AMENDMENT_WITHDRAWN);

    /** The eight reserved lifecycle contracts, in AC-S6-4 order. */
    public static List<Entry> all() {
        return ALL;
    }

    private static Entry reserved(String eventType, Class<?> payloadClass, String documentStatus) {
        return new Entry(eventType, 1, payloadClass, documentStatus, Publication.RESERVED);
    }
}
